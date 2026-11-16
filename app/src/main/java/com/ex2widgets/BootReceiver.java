package com.ex2widgets;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ex2widgets.widgets.OverlayWidgetService;
import com.ex2widgets.widgets.WidgetModes;
import com.ex2widgets.widgets.WidgetUpdateService;

/** После загрузки и после обновления приложения виджеты возвращаются на экран. */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        LogStore.add(c, "boot: " + (intent == null ? "?" : intent.getAction()));
        WidgetModes.refreshAuto(c);
        WidgetUpdateService.sync(c);
        OverlayWidgetService.sync(c);
    }
}
