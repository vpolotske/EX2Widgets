package com.ex2widgets.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ex2widgets.R;
import com.ex2widgets.car.CarProps;
import com.ex2widgets.car.CarStatus;

/**
 * Те же виджеты, что и на рабочем столе, но окнами наложения. Разметка,
 * идентификаторы и состояния общие с WidgetsRenderer, поэтому внешний вид и
 * команды совпадают с виджетом: отличается только способ показать результат.
 */
final class OverlayWidgetViews {

    private static final int[] FAN_BARS = {
            R.id.fan_bar_1, R.id.fan_bar_2, R.id.fan_bar_3, R.id.fan_bar_4,
            R.id.fan_bar_5, R.id.fan_bar_6, R.id.fan_bar_7, R.id.fan_bar_8
    };

    private static final int[] CLIMATE_TILES = {
            R.id.hub_power, R.id.temp_minus, R.id.temp_plus, R.id.fan_minus, R.id.fan_plus,
            R.id.dir_glass, R.id.dir_face, R.id.dir_feet, R.id.dir_face_feet, R.id.dir_glass_feet,
            R.id.preset_cool, R.id.ac_tile, R.id.preset_heat,
            R.id.heat_driver, R.id.heat_wheel, R.id.heat_passenger,
            R.id.glass_front, R.id.glass_rear
    };

    private static final String[] CLIMATE_ACTIONS = {
            WidgetsCommon.POWER_TOGGLE, WidgetsCommon.TEMP_MINUS, WidgetsCommon.TEMP_PLUS,
            WidgetsCommon.FAN_MINUS, WidgetsCommon.FAN_PLUS,
            WidgetsCommon.DIR_GLASS, WidgetsCommon.DIR_FACE, WidgetsCommon.DIR_FEET,
            WidgetsCommon.DIR_FACE_FEET, WidgetsCommon.DIR_GLASS_FEET,
            WidgetsCommon.PRESET_COOL, WidgetsCommon.AC_TOGGLE, WidgetsCommon.PRESET_HEAT,
            WidgetsCommon.SEAT_DRIVER_CYCLE, WidgetsCommon.WHEEL_HEAT_TOGGLE,
            WidgetsCommon.SEAT_PASSENGER_CYCLE,
            WidgetsCommon.WINDSHIELD_HEAT_TOGGLE, WidgetsCommon.REAR_DEFROST_TOGGLE
    };

    private static final int[] DRIVE_TILES = {
            R.id.drive_eco, R.id.drive_comfort, R.id.drive_sport
    };

    private static final String[] DRIVE_ACTIONS = {
            WidgetsCommon.DRIVE_ECO, WidgetsCommon.DRIVE_COMFORT, WidgetsCommon.DRIVE_SPORT
    };

    private static final int[] REGEN_TILES = {
            R.id.regen_low, R.id.regen_mid, R.id.regen_high
    };

    private static final String[] REGEN_ACTIONS = {
            WidgetsCommon.REGEN_LOW, WidgetsCommon.REGEN_MID, WidgetsCommon.REGEN_HIGH
    };

    private static final int[] WINDOW_TILES = {
            R.id.win_fl, R.id.win_fr, R.id.win_rl, R.id.win_rr,
            R.id.win_open_all, R.id.win_close_all
    };

    private static final int[] WINDOW_LABELS = {
            R.id.win_fl_label, R.id.win_fr_label, R.id.win_rl_label, R.id.win_rr_label
    };

    private static final int[] WINDOW_VALUES = {
            R.id.win_fl_value, R.id.win_fr_value, R.id.win_rl_value, R.id.win_rr_value
    };

    private static final String[] WINDOW_ACTIONS = {
            WidgetsCommon.WINDOW_FL_TOGGLE, WidgetsCommon.WINDOW_FR_TOGGLE,
            WidgetsCommon.WINDOW_RL_TOGGLE, WidgetsCommon.WINDOW_RR_TOGGLE,
            WidgetsCommon.WINDOWS_OPEN_ALL, WidgetsCommon.WINDOWS_CLOSE_ALL
    };

