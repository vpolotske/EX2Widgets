package com.ex2widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ex2widgets.widgets.OverlayWidgetService;
import com.ex2widgets.widgets.WidgetCatalog;
import com.ex2widgets.widgets.WidgetModes;
import com.ex2widgets.widgets.WidgetUpdateService;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран настроек: способ показа, общая прозрачность и свой блок на каждый
 * виджет. Положение панелей меняется перетаскиванием прямо на рабочем столе,
 * здесь его можно вернуть к макету EX2 HUB или закрепить от случайного сдвига.
 */
public final class MainActivity extends Activity {

    /** Ползунок размера: 50…200 % при ходе 0…150. */
    private static final int SIZE_MIN = 50;
    private static final int SIZE_RANGE = 150;

    private final List<Row> rows = new ArrayList<>();

    private TextView modeStatus;
    private TextView permissionStatus;
    private TextView permissionHint;
    private TextView globalAlphaValue;
    private SeekBar globalAlphaSeek;
    /** Экран разрешений, который реально открылся, — им же и отвечаем на «не открылся». */
    private String permissionScreen;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);

        modeStatus = findViewById(R.id.modeStatus);
        permissionStatus = findViewById(R.id.permissionStatus);
        permissionHint = findViewById(R.id.permissionHint);
        globalAlphaValue = findViewById(R.id.globalAlphaValue);
        globalAlphaSeek = findViewById(R.id.globalAlphaSeek);

        findViewById(R.id.modeAuto).setOnClickListener(v -> {
            WidgetModes.followAuto(this);
            refresh();
        });
        findViewById(R.id.modeOverlay).setOnClickListener(v -> {
            WidgetModes.set(this, Prefs.WIDGET_MODE_STOCK);
            refresh();
        });
        findViewById(R.id.modeWidget).setOnClickListener(v -> {
            WidgetModes.set(this, Prefs.WIDGET_MODE_THIRD_PARTY);
            refresh();
        });
        findViewById(R.id.overlayPermission).setOnClickListener(v -> requestOverlayPermission());

        globalAlphaSeek.setMax(100);
        globalAlphaSeek.setOnSeekBarChangeListener(new SeekCommit() {
            @Override
            void onValue(int percent) {
                globalAlphaValue.setText(getString(R.string.fmt_percent, percent));
            }

            @Override
            void onCommit(int percent) {
                Prefs.setWidgetBgOpacity(MainActivity.this, percent);
                repaint();
            }
        });

        LinearLayout container = findViewById(R.id.widgetsContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (WidgetCatalog.Spec spec : WidgetCatalog.all()) {
            View view = inflater.inflate(R.layout.item_widget, container, false);
            Row row = new Row(spec, view);
            rows.add(row);
            container.addView(view);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WidgetModes.current(this);
        // Разрешение могли выдать, пока этот экран был сзади — служба об этом
        // не узнает сама, пусть проверит сразу.
        OverlayWidgetService.sync(this);
        refresh();
    }

    private void refresh() {
        refreshMode();
        refreshPermission();
        int global = Prefs.widgetBgOpacity(this);
        globalAlphaSeek.setOnSeekBarChangeListener(null);
        globalAlphaSeek.setProgress(global);
        globalAlphaSeek.setOnSeekBarChangeListener(new SeekCommit() {
            @Override
            void onValue(int percent) {
                globalAlphaValue.setText(getString(R.string.fmt_percent, percent));
            }

            @Override
            void onCommit(int percent) {
                Prefs.setWidgetBgOpacity(MainActivity.this, percent);
                repaint();
            }
        });
        globalAlphaValue.setText(getString(R.string.fmt_percent, global));
        for (Row row : rows) row.refresh();
    }

    private void refreshMode() {
        int mode = Prefs.widgetMode(this);
        String launcher = WidgetModes.launcherName(this);
        StringBuilder text = new StringBuilder();
        if (!Prefs.widgetModeManual(this)) {
            text.append(getString(R.string.status_mode_auto, launcher));
        } else if (mode == Prefs.WIDGET_MODE_STOCK) {
            text.append(getString(R.string.status_mode_overlay));
        } else {
            text.append(getString(R.string.status_mode_widget, launcher));
        }
        modeStatus.setText(text.toString());
    }

    /**
     * Отдельная строка про наложение: ползунок «Поверх экрана» работает в любом
     * режиме, поэтому и про разрешение спрашиваем, когда хоть одна панель
     * включена, а не только в режиме «Поверх экрана».
     */
    private void refreshPermission() {
        boolean permitted = Settings.canDrawOverlays(this);
        boolean wanted = OverlayWidgetService.hasEnabled(this);
        if (permitted) {
            permissionStatus.setText(R.string.status_perm_overlay_granted);
            permissionHint.setVisibility(View.GONE);
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.status_perm_overlay_missing));
        if (wanted) text.append(" ").append(getString(R.string.status_no_permission));
        if (permissionScreen != null) {
            text.append("\n").append(getString(R.string.status_perm_opened, permissionScreen));
        }
        permissionStatus.setText(text.toString());
        permissionHint.setVisibility(View.VISIBLE);
        permissionHint.setText(getString(R.string.settings_perm_manual, getPackageName()));
    }

    /** Прозрачность и размер применяются к обеим дорогам показа сразу. */
    private void repaint() {
        if (anyPlaced()) WidgetUpdateService.refreshOpacity(this);
        OverlayWidgetService.restart(this);
    }

    private boolean anyPlaced() {
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        for (WidgetCatalog.Spec spec : WidgetCatalog.all()) {
            if (placed(awm, spec) > 0) return true;
        }
        return false;
    }

    private int placed(AppWidgetManager awm, WidgetCatalog.Spec spec) {
        try {
            int[] ids = awm.getAppWidgetIds(new ComponentName(this, spec.provider));
            return ids == null ? 0 : ids.length;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Экран выдачи наложения ищется среди нескольких путей: Flyme Auto и MIUI
     * отвечают по-разному, а молча провалившийся запрос выглядит для
     * пользователя как «кнопка не работает».
     */
    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.status_perm_overlay_granted, Toast.LENGTH_SHORT).show();
            OverlayWidgetService.sync(this);
            refresh();
            return;
        }
        String pkg = "package:" + getPackageName();
        Intent[] intents = {
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg)),
                miuiPermissionEditor("com.miui.permcenter.permissions.PermissionsEditorActivity"),
                miuiPermissionEditor("com.miui.permcenter.permissions.AppPermissionsEditorActivity"),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(pkg)),
        };
        String[] labels = {
                getString(R.string.perm_screen_overlay),
                getString(R.string.perm_screen_miui),
                getString(R.string.perm_screen_miui),
                getString(R.string.perm_screen_overlay_list),
                getString(R.string.perm_screen_details),
        };
        for (int i = 0; i < intents.length; i++) {
            if (intents[i] == null || !resolvable(intents[i])) continue;
            try {
                startActivity(intents[i]);
                permissionScreen = labels[i];
                LogStore.add(this, "overlay permission screen opened: " + labels[i]);
                refresh();
                return;
            } catch (Throwable t) {
                LogStore.add(this, "overlay permission screen failed: " + labels[i] + " " + t);
            }
        }
        permissionScreen = null;
        LogStore.add(this, "no overlay permission screen available");
        Toast.makeText(this, R.string.status_perm_no_screen, Toast.LENGTH_LONG).show();
        refresh();
    }

    /** Разрешение на наложение в MIUI живёт в своём редакторе прав приложения. */
    private Intent miuiPermissionEditor(String activity) {
        try {
            return new Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", activity)
                    .putExtra("extra_pkgname", getPackageName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean resolvable(Intent intent) {
        try {
            return getPackageManager().resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Блок одного виджета: показ наложением, размер, прозрачность, положение. */
    private final class Row {

        private final WidgetCatalog.Spec spec;
        private final TextView name;
        private final TextView status;
        private final Switch show;
        private final TextView sizeValue;
        private final SeekBar sizeSeek;
        private final TextView alphaValue;
        private final SeekBar alphaSeek;
        private final Button reset;
        private final Button pin;

        Row(WidgetCatalog.Spec spec, View view) {
            this.spec = spec;
            name = view.findViewById(R.id.widgetName);
            status = view.findViewById(R.id.widgetStatus);
            show = view.findViewById(R.id.widgetShow);
            sizeValue = view.findViewById(R.id.widgetSizeValue);
            sizeSeek = view.findViewById(R.id.widgetSizeSeek);
            alphaValue = view.findViewById(R.id.widgetAlphaValue);
            alphaSeek = view.findViewById(R.id.widgetAlphaSeek);
            reset = view.findViewById(R.id.widgetResetPosition);
            pin = view.findViewById(R.id.widgetPin);
            name.setText(spec.nameRes);
            sizeSeek.setMax(SIZE_RANGE);
            alphaSeek.setMax(100);
            reset.setOnClickListener(v -> resetPosition());
            pin.setOnClickListener(v -> togglePin());
        }

        void refresh() {
            boolean enabled = Prefs.widgetEnabled(MainActivity.this, spec.key);
            int size = Prefs.widgetSizePct(MainActivity.this, spec.key);
            int alpha = Prefs.widgetAlphaPct(MainActivity.this, spec.key);

            show.setOnClickListener(null);
            show.setChecked(enabled);
            show.setOnClickListener(v -> setShow(show.isChecked()));

            sizeSeek.setOnSeekBarChangeListener(null);
            sizeSeek.setProgress(size - SIZE_MIN);
            sizeSeek.setOnSeekBarChangeListener(new SeekCommit() {
                @Override
                void onValue(int percent) {
                    sizeValue.setText(getString(R.string.fmt_percent, percent + SIZE_MIN));
                }

                @Override
                void onCommit(int percent) {
                    setSize(percent + SIZE_MIN);
                }
            });
            sizeValue.setText(getString(R.string.fmt_percent, size));

            alphaSeek.setOnSeekBarChangeListener(null);
            alphaSeek.setProgress(alpha);
            alphaSeek.setOnSeekBarChangeListener(new SeekCommit() {
                @Override
                void onValue(int percent) {
                    alphaValue.setText(getString(R.string.fmt_percent, percent));
                }

                @Override
                void onCommit(int percent) {
                    setAlpha(percent);
                }
            });
            alphaValue.setText(getString(R.string.fmt_percent, alpha));

            pin.setText(Prefs.overlayPinned(MainActivity.this, spec.key)
                    ? R.string.settings_unpin : R.string.settings_pin);
            status.setText(statusLine(enabled));
        }

        private String statusLine(boolean enabled) {
            int count = placed(AppWidgetManager.getInstance(MainActivity.this), spec);
            String widgetPart = count == 0
                    ? getString(R.string.status_widget_none)
                    : getString(R.string.status_widget_count, count);
            String overlayPart;
            if (!enabled) {
                overlayPart = getString(R.string.overlay_state_off);
            } else if (!Settings.canDrawOverlays(MainActivity.this)) {
                overlayPart = getString(R.string.overlay_state_no_permission);
            } else if (!OverlayWidgetService.isRunning()) {
                overlayPart = getString(R.string.overlay_state_starting);
            } else if (Prefs.overlayPinned(MainActivity.this, spec.key)) {
                overlayPart = getString(R.string.overlay_state_pinned);
            } else {
                overlayPart = getString(R.string.overlay_state_on);
            }
            return getString(R.string.status_widget_line, widgetPart, overlayPart);
        }

        private void setShow(boolean enabled) {
            Prefs.setWidgetEnabled(MainActivity.this, spec.key, enabled);
            OverlayWidgetService.sync(MainActivity.this);
            refresh();
            if (enabled && !Settings.canDrawOverlays(MainActivity.this)) {
                requestOverlayPermission();
            }
        }

        private void setSize(int percent) {
            Prefs.setWidgetSizePct(MainActivity.this, spec.key, percent);
            OverlayWidgetService.restart(MainActivity.this);
        }

        private void setAlpha(int percent) {
            Prefs.setWidgetAlphaPct(MainActivity.this, spec.key, percent);
            repaint();
        }

        private void resetPosition() {
            Prefs.clearOverlayPos(MainActivity.this, spec.key);
            OverlayWidgetService.restart(MainActivity.this);
        }

        private void togglePin() {
            boolean pinned = !Prefs.overlayPinned(MainActivity.this, spec.key);
            Prefs.setOverlayPinned(MainActivity.this, spec.key, pinned);
            pin.setText(pinned ? R.string.settings_unpin : R.string.settings_pin);
        }
    }

    /** Ползунок пишет значение на строке сразу, а применяет его по отпусканию. */
    private abstract static class SeekCommit implements SeekBar.OnSeekBarChangeListener {

        abstract void onValue(int percent);

        abstract void onCommit(int percent);

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            onValue(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            onCommit(seekBar.getProgress());
        }
    }
}
