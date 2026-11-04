package app.msime.android;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** 普通键盘与文本编辑共用底栏几何，中间槽位由各自功能填充。 */
public final class KeyboardBottomBarLayout extends LinearLayout {
    public KeyboardBottomBarLayout(Context context, View start, View center, View end) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        addView(start, edgeParams(context));
        addView(center, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        addView(end, edgeParams(context));
    }

    private static LayoutParams edgeParams(Context context) {
        LayoutParams params = new LayoutParams(DimensionPolicy.pixels(context, 56), LayoutParams.MATCH_PARENT);
        params.setMarginStart(DimensionPolicy.pixels(context, 8));
        params.setMarginEnd(DimensionPolicy.pixels(context, 8));
        return params;
    }
}
