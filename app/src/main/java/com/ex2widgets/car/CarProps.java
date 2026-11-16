package com.ex2widgets.car;

import android.content.Context;

import com.ex2widgets.R;

public final class CarProps {

    public static final int DRIVE_MODE = 570491136;      // 0x22010100
    public static final int DRIVE_ECO = 570491137;       // 0x22010101
    public static final int DRIVE_COMFORT = 570491138;   // 0x22010102
    public static final int DRIVE_SPORT = 570491139;     // 0x22010103

    public static final int REGEN_MODE = 537003264;      // 0x20020500
    public static final int REGEN_WEAK = 537003265;      // 0x20020501
    public static final int REGEN_MEDIUM = 537003266;    // 0x20020502
    public static final int REGEN_STRONG = 537003267;    // 0x20020503

    public static final int GEAR = 289408001;            // 0x11400401
    public static final int PERF_VEHICLE_SPEED = 291504647;
    public static final int ESC_SPEED = 557884185;
    public static final int SOC = 557885165;
    public static final int RANGE = 289407752;
    public static final int AVG_CONSUMPTION = 557885176;
    public static final int AUX_12V = 557875206;
    public static final int HVAC_TEMP_SET = 557885148;
    public static final int HVAC_TEMP_SET_AOSP = 354419971;
    public static final int HVAC_TEMP_SET_CANONICAL = 605160192;  // 0x24120300 float °C
    public static final int HVAC_TEMP_CURRENT = 358614274;
    public static final int HVAC_TEMP_AOSP = 358614275;
    public static final int HVAC_TEMP_ADJUST = 624955446;
    public static final int HVAC_POWER_ON = 354419984;            // 0x15200510 bool
    public static final int HVAC_FAN_SPEED = 356517120;
    public static final int HVAC_FAN_DIRECTION = 356517121;
    public static final int HVAC_FAN_DIRECTION_VENDOR = 557846560;
    public static final int HVAC_AC_ON = 358613145;
    public static final int HVAC_AC_ON_DA = 354419973;            // 0x15200505 bool
    public static final int HVAC_RECIRC_ON = 557846566;
    public static final int HVAC_REAR_DEFROSTER_ON = 354419988;
    public static final int HVAC_FRONT_MAX_DEFROST_ON = 354419975;
    public static final int HVAC_WINDSHIELD_HEAT = 270535680;
    public static final int STEERING_WHEEL_HEAT = 289408269;
    public static final int SEAT_HEAT = 356517131;
    public static final int ODOMETER_VENDOR = 289407492;
    public static final int ODOMETER = 291504644;
    public static final float CAPACITY_KWH = 39.0f;
    public static final int AC_AMBIENT_TEMP = 557884279;
    public static final int ENV_OUTSIDE_TEMPERATURE = 291505923;
    public static final int DRIVE_MILEAGE = 658548345;

    public static final int SPEED_AUTO_LOCK = 557850645;   // 0x21402015, as in Sapo
    public static final int WINDOW_POSITION = 322964416;   // 0x13400BC0, 0..100 %
    public static final int DOOR_POS = 373295872;          // 0x16400B00, 1 — открыто, 2 — закрыто
    public static final int FRONT_TRUNK_SWT = 557887560;   // 0x2140B048, кнопка переднего багажника
    public static final int LOCK_HOOD = 557884754;         // 0x2140A552, замок капота
    public static final int DAY_NIGHT_SETTING = 538247424;
    public static final int DAY_NIGHT_DAY = 538247425;
    public static final int DAY_NIGHT_NIGHT = 538247426;
    public static final int DAY_NIGHT_AUTO = 538247427;

    public static final int[] AREAS = new int[]{0, 1};
    public static final int[] CABIN_AREAS = new int[]{1, 4, 0, 7};
    public static final int[] HVAC_AREAS = new int[]{1, 4, 0};
    public static final int[] FAN_AREAS = new int[]{1, 49, 5, 0};
    public static final int[] FAN_DIRECTION_AREAS = new int[]{1, 4, 16, 64};
    public static final int[] AC_AREAS = new int[]{1, 5, 49, 0};
    public static final int[] SEAT_AREAS = new int[]{1, 4};
    public static final int[] WHEEL_HEAT_AREAS = new int[]{0, 1};
    public static final int GEAR_PARK = 4;

    // Зоны, в которых климат отвечает на этом ГУ: порядок повторов взят из
    // разобранного EX Sapo Central (setFanLevel / setZoneTemp / setAcPower).
    public static final int AREA_GLOBAL = 0;
    public static final int AREA_SEAT_LEFT = 1;
    public static final int AREA_SEAT_RIGHT = 4;
    public static final int AREA_ROW_1_ALL = 5;
    public static final int AREA_ROW_1_2 = 49;
    public static final int AREA_HVAC_ALL = 117;
    public static final int AREA_HVAC_DA = 75;
    public static final int AREA_REAR_DEFROST_DA = 2;
    public static final int[] HVAC_WRITE_AREAS = new int[]{1, 49, 5, 0};
    public static final int FAN_MAX = 8;

    // Зоны кузова: стёкла по AOSP-областям рядов, капот — отдельной зоной DOOR_POS.
    public static final int AREA_WINDOW_FL = 0x10;
    public static final int AREA_WINDOW_FR = 0x40;
    public static final int AREA_WINDOW_RL = 0x100;
    public static final int AREA_WINDOW_RR = 0x400;
    public static final int[] WINDOW_AREAS = new int[]{
            AREA_WINDOW_FL, AREA_WINDOW_FR, AREA_WINDOW_RL, AREA_WINDOW_RR
    };
    public static final int AREA_DOOR_HOOD = 0x10000000;

    /** Стекло поднимается до 88 %: дальше на этом ГУ привод упирается. */
    public static final int WINDOW_OPEN_PERCENT = 88;

    private CarProps() {
    }

    public static String driveLabel(Context c, int value) {
        switch (value) {
            case DRIVE_ECO:
                return c.getString(R.string.hub_drive_eco);
            case DRIVE_SPORT:
                return c.getString(R.string.hub_drive_sport);
            case DRIVE_COMFORT:
            default:
                return c.getString(R.string.hub_drive_comfort);
        }
    }

    public static String regenLabel(Context c, int value) {
        switch (value) {
            case REGEN_WEAK:
                return c.getString(R.string.hub_regen_low);
            case REGEN_STRONG:
                return c.getString(R.string.hub_regen_high);
            case REGEN_MEDIUM:
            default:
                return c.getString(R.string.hub_regen_mid);
        }
    }
}

