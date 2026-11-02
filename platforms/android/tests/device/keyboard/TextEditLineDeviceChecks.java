package app.msime.android.test;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/** 真实编辑器中的文档边界、Shift 选择、返回、历史操作和换行手势。 */
final class TextEditLineDeviceChecks {
    private final DeviceSmoke t;

    TextEditLineDeviceChecks(DeviceSmoke test) { t = test; }

    void run() throws Exception {
        open("edit");
        t.stage = "document boundaries remain distinct from line boundaries";
        setSelection(16);
        tap("移到开头");
        selection(0, 0);
        tap("移到结尾");
        selection(TrackpadEditorActivity.TEXT.length(), TrackpadEditorActivity.TEXT.length());
        t.stage = "document boundaries extend selection";
        setSelection(16);
        tap("选择模式");
        tap("移到开头");
        selection(0, 16);
        tap("取消选择");
        setSelection(16);
        tap("选择模式");
        tap("移到结尾");
        selection(16, TrackpadEditorActivity.TEXT.length());
        tap("取消选择");
        t.stage = "header back preserves the editor selection and resets the toggle";
        tap("选择模式");
        tap("返回键盘");
        t.await(t.key("n"));
        selection(16, TrackpadEditorActivity.TEXT.length());
        tap("文本编辑");
        t.await(t.described("选择模式").and(node -> !node.isSelected()));
        selection(16, TrackpadEditorActivity.TEXT.length());
        for (String removed : new String[] {"选择当前行", "移到行首", "移到行尾", "打开符号面板"})
            for (AccessibilityWindowInfo window : t.automation.getWindows())
                if (t.find(window.getRoot(), t.described(removed).and(TextEditLineDeviceChecks::panelControl)) != null)
                    throw new AssertionError("Removed editing control remains visible: " + removed);

        open("wrapped");
        t.stage = "document boundaries inside a wrapped paragraph";
        String report = t.await(t.field("trackpad-status").and(node -> node.getText() != null
            && node.getText().toString().startsWith("wrap-anchor="))).getText().toString();
        String[] edges = report.replace("wrap-anchor=", "").replace("wrap-start=", "").replace("wrap-end=", "").split(";");
        int anchor = Integer.parseInt(edges[0]), start = Integer.parseInt(edges[1]), end = Integer.parseInt(edges[2]);
        if (start <= 0 || start >= anchor || end <= anchor || end >= TrackpadEditorActivity.WRAPPED_TEXT.length())
            throw new AssertionError("Synthetic paragraph did not wrap around the initial cursor: " + report);
        setSelection(anchor);
        tap("移到开头");
        selection(0, 0);
        setSelection(anchor);
        tap("移到结尾");
        selection(TrackpadEditorActivity.WRAPPED_TEXT.length(), TrackpadEditorActivity.WRAPPED_TEXT.length());

        open("terminal");
        t.stage = "document boundaries reach a terminal without surrounding text";
        tap("移到开头");
        t.await(t.field("trackpad-status").and(node -> DeviceSmoke.equalsText("0:122:12288,1:122:12288", node.getText())));
        tap("选择模式");
        tap("移到结尾");
        t.await(t.field("trackpad-status").and(node -> node.getText() != null
            && node.getText().toString().contains("0:123:12353,1:123:12353")));

        open("edit");
        t.stage = "return short tap inserts exactly one newline";
        setSelection(TrackpadEditorActivity.TEXT.length());
        Rect key = returnBounds();
        gesture(key, false);
        text(TrackpadEditorActivity.TEXT + "\n");
        t.stage = "header undo and redo reach the current editor";
        tap("撤销");
        text(TrackpadEditorActivity.TEXT);
        tap("重做");
        text(TrackpadEditorActivity.TEXT + "\n");
        t.stage = "return slide out cancels both actions";
        key = returnBounds();
        gesture(key, true);
        text(TrackpadEditorActivity.TEXT + "\n");
        if (visible("符号面板")) throw new AssertionError("Sliding out opened symbols");
        t.stage = "return long press opens symbols without a newline on release";
        key = returnBounds();
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, key.centerX(), key.centerY());
        try { t.await(t.described("符号面板")); }
        finally { touch(down, MotionEvent.ACTION_UP, key.centerX(), key.centerY()); }
        t.automation.waitForIdle(300, 5000);
        t.await(t.described("符号面板"));
        text(TrackpadEditorActivity.TEXT + "\n");
        if (visible("文本编辑面板")) throw new AssertionError("Text edit panel covers symbols");
    }

    private void open(String kind) throws Exception {
        t.shellDiscard("am start -W -f 0x10008000 -n app.msime.android.test/"
            + "app.msime.android.test.TrackpadEditorActivity --es kind " + kind);
        t.dropStaleNodes();
        t.await(t.field("trackpad-editor"));
        t.await(t.key("n"));
        tap("文本编辑");
        t.await(t.described("文本编辑面板"));
    }

    private void tap(String description) throws Exception {
        t.dropStaleNodes();
        t.tap(t.described(description).and(AccessibilityNodeInfo::isClickable)
            .and(node -> description.equals("文本编辑") || panelControl(node)));
    }

    static boolean panelControl(AccessibilityNodeInfo node) {
        for (AccessibilityNodeInfo parent = node.getParent(); parent != null; parent = parent.getParent())
            if (DeviceSmoke.equalsText("文本编辑面板", parent.getContentDescription())) return true;
        return false;
    }

    private void setSelection(int at) {
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, at);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, at);
        if (!t.await(t.field("trackpad-editor")).performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args))
            throw new AssertionError("Synthetic cursor setup failed");
        selection(at, at);
    }

    private void selection(int start, int end) {
        t.await(t.field("trackpad-editor").and(node ->
            Math.min(node.getTextSelectionStart(), node.getTextSelectionEnd()) == start
                && Math.max(node.getTextSelectionStart(), node.getTextSelectionEnd()) == end));
    }

    private void text(String expected) {
        t.await(t.field("trackpad-editor").and(node -> DeviceSmoke.equalsText(expected, node.getText())));
    }

    private Rect returnBounds() {
        Rect bounds = new Rect();
        t.awaitStableBounds(t.describedPrefix("插入换行")).getBoundsInScreen(bounds);
        return bounds;
    }

    private void gesture(Rect key, boolean cancel) throws Exception {
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, key.centerX(), key.centerY());
        float x = cancel ? key.left - key.width() : key.centerX();
        if (cancel) touch(down, MotionEvent.ACTION_MOVE, x, key.centerY());
        touch(down, MotionEvent.ACTION_UP, x, key.centerY());
        t.automation.waitForIdle(300, 5000);
    }

    private void touch(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            if (!t.automation.injectInputEvent(event, false)) throw new AssertionError("Synthetic touch rejected");
        } finally { event.recycle(); }
    }

    private boolean visible(String description) {
        t.dropStaleNodes();
        for (AccessibilityWindowInfo window : t.automation.getWindows())
            if (t.find(window.getRoot(), t.described(description)) != null) return true;
        return false;
    }
}
