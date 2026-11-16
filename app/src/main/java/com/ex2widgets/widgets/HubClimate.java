package com.ex2widgets.widgets;

import android.content.Context;

import com.ex2widgets.car.CarConnection;
import com.ex2widgets.car.CarProps;

/**
 * Состояние климата и команды записи. Цепочки повторов, зоны и семантику пресетов
 * перенесены из EX2 HUB (GeelyVhal): там они проверены на этом же ГУ, поэтому
 * виджет пишет те же адреса в том же порядке, а не «первый попавшийся».
 */
public final class HubClimate {

    public static final int DIRECTION_FACE = 1;
    public static final int DIRECTION_FEET = 2;
    public static final int DIRECTION_FACE_FEET = 3;
    public static final int DIRECTION_GLASS = 4;
    public static final int DIRECTION_GLASS_FEET = 6;

    public static final int PRESET_NONE = 0;
    public static final int PRESET_COOL = 1;
    public static final int PRESET_HEAT = 2;

    public static final int FAN_MAX = CarProps.FAN_MAX;
    public static final int HEAT_MAX_LEVEL = 3;
    public static final double SETPOINT_MIN = 17.0;
    public static final double SETPOINT_MAX = 32.0;
    public static final double TEMP_MIN_C = 15.5;
    public static final double TEMP_MAX_C = 33.0;
    public static final double ADJUST_MIN_C = 16.0;
    public static final double ADJUST_MAX_C = 32.0;
    public static final int PRESET_FAN = 6;
    public static final int PRESET_FAN_REPEAT = 2;
    public static final double PRESET_HEAT_REPEAT = 27.0;

    /** Пресет считается включённым, пока его держит приложение и чтение ему не противоречит. */
    private static volatile int engaged = PRESET_NONE;

    private HubClimate() {
    }

    public static final class State {
        public Boolean hvacOn;
        public Boolean acOn;
        public Integer fanSpeed;
        public Double setpoint;
        public Integer direction;
        public Integer seatDriver;
        public Integer seatPassenger;
        public Integer wheelHeat;
        public Boolean rearDefrost;
        public Boolean windshieldHeat;

        public boolean reachable() {
            return hvacOn != null || acOn != null || fanSpeed != null || setpoint != null
                    || direction != null || seatDriver != null || seatPassenger != null
                    || wheelHeat != null || rearDefrost != null;
        }
    }

    public static State read(Context c) {
        State s = new State();
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return s;
        s.hvacOn = readFlag(cc, CarProps.HVAC_POWER_ON,
                new int[]{CarProps.AREA_ROW_1_ALL, CarProps.AREA_GLOBAL, CarProps.AREA_SEAT_LEFT});
        s.acOn = readFlag(cc, CarProps.HVAC_AC_ON_DA, new int[]{CarProps.AREA_HVAC_ALL,
                CarProps.AREA_HVAC_DA, CarProps.AREA_SEAT_LEFT, CarProps.AREA_GLOBAL});
        if (s.acOn == null) {
            s.acOn = readFlag(cc, CarProps.HVAC_AC_ON, CarProps.HVAC_WRITE_AREAS);
        }
        s.fanSpeed = readFan(cc);
        s.setpoint = readSetpoint(cc);
        s.direction = readDirection(cc);
        s.seatDriver = readLevel(cc, CarProps.SEAT_HEAT, new int[]{CarProps.AREA_SEAT_LEFT});
        s.seatPassenger = readLevel(cc, CarProps.SEAT_HEAT, new int[]{CarProps.AREA_SEAT_RIGHT});
        s.wheelHeat = readLevel(cc, CarProps.STEERING_WHEEL_HEAT,
                new int[]{CarProps.AREA_SEAT_LEFT, CarProps.AREA_GLOBAL});
        s.rearDefrost = readFlag(cc, CarProps.HVAC_REAR_DEFROSTER_ON,
                new int[]{CarProps.AREA_REAR_DEFROST_DA, CarProps.AREA_GLOBAL});
        // 0x10200C00 на этом ГУ не отвечает на чтение: неизвестное состояние
        // считается выключенным, иначе кнопка осталась бы навсегда нерабочей.
        s.windshieldHeat = readFlag(cc, CarProps.HVAC_WINDSHIELD_HEAT,
                new int[]{CarProps.AREA_HVAC_ALL});
        return s;
    }

