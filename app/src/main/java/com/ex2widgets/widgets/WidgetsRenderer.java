package com.ex2widgets.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.ex2widgets.R;
import com.ex2widgets.car.CarProps;
import com.ex2widgets.car.CarStatus;

/**
 * Отрисовка виджетов через RemoteViews. Доступны только разрешённые
 * AppWidget-мутации: текст, цвет, фон и pendingIntent — поэтому активное
 * состояние показывают готовые drawable и tint значка, а не альфа или анимация.
 * Тот же набор полей и состояний рисует наложение, так что виджет и панель
 * выглядят одинаково.
 */
public final class WidgetsRenderer {

    private static final int[] FAN_BARS = {
            R.id.fan_bar_1, R.id.fan_bar_2, R.id.fan_bar_3, R.id.fan_bar_4,
            R.id.fan_bar_5, R.id.fan_bar_6, R.id.fan_bar_7, R.id.fan_bar_8
    };

    private static final int[] WINDOW_TILES = {
            R.id.win_fl, R.id.win_fr, R.id.win_rl, R.id.win_rr
    };

    private static final int[] WINDOW_LABELS = {
            R.id.win_fl_label, R.id.win_fr_label, R.id.win_rl_label, R.id.win_rr_label
    };

    private static final int[] WINDOW_VALUES = {
            R.id.win_fl_value, R.id.win_fr_value, R.id.win_rl_value, R.id.win_rr_value
    };

    private WidgetsRenderer() {
    }

    /** Единая точка входа: провайдер и сервис рисуют виджет по ключу каталога. */
    public static RemoteViews build(Context c, String key) {
        switch (key) {
            case WidgetCatalog.DRIVE:
                return hubDrive(c, intOrNull(CarStatus.driveNow(c)));
            case WidgetCatalog.REGEN:
                return hubRegen(c, intOrNull(CarStatus.regenNow(c)));
            case WidgetCatalog.OUTSIDE_TEMP:
                return hubOutsideTemp(c, CarStatus.outsideTempC(c));
            case WidgetCatalog.WINDOWS:
                return hubWindows(c, readWindows(c));
            case WidgetCatalog.FRUNK:
                return hubFrunk(c, CarStatus.hoodOpen(c),
                        CarStatus.gearNow(c));
            case WidgetCatalog.CLIMATE:
            default:
                return hubClimate(c, HubClimate.read(c));
        }
    }

