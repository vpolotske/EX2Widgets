package com.ex2widgets;

import android.content.Context;
import android.content.SharedPreferences;

/** Настройки приложения: режим показа, размеры и прозрачность каждого виджета. */
public final class Prefs {

    public static final int WIDGET_MODE_UNSET = -1;
    public static final int WIDGET_MODE_STOCK = 0;
    public static final int WIDGET_MODE_THIRD_PARTY = 1;

    /** Положение панели ещё не двигали: брать место из макета. */
    public static final int POSITION_UNSET = Integer.MIN_VALUE;

    private static volatile SharedPreferences cached;

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        SharedPreferences local = cached;
        if (local != null) return local;
        synchronized (Prefs.class) {
            local = cached;
            if (local == null) {
                local = context.getApplicationContext()
                        .getSharedPreferences("ex2widgets", Context.MODE_PRIVATE);
                cached = local;
            }
            return local;
        }
    }

    public static int widgetBgOpacity(Context c) {
        return clamp(get(c).getInt("bg_opacity_pct", 100), 0, 100);
    }

    public static void setWidgetBgOpacity(Context c, int value) {
        get(c).edit().putInt("bg_opacity_pct", clamp(value, 0, 100)).apply();
    }

    public static boolean widgetEnabled(Context c, String key) {
        return get(c).getBoolean("widget_" + key + "_enabled", false);
    }

    public static void setWidgetEnabled(Context c, String key, boolean value) {
        get(c).edit().putBoolean("widget_" + key + "_enabled", value).apply();
    }

    public static int widgetSizePct(Context c, String key) {
        return clamp(get(c).getInt("widget_" + key + "_size_pct", 100), 50, 200);
    }

    public static void setWidgetSizePct(Context c, String key, int value) {
        get(c).edit().putInt("widget_" + key + "_size_pct", clamp(value, 50, 200)).apply();
    }

    public static int widgetAlphaPct(Context c, String key) {
        return clamp(get(c).getInt("widget_" + key + "_alpha_pct", 100), 0, 100);
    }

    public static void setWidgetAlphaPct(Context c, String key, int value) {
        get(c).edit().putInt("widget_" + key + "_alpha_pct", clamp(value, 0, 100)).apply();
    }

    /** Пиксели текущего экрана; POSITION_UNSET — панель ещё не сдвигали с макетного места. */
    public static int overlayX(Context c, String key) {
        return get(c).getInt("overlay_" + key + "_x", POSITION_UNSET);
    }

    public static int overlayY(Context c, String key) {
        return get(c).getInt("overlay_" + key + "_y", POSITION_UNSET);
    }

    public static void setOverlayPos(Context c, String key, int x, int y) {
        get(c).edit().putInt("overlay_" + key + "_x", x).putInt("overlay_" + key + "_y", y).apply();
    }

    /** Вернуть макетное место: сохранённые пиксели экрана больше не при чём. */
    public static void clearOverlayPos(Context c, String key) {
        get(c).edit().remove("overlay_" + key + "_x").remove("overlay_" + key + "_y").apply();
    }

    /**
     * Прятать ли панели, когда впереди приложение. Выключено — панели висят
     * всегда, как в 1.0.1: это аварийный режим на случай, если система не
     * говорит правду про передний экран.
     */
    public static boolean hideBehindApps(Context c) {
        return get(c).getBoolean("hide_behind_apps", true);
    }

    public static void setHideBehindApps(Context c, boolean value) {
        get(c).edit().putBoolean("hide_behind_apps", value).apply();
    }

    public static boolean overlayPinned(Context c, String key) {
        return get(c).getBoolean("overlay_" + key + "_pinned", false);
    }

    public static void setOverlayPinned(Context c, String key, boolean value) {
        get(c).edit().putBoolean("overlay_" + key + "_pinned", value).apply();
    }

    public static int widgetMode(Context c) {
        return get(c).getInt("widget_mode", WIDGET_MODE_UNSET);
    }

    public static void setWidgetMode(Context c, int mode) {
        get(c).edit().putInt("widget_mode", mode == WIDGET_MODE_STOCK
                ? WIDGET_MODE_STOCK : WIDGET_MODE_THIRD_PARTY).apply();
    }

    public static boolean widgetModeManual(Context c) {
        return get(c).getBoolean("widget_mode_manual", false);
    }

    public static void setWidgetModeManual(Context c, boolean value) {
        get(c).edit().putBoolean("widget_mode_manual", value).apply();
    }

    public static String widgetModeLauncher(Context c) {
        return get(c).getString("widget_mode_launcher", "");
    }

    public static void setWidgetModeLauncher(Context c, String pkg) {
        get(c).edit().putString("widget_mode_launcher", pkg == null ? "" : pkg).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
