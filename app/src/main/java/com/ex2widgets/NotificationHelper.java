package com.ex2widgets;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/**
 * Две постоянные службы держат виджеты живыми: опрос VHAL и окна наложения.
 * Android требует уведомления для каждой из них — оба молчаливые, без звука.
 * Приложение намеренно строится без androidx: достаточно платформенных API,
 * какие есть начиная с minSdk 28.
 */
public final class NotificationHelper {

    public static final int ID_WIDGETS = 21;
    public static final int ID_OVERLAY = 22;

    private static final String CHANNEL_WIDGETS = "widgets";
    private static final String CHANNEL_OVERLAY = "overlay";

    private NotificationHelper() {
    }

    public static Notification widgets(Context c) {
        return build(c, CHANNEL_WIDGETS, c.getString(R.string.channel_widgets),
                c.getString(R.string.notif_widgets),
                c.getString(R.string.notif_widgets_text));
    }

    public static Notification overlay(Context c) {
        return build(c, CHANNEL_OVERLAY, c.getString(R.string.channel_overlay),
                c.getString(R.string.notif_overlay),
                c.getString(R.string.notif_overlay_text));
    }

    private static Notification build(Context c, String channel, String channelName,
                                      String title, String text) {
        channel(c, channel, channelName);
        // Тишину даёт канал IMPORTANCE_MIN без звука: у платформенного
        // Builder нет setSilent, а приоритет ниже минимального не опускается.
        return new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat_widgets)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private static void channel(Context c, String id, String name) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(id) != null) return;
            NotificationChannel channel =
                    new NotificationChannel(id, name, NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            nm.createNotificationChannel(channel);
        } catch (Throwable ignored) {
        }
    }
}