    // ---------------------------------------------------------------------------------
    // Команды панели
    // ---------------------------------------------------------------------------------

    public static void togglePower(Context c, State s) {
        if (s.hvacOn == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        cc.writeBool(CarProps.HVAC_POWER_ON, CarProps.AREA_ROW_1_ALL, !s.hvacOn);
    }

    public static void stepFan(Context c, State s, int delta) {
        if (s.fanSpeed == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeFan(cc, Math.max(0, Math.min(FAN_MAX, s.fanSpeed + delta)));
    }

    public static void stepTemp(Context c, State s, boolean up) {
        if (s.setpoint == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        double next = up ? Math.floor(s.setpoint) + 1.0 : Math.ceil(s.setpoint) - 1.0;
        writeSetpoint(cc, clamp(next, SETPOINT_MIN, SETPOINT_MAX));
    }

    public static void setDirection(Context c, int mask) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeDirection(cc, mask);
    }

    public static void toggleAc(Context c, State s) {
        if (s.acOn == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeAc(cc, !s.acOn);
    }

    public static void cycleSeat(Context c, State s, boolean driver) {
        Integer current = driver ? s.seatDriver : s.seatPassenger;
        if (current == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        int area = driver ? CarProps.AREA_SEAT_LEFT : CarProps.AREA_SEAT_RIGHT;
        writeIntFirst(cc, CarProps.SEAT_HEAT, new int[]{area},
                current >= HEAT_MAX_LEVEL ? 0 : current + 1);
    }

    public static void toggleWheel(Context c, State s) {
        if (s.wheelHeat == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeWheel(cc, s.wheelHeat > 0 ? 0 : 1);
    }

    public static void toggleRearDefrost(Context c, State s) {
        if (s.rearDefrost == null) return;
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeRearDefrost(cc, !s.rearDefrost);
    }

    /** Обогрев лобового: неизвестное состояние читается как «выкл», кнопка всегда рабочая. */
    public static void toggleWindshieldHeat(Context c, State s) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        boolean on = !Boolean.TRUE.equals(s.windshieldHeat);
        cc.writeBool(CarProps.HVAC_WINDSHIELD_HEAT, CarProps.AREA_HVAC_ALL, on);
    }

    // ---------------------------------------------------------------------------------
    // Абсолютные команды: их просят голосовые сценарии, где значение задано словами
    // ---------------------------------------------------------------------------------

    public static void setFanLevel(Context c, int level) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeFan(cc, clamp(level, 0, FAN_MAX));
    }

    public static void setSetpointC(Context c, double value) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeSetpoint(cc, clamp(value, SETPOINT_MIN, SETPOINT_MAX));
    }

    public static void setAcOn(Context c, boolean on) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeAc(cc, on);
    }

    public static void setSeatLevel(Context c, boolean driver, int level) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        writeIntFirst(cc, CarProps.SEAT_HEAT,
                new int[]{driver ? CarProps.AREA_SEAT_LEFT : CarProps.AREA_SEAT_RIGHT},
                clamp(level, 0, HEAT_MAX_LEVEL));
    }

