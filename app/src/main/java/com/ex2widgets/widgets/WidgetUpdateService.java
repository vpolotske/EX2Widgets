package com.ex2widgets.widgets;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import com.ex2widgets.LogStore;
import com.ex2widgets.NotificationHelper;
import com.ex2widgets.R;
import com.ex2widgets.car.CarConnection;
import com.ex2widgets.car.CarProps;
import com.ex2widgets.car.CarStatus;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Опрос VHAL и перерисовка двух виджетов EX2 HUB. Кнопки виджета и наложения
 * приходят сюда одним и тем же действием, поэтому запись и показ идут из
 * одного места и не расходятся в зонах и цепочках.
 */
public final class WidgetUpdateService extends Service {

    private static final long POLL_MS = 3000L;
    private static final long RETRY_MS = 6000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingActions = new ConcurrentLinkedQueue<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!hasAnyWidget(WidgetUpdateService.this)) {
                if (pendingActions.isEmpty()) {
                    stopSelf();
                    return;
                }
                handler.postDelayed(this, RETRY_MS);
                return;
            }
            requestUpdate();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            requestUpdate();
        }
    };

    public static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, WidgetUpdateService.class));
        } catch (Throwable t) {
            LogStore.add(c, "widget service start failed: " + t);
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WidgetUpdateService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void sync(Context c) {
        if (hasAnyWidget(c)) start(c);
        else stop(c);
    }

    public static void refreshOpacity(Context c) {
        action(c, WidgetsCommon.REFRESH_OPACITY);
    }

    /** Нажатие наложения: то же действие, что и PendingIntent виджета. */
    public static void action(Context c, String action) {
        try {
            c.startForegroundService(
                    new Intent(c, WidgetUpdateService.class).setAction(action));
        } catch (Throwable t) {
            LogStore.add(c, "widget action failed " + action + ": " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_WIDGETS, NotificationHelper.widgets(this));
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action != null) pendingActions.add(action);
        requestUpdate();
        return START_STICKY;
    }

    private void requestUpdate() {
        if (!busy.compareAndSet(false, true)) return;
        executor.execute(() -> {
            try {
                updateAll();
            } catch (Throwable t) {
                LogStore.add(this, "widget update failed: " + t);
            } finally {
                busy.set(false);
            }
        });
    }

    private void updateAll() {
        boolean carReady = CarConnection.get().ensure(this);
        if (!carReady) HubClimate.resetEngaged();

        if (!pendingActions.isEmpty()) {
            if (!carReady) {
                scheduleRetry();
                return;
            }
            HubClimate.State state = HubClimate.read(this);
            String action;
            while ((action = pendingActions.poll()) != null) {
                try {
                    if (!WidgetsCommon.REFRESH_OPACITY.equals(action)) handleAction(action, state);
                } catch (Throwable t) {
                    LogStore.add(this, "widget action failed " + action + ": " + t);
                }
            }
        }

        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        boolean placed = false;
        for (WidgetCatalog.Spec spec : WidgetCatalog.all()) {
            int[] ids = ids(awm, spec.provider);
            if (ids.length == 0) continue;
            placed = true;
            update(awm, ids, WidgetsRenderer.build(this, spec.key));
        }
        if (!placed) {
            stopSelf();
        }
    }

    private void handleAction(String action, HubClimate.State s) {
        switch (action) {
            case WidgetsCommon.POWER_TOGGLE:
                HubClimate.togglePower(this, s);
                break;
            case WidgetsCommon.TEMP_MINUS:
                HubClimate.stepTemp(this, s, false);
                break;
            case WidgetsCommon.TEMP_PLUS:
                HubClimate.stepTemp(this, s, true);
                break;
            case WidgetsCommon.FAN_MINUS:
                HubClimate.stepFan(this, s, -1);
                break;
            case WidgetsCommon.FAN_PLUS:
                HubClimate.stepFan(this, s, 1);
                break;
            case WidgetsCommon.DIR_GLASS:
                HubClimate.setDirection(this, HubClimate.DIRECTION_GLASS);
                break;
            case WidgetsCommon.DIR_FACE:
                HubClimate.setDirection(this, HubClimate.DIRECTION_FACE);
                break;
            case WidgetsCommon.DIR_FEET:
                HubClimate.setDirection(this, HubClimate.DIRECTION_FEET);
                break;
            case WidgetsCommon.DIR_FACE_FEET:
                HubClimate.setDirection(this, HubClimate.DIRECTION_FACE_FEET);
                break;
            case WidgetsCommon.DIR_GLASS_FEET:
                HubClimate.setDirection(this, HubClimate.DIRECTION_GLASS_FEET);
                break;
            case WidgetsCommon.AC_TOGGLE:
                HubClimate.toggleAc(this, s);
                break;
            case WidgetsCommon.PRESET_COOL:
                HubClimate.applyPreset(this, s, HubClimate.PRESET_COOL);
                break;
            case WidgetsCommon.PRESET_HEAT:
                HubClimate.applyPreset(this, s, HubClimate.PRESET_HEAT);
                break;
            case WidgetsCommon.SEAT_DRIVER_CYCLE:
                HubClimate.cycleSeat(this, s, true);
                break;
            case WidgetsCommon.SEAT_PASSENGER_CYCLE:
                HubClimate.cycleSeat(this, s, false);
                break;
            case WidgetsCommon.WHEEL_HEAT_TOGGLE:
                HubClimate.toggleWheel(this, s);
                break;
            case WidgetsCommon.REAR_DEFROST_TOGGLE:
                HubClimate.toggleRearDefrost(this, s);
                break;
            case WidgetsCommon.WINDSHIELD_HEAT_TOGGLE:
                HubClimate.toggleWindshieldHeat(this, s);
                break;
            case WidgetsCommon.DRIVE_ECO:
                WidgetsCommon.applyDrive(this, CarProps.DRIVE_ECO);
                break;
            case WidgetsCommon.DRIVE_COMFORT:
                WidgetsCommon.applyDrive(this, CarProps.DRIVE_COMFORT);
                break;
            case WidgetsCommon.DRIVE_SPORT:
                WidgetsCommon.applyDrive(this, CarProps.DRIVE_SPORT);
                break;
            case WidgetsCommon.REGEN_LOW:
                WidgetsCommon.applyRegen(this, CarProps.REGEN_WEAK);
                break;
            case WidgetsCommon.REGEN_MID:
                WidgetsCommon.applyRegen(this, CarProps.REGEN_MEDIUM);
                break;
            case WidgetsCommon.REGEN_HIGH:
                WidgetsCommon.applyRegen(this, CarProps.REGEN_STRONG);
                break;
            case WidgetsCommon.WINDOW_FL_TOGGLE:
                WidgetsCommon.toggleWindow(this, CarProps.AREA_WINDOW_FL);
                break;
            case WidgetsCommon.WINDOW_FR_TOGGLE:
                WidgetsCommon.toggleWindow(this, CarProps.AREA_WINDOW_FR);
                break;
            case WidgetsCommon.WINDOW_RL_TOGGLE:
                WidgetsCommon.toggleWindow(this, CarProps.AREA_WINDOW_RL);
                break;
            case WidgetsCommon.WINDOW_RR_TOGGLE:
                WidgetsCommon.toggleWindow(this, CarProps.AREA_WINDOW_RR);
                break;
            case WidgetsCommon.WINDOWS_OPEN_ALL:
                WidgetsCommon.setAllWindows(this, CarProps.WINDOW_OPEN_PERCENT);
                break;
            case WidgetsCommon.WINDOWS_CLOSE_ALL:
                WidgetsCommon.setAllWindows(this, 0);
                break;
            case WidgetsCommon.FRUNK_OPEN:
                if (CarStatus.gearNow(this) == CarProps.GEAR_PARK) {
                    WidgetsCommon.openFrunk(this);
                    HubBody.markFrunkRequested();
                } else {
                    LogStore.add(this, "frunk ignored: not in P");
                }
                break;
            default:
                break;
        }
    }

    private int[] ids(AppWidgetManager awm, Class<?> provider) {
        try {
            int[] ids = awm.getAppWidgetIds(new ComponentName(this, provider));
            return ids == null ? new int[0] : ids;
        } catch (Throwable t) {
            return new int[0];
        }
    }

    private static boolean hasAnyWidget(Context c) {
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(c);
            for (Class<?> provider : WidgetCatalog.providers()) {
                int[] ids = awm.getAppWidgetIds(new ComponentName(c, provider));
                if (ids != null && ids.length > 0) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean update(AppWidgetManager awm, int[] ids, RemoteViews views) {
        if (ids.length == 0) return false;
        for (int id : ids) awm.updateAppWidget(id, views);
        return true;
    }

    private void scheduleRetry() {
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, RETRY_MS);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
