package com.ex2widgets.widgets;

import android.content.Context;
import android.content.res.Configuration;

import com.ex2widgets.R;

/**
 * Подписи и цвета, общие для отрисовки RemoteViews и обычного наложения:
 * один и тот же текст виджет показывает в обоих режимах.
 */
final class HubViews {

    private HubViews() {
    }

    /** Палитра HUB всегда тёмная: виджет выглядит одинаково на любом ГУ. */
    static Context theme(Context c) {
        try {
            Configuration cfg = new Configuration(c.getResources().getConfiguration());
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
            return c.createConfigurationContext(cfg);
        } catch (Throwable ignored) {
            return c;
        }
    }

    /** Уставка как в приложении: целый градус без дроби, половиной — с запятой. */
    static String setpointText(Double setpoint) {
        if (setpoint == null) return "\u2014";
        long whole = Math.round(setpoint);
        if (Math.abs(setpoint - whole) < 0.01) return whole + "\u00B0";
        return trim(setpoint) + "\u00B0";
    }

    static String fanText(Integer level) {
        if (level == null) return "\u2014";
        return level + " / " + HubClimate.FAN_MAX;
    }

    static int valueRes(Double setpoint) {
        return setpoint == null ? R.color.hub_dim : R.color.hub_value;
    }

    /** Наружная температура как в HUB: целый градус, плюс у положительной. */
    static String outsideText(float tempC) {
        if (Float.isNaN(tempC)) return "\u2014";
        int value = Math.round(tempC);
        return (value > 0 ? "+" + value : String.valueOf(value)) + "\u00B0";
    }

    /** Насколько опущено стекло: «45 %» либо прочерк, когда автомобиль не ответил. */
    static String percentText(int percent) {
        if (percent == Integer.MIN_VALUE) return "\u2014";
        return percent + "\u00A0%";
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2)
                : text.replace('.', ',');
    }
}
