package com.ex2widgets.widgets;

/**
 * Промежуточное состояние кузова. Команда переднего багажника подтверждается
 * только чтением капота, а оно может запаздывать: пока идёт окно ожидания,
 * виджет честно пишет «Запрос отправлен», а не «Открыт».
 */
public final class HubBody {

    private static final long REQUEST_WINDOW_MS = 20_000L;

    private static volatile long frunkRequestedAt;

    private HubBody() {
    }

    public static void markFrunkRequested() {
        frunkRequestedAt = System.currentTimeMillis();
    }

    public static void clearFrunkRequested() {
        frunkRequestedAt = 0L;
    }

    public static boolean frunkPending() {
        long at = frunkRequestedAt;
        return at != 0L && System.currentTimeMillis() - at < REQUEST_WINDOW_MS;
    }
}
