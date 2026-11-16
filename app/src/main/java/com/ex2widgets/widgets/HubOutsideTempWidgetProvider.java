package com.ex2widgets.widgets;

/** Карточка наружного воздуха в списке виджетов рабочего экрана. */
public final class HubOutsideTempWidgetProvider extends BaseWidgetProvider {

    @Override
    protected String key() {
        return WidgetCatalog.OUTSIDE_TEMP;
    }
}
