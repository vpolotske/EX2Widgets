package com.ex2widgets.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import com.ex2widgets.Prefs;
import com.ex2widgets.R;
import com.ex2widgets.car.CarConnection;
import com.ex2widgets.car.CarProps;
import com.ex2widgets.car.CarStatus;

/**
 * Действия, ключи и команды виджетов EX2 Widgets.
 *
 * Записи идут по тем же адресам и зонам, что проверены на этом ГУ: климат —
 * цепочками HubClimate, режимы движения — CarStatus.apply*. Наружная
 * температура только читается, окнам и капоту команды шлёт CarStatus, где
 * неподтверждённые записи описаны явно.
 */
public final class WidgetsCommon {

    public static final String DRIVE_ECO = "com.ex2widgets.widget.DRIVE_ECO";
    public static final String DRIVE_COMFORT = "com.ex2widgets.widget.DRIVE_COMFORT";
    public static final String DRIVE_SPORT = "com.ex2widgets.widget.DRIVE_SPORT";
    public static final String REGEN_LOW = "com.ex2widgets.widget.REGEN_LOW";
    public static final String REGEN_MID = "com.ex2widgets.widget.REGEN_MID";
    public static final String REGEN_HIGH = "com.ex2widgets.widget.REGEN_HIGH";

    public static final String POWER_TOGGLE = "com.ex2widgets.widget.POWER_TOGGLE";
    public static final String TEMP_MINUS = "com.ex2widgets.widget.TEMP_MINUS";
    public static final String TEMP_PLUS = "com.ex2widgets.widget.TEMP_PLUS";
    public static final String FAN_MINUS = "com.ex2widgets.widget.FAN_MINUS";
    public static final String FAN_PLUS = "com.ex2widgets.widget.FAN_PLUS";
    public static final String DIR_GLASS = "com.ex2widgets.widget.DIR_GLASS";
    public static final String DIR_FACE = "com.ex2widgets.widget.DIR_FACE";
    public static final String DIR_FEET = "com.ex2widgets.widget.DIR_FEET";
    public static final String DIR_FACE_FEET = "com.ex2widgets.widget.DIR_FACE_FEET";
    public static final String DIR_GLASS_FEET = "com.ex2widgets.widget.DIR_GLASS_FEET";
    public static final String AC_TOGGLE = "com.ex2widgets.widget.AC_TOGGLE";
    public static final String PRESET_COOL = "com.ex2widgets.widget.PRESET_COOL";
    public static final String PRESET_HEAT = "com.ex2widgets.widget.PRESET_HEAT";
    public static final String SEAT_DRIVER_CYCLE = "com.ex2widgets.widget.SEAT_DRIVER_CYCLE";
    public static final String SEAT_PASSENGER_CYCLE = "com.ex2widgets.widget.SEAT_PASSENGER_CYCLE";
    public static final String WHEEL_HEAT_TOGGLE = "com.ex2widgets.widget.WHEEL_HEAT_TOGGLE";
    public static final String REAR_DEFROST_TOGGLE = "com.ex2widgets.widget.REAR_DEFROST_TOGGLE";
    public static final String WINDSHIELD_HEAT_TOGGLE = "com.ex2widgets.widget.WINDSHIELD_HEAT_TOGGLE";

    public static final String WINDOW_FL_TOGGLE = "com.ex2widgets.widget.WINDOW_FL_TOGGLE";
    public static final String WINDOW_FR_TOGGLE = "com.ex2widgets.widget.WINDOW_FR_TOGGLE";
    public static final String WINDOW_RL_TOGGLE = "com.ex2widgets.widget.WINDOW_RL_TOGGLE";
    public static final String WINDOW_RR_TOGGLE = "com.ex2widgets.widget.WINDOW_RR_TOGGLE";
    public static final String WINDOWS_OPEN_ALL = "com.ex2widgets.widget.WINDOWS_OPEN_ALL";
    public static final String WINDOWS_CLOSE_ALL = "com.ex2widgets.widget.WINDOWS_CLOSE_ALL";

    public static final String FRUNK_OPEN = "com.ex2widgets.widget.FRUNK_OPEN";

    public static final String REFRESH = "com.ex2widgets.widget.REFRESH";
    public static final String REFRESH_OPACITY = "com.ex2widgets.widget.REFRESH_OPACITY";

    private static final int[] DRIVE_ORDER = {
            CarProps.DRIVE_ECO, CarProps.DRIVE_COMFORT, CarProps.DRIVE_SPORT
    };
    private static final int[] REGEN_ORDER = {
            CarProps.REGEN_WEAK, CarProps.REGEN_MEDIUM, CarProps.REGEN_STRONG
    };

