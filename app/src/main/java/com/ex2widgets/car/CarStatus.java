package com.ex2widgets.car;

import android.content.Context;

import com.ex2widgets.LogStore;

/**
 * Чтения и записи VHAL, нужные виджетам.
 *
 * Проверено на живом ГУ: режим движения, рекуперация и наружная температура
 * (чтение и запись), состояние стёкол и капота (чтение). Записи стёклам и
 * команда переднего багажника на машине ещё не подтверждались — они уходят той
 * же цепочкой кандидатов, что и EX2 HUB, и виджет честно показывает
 * неподтверждённое состояние до тех пор, пока автомобиль не ответит чтением.
 */
public final class CarStatus {

    private static final long WRITE_SETTLE_MS = 450L;
    private static final long WRITE_CHAIN_MS = 250L;

    private CarStatus() {
    }

    // ------------------------------------------------------------------ чтение

    public static int driveNow(Context c) {
        return readFirst(c, CarProps.DRIVE_MODE);
    }

    public static int regenNow(Context c) {
        return readFirst(c, CarProps.REGEN_MODE);
    }

    public static int gearNow(Context c) {
        return readFirst(c, CarProps.GEAR);
    }

    /** Наружная температура в °C или NaN: сначала вендорский датчик, потом AOSP. */
    public static float outsideTempC(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Float.NaN;

        int amb = cc.readInt(CarProps.AC_AMBIENT_TEMP, 0);
        float env = cc.readFloat(CarProps.ENV_OUTSIDE_TEMPERATURE, 0);
        if (Float.isNaN(env)) {
            int raw = cc.readInt(CarProps.ENV_OUTSIDE_TEMPERATURE, 0);
            if (raw != Integer.MIN_VALUE) env = raw;
        }
        float result = Float.NaN;
        if (amb != Integer.MIN_VALUE && amb != 0) {
            float t = (amb - 80) / 2.0f;
            if (t > -50f && t < 70f) result = t;
        }
        if (Float.isNaN(result) && !Float.isNaN(env) && env != 0f) {
            // EX2 отдаёт ENV_OUTSIDE_TEMPERATURE в десятых долях градуса.
            float t = env / 10f;
            if (t > -60f && t < 90f) result = t;
        }
        if (Float.isNaN(result)) {
            LogStore.add(c, "outside temp unknown amb=" + amb + " env=" + env);
        }
        return result;
    }

    /** Процент опускания стекла зоны; Integer.MIN_VALUE — автомобиль не ответил. */
    public static int windowPercent(Context c, int area) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Integer.MIN_VALUE;
        int value = cc.readInt(CarProps.WINDOW_POSITION, area);
        return value < 0 || value > 100 ? Integer.MIN_VALUE : value;
    }

    /** Капот: 1 — открыт, 2 — закрыт; null — состояния нет. */
    public static Boolean hoodOpen(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return null;
        return doorOpen(cc.readInt(CarProps.DOOR_POS, CarProps.AREA_DOOR_HOOD));
    }

    public static Boolean doorOpen(int raw) {
        if (raw == 1) return Boolean.TRUE;
        if (raw == 2) return Boolean.FALSE;
        return null;
    }

    // ------------------------------------------------------------------- запись

    /** Режим движения: запись по всем рабочим зонам и чтение обратно. */
    public static boolean applyDrive(Context c, int value) {
        return applyMode(c, CarProps.DRIVE_MODE, value, WRITE_SETTLE_MS);
    }

    public static boolean applyRegen(Context c, int value) {
        return applyMode(c, CarProps.REGEN_MODE, value, WRITE_CHAIN_MS);
    }

    /** Стекло: 0 — закрыть, WINDOW_OPEN_PERCENT — открыть. */
    public static boolean setWindow(Context c, int area, int percent) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return false;
        int value = Math.max(0, Math.min(CarProps.WINDOW_OPEN_PERCENT, percent));
        boolean ok = cc.writeInt(CarProps.WINDOW_POSITION, area, value);
        LogStore.add(c, "window area=" + area + " -> " + value + " ok=" + ok);
        return ok;
    }

    /**
     * Открытие переднего багажника: цепочка кандидатов EX2 HUB — сначала
     * FRONT_TRUNK_SWT, затем замок капота. Достаточно одной команды, которая
     * прошла без отказа; подтверждением служит состояние капота в DOOR_POS.
     */
    public static boolean openFrunk(Context c) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return false;
        boolean any = false;
        if (cc.writeBool(CarProps.FRONT_TRUNK_SWT, CarProps.AREA_GLOBAL, true)) {
            any = true;
        } else {
            sleep(WRITE_CHAIN_MS);
            if (cc.writeInt(CarProps.FRONT_TRUNK_SWT, CarProps.AREA_GLOBAL, 1)) any = true;
        }
        if (!any) {
            sleep(WRITE_CHAIN_MS);
            if (cc.writeBool(CarProps.LOCK_HOOD, CarProps.AREA_GLOBAL, true)) {
                any = true;
            } else {
                sleep(WRITE_CHAIN_MS);
                any = cc.writeInt(CarProps.LOCK_HOOD, CarProps.AREA_GLOBAL, 1);
            }
        }
        LogStore.add(c, "frunk open request sent=" + any);
        return any;
    }

    private static boolean applyMode(Context c, int property, int value, long settleMs) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return false;
        boolean written = false;
        for (int area : CarProps.AREAS) {
            if (cc.writeInt(property, area, value)) written = true;
        }
        sleep(settleMs);
        int read = readFirst(c, property);
        boolean exact = read == value;
        LogStore.add(c, "write prop=" + property + " value=" + value
                + " sent=" + written + " read=" + read + " exact=" + exact);
        return written;
    }

    private static int readFirst(Context c, int property) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(c)) return Integer.MIN_VALUE;
        for (int area : CarProps.AREAS) {
            int value = cc.readInt(property, area);
            if (value != Integer.MIN_VALUE) return value;
        }
        return Integer.MIN_VALUE;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