    private static final int[] FRUNK_TILES = {R.id.frunk_open};

    private static final String[] FRUNK_ACTIONS = {WidgetsCommon.FRUNK_OPEN};

    private static final int[] TEMP_TILES = {R.id.widget_bg_layer};

    private static final String[] TEMP_ACTIONS = {WidgetsCommon.REFRESH};

    private OverlayWidgetViews() {
    }

    static View create(Context c, String key) {
        Context t = HubViews.theme(c);
        View root = LayoutInflater.from(t).inflate(WidgetCatalog.of(key).layoutRes, null);
        OverlayBackground.apply(root, c, key);
        bindClicks(c, root, key);
        return root;
    }

    static void update(Context c, View root, String key) {
        if (root == null) return;
        Context t = HubViews.theme(c);
        OverlayBackground.apply(root, c, key);
        switch (key) {
            case WidgetCatalog.DRIVE:
                updateDrive(t, root, intOrNull(CarStatus.driveNow(c)));
                break;
            case WidgetCatalog.REGEN:
                updateRegen(t, root, intOrNull(CarStatus.regenNow(c)));
                break;
            case WidgetCatalog.OUTSIDE_TEMP:
                updateOutsideTemp(t, root, CarStatus.outsideTempC(c));
                break;
            case WidgetCatalog.WINDOWS:
                updateWindows(t, root, WidgetsRenderer.readWindows(c));
                break;
            case WidgetCatalog.FRUNK:
                updateFrunk(t, root, CarStatus.hoodOpen(c), CarStatus.gearNow(c));
                break;
            case WidgetCatalog.CLIMATE:
            default:
                updateClimate(t, root, HubClimate.read(c));
                break;
        }
    }

    private static void updateClimate(Context t, View root, HubClimate.State s) {
        boolean power = Boolean.TRUE.equals(s.hvacOn);
        tile(t, root, R.id.hub_power, R.id.hub_power_text, 0, power);
        text(root, R.id.hub_power_text, t.getString(power
                ? R.string.hub_power_on : R.string.hub_power_off));

        text(root, R.id.temp_value, HubViews.setpointText(s.setpoint));
        color(t, root, R.id.temp_value, HubViews.valueRes(s.setpoint));
        icon(t, root, R.id.temp_minus_icon, s.setpoint != null);
        icon(t, root, R.id.temp_plus_icon, s.setpoint != null);

        text(root, R.id.fan_value, HubViews.fanText(s.fanSpeed));
        for (int i = 0; i < FAN_BARS.length; i++) {
            background(root, FAN_BARS[i],
                    s.fanSpeed != null && i < s.fanSpeed ? R.drawable.hub_bar_on : R.drawable.hub_bar_off);
        }
        icon(t, root, R.id.fan_minus_icon, s.fanSpeed != null);
        icon(t, root, R.id.fan_plus_icon, s.fanSpeed != null);

        int mode = s.direction == null ? HubClimate.DIRECTION_FACE : s.direction;
        tile(t, root, R.id.dir_glass, 0, R.id.dir_glass_icon, mode == HubClimate.DIRECTION_GLASS);
        tile(t, root, R.id.dir_face, 0, R.id.dir_face_icon, mode == HubClimate.DIRECTION_FACE);
        tile(t, root, R.id.dir_feet, 0, R.id.dir_feet_icon, mode == HubClimate.DIRECTION_FEET);
        tile(t, root, R.id.dir_face_feet, 0, R.id.dir_face_feet_icon,
                mode == HubClimate.DIRECTION_FACE_FEET);
        tile(t, root, R.id.dir_glass_feet, 0, R.id.dir_glass_feet_icon,
                mode == HubClimate.DIRECTION_GLASS_FEET);

        tile(t, root, R.id.preset_cool, R.id.preset_cool_text, R.id.preset_cool_icon,
                HubClimate.presetActive(HubClimate.PRESET_COOL, s));
        tile(t, root, R.id.ac_tile, R.id.ac_text, 0, Boolean.TRUE.equals(s.acOn));
        tile(t, root, R.id.preset_heat, R.id.preset_heat_text, R.id.preset_heat_icon,
                HubClimate.presetActive(HubClimate.PRESET_HEAT, s));

        seat(t, root, R.id.heat_driver, R.id.heat_driver_text, R.id.heat_driver_icon,
                new int[]{R.id.heat_driver_wave_1, R.id.heat_driver_wave_2,
                        R.id.heat_driver_wave_3}, s.seatDriver);
        seat(t, root, R.id.heat_passenger, R.id.heat_passenger_text, R.id.heat_passenger_icon,
                new int[]{R.id.heat_passenger_wave_1, R.id.heat_passenger_wave_2,
                        R.id.heat_passenger_wave_3}, s.seatPassenger);
        tile(t, root, R.id.heat_wheel, R.id.heat_wheel_text, R.id.heat_wheel_icon,
                s.wheelHeat != null && s.wheelHeat > 0);

        tile(t, root, R.id.glass_front, R.id.glass_front_text, R.id.glass_front_icon,
                Boolean.TRUE.equals(s.windshieldHeat));
        tile(t, root, R.id.glass_rear, R.id.glass_rear_text, R.id.glass_rear_icon,
                Boolean.TRUE.equals(s.rearDefrost));
    }

