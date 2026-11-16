package com.ex2widgets.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/**
 * Общая часть провайдеров: виджет рисуется тем же рендерером, что и все
 * остальные, а опрос VHAL забирает на себя WidgetUpdateService.
 */
abstract class BaseWidgetProvider extends AppWidgetProvider {

    /** Ключ виджета в WidgetCatalog — по нему выбираются разметка и настройки. */
    protected abstract String key();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = WidgetsRenderer.build(context, key());
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
        WidgetUpdateService.start(context);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdateService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetUpdateService.sync(context);
    }
}
