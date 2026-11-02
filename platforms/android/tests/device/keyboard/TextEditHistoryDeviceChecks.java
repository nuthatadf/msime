package app.msime.android.test;

import android.graphics.Bitmap;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

/** 不能把未知撤销栈猜成空栈；密码限制则必须在面板标题行和读屏状态里即时反映。 */
final class TextEditHistoryDeviceChecks {
    private final DeviceSmoke t;

    TextEditHistoryDeviceChecks(DeviceSmoke test) { t = test; }

    void run() throws Exception {
        t.shellDiscard("am start -W -f 0x10008000 -n app.msime.android.test/app.msime.android.test.HistoryEditorActivity");
        open("history-first", true);
        capture("unknown");
        back();
        open("history-password", false);
        capture("password-disabled");
        back();
        open("history-unsupported", true);
        back();
        open("history-second", true);
        back();
    }

    private void open(String editor, boolean enabled) throws Exception {
        t.stage = "text editor history availability " + editor;
        t.tap(t.field(editor));
        t.await(t.field(editor).and(AccessibilityNodeInfo::isFocused));
        t.tap(t.described("文本编辑").and(AccessibilityNodeInfo::isClickable));
        t.await(t.described("文本编辑面板"));
        for (String action : new String[] {"撤销", "重做"})
            t.await(t.described(action).and(TextEditLineDeviceChecks::panelControl)
                .and(node -> node.isEnabled() == enabled));
    }

    private void back() throws Exception {
        t.tap(t.described("返回键盘").and(TextEditLineDeviceChecks::panelControl));
    }

    private void capture(String mode) throws Exception {
        Bitmap image = t.automation.takeScreenshot();
        if (image == null) throw new AssertionError("History availability screenshot unavailable");
        try (var output = new FileOutputStream(new File(t.getTargetContext().getExternalFilesDir(null),
                "text-edit-history-" + mode + ".png"))) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output))
                throw new AssertionError("History availability screenshot failed");
        } finally { image.recycle(); }
    }
}