    private static void updateDrive(Context t, View root, Integer drive) {
        tile(t, root, R.id.drive_eco, R.id.drive_eco_text, R.id.drive_eco_icon,
                drive != null && drive == CarProps.DRIVE_ECO);
        tile(t, root, R.id.drive_comfort, R.id.drive_comfort_text, R.id.drive_comfort_icon,
                drive != null && drive == CarProps.DRIVE_COMFORT);
        tile(t, root, R.id.drive_sport, R.id.drive_sport_text, R.id.drive_sport_icon,
                drive != null && drive == CarProps.DRIVE_SPORT);
    }

    private static void updateRegen(Context t, View root, Integer regen) {
        tile(t, root, R.id.regen_low, R.id.regen_low_text, R.id.regen_low_icon,
                regen != null && regen == CarProps.REGEN_WEAK);
        tile(t, root, R.id.regen_mid, R.id.regen_mid_text, R.id.regen_mid_icon,
                regen != null && regen == CarProps.REGEN_MEDIUM);
        tile(t, root, R.id.regen_high, R.id.regen_high_text, R.id.regen_high_icon,
                regen != null && regen == CarProps.REGEN_STRONG);
    }

    private static void updateOutsideTemp(Context t, View root, float tempC) {
        text(root, R.id.temp_out_value, HubViews.outsideText(tempC));
        color(t, root, R.id.temp_out_value, Float.isNaN(tempC) ? R.color.hub_dim : R.color.hub_value);
    }

    private static void updateWindows(Context t, View root, int[] percent) {
        int open = 0;
        boolean any = false;
        for (int i = 0; i < WINDOW_TILES.length - 2; i++) {
            int value = percent == null || i >= percent.length ? Integer.MIN_VALUE : percent[i];
            boolean lifted = value != Integer.MIN_VALUE && value > 2;
            if (lifted) {
                open++;
                any = true;
            }
            tile(t, root, WINDOW_TILES[i], WINDOW_LABELS[i], 0, lifted);
            color(t, root, WINDOW_VALUES[i],
                    value == Integer.MIN_VALUE ? R.color.hub_dim : R.color.hub_value);
            text(root, WINDOW_VALUES[i], HubViews.percentText(value));
        }
        text(root, R.id.windows_summary, t.getString(any
                ? R.string.hub_windows_open_count : R.string.hub_windows_all_closed, open));
        tile(t, root, R.id.win_open_all, R.id.win_open_all_text, R.id.win_open_all_icon, false);
        tile(t, root, R.id.win_close_all, R.id.win_close_all_text, R.id.win_close_all_icon, false);
    }

