package com.ex2widgets;

import android.content.Context;
import android.util.Log;

/** Служебный журнал приложения: всё про виджеты, наложения и VHAL уходит в logcat. */
public final class LogStore {

    private static final String TAG = "EX2Widgets";

    private LogStore() {
    }

    public static void add(Context c, String message) {
        Log.i(TAG, message);
    }

    public static void warn(Context c, String message, Throwable error) {
        Log.w(TAG, message, error);
    }
}
