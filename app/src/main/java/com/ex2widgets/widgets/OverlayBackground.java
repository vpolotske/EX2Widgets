package com.ex2widgets.widgets;

import android.content.Context;
import android.view.View;

/**
 * Прозрачность наложения: окно — настоящая View, поэтому фон строится в коде
 * и ползунок работает точностью до процента, а не шагом готовых drawable.
 * Фон получают те же поля, что и у виджета: корень карточки или обе плитки
 * ряда «езда + рекуперация».
 */
final class OverlayBackground {

    private OverlayBackground() {
    }

    static void apply(View root, Context c, String key) {
        if (root == null) return;
        for (int id : WidgetCatalog.of(key).targetIds) {
            View view = root.getId() == id ? root : root.findViewById(id);
            // Отдельный drawable на каждое поле: общий экземпляр связал бы
            // плитки в одну перекрашиваемую пару.
            if (view != null) view.setBackground(WidgetsCommon.hubBgDrawable(c, key));
        }
    }
}