    private static void updateFrunk(Context t, View root, Boolean open, int gear) {
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
        text(root, R.id.frunk_state, t.getString(stateRes));
        color(t, root, R.id.frunk_state,
                stateRes == R.string.hub_frunk_is_open || stateRes == R.string.hub_frunk_requested
                        ? R.color.hub_icon_on : R.color.hub_value);
        tile(t, root, R.id.frunk_open, R.id.frunk_open_text, 0, Boolean.TRUE.equals(open));
        text(root, R.id.frunk_open_text, t.getString(R.string.hub_frunk_open));
    }

    private static void bindClicks(Context c, View root, String key) {
        int[] tiles;
        String[] actions;
        switch (key) {
            case WidgetCatalog.DRIVE:
                tiles = DRIVE_TILES;
                actions = DRIVE_ACTIONS;
                break;
            case WidgetCatalog.REGEN:
                tiles = REGEN_TILES;
                actions = REGEN_ACTIONS;
                break;
            case WidgetCatalog.OUTSIDE_TEMP:
                tiles = TEMP_TILES;
                actions = TEMP_ACTIONS;
                break;
            case WidgetCatalog.WINDOWS:
                tiles = WINDOW_TILES;
                actions = WINDOW_ACTIONS;
                break;
            case WidgetCatalog.FRUNK:
                tiles = FRUNK_TILES;
                actions = FRUNK_ACTIONS;
                break;
            case WidgetCatalog.CLIMATE:
            default:
                tiles = CLIMATE_TILES;
                actions = CLIMATE_ACTIONS;
                break;
        }
        for (int i = 0; i < tiles.length; i++) {
            View tile = root.findViewById(tiles[i]);
            if (tile == null) continue;
            String action = actions[i];
            tile.setOnClickListener(v -> WidgetUpdateService.action(c, action));
        }
    }

    /**
     * Плитка в стиле HUB: активная получает подсветку, светлый текст и значок.
     * textId или iconId равны нулю, когда у плитки только подпись или только значок.
     */
    private static void tile(Context t, View root, int tileId, int textId, int iconId,
                             boolean active) {
        background(root, tileId, active ? R.drawable.hub_control_on : R.drawable.hub_control);
        if (textId != 0) {
            color(t, root, textId, active ? R.color.hub_control_on_text : R.color.hub_control_text);
        }
        if (iconId != 0) {
            tint(t, root, iconId, active ? R.color.hub_icon_on : R.color.hub_icon);
        }
    }

    /** Значок кнопки шага: в норме подсветка панели, при недоступном чтении — приглушённый. */
    private static void icon(Context t, View root, int iconId, boolean readable) {
        tint(t, root, iconId, readable ? R.color.hub_icon : R.color.hub_dim);
    }

    private static void seat(Context t, View root, int tileId, int textId, int iconId,
                             int[] waves, Integer level) {
        tile(t, root, tileId, textId, iconId, level != null && level > 0);
        for (int i = 0; i < waves.length; i++) {
            View wave = root.findViewById(waves[i]);
            if (wave != null) {
                wave.setVisibility(level != null && level > i ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private static void tint(Context t, View root, int iconId, int colorRes) {
        View view = root.findViewById(iconId);
        if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(t.getColor(colorRes));
        }
    }

    private static void color(Context t, View root, int viewId, int colorRes) {
        View view = root.findViewById(viewId);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(t.getColor(colorRes));
        }
    }

    private static void text(View root, int viewId, String value) {
        if (root.findViewById(viewId) instanceof TextView) {
            ((TextView) root.findViewById(viewId)).setText(value);
        }
    }

    private static void background(View root, int viewId, int drawableRes) {
        View view = root.findViewById(viewId);
        if (view != null) view.setBackgroundResource(drawableRes);
    }

    private static Integer intOrNull(int value) {
        return value == Integer.MIN_VALUE ? null : value;
    }
}
