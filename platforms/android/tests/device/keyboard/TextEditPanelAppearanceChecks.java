package app.msime.android.test;

import android.view.View;
import android.widget.Button;
import app.msime.android.KeyboardTypographyPolicy;

/** 在实际 Android 字体测量中检验窄浮动单元格，不能靠截图缩放掩盖文字贴线。 */
final class TextEditPanelAppearanceChecks {
    private TextEditPanelAppearanceChecks() { }

    static void selectionInsets(DeviceSmoke test) throws Exception {
        Class<?> type = test.getTargetContext().getClassLoader().loadClass("app.msime.android.TextEditPanelButton");
        Throwable[] failure = new Throwable[1];
        test.runOnMainSync(() -> {
            try {
                Button button = (Button) type.getConstructor(android.content.Context.class)
                    .newInstance(test.getTargetContext());
                button.setLayoutParams(new android.view.ViewGroup.LayoutParams(0, 0));
                KeyboardTypographyPolicy.normalizeKeyCap(button);
                button.setAllCaps(false);
                KeyboardTypographyPolicy.setFittedKeyTextSize(button, 11, 16);
                button.setPadding(0, 0, 0, 0);
                type.getMethod("setSeamless", int.class).invoke(button, 0xff335577);
                float density = test.getTargetContext().getResources().getDisplayMetrics().density;
                for (String label : new String[] {"开始选择", "取消选择"}) {
                    button.setText(label);
                    for (int widthDp : new int[] {58, 64, 80}) {
                        int width = Math.round(widthDp * density), height = Math.round(44 * density);
                        button.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                        button.layout(0, 0, width, height);
                        float textWidth = button.getLayout().getLineWidth(0);
                        if (button.getPaddingLeft() < Math.round(6 * density)
                                || button.getPaddingRight() < Math.round(6 * density)
                                || button.getLayout().getLineCount() != 1
                                || textWidth > width - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight() + .5f)
                            throw new AssertionError("Selection text touches its cell edge at " + widthDp + "dp");
                    }
                }
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError("Selection content inset failed: " + failure[0], failure[0]);
    }
}
