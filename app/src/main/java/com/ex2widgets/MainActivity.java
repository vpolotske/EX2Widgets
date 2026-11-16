package com.ex2widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
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
    private TextView globalAlphaValue;
    private SeekBar globalAlphaSeek;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);

        modeStatus = findViewById(R.id.modeStatus);
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
        findViewById(R.id.overlayPermission).setOnClickListener(v -> openOverlayPermission());

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
        refresh();
    }

    private void refresh() {
        refreshMode();
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
        boolean permitted = Settings.canDrawOverlays(this);
        StringBuilder text = new StringBuilder();
        if (!Prefs.widgetModeManual(this)) {
            text.append(getString(R.string.status_mode_auto, launcher));
        } else if (mode == Prefs.WIDGET_MODE_STOCK) {
            text.append(getString(R.string.status_mode_overlay));
        } else {
            text.append(getString(R.string.status_mode_widget, launcher));
        }
        if (mode == Prefs.WIDGET_MODE_STOCK && !permitted) {
            text.append("\n").append(getString(R.string.status_no_permission));
        }
        modeStatus.setText(text.toString());
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

    private void openOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            LogStore.add(this, "overlay permission screen unavailable: " + t);
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
            } else if (OverlayWidgetService.isRunning()) {
                overlayPart = getString(R.string.overlay_state_on);
            } else {
                overlayPart = getString(R.string.status_overlay_wait);
            }
            return getString(R.string.status_widget_line, widgetPart, overlayPart);
        }

        private void setShow(boolean enabled) {
            Prefs.setWidgetEnabled(MainActivity.this, spec.key, enabled);
            OverlayWidgetService.sync(MainActivity.this);
            refresh();
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
            Prefs.setOverlayPos(MainActivity.this, spec.key, spec.defaultX, spec.defaultY);
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
