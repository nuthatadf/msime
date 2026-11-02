package app.msime.android;

import android.view.KeyEvent;
import java.util.List;

/** 四行侧栏与无缝方向区共用的动作、点击边界和分隔线；旧动作身份仍可读入。 */
public final class TextEditPanelModel {
    public static final int ROWS = 4;
    /** 横向分十份，两侧各占两份，中央区域占六份。 */
    public static final int COLUMNS = 10;
    public static final String ORDER_PREFERENCE = "touch_text_edit_button_order";

    /** 键的动作，IME 按它接线。 */
    public enum Action {
        LEFT, UP, SELECT, DOWN, RIGHT, SELECT_LINE, SELECT_ALL, COPY, CUT, PASTE, DOCUMENT_START, DOCUMENT_END, DELETE,
        NUMBER_PAD, SPACE, RETURN, LINE_START, LINE_END, SYMBOLS
    }

    /**
     * 一个键。
     *
     * @param label 键面文字
     * @param description 无障碍描述
     * @param row 起始行（从 0 起）
     * @param column 起始列（从 0 起）
     * @param rowSpan 占几行
     * @param columnSpan 占几个横份
     * @param repeats 按住是否连发（方向键和删除）
     */
    public record Key(Action action, String label, String description, int row, int column,
                      int rowSpan, int columnSpan, boolean repeats) {}

    private static final List<Key> KEYS = List.of(
        new Key(Action.LEFT, "‹", "光标左移", 0, 2, 3, 2, true),
        new Key(Action.UP, "⌃", "光标上移", 0, 4, 1, 2, true),
        new Key(Action.SELECT, "开始选择", "选择模式", 1, 4, 1, 2, false),
        new Key(Action.DOWN, "⌄", "光标下移", 2, 4, 1, 2, true),
        new Key(Action.RIGHT, "›", "光标右移", 0, 6, 3, 2, true),
        new Key(Action.SELECT_ALL, "全选", "全选", 0, 0, 1, 2, false),
        new Key(Action.COPY, "复制", "复制", 0, 0, 1, 2, false),
        new Key(Action.CUT, "剪切", "剪切", 0, 0, 1, 2, false),
        new Key(Action.PASTE, "粘贴", "粘贴", 0, 0, 1, 2, false),
        new Key(Action.DOCUMENT_START, "文首", "移到开头", 3, 2, 1, 3, false),
        new Key(Action.DOCUMENT_END, "文尾", "移到结尾", 3, 5, 1, 3, false),
        new Key(Action.DELETE, "⌫", "退格", 0, 0, 1, 2, true),
        new Key(Action.NUMBER_PAD, "123", "打开数字键盘", 0, 0, 1, 2, false),
        new Key(Action.SPACE, "空格", "插入空格", 0, 0, 1, 2, false),
        new Key(Action.RETURN, "换行", "插入换行，长按打开符号", 3, 8, 1, 2, false));

    private TextEditPanelModel() { }

    /** 七个可排序位置为左列四格、右列前三格；换行固定，旧选行动作不占位置。 */
    public static List<Key> keys(List<Action> order) {
        List<Action> visible = visibleOrder(order);
        java.util.ArrayList<Key> result = new java.util.ArrayList<>();
        for (Key key : KEYS) {
            int slot = visible.indexOf(key.action());
            if (slot < 0) result.add(key);
            else {
                int row = slot < 4 ? slot : slot - 4;
                int column = slot < 4 ? 0 : 8;
                result.add(new Key(key.action(), key.label(), key.description(),
                    row, column, 1, 2, key.repeats()));
            }
        }
        result.sort(java.util.Comparator.comparingInt(Key::row).thenComparingInt(Key::column));
        return List.copyOf(result);
    }

    public static boolean isSortable(Action action) {
        return action == Action.COPY || action == Action.CUT || action == Action.PASTE
            || action == Action.SELECT_ALL || action == Action.DELETE
            || action == Action.NUMBER_PAD || action == Action.SPACE;
    }

    public static List<Action> visibleOrder(List<Action> order) {
        java.util.ArrayList<Action> result = new java.util.ArrayList<>();
        for (Action action : order) if (isSortable(action)) result.add(action);
        return List.copyOf(result);
    }

    /** 旧偏好身份与未显示动作的位置不变，排序草稿只替换当前可见的七项。 */
    public static List<Action> mergeVisibleOrder(List<Action> original, List<Action> visible) {
        java.util.HashSet<Action> expected = new java.util.HashSet<>(visibleOrder(original));
        if (visible.size() != expected.size() || !expected.equals(new java.util.HashSet<>(visible)))
            throw new IllegalArgumentException("Incomplete text edit order");
        java.util.ArrayList<Action> result = new java.util.ArrayList<>();
        int index = 0;
        for (Action action : original)
            result.add(isSortable(action) ? visible.get(index++) : action);
        return List.copyOf(result);
    }