    public static void setWheelLevel(Context c, int level) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeWheel(cc, clamp(level, 0, HEAT_MAX_LEVEL));
    }

    public static void setRearDefrostOn(Context c, boolean on) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeRearDefrost(cc, on);
    }

    public static void setFrontDefrostOn(Context c, boolean on) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) writeFrontDefrost(cc, on);
    }

    public static void setWindshieldHeatOn(Context c, boolean on) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) cc.writeBool(CarProps.HVAC_WINDSHIELD_HEAT, CarProps.AREA_HVAC_ALL, on);
    }

    public static Boolean readFrontDefrost(Context c) {
        CarConnection cc = CarConnection.get();
        return cc.ensure(c) ? readFrontDefrost(cc) : null;
    }

    // ---------------------------------------------------------------------------------
    // Быстрые режимы
    // ---------------------------------------------------------------------------------

    public static int engagedPreset() {
        return engaged;
    }

    public static boolean presetHeld(int preset, State s) {
        if (!Boolean.TRUE.equals(s.hvacOn)) return false;
        if (preset == PRESET_COOL) {
            return Boolean.TRUE.equals(s.acOn) && s.setpoint != null && s.setpoint == SETPOINT_MIN
                    && s.direction != null && s.direction == DIRECTION_FACE;
        }
        if (preset == PRESET_HEAT) {
            return s.setpoint != null && s.setpoint == SETPOINT_MAX
                    && s.direction != null && s.direction == DIRECTION_GLASS_FEET;
        }
        return false;
    }

    public static boolean presetActive(int preset, State s) {
        return engaged == preset && presetHeld(preset, s);
    }

    /**
     * Одно нажатие. Первое задаёт сценарий, повторное снимает его: обогревы гаснут,
     * кондиционер выключается, вентилятор уходит на вторую ступень, у обогрева
     * уставка становится 27 °C. Обдув и уставка охлаждения остаются на месте.
     */
    public static void applyPreset(Context c, State s, int preset) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return;
        boolean off = engaged == preset && presetHeld(preset, s);
        boolean cool = preset == PRESET_COOL;

        if (Boolean.TRUE.equals(readFrontDefrost(cc))) {
            writeFrontDefrost(cc, false);
        }
        if (!off && Boolean.FALSE.equals(s.hvacOn)) {
            cc.writeBool(CarProps.HVAC_POWER_ON, CarProps.AREA_ROW_1_ALL, true);
        }
        Double tempTarget = off ? (cool ? null : PRESET_HEAT_REPEAT)
                : (cool ? SETPOINT_MIN : SETPOINT_MAX);
        Integer directionTarget = off ? null : (cool ? DIRECTION_FACE : DIRECTION_GLASS_FEET);
        int fanTarget = off ? PRESET_FAN_REPEAT : PRESET_FAN;
        boolean acTarget = !off && cool;
        boolean heaters = !off && !cool;

        if (tempTarget != null && s.setpoint != null && s.setpoint.doubleValue() != tempTarget) {
            writeSetpoint(cc, tempTarget);
        }
        if (directionTarget != null && s.direction != null
                && s.direction.intValue() != directionTarget.intValue()) {
            writeDirection(cc, directionTarget);
        }
        if (s.fanSpeed != null && s.fanSpeed != fanTarget) {
            writeFan(cc, fanTarget);
        }
        if (s.acOn != null && s.acOn != acTarget) {
            writeAc(cc, acTarget);
        }
        int seatTarget = heaters ? HEAT_MAX_LEVEL : 0;
        int wheelTarget = heaters ? 1 : 0;
        if (s.seatDriver != null && s.seatDriver != seatTarget) {
            writeIntFirst(cc, CarProps.SEAT_HEAT,
                    new int[]{CarProps.AREA_SEAT_LEFT}, seatTarget);
        }
        if (s.seatPassenger != null && s.seatPassenger != seatTarget) {
            writeIntFirst(cc, CarProps.SEAT_HEAT,
                    new int[]{CarProps.AREA_SEAT_RIGHT}, seatTarget);
        }
        if (s.wheelHeat != null && s.wheelHeat != wheelTarget) {
            writeWheel(cc, wheelTarget);
        }
        if (s.rearDefrost != null && s.rearDefrost != heaters) {
            writeRearDefrost(cc, heaters);
        }
        if (s.windshieldHeat != null && s.windshieldHeat != heaters) {
            cc.writeBool(CarProps.HVAC_WINDSHIELD_HEAT, CarProps.AREA_HVAC_ALL, heaters);
        }
        engaged = off ? PRESET_NONE : preset;
    }

    /** Климат гаснет целиком, когда виджет теряет связь с машиной. */
    public static void resetEngaged() {
        engaged = PRESET_NONE;
    }

    // ---------------------------------------------------------------------------------
    // Цепочки записи
    // ---------------------------------------------------------------------------------

    private static void writeFan(CarConnection cc, int level) {
        // Зона 5 — каноническая по логу, остальные остаются запасом.
        writeIntFirst(cc, CarProps.HVAC_FAN_SPEED,
                new int[]{CarProps.AREA_ROW_1_ALL, CarProps.AREA_SEAT_LEFT,
                        CarProps.AREA_ROW_1_2, CarProps.AREA_GLOBAL}, level);
    }

    private static void writeAc(CarConnection cc, boolean on) {
        if (cc.writeBool(CarProps.HVAC_AC_ON_DA, CarProps.AREA_HVAC_ALL, on)) return;
        if (cc.writeBool(CarProps.HVAC_AC_ON_DA, CarProps.AREA_HVAC_DA, on)) return;
        if (writeBoolFirst(cc, CarProps.HVAC_AC_ON, CarProps.HVAC_WRITE_AREAS, on)) return;
        writeIntFirst(cc, CarProps.HVAC_AC_ON, CarProps.HVAC_WRITE_AREAS, on ? 1 : 0);
    }

    private static void writeSetpoint(CarConnection cc, double value) {
        Float adjusted = adjustTempC(value);
        if (adjusted == null) return;
        if (cc.writeFloat(CarProps.HVAC_TEMP_AOSP, CarProps.AREA_SEAT_LEFT, adjusted)) return;
        if (cc.writeFloat(CarProps.HVAC_TEMP_SET_CANONICAL, CarProps.AREA_GLOBAL, adjusted)) return;
        if (cc.writeFloat(CarProps.HVAC_TEMP_SET, CarProps.AREA_SEAT_LEFT, adjusted)) return;
        cc.writeFloat(CarProps.HVAC_TEMP_ADJUST, CarProps.AREA_SEAT_LEFT, adjusted);
    }

    private static void writeDirection(CarConnection cc, int mask) {
        if (cc.writeInt(CarProps.HVAC_FAN_DIRECTION_VENDOR, CarProps.AREA_GLOBAL, mask)) return;
        if (cc.writeInt(CarProps.HVAC_FAN_DIRECTION, CarProps.AREA_GLOBAL, mask)) return;
        cc.writeInt(CarProps.HVAC_FAN_DIRECTION, CarProps.AREA_SEAT_LEFT, mask);
    }

    private static void writeWheel(CarConnection cc, int level) {
        if (cc.writeInt(CarProps.STEERING_WHEEL_HEAT, CarProps.AREA_SEAT_LEFT, level)) return;
        cc.writeInt(CarProps.STEERING_WHEEL_HEAT, CarProps.AREA_GLOBAL, level);
    }

    private static void writeRearDefrost(CarConnection cc, boolean on) {
        if (cc.writeBool(CarProps.HVAC_REAR_DEFROSTER_ON, CarProps.AREA_REAR_DEFROST_DA, on)) return;
        cc.writeBool(CarProps.HVAC_REAR_DEFROSTER_ON, CarProps.AREA_GLOBAL, on);
    }

    private static void writeFrontDefrost(CarConnection cc, boolean on) {
        if (cc.writeBool(CarProps.HVAC_FRONT_MAX_DEFROST_ON, CarProps.AREA_HVAC_DA, on)) return;
        cc.writeBool(CarProps.HVAC_FRONT_MAX_DEFROST_ON, CarProps.AREA_ROW_1_ALL, on);
    }

    private static Boolean readFrontDefrost(CarConnection cc) {
        return readFlag(cc, CarProps.HVAC_FRONT_MAX_DEFROST_ON,
                new int[]{CarProps.AREA_HVAC_DA, CarProps.AREA_ROW_1_ALL});
    }

    // ---------------------------------------------------------------------------------
    // Чтение
    // ---------------------------------------------------------------------------------

    private static Integer readFan(CarConnection cc) {
        for (int area : CarProps.FAN_AREAS) {
            int v = cc.readInt(CarProps.HVAC_FAN_SPEED, area);
            if (v == Integer.MIN_VALUE) {
                float f = cc.readFloat(CarProps.HVAC_FAN_SPEED, area);
                if (Float.isNaN(f)) continue;
                v = Math.round(f);
            }
            if (v >= 0 && v <= FAN_MAX) return v;
        }
        return null;
    }

    private static Double readSetpoint(CarConnection cc) {
        int[][] floatChannels = {
                {CarProps.HVAC_TEMP_AOSP, CarProps.AREA_SEAT_LEFT},
                {CarProps.HVAC_TEMP_SET_CANONICAL, CarProps.AREA_GLOBAL},
                {CarProps.HVAC_TEMP_SET, CarProps.AREA_SEAT_LEFT},
                {CarProps.HVAC_TEMP_ADJUST, CarProps.AREA_SEAT_LEFT},
        };
        for (int[] channel : floatChannels) {
            Double value = climateTempC(cc.readFloat(channel[0], channel[1]));
            if (value != null) return value;
        }
        // Шаг 0x15600503 в зоне 1: шкала «°C − 16», только для показа.
        for (int area : CarProps.HVAC_WRITE_AREAS) {
            int v = cc.readInt(CarProps.HVAC_TEMP_AOSP, area);
            if (v == Integer.MIN_VALUE || v > 40) continue;
            Double value = climateTempC((double) v + 16.0);
            if (value != null) return value;
        }
        return null;
    }

    private static Integer readDirection(CarConnection cc) {
        Integer v = readMask(cc, CarProps.HVAC_FAN_DIRECTION_VENDOR,
                new int[]{CarProps.AREA_GLOBAL, CarProps.AREA_SEAT_LEFT});
        if (v != null) return v;
        return readMask(cc, CarProps.HVAC_FAN_DIRECTION, CarProps.FAN_DIRECTION_AREAS);
    }

    private static Integer readMask(CarConnection cc, int property, int[] areas) {
        for (int area : areas) {
            int v = cc.readInt(property, area);
            if (v == Integer.MIN_VALUE) {
                float f = cc.readFloat(property, area);
                if (Float.isNaN(f)) continue;
                v = Math.round(f);
            }
            if (isDirection(v)) return v;
        }
        return null;
    }

    private static boolean isDirection(int v) {
        return v == DIRECTION_FACE || v == DIRECTION_FEET || v == DIRECTION_FACE_FEET
                || v == DIRECTION_GLASS || v == DIRECTION_GLASS_FEET;
    }

    private static Integer readLevel(CarConnection cc, int property, int[] areas) {
        for (int area : areas) {
            int v = cc.readInt(property, area);
            if (v == Integer.MIN_VALUE) {
                float f = cc.readFloat(property, area);
                if (Float.isNaN(f)) continue;
                v = Math.round(f);
            }
            if (v >= 0 && v <= HEAT_MAX_LEVEL) return v;
        }
        return null;
    }

    private static Boolean readFlag(CarConnection cc, int property, int[] areas) {
        for (int area : areas) {
            Boolean b = cc.readBool(property, area);
            if (b != null) return b;
        }
        for (int area : areas) {
            int v = cc.readInt(property, area);
            if (v == Integer.MIN_VALUE) continue;
            if (v == 0 || v == 1 || v == 2) return v == 1;
        }
        return null;
    }

    private static boolean writeBoolFirst(CarConnection cc, int property, int[] areas, boolean value) {
        for (int area : areas) {
            if (cc.writeBool(property, area, value)) return true;
        }
        return false;
    }

    private static boolean writeIntFirst(CarConnection cc, int property, int[] areas, int value) {
        for (int area : areas) {
            if (cc.writeInt(property, area, value)) return true;
        }
        return false;
    }

    /** Принимается только уставка, кратная полуграду и лежащая в 15,5…33. */
    private static Double climateTempC(double raw) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return null;
        double quantized = Math.round(raw * 2.0) / 2.0;
        return quantized >= TEMP_MIN_C && quantized <= TEMP_MAX_C ? quantized : null;
    }

    private static Double climateTempC(float raw) {
        return Float.isNaN(raw) ? null : climateTempC((double) raw);
    }

    private static Float adjustTempC(double value) {
        Double checked = climateTempC(value);
        if (checked == null) return null;
        double quantized = Math.round(checked * 2.0) / 2.0;
        return (float) clamp(quantized, ADJUST_MIN_C, ADJUST_MAX_C);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