    public static RemoteViews hubClimate(Context c, HubClimate.State s) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_climate);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.CLIMATE));

        boolean power = Boolean.TRUE.equals(s.hvacOn);
        tile(v, t, R.id.hub_power, R.id.hub_power_text, 0, power);
        v.setTextViewText(R.id.hub_power_text,
                t.getString(power ? R.string.hub_power_on : R.string.hub_power_off));

        v.setTextViewText(R.id.temp_value, HubViews.setpointText(s.setpoint));
        v.setTextColor(R.id.temp_value, t.getColor(HubViews.valueRes(s.setpoint)));
        icon(v, R.id.temp_minus_icon, t, s.setpoint != null);
        icon(v, R.id.temp_plus_icon, t, s.setpoint != null);

        v.setTextViewText(R.id.fan_value, HubViews.fanText(s.fanSpeed));
        for (int i = 0; i < FAN_BARS.length; i++) {
            v.setInt(FAN_BARS[i], "setBackgroundResource",
                    s.fanSpeed != null && i < s.fanSpeed ? R.drawable.hub_bar_on : R.drawable.hub_bar_off);
        }
        icon(v, R.id.fan_minus_icon, t, s.fanSpeed != null);
        icon(v, R.id.fan_plus_icon, t, s.fanSpeed != null);

        int mode = s.direction == null ? HubClimate.DIRECTION_FACE : s.direction;
        tile(v, t, R.id.dir_glass, 0, R.id.dir_glass_icon, mode == HubClimate.DIRECTION_GLASS);
        tile(v, t, R.id.dir_face, 0, R.id.dir_face_icon, mode == HubClimate.DIRECTION_FACE);
        tile(v, t, R.id.dir_feet, 0, R.id.dir_feet_icon, mode == HubClimate.DIRECTION_FEET);
        tile(v, t, R.id.dir_face_feet, 0, R.id.dir_face_feet_icon,
                mode == HubClimate.DIRECTION_FACE_FEET);
        tile(v, t, R.id.dir_glass_feet, 0, R.id.dir_glass_feet_icon,
                mode == HubClimate.DIRECTION_GLASS_FEET);

        tile(v, t, R.id.preset_cool, R.id.preset_cool_text, R.id.preset_cool_icon,
                HubClimate.presetActive(HubClimate.PRESET_COOL, s));
        tile(v, t, R.id.ac_tile, R.id.ac_text, 0, Boolean.TRUE.equals(s.acOn));
        tile(v, t, R.id.preset_heat, R.id.preset_heat_text, R.id.preset_heat_icon,
                HubClimate.presetActive(HubClimate.PRESET_HEAT, s));

        seat(v, t, R.id.heat_driver, R.id.heat_driver_text, R.id.heat_driver_icon,
                new int[]{R.id.heat_driver_wave_1, R.id.heat_driver_wave_2, R.id.heat_driver_wave_3},
                s.seatDriver);
        seat(v, t, R.id.heat_passenger, R.id.heat_passenger_text, R.id.heat_passenger_icon,
                new int[]{R.id.heat_passenger_wave_1, R.id.heat_passenger_wave_2,
                        R.id.heat_passenger_wave_3}, s.seatPassenger);
        tile(v, t, R.id.heat_wheel, R.id.heat_wheel_text, R.id.heat_wheel_icon,
                s.wheelHeat != null && s.wheelHeat > 0);

        tile(v, t, R.id.glass_front, R.id.glass_front_text, R.id.glass_front_icon,
                Boolean.TRUE.equals(s.windshieldHeat));
        tile(v, t, R.id.glass_rear, R.id.glass_rear_text, R.id.glass_rear_icon,
                Boolean.TRUE.equals(s.rearDefrost));

        click(v, c, R.id.hub_power, WidgetsCommon.POWER_TOGGLE);
        click(v, c, R.id.temp_minus, WidgetsCommon.TEMP_MINUS);
        click(v, c, R.id.temp_plus, WidgetsCommon.TEMP_PLUS);
        click(v, c, R.id.fan_minus, WidgetsCommon.FAN_MINUS);
        click(v, c, R.id.fan_plus, WidgetsCommon.FAN_PLUS);
        click(v, c, R.id.dir_glass, WidgetsCommon.DIR_GLASS);
        click(v, c, R.id.dir_face, WidgetsCommon.DIR_FACE);
        click(v, c, R.id.dir_feet, WidgetsCommon.DIR_FEET);
        click(v, c, R.id.dir_face_feet, WidgetsCommon.DIR_FACE_FEET);
        click(v, c, R.id.dir_glass_feet, WidgetsCommon.DIR_GLASS_FEET);
        click(v, c, R.id.preset_cool, WidgetsCommon.PRESET_COOL);
        click(v, c, R.id.ac_tile, WidgetsCommon.AC_TOGGLE);
        click(v, c, R.id.preset_heat, WidgetsCommon.PRESET_HEAT);
        click(v, c, R.id.heat_driver, WidgetsCommon.SEAT_DRIVER_CYCLE);
        click(v, c, R.id.heat_wheel, WidgetsCommon.WHEEL_HEAT_TOGGLE);
        click(v, c, R.id.heat_passenger, WidgetsCommon.SEAT_PASSENGER_CYCLE);
        click(v, c, R.id.glass_front, WidgetsCommon.WINDSHIELD_HEAT_TOGGLE);
        click(v, c, R.id.glass_rear, WidgetsCommon.REAR_DEFROST_TOGGLE);
        return v;
    }

    public static RemoteViews hubDrive(Context c, Integer drive) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_drive);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.DRIVE));

        tile(v, t, R.id.drive_eco, R.id.drive_eco_text, R.id.drive_eco_icon,
                drive != null && drive == CarProps.DRIVE_ECO);
        tile(v, t, R.id.drive_comfort, R.id.drive_comfort_text, R.id.drive_comfort_icon,
                drive != null && drive == CarProps.DRIVE_COMFORT);
        tile(v, t, R.id.drive_sport, R.id.drive_sport_text, R.id.drive_sport_icon,
                drive != null && drive == CarProps.DRIVE_SPORT);

        click(v, c, R.id.drive_eco, WidgetsCommon.DRIVE_ECO);
        click(v, c, R.id.drive_comfort, WidgetsCommon.DRIVE_COMFORT);
        click(v, c, R.id.drive_sport, WidgetsCommon.DRIVE_SPORT);
        return v;
    }

    public static RemoteViews hubRegen(Context c, Integer regen) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_regen);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.REGEN));

        tile(v, t, R.id.regen_low, R.id.regen_low_text, R.id.regen_low_icon,
                regen != null && regen == CarProps.REGEN_WEAK);
        tile(v, t, R.id.regen_mid, R.id.regen_mid_text, R.id.regen_mid_icon,
                regen != null && regen == CarProps.REGEN_MEDIUM);
        tile(v, t, R.id.regen_high, R.id.regen_high_text, R.id.regen_high_icon,
                regen != null && regen == CarProps.REGEN_STRONG);

        click(v, c, R.id.regen_low, WidgetsCommon.REGEN_LOW);
        click(v, c, R.id.regen_mid, WidgetsCommon.REGEN_MID);
        click(v, c, R.id.regen_high, WidgetsCommon.REGEN_HIGH);
        return v;
    }

    public static RemoteViews hubOutsideTemp(Context c, float tempC) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_outside_temp);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.OUTSIDE_TEMP));
        v.setTextViewText(R.id.temp_out_value, HubViews.outsideText(tempC));
        v.setTextColor(R.id.temp_out_value,
                t.getColor(Float.isNaN(tempC) ? R.color.hub_dim : R.color.hub_value));
        click(v, c, R.id.widget_bg_layer, WidgetsCommon.REFRESH);
        return v;
    }

    public static RemoteViews hubWindows(Context c, int[] percent) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_windows);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.WINDOWS));

        int open = 0;
        boolean any = false;
        for (int i = 0; i < WINDOW_TILES.length; i++) {
            int value = percent == null || i >= percent.length ? Integer.MIN_VALUE : percent[i];
            boolean lifted = value != Integer.MIN_VALUE && value > 2;
            if (lifted) {
                open++;
                any = true;
            }
            tile(v, t, WINDOW_TILES[i], WINDOW_LABELS[i], 0, lifted);
            v.setTextColor(WINDOW_VALUES[i],
                    t.getColor(value == Integer.MIN_VALUE ? R.color.hub_dim : R.color.hub_value));
            v.setTextViewText(WINDOW_VALUES[i], HubViews.percentText(value));
        }
        v.setTextViewText(R.id.windows_summary, t.getString(any
                ? R.string.hub_windows_open_count : R.string.hub_windows_all_closed, open));

        String[] actions = {
                WidgetsCommon.WINDOW_FL_TOGGLE, WidgetsCommon.WINDOW_FR_TOGGLE,
                WidgetsCommon.WINDOW_RL_TOGGLE, WidgetsCommon.WINDOW_RR_TOGGLE
        };
        for (int i = 0; i < WINDOW_TILES.length; i++) {
            click(v, c, WINDOW_TILES[i], actions[i]);
        }
        tile(v, t, R.id.win_open_all, R.id.win_open_all_text, R.id.win_open_all_icon, false);
        tile(v, t, R.id.win_close_all, R.id.win_close_all_text, R.id.win_close_all_icon, false);
        click(v, c, R.id.win_open_all, WidgetsCommon.WINDOWS_OPEN_ALL);
        click(v, c, R.id.win_close_all, WidgetsCommon.WINDOWS_CLOSE_ALL);
        return v;
    }

    public static RemoteViews hubFrunk(Context c, Boolean open, int gear) {
        Context t = HubViews.theme(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hub_frunk);
        v.setInt(R.id.widget_bg_layer, "setBackgroundResource",
                WidgetsCommon.hubBgResForPct(c, WidgetCatalog.FRUNK));

        boolean parked = gear == CarProps.GEAR_PARK;
        boolean requested = HubBody.frunkPending() && !Boolean.TRUE.equals(open);
        int stateRes;
        if (Boolean.TRUE.equals(open)) {
            stateRes = R.string.hub_frunk_is_open;
        } else if (requested) {
            stateRes = R.string.hub_frunk_requested;
        } else if (Boolean.FALSE.equals(open)) {
            stateRes = parked ? R.string.hub_frunk_closed : R.string.hub_frunk_only_park;
        } else {
            stateRes = R.string.hub_frunk_no_car;
        }
        v.setTextViewText(R.id.frunk_state, t.getString(stateRes));
        v.setTextColor(R.id.frunk_state, t.getColor(stateRes == R.string.hub_frunk_is_open
                || stateRes == R.string.hub_frunk_requested
                ? R.color.hub_icon_on : R.color.hub_value));

        boolean active = Boolean.TRUE.equals(open);
        tile(v, t, R.id.frunk_open, R.id.frunk_open_text, 0, active);
        v.setTextViewText(R.id.frunk_open_text, t.getString(R.string.hub_frunk_open));
        click(v, c, R.id.frunk_open, WidgetsCommon.FRUNK_OPEN);
        return v;
    }

    /** Процент опускания стёкол по зонам каталога; Integer.MIN_VALUE — ответа нет. */
    public static int[] readWindows(Context c) {
        int[] values = new int[CarProps.WINDOW_AREAS.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = CarStatus.windowPercent(c, CarProps.WINDOW_AREAS[i]);
        }
        return values;
    }

    /**
     * Плитка в стиле HUB: активная получает подсветку, светлый текст и значок.
     * textId или iconId равны нулю, когда у плитки только подпись или только значок.
     */
    private static void tile(RemoteViews v, Context t, int tileId, int textId, int iconId, boolean active) {
        v.setInt(tileId, "setBackgroundResource",
                active ? R.drawable.hub_control_on : R.drawable.hub_control);
        if (textId != 0) {
            v.setTextColor(textId, t.getColor(
                    active ? R.color.hub_control_on_text : R.color.hub_control_text));
        }
        if (iconId != 0) {
            v.setInt(iconId, "setColorFilter",
                    t.getColor(active ? R.color.hub_icon_on : R.color.hub_icon));
        }
    }

    /** Значок кнопки шага: в норме подсветка панели, при недоступном чтении — приглушённый. */
    private static void icon(RemoteViews v, int iconId, Context t, boolean readable) {
        v.setInt(iconId, "setColorFilter",
                t.getColor(readable ? R.color.hub_icon : R.color.hub_dim));
    }

    private static void seat(RemoteViews v, Context t, int tileId, int textId, int iconId,
                             int[] waves, Integer level) {
        tile(v, t, tileId, textId, iconId, level != null && level > 0);
        for (int i = 0; i < waves.length; i++) {
            v.setViewVisibility(waves[i], level != null && level > i
                    ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private static void click(RemoteViews v, Context c, int viewId, String action) {
        Intent intent = new Intent(c, WidgetUpdateService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(c, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pi);
    }

    private static Integer intOrNull(int value) {
        return value == Integer.MIN_VALUE ? null : value;
    }
}