    public static boolean isCentral(Key key) { return key.column() > 0 && key.column() < 8; }

    public static Key key(Action action) {
        for (Key key : KEYS) if (key.action() == action) return key;
        throw new IllegalArgumentException("Unknown text edit action " + action);
    }

    /** 视图测量和布局直接使用的像素边界。 */
    public record Bounds(int left, int top, int right, int bottom) {
        public int width() { return Math.max(0, right - left); }
        public int height() { return Math.max(0, bottom - top); }
    }

    public static Bounds bounds(Key key, int width, int height, int margin) {
        if (isCentral(key)) {
            Bounds panel = centralBounds(width, height, margin);
            int left = key.column() == 2 ? panel.left() : width * key.column() / COLUMNS;
            int end = key.column() + key.columnSpan();
            int right = end == 8 ? panel.right() : width * end / COLUMNS;
            return new Bounds(left, seam(key.row(), height, margin), right,
                seam(key.row() + key.rowSpan(), height, margin));
        }
        int left = width * key.column() / COLUMNS;
        int right = width * (key.column() + key.columnSpan()) / COLUMNS;
        int top = height * key.row() / ROWS;
        int bottom = height * (key.row() + key.rowSpan()) / ROWS;
        int xInset = Math.min(margin, Math.max(0, (right - left - 1) / 2));
        int yInset = Math.min(margin, Math.max(0, (bottom - top - 1) / 2));
        return new Bounds(left + xInset, top + yInset, right - xInset, bottom - yInset);
    }

    public static Bounds centralBounds(int width, int height, int margin) {
        int inset = Math.min(Math.max(0, margin), Math.max(0, (height / ROWS - 1) / 2));
        return new Bounds(width * 2 / COLUMNS + inset, inset,
            width * 8 / COLUMNS - inset, height - inset);
    }

    /** 前两条缝对齐侧键间隙中线，末条缝对齐末排侧键上边，保证首尾排等高。 */
    public static int seam(int row, int height, int margin) {
        int inset = Math.min(Math.max(0, margin), Math.max(0, (height / ROWS - 1) / 2));
        if (row == 0) return inset;
        if (row == ROWS) return height - inset;
        return height * row / ROWS + (row == ROWS - 1 ? inset : 0);
    }

    /** 图标沿侧栏行中心定位，点击区域仍延伸到相邻分隔线。 */
    public static float contentCenterY(Key key, int height) {
        int row = key.action() == Action.LEFT || key.action() == Action.RIGHT ? 1 : key.row();
        return (height * row / ROWS + height * (row + 1) / ROWS) / 2f;
    }

    /** 交给编辑器的按键码；不走按键事件的动作返回 {@link KeyEvent#KEYCODE_UNKNOWN}。 */
    public static int keyCode(Action action) {
        return switch (action) {
            case LEFT -> KeyEvent.KEYCODE_DPAD_LEFT;
            case RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT;
            case UP -> KeyEvent.KEYCODE_DPAD_UP;
            case DOWN -> KeyEvent.KEYCODE_DPAD_DOWN;
            case DOCUMENT_START, LINE_START -> KeyEvent.KEYCODE_MOVE_HOME;
            case DOCUMENT_END, LINE_END -> KeyEvent.KEYCODE_MOVE_END;
            case DELETE -> KeyEvent.KEYCODE_DEL;
            case SPACE -> KeyEvent.KEYCODE_SPACE;
            case RETURN -> KeyEvent.KEYCODE_ENTER;
            default -> KeyEvent.KEYCODE_UNKNOWN;
        };
    }

    /** 移动键在选择模式下带 Shift；只有文档首尾始终带 Ctrl，行边界与删除不带 Ctrl。 */
    public static int metaState(Action action, boolean selecting) {
        int meta = 0;
        boolean moves = action == Action.LEFT || action == Action.RIGHT || action == Action.UP
            || action == Action.DOWN || action == Action.DOCUMENT_START || action == Action.DOCUMENT_END
            || action == Action.LINE_START || action == Action.LINE_END;
        if (moves && selecting) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (action == Action.DOCUMENT_START || action == Action.DOCUMENT_END)
            meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        return meta;
    }

    /** 交给 InputConnection.performContextMenuAction 的资源 id；不是菜单动作时为 0。 */
    public static int contextMenuAction(Action action) {
        return switch (action) {
            case SELECT_ALL -> android.R.id.selectAll;
            case COPY -> android.R.id.copy;
            case CUT -> android.R.id.cut;
            case PASTE -> android.R.id.paste;
            default -> 0;
        };
    }

    /** 这个动作之后选择模式是否关掉：剪切、粘贴和删除之后已经没有要延伸的选区了。 */
    public static boolean endsSelecting(Action action) {
        return action == Action.CUT || action == Action.PASTE || action == Action.DELETE;
    }
}