    /** Готовые фоны с шагом 5 % — индекс это процент, делённый на пять. */
    private static final int[] CARD_RAMPS = {
            R.drawable.hub_card_000, R.drawable.hub_card_005, R.drawable.hub_card_010,
            R.drawable.hub_card_015, R.drawable.hub_card_020, R.drawable.hub_card_025,
            R.drawable.hub_card_030, R.drawable.hub_card_035, R.drawable.hub_card_040,
            R.drawable.hub_card_045, R.drawable.hub_card_050, R.drawable.hub_card_055,
            R.drawable.hub_card_060, R.drawable.hub_card_065, R.drawable.hub_card_070,
            R.drawable.hub_card_075, R.drawable.hub_card_080, R.drawable.hub_card_085,
            R.drawable.hub_card_090, R.drawable.hub_card_095, R.drawable.hub_card_100
    };

    private static final int[] PANEL_RAMPS = {
            R.drawable.hub_panel_000, R.drawable.hub_panel_005, R.drawable.hub_panel_010,
            R.drawable.hub_panel_015, R.drawable.hub_panel_020, R.drawable.hub_panel_025,
            R.drawable.hub_panel_030, R.drawable.hub_panel_035, R.drawable.hub_panel_040,
            R.drawable.hub_panel_045, R.drawable.hub_panel_050, R.drawable.hub_panel_055,
            R.drawable.hub_panel_060, R.drawable.hub_panel_065, R.drawable.hub_panel_070,
            R.drawable.hub_panel_075, R.drawable.hub_panel_080, R.drawable.hub_panel_085,
            R.drawable.hub_panel_090, R.drawable.hub_panel_095, R.drawable.hub_panel_100
    };

    private WidgetsCommon() {
    }

    public static String[] widgetKeys() {
        return WidgetCatalog.keys();
    }

    /**
     * Прозрачность виджета: индивидуальный ползунок умножается на общий,
     * поэтому настройка приложения и у конкретного виджета действуют вместе.
     */
    public static int alphaPct(Context c, String key) {
        int pct = Math.round(Prefs.widgetAlphaPct(c, key) / 100f
                * Prefs.widgetBgOpacity(c) / 100f * 100f);
        return Math.max(0, Math.min(100, pct));
    }

    /**
     * Фон наложения строится в коде: у настоящих View нет ограничений
     * AppWidget, поэтому ползунок работает с точностью до одного процента.
     */
    public static Drawable hubBgDrawable(Context c, String key) {
        WidgetCatalog.Spec spec = WidgetCatalog.of(key);
        int pct = alphaPct(c, key);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(c, spec.cornerDp));
        bg.setStroke(Math.max(1, Math.round(dp(c, 1f))),
                colorWithAlpha(c, R.color.hub_card_border_100, pct));
        bg.setColor(colorWithAlpha(c, R.color.hub_card_bg_100, pct));
        return bg;
    }

    /** Тот же фон для RemoteViews: виджет рабочего экрана умеет только выбирать готовый drawable. */
    public static int hubBgResForPct(Context c, String key) {
        int step = Math.round(alphaPct(c, key) / 5f);
        int[] ramp = WidgetCatalog.of(key).panel() ? PANEL_RAMPS : CARD_RAMPS;
        return ramp[Math.max(0, Math.min(ramp.length - 1, step))];
    }

    // ---------------------------------------------------------------------------------
    // Режим движения и рекуперация
    // ---------------------------------------------------------------------------------

    public static void applyDrive(Context c, int value) {
        CarStatus.applyDrive(c, value);
    }

    public static void applyRegen(Context c, int value) {
        CarStatus.applyRegen(c, value);
    }

    public static void stepDrive(Context c, int direction) {
        applyDrive(c, step(DRIVE_ORDER, CarStatus.driveNow(c), direction));
    }

    public static void stepRegen(Context c, int direction) {
        applyRegen(c, step(REGEN_ORDER, CarStatus.regenNow(c), direction));
    }

    // ---------------------------------------------------------------------------------
    // Стёкла и капот
    // ---------------------------------------------------------------------------------

    /** Плитка окна: открыть, если стекло закрыто, и закрыть, если приоткрыто. */
    public static void toggleWindow(Context c, int area) {
        int percent = CarStatus.windowPercent(c, area);
        boolean open = percent == Integer.MIN_VALUE || percent <= 2;
        CarStatus.setWindow(c, area, open ? CarProps.WINDOW_OPEN_PERCENT : 0);
    }

    public static void setAllWindows(Context c, int percent) {
        for (int area : CarProps.WINDOW_AREAS) {
            CarStatus.setWindow(c, area, percent);
        }
    }

    public static void openFrunk(Context c) {
        CarStatus.openFrunk(c);
    }

    // ---------------------------------------------------------------------------------
    // Служебное
    // ---------------------------------------------------------------------------------

    private static int step(int[] order, int current, int direction) {
        int index = -1;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == current) {
                index = i;
                break;
            }
        }
        if (index < 0) index = order.length / 2;
        return order[Math.max(0, Math.min(order.length - 1, index + direction))];
    }

    private static int colorWithAlpha(Context c, int colorRes, int pct) {
        int color = c.getColor(colorRes);
        return (color & 0x00FFFFFF) | (Math.round(pct * 2.55f) << 24);
    }

    private static float dp(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().density;
    }
}
