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
import android.text.TextUtils;
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
    private static final String ACTION_RESTART = "com.ex2widgets.overlay.RESTART";

    /** Тип события «экран вышел на передний план» для прежнего уровня API. */
    private static final int RESUMED_EVENT = 1;
    /** Раз в десять секунд переспрашиваем, какой пакет теперь рабочий стол. */
    private static final long LAUNCHER_TTL_MS = 10_000L;
    /** Сколько последних выходов экранов показывать в настройках. */
    private static final int RESUME_LOG_SIZE = 5;

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
    /** Время выхода экрана — только для человекочитаемого журнала в настройках. */
    private static final java.text.SimpleDateFormat RESUME_TIME =
            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
    private final List<String> resumeLog = new ArrayList<>();
    public static volatile boolean running;
    private WindowManager windowManager;
    private volatile boolean destroyed;
    private int lastThemeSignature = Integer.MIN_VALUE;

    /** Класс переднего экрана — нужен, чтобы отличить список приложений от рабочего стола. */
    private String lastTopClass = "";
    /** До какого момента события использования уже прочитаны. */
    private long usageSince;
    /** Последнее обработанное событие UsageEvents: защищает от повторов при перекрытии окна. */
    private long lastUsageEventTime;
    /** Начальное окно истории нужно, чтобы узнать экран, открытый до запуска службы. */
    private static final long USAGE_INITIAL_LOOKBACK_MS = 30L * 60L * 1000L;
    /** Небольшое перекрытие запросов компенсирует задержку публикации событий прошивкой. */
    private static final long USAGE_QUERY_OVERLAP_MS = 2_000L;
    /** Все HOME-пакеты разом: победитель resolveActivity не всегда тот, что на экране. */
    private java.util.Set<String> homeCache = java.util.Collections.emptySet();
    private long homeAt;
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
    /** Последние выходы экранов со временем — по ним видно, что вранья нет. */
    private static volatile String lastResumes = "";

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
            for (OverlaySpec spec : specs) {
                boolean enabled = Prefs.widgetEnabled(OverlayWidgetService.this, spec.key);
                anyEnabled |= enabled;
                // Закреплённая панель не зависит от того, что сейчас открыто.
                boolean pinned = Prefs.overlayPinned(OverlayWidgetService.this, spec.key);
                boolean show = enabled && (pinned || state.onHome);
                if (show) {
                    ensure(spec);
                    update(spec);
                } else {
                    remove(spec);
                }
            }
            if (!anyEnabled) {
                stopSelf();
                return;
            }
            // Один темп и в видимом, и в спрятанном состоянии: возврат на
            // рабочий стол должен отрабатывать за ту же секунду, что и уход.
            handler.postDelayed(this, POLL_MS);
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
        // Закреплённые панели не слушают перетаскивание вовсе: слушатель,
        // который ничего не делает, только съел бы касания карточки.
        if (Prefs.overlayLocked(this)) return;
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
     * Что сейчас у пользователя перед глазами. В режиме скрытия панели
     * показываются только при подтверждённом HOME; неизвестное состояние
     * обрабатывается безопасно — панели скрываются до определения экрана.
     */
    private ForegroundState foregroundState() {
        // Заполняем HOME-кэш независимо от того, какой пакет сейчас активен:
        // иначе при открытых настройках собственное приложение возвращается
        // раньше isHome(), и диагностика ошибочно показывает рабочий стол «?».
        homePackages();
        readUsageEvents();
        // Если UsageEvents ещё не дал ни одного актуального события, каждый
        // цикл обновляем резервное определение через список задач. Не сохраняем
        // пакет, прочитанный один раз при запуске службы.
        if (!"usage".equals(lastTopSource)) readTasksTop();
        String pkg = lastTopPackage;
        if (pkg.isEmpty()) {
            // Fail closed: при включённом скрытии неизвестный экран не должен
            // приводить к показу плавающих окон поверх произвольного приложения.
            lastTopSource = "";
            lastHideReason = getString(R.string.overlay_hide_unknown);
            return new ForegroundState(!Prefs.hideBehindApps(this));
        }
        // Наши экраны (настройки) панелью перекрывать нельзя в любом режиме.
        if (pkg.equals(getPackageName())) {
            lastHideReason = getString(R.string.overlay_hide_ours);
            return new ForegroundState(false);
        }
        if (!Prefs.hideBehindApps(this)) {
            lastHideReason = "";
            return new ForegroundState(true);
        }
        // Список приложений принадлежит пакету рабочего стола, но экраном
        // человека он не является: панели закрыли бы половину значков.
        if (WidgetModes.STOCK_LAUNCHER.equals(pkg) && lastTopClass.contains("AppListActivity")) {
            lastHideReason = getString(R.string.overlay_hide_applist);
            return new ForegroundState(false);
        }
        if (isHome(pkg)) {
            lastHideReason = "";
            return new ForegroundState(true);
        }
        // Режим «прятать за приложениями» означает показывать панели только
        // на HOME. Системные поверхности и пакеты без launcher-значка тоже
        // не считаются рабочим столом.
        lastHideReason = getString(R.string.overlay_hide_app, appName(pkg));
        return new ForegroundState(false);
    }

    /**
     * События использования. При первом запросе читаем ограниченную историю,
     * чтобы восстановить экран, который был открыт до запуска службы. Затем
     * используем короткое перекрытие запросов и отсечение уже обработанных
     * временных меток, чтобы не терять события на границе интервалов.
     */
    private boolean readUsageEvents() {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return false;
            long now = System.currentTimeMillis();
            long since;
            if (usageSince == 0L) {
                // При старте службы текущая Activity могла быть открыта раньше.
                // Читаем ограниченную историю и выбираем последнее событие RESUMED.
                since = Math.max(0L, now - USAGE_INITIAL_LOOKBACK_MS);
            } else {
                // Перекрываем соседние запросы на 2 секунды, поскольку некоторые
                // автомобильные прошивки публикуют UsageEvents с задержкой.
                since = Math.max(0L, usageSince - USAGE_QUERY_OVERLAP_MS);
            }
            android.app.usage.UsageEvents events = usm.queryEvents(since, now);
            usageSince = now;
            usageAvailable = true;
            if (events == null) return false;
            boolean read = false;
            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getTimeStamp() < lastUsageEventTime) continue;
                lastUsageEventTime = event.getTimeStamp();
                if (event.getEventType() != RESUMED_EVENT) continue;
                observe(event);
                read = true;
            }
            // Источник меняется на usage только после фактически полученного
            // нового RESUMED-события, а не просто из-за непустого старого пакета.
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
        lastTopSource = "usage";
        rememberResume(event.getTimeStamp(), pkg, lastTopClass);
    }

    /** Последние выходы экранов: по ним настраивающий человек видит, что реально читает служба. */
    private void rememberResume(long at, String pkg, String cls) {
        String line = RESUME_TIME.format(new java.util.Date(at)) + " " + pkg
                + (cls.isEmpty() ? "" : "/" + cls);
        synchronized (resumeLog) {
            if (resumeLog.isEmpty() || !resumeLog.get(resumeLog.size() - 1).equals(line)) {
                resumeLog.add(line);
                while (resumeLog.size() > RESUME_LOG_SIZE) resumeLog.remove(0);
                StringBuilder text = new StringBuilder();
                for (String item : resumeLog) {
                    if (text.length() > 0) text.append("\n");
                    text.append(item);
                }
                lastResumes = text.toString();
            }
        }
    }

    /** Запасной источник переднего экрана: список задач. */
    private boolean readTasksTop() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return false;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0) == null) {
                clearFailedTaskSnapshot();
                return false;
            }
            ComponentName top = tasks.get(0).topActivity;
            if (top == null || top.getPackageName() == null
                    || top.getPackageName().isEmpty()) {
                clearFailedTaskSnapshot();
                return false;
            }
            lastTopPackage = top.getPackageName();
            lastTopClass = top.getClassName() == null ? "" : top.getClassName();
            lastTopSource = "tasks";
            return true;
        } catch (Throwable t) {
            clearFailedTaskSnapshot();
            if (!taskFailureLogged) {
                taskFailureLogged = true;
                LogStore.add(this, "overlay task probe failed: " + t);
            }
            return false;
        }
    }

    private void clearFailedTaskSnapshot() {
        if ("tasks".equals(lastTopSource)) {
            lastTopPackage = "";
            lastTopClass = "";
            lastTopSource = "";
        }
    }

    /** Новый экран или перезапуск: прежнее решение и прежнее окно событий ни при чём. */
    private void resetUsage() {
        usageSince = 0L;
        lastUsageEventTime = 0L;
        lastTopPackage = "";
        lastTopClass = "";
        lastTopSource = "";
        usageAvailable = false;
        lastHideReason = "";
        synchronized (resumeLog) {
            resumeLog.clear();
            lastResumes = "";
        }
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

    /** Дом — любой из HOME-пакетов; типовые имена страхуют, если PackageManager молчит. */
    private boolean isHome(String pkg) {
        return homePackages().contains(pkg) || pkg.endsWith(".launcher")
                || pkg.equals("com.miui.home");
    }

    /** Список HOME протухает по времени, а не на каждый тик опроса. */
    private java.util.Set<String> homePackages() {
        long now = System.currentTimeMillis();
        if (homeCache.isEmpty() || now - homeAt > LAUNCHER_TTL_MS) {
            homeAt = now;
            homeCache = WidgetModes.homePackages(this);
            lastHomePackage = TextUtils.join(", ", homeCache);
        }
        return homeCache;
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

    /** Последние выходы экранов, которые прочитала служба: по ним видно, чем она живёт. */
    public static String resumesLog() {
        return lastResumes;
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
