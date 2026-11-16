package com.ex2widgets.widgets;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.ex2widgets.LogStore;
import com.ex2widgets.NotificationHelper;
import com.ex2widgets.Prefs;
import com.ex2widgets.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Плавающие виджеты для лончеров без AppWidgetHost — на штатном экране
 * Flyme Auto это основной способ показать виджет. Окна используют ту же
 * разметку, те же идентификаторы и состояния, что и RemoteViews-виджеты,
 * поэтому панель климата и ряд движения выглядят как рабочий стол приложения.
 */
public final class OverlayWidgetService extends Service {

    private static final long POLL_MS = 1000L;
    /** Спрятанные панели: возврат на рабочий стол ждём реже, чем обновляем видимые. */
    private static final long HIDDEN_POLL_MS = 3000L;
    private static final String ACTION_RESTART = "com.ex2widgets.overlay.RESTART";

    /** Тип события «экран вышел на передний план» для прежнего уровня API. */
    private static final int RESUMED_EVENT = 1;
    /**
     * Окно событий при первом опросе: за ним экран, который открыли до запуска
     * службы, уже не виден, а шире — только лишний чтение журнала.
     */
    private static final long USAGE_FIRST_WINDOW_MS = 300_000L;
    /** Раз в десять секунд переспрашиваем, какой пакет теперь рабочий стол. */
    private static final long LAUNCHER_TTL_MS = 10_000L;

    /**
     * Макет EX2 HUB снимался с экрана 1768×1080, поэтому размеры и места в
     * каталоге — пиксели макета, а не пикселя конкретного дисплея. Перенос
     * идёт одним равномерным масштабом: на самом ГУ он равен единице, на
     * телефоне панели остаются в границах экрана, а не уезжают за край.
     */
    private static final float DESIGN_WIDTH = 1768f;
    private static final float DESIGN_HEIGHT = 1080f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<OverlaySpec> specs = new ArrayList<>();
    public static volatile boolean running;
    private WindowManager windowManager;
    private volatile boolean destroyed;
    private int lastThemeSignature = Integer.MIN_VALUE;

    /** Класс переднего экрана — нужен, чтобы отличить список приложений от рабочего стола. */
    private String lastTopClass = "";
    /** До какого момента события использования уже прочитаны. */
    private long usageSince;
    private String launcherCache = "";
    private long launcherAt;
    /** Ошибку доступа к использованию пишем один раз: опрос идёт каждую секунду. */
    private boolean usageFailureLogged;
    /** То же про список задач: без доступа к использованию он спрашивается часто. */
    private boolean taskFailureLogged;

