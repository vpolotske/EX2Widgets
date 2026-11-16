package com.ex2widgets.widgets;

/** Панель климата со всеми обогревами в списке виджетов рабочего экрана. */
public final class HubClimateWidgetProvider extends BaseWidgetProvider {

    @Override
    protected String key() {
        return WidgetCatalog.CLIMATE;
    }
}
