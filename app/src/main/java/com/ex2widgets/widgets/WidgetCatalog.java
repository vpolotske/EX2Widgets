package com.ex2widgets.widgets;

import android.appwidget.AppWidgetProvider;

import com.ex2widgets.R;

/**
 * Список виджетов приложения: один источник правды для размеров, мест по
 * умолчанию, семейства фона и провайдера. Каталог читают и настройки, и
 * наложения, поэтому виджет не может разъехаться с тем, что показывает
 * рабочий стол.
 */
public final class WidgetCatalog {

    public static final String CLIMATE = "hub_climate";
    public static final String DRIVE = "hub_drive";
    public static final String REGEN = "hub_regen";
    public static final String OUTSIDE_TEMP = "hub_outside_temp";
    public static final String WINDOWS = "hub_windows";
    public static final String FRUNK = "hub_frunk";

    /** Карточка рабочего стола EX2 HUB. */
    private static final float CARD_CORNER = 21.1f;

    /** Ряд карточек: у «езды и рекуперации» скругление меньше. */
    private static final float PANEL_CORNER = 15.5f;

    private static final Spec[] SPECS = {
            new Spec(CLIMATE, R.string.widget_name_hub_climate,
                    R.layout.widget_hub_climate, HubClimateWidgetProvider.class,
                    656, 1001, 1078, 31, CARD_CORNER, R.id.widget_bg_layer),
            new Spec(DRIVE, R.string.widget_name_hub_drive,
                    R.layout.widget_hub_drive, HubDriveWidgetProvider.class,
                    507, 186, 20, 706, PANEL_CORNER, R.id.widget_bg_layer),
            new Spec(REGEN, R.string.widget_name_hub_regen,
                    R.layout.widget_hub_regen, HubRegenWidgetProvider.class,
                    507, 186, 547, 706, PANEL_CORNER, R.id.widget_bg_layer),
            new Spec(OUTSIDE_TEMP, R.string.widget_name_hub_outside_temp,
                    R.layout.widget_hub_outside_temp, HubOutsideTempWidgetProvider.class,
                    235, 186, 20, 31, CARD_CORNER, R.id.widget_bg_layer),
            new Spec(WINDOWS, R.string.widget_name_hub_windows,
                    R.layout.widget_hub_windows, HubWindowsWidgetProvider.class,
                    660, 460, 20, 227, CARD_CORNER, R.id.widget_bg_layer),
            new Spec(FRUNK, R.string.widget_name_hub_frunk,
                    R.layout.widget_hub_frunk, HubFrunkWidgetProvider.class,
                    252, 186, 380, 31, CARD_CORNER, R.id.widget_bg_layer),
    };

    private WidgetCatalog() {
    }

    public static Spec[] all() {
        return SPECS.clone();
    }

    public static String[] keys() {
        String[] keys = new String[SPECS.length];
        for (int i = 0; i < SPECS.length; i++) keys[i] = SPECS[i].key;
        return keys;
    }

    public static Spec of(String key) {
        for (Spec spec : SPECS) {
            if (spec.key.equals(key)) return spec;
        }
        return SPECS[0];
    }

    public static Class<?>[] providers() {
        Class<?>[] list = new Class<?>[SPECS.length];
        for (int i = 0; i < SPECS.length; i++) list[i] = SPECS[i].provider;
        return list;
    }

    /**
     * Один виджет каталога. `targetIds` — какие поля получают фон по ползунку
     * прозрачности: обычно корень, а у ряда «езда + рекуперация» две карточки.
     */
    public static final class Spec {
        public final String key;
        public final int nameRes;
        public final int layoutRes;
        public final Class<? extends AppWidgetProvider> provider;
        public final int widthDp;
        public final int heightDp;
        public final int defaultX;
        public final int defaultY;
        public final float cornerDp;
        public final int[] targetIds;

        Spec(String key, int nameRes, int layoutRes,
             Class<? extends AppWidgetProvider> provider,
             int widthDp, int heightDp, int defaultX, int defaultY,
             float cornerDp, int... targetIds) {
            this.key = key;
            this.nameRes = nameRes;
            this.layoutRes = layoutRes;
            this.provider = provider;
            this.widthDp = widthDp;
            this.heightDp = heightDp;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.cornerDp = cornerDp;
            this.targetIds = targetIds;
        }

        /** Семейство фона: ряд карточек или одиночная карточка. */
        public boolean panel() {
            return cornerDp == PANEL_CORNER;
        }
    }
}