    // Снимки определения переднего экрана: их показывает экран настроек,
    // поэтому поля статические — MainActivity читает их без экземпляра службы.
    private static volatile String lastTopPackage = "";
    private static volatile String lastTopSource = "";
    private static volatile String lastHomePackage = "";
    /** Доступен ли доступ к использованию: без него панели прятать нечем. */
    private static volatile boolean usageAvailable;
    /** Почему панели спрятаны сейчас; пусто — прятать нечего или причина неизвестна. */
    private static volatile String lastHideReason = "";

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            WidgetModes.refreshAuto(OverlayWidgetService.this);
            if (!Settings.canDrawOverlays(OverlayWidgetService.this)) {
                stopSelf();
                return;
            }
            int themeSignature = themeSignature();
            if (lastThemeSignature != Integer.MIN_VALUE
                    && lastThemeSignature != themeSignature) {
                lastThemeSignature = themeSignature;
                resetUsage();
                for (OverlaySpec spec : specs) {
                    remove(spec);
                }
                handler.postDelayed(this, 100L);
                return;
            }
            lastThemeSignature = themeSignature;
            ForegroundState state = foregroundState();
            boolean anyEnabled = false;
            boolean anyShown = false;
            for (OverlaySpec spec : specs) {
                boolean enabled = Prefs.widgetEnabled(OverlayWidgetService.this, spec.key);
                anyEnabled |= enabled;
                // Закреплённая панель не зависит от того, что сейчас открыто.
                boolean pinned = Prefs.overlayPinned(OverlayWidgetService.this, spec.key);
                boolean show = enabled && (pinned || state.onHome);
                if (show) {
                    ensure(spec);
                    update(spec);
                    anyShown = true;
                } else {
                    remove(spec);
                }
            }
            if (!anyEnabled) {
                stopSelf();
                return;
            }
            // Пока открыто чужое приложение, панели не нужны вовсе: опрос
            // продолжается реже, и при возврате на рабочий стол они появятся
            // через несколько секунд.
            handler.postDelayed(this, anyShown ? POLL_MS : HIDDEN_POLL_MS);
        }
    };

    public static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, OverlayWidgetService.class));
        } catch (Throwable t) {
            LogStore.add(c, "overlay service start failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, OverlayWidgetService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void restart(Context c) {
        if (!Settings.canDrawOverlays(c) || !hasEnabled(c)) {
            stop(c);
            return;
        }
        try {
            c.startForegroundService(
                    new Intent(c, OverlayWidgetService.class).setAction(ACTION_RESTART));
        } catch (Throwable t) {
            LogStore.add(c, "overlay service restart failed: " + t);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static void sync(Context c) {
        WidgetModes.refreshAuto(c);
        boolean allowed = Settings.canDrawOverlays(c);
        boolean enabled = hasEnabled(c);
        LogStore.add(c, "overlay sync: allowed=" + allowed
                + " enabled=" + enabled + " running=" + running);
        if (!allowed || !enabled) {
            stop(c);
        } else {
            start(c);
        }
    }

    public static boolean hasEnabled(Context c) {
        for (String key : WidgetsCommon.widgetKeys()) {
            if (Prefs.widgetEnabled(c, key)) return true;
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(NotificationHelper.ID_OVERLAY, NotificationHelper.overlay(this));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LogStore.add(this, "overlay service created");
        for (String key : WidgetsCommon.widgetKeys()) {
            specs.add(new OverlaySpec(key));
        }
        handler.post(poll);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            resetUsage();
            for (OverlaySpec spec : specs) {
                remove(spec);
            }
            handler.removeCallbacks(poll);
            handler.post(poll);
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (destroyed) return;
        resetUsage();
        for (OverlaySpec spec : specs) {
            remove(spec);
        }
        lastThemeSignature = Integer.MIN_VALUE;
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        LogStore.add(this, "overlay service destroyed");
        handler.removeCallbacksAndMessages(null);
        for (OverlaySpec spec : specs) {
            remove(spec);
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensure(OverlaySpec spec) {
        if (spec.view != null || windowManager == null) return;
        try {
            int size = Prefs.widgetSizePct(this, spec.key);
            float factor = designFactor() * size / 100f;
            // Плотность scaled-контекста равна factor, поэтому каждый dp и sp
            // разметки занимает ровно factor пикселей экрана: панель выглядит
            // как макет, а не как макет, обрезанный по размеру окна.
            Context host = scaledContext(factor);
            View content = OverlayWidgetViews.create(host, spec.key);
            FrameLayout container = new FrameLayout(this);
            container.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
            int width = Math.min(Math.max(1, Math.round(spec.widthDp * factor)),
                    screen.widthPixels);
            int height = Math.min(Math.max(1, Math.round(spec.heightDp * factor)),
                    screen.heightPixels);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    width, height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = place(Prefs.overlayX(this, spec.key), spec.defaultX, factor, width,
                    screen.widthPixels);
            lp.y = place(Prefs.overlayY(this, spec.key), spec.defaultY, factor, height,
                    screen.heightPixels);

            bindDrag(spec, container, content, lp);
            windowManager.addView(container, lp);
            spec.host = host;
            spec.view = container;
            spec.content = content;
            spec.params = lp;
            LogStore.add(this, "overlay added " + spec.key + " " + width + "x" + height
                    + " at " + lp.x + "," + lp.y + " factor=" + factor);
        } catch (Throwable t) {
            LogStore.add(this, "overlay add " + spec.key + " failed: " + t);
            // Разрешение могли отозвать, пока служба работала: повторять бессмысленно.
            if (!Settings.canDrawOverlays(this)) stopSelf();
        }
    }

    private void update(OverlaySpec spec) {
        if (spec.view == null || spec.content == null) return;
        try {
            Context host = spec.host != null ? spec.host : this;
            OverlayWidgetViews.update(host, spec.content, spec.key);
        } catch (Throwable t) {
            LogStore.add(this, "overlay update " + spec.key + " failed: " + t);
        }
    }

    private void remove(OverlaySpec spec) {
        if (spec.view == null) return;
        try {
            windowManager.removeView(spec.view);
        } catch (Throwable ignored) {
        }
        spec.host = null;
        spec.view = null;
        spec.content = null;
        spec.params = null;
    }

    /** Макет 1768×1080 целиком, с сохранением пропорций, на текущем экране. */
    private float designFactor() {
        android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
        float width = screen.widthPixels > 0 ? screen.widthPixels : DESIGN_WIDTH;
        float height = screen.heightPixels > 0 ? screen.heightPixels : DESIGN_HEIGHT;
        return Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
    }

    /**
     * Контекст с плотностью factor: разметка виджета пересчитывает в пиксели
     * все dp и sp через неё, поэтому окно и содержимое масштабируются вместе.
     */
    private Context scaledContext(float factor) {
        Configuration config = new Configuration(getResources().getConfiguration());
        config.densityDpi = Math.max(1, Math.round(160f * factor));
        // Крупный системный шрифт не должен ломать сетку макета: панель и так
        // масштабируется ползунком размера.
        config.fontScale = 1f;
        try {
            return createConfigurationContext(config);
        } catch (Throwable t) {
            LogStore.add(this, "overlay scaled context failed: " + t);
            return this;
        }
    }

    /** Место панели: сохранённое перетаскиванием или макетное, но всегда в границах экрана. */
    private int place(int stored, int defaultDesignPx, float factor, int sizePx, int limitPx) {
        int value = stored == Prefs.POSITION_UNSET
                ? Math.round(defaultDesignPx * factor) : stored;
        return Math.max(0, Math.min(value, Math.max(0, limitPx - sizePx)));
    }

    private void bindDrag(OverlaySpec spec, FrameLayout container, View content,
                          WindowManager.LayoutParams lp) {
        View drag = content.findViewById(R.id.widget_bg_layer);
        if (drag == null || drag.getVisibility() != View.VISIBLE) drag = content;
        View.OnTouchListener listener = new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved) {
                            lp.x = startX + Math.round(dx);
                            lp.y = startY + Math.round(dy);
                            try {
                                windowManager.updateViewLayout(container, lp);
                            } catch (Throwable ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (moved) {
                            Prefs.setOverlayPos(OverlayWidgetService.this, spec.key, lp.x, lp.y);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
        drag.setOnTouchListener(listener);
    }

    private int themeSignature() {
        Configuration config = getResources().getConfiguration();
        int night = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int orientation = config.orientation;
        return night * 10 + orientation;
    }

    /**
     * Что сейчас у пользователя перед глазами. Единственный честный источник —
     * события использования: ActivityManager.getRunningTasks на Android 8+
     * отдаёт урезанный список, и на ГУ в нём весь день остаётся рабочий стол,
     * даже когда открыто системное приложение (из-за него панели и висели
     * поверх чужих экранов).
     */
    private ForegroundState foregroundState() {
        readUsageEvents();
        // Доступа к журналу нет или событий с запуска не было: начнём хотя бы
        // со списка задач — он может врать, но пустота врёт вернее.
        if (!usageAvailable || lastTopPackage.isEmpty()) readTasksTop();
        String pkg = lastTopPackage;
        if (pkg.isEmpty()) {
            // Ни событий, ни задач: оставляем панели видимыми, но причину видно
            // в настройках, а «Всегда показывать» работает без этой проверки.
            lastTopSource = "";
            lastHideReason = "";
            return new ForegroundState(true);
        }
        // Наши экраны (настройки) панелью перекрывать нельзя.
        if (pkg.equals(getPackageName())) {
            lastHideReason = getString(R.string.overlay_hide_ours);
            return new ForegroundState(false);
        }
        boolean appList = WidgetModes.STOCK_LAUNCHER.equals(pkg)
                && lastTopClass.contains("AppListActivity");
        boolean onHome = !appList && isHome(pkg, launcherPackage());
        lastHideReason = onHome ? "" : getString(appList
                ? R.string.overlay_hide_applist : R.string.overlay_hide_app, appName(pkg));
        return new ForegroundState(onHome);
    }

    /**
     * События использования с прошлого опроса. Водяной знак сдвигаем до самого
     * запроса, а не до последней записи: иначе на неподвижном экране окно
     * растёт, и каждый опрос читает всё больше событий.
     */
    private boolean readUsageEvents() {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return false;
            long now = System.currentTimeMillis();
            long since = usageSince == 0L ? now - USAGE_FIRST_WINDOW_MS : usageSince;
            usageSince = now;
            android.app.usage.UsageEvents events = usm.queryEvents(since, now);
            usageAvailable = true;
            if (events == null) return false;
            boolean read = false;
            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                read = true;
                observe(event);
            }
            if (!lastTopPackage.isEmpty()) lastTopSource = "usage";
            return read;
        } catch (Throwable t) {
            usageAvailable = false;
            if (!usageFailureLogged) {
                usageFailureLogged = true;
                LogStore.add(this, "overlay usage probe failed: " + t);
            }
            return false;
        }
    }

    /** Из события использования важен только выход экрана на передний план. */
    private void observe(android.app.usage.UsageEvents.Event event) {
        if (event.getEventType() != RESUMED_EVENT) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName();
        if (pkg.isEmpty()) return;
        lastTopPackage = pkg;
        lastTopClass = event.getClassName() == null ? "" : event.getClassName();
    }

    /** Запасной источник переднего экрана: список задач. */
    private boolean readTasksTop() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return false;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0) == null) return false;
            ComponentName top = tasks.get(0).topActivity;
            if (top == null || top.getPackageName() == null
                    || top.getPackageName().isEmpty()) return false;
            lastTopPackage = top.getPackageName();
            lastTopClass = top.getClassName() == null ? "" : top.getClassName();
            lastTopSource = "tasks";
            return true;
        } catch (Throwable t) {
            if (!taskFailureLogged) {
                taskFailureLogged = true;
                LogStore.add(this, "overlay task probe failed: " + t);
            }
            return false;
        }
    }

    /** Новый экран или перезапуск: прежнее решение и прежнее окно событий ни при чём. */
    private void resetUsage() {
        usageSince = 0L;
        lastTopPackage = "";
        lastTopClass = "";
        lastTopSource = "";
        usageAvailable = false;
        lastHideReason = "";
    }

    /** Как назвать передний экран в строке состояния: имя приложения, а не пакет. */
    private String appName(String pkg) {
        try {
            android.content.pm.ApplicationInfo info =
                    getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString();
        } catch (Throwable ignored) {
        }
        return pkg;
    }

    /** Дом — это HOME; если он не разрешился, узнаём его по типовым именам пакетов. */
    private boolean isHome(String pkg, String home) {
        if (!home.isEmpty()) return home.equals(pkg);
        return pkg.equals(WidgetModes.STOCK_LAUNCHER) || pkg.endsWith(".launcher")
                || pkg.equals("com.miui.home");
    }

    /** HOME разрешился один раз и протухает по времени, а не на каждый тик. */
    private String launcherPackage() {
        long now = System.currentTimeMillis();
        if (launcherCache.isEmpty() || now - launcherAt > LAUNCHER_TTL_MS) {
            launcherAt = now;
            launcherCache = WidgetModes.launcherPackage(this);
            lastHomePackage = launcherCache;
        }
        return launcherCache;
    }

    /** Пакет, который служба считает передним экраном; пусто — не определён. */
    public static String foregroundPackage() {
        return lastTopPackage;
    }

    /** Разрешённый HOME; пусто — рабочий стол не опознан. */
    public static String homePackage() {
        return lastHomePackage;
    }

    /** "usage", "tasks" или "" — чем сейчас определён передний экран. */
    public static String detectionSource() {
        return lastTopSource;
    }

    /** Почему панели сейчас спрятаны; пусто — на рабочем столе или определение молчит. */
    public static String hideReason() {
        return lastHideReason;
    }

    /**
     * Выдан ли доступ «Использование». Без него журнал отдаёт пусто, а пустой
     * журнал неотличим от того, что экран не менялся, поэтому спрашиваем appop
     * напрямую: имя операции — публичная строка, кода из hidden-API не нужно.
     */
    @SuppressWarnings("deprecation")
    public static boolean usageAccessGranted(Context c) {
        try {
            android.app.AppOpsManager aom = (android.app.AppOpsManager)
                    c.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            return aom.checkOpNoThrow("android:get_usage_stats",
                    android.os.Process.myUid(), c.getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            LogStore.add(c, "overlay usage access check failed: " + t);
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * density());
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private static final class ForegroundState {
        final boolean onHome;

        ForegroundState(boolean onHome) {
            this.onHome = onHome;
        }
    }

    /**
     * Место наложения на экране 1768x1080 — ровно те координаты и размеры,
     * какие панель занимает на рабочем столе EX2 HUB (WidgetCatalog).
     */
    private static final class OverlaySpec {
        final String key;
        final int widthDp;
        final int heightDp;
        final int defaultX;
        final int defaultY;
        Context host;
        View view;
        View content;
        WindowManager.LayoutParams params;

        OverlaySpec(String key) {
            WidgetCatalog.Spec spec = WidgetCatalog.of(key);
            this.key = key;
            this.widthDp = spec.widthDp;
            this.heightDp = spec.heightDp;
            this.defaultX = spec.defaultX;
            this.defaultY = spec.defaultY;
        }
    }
}
