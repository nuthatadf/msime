import android.view.KeyEvent;
import app.msime.android.TextEditPanelModel;
import app.msime.android.TextEditPanelModel.Action;
import app.msime.android.TextEditPanelModel.Key;
import app.msime.android.TextEditLineSelection;
import java.util.EnumSet;
import java.util.List;

public final class TextEditPanelModelSmoke {
    public static void main(String[] args) {
        List<Action> order = List.of(Action.COPY, Action.CUT, Action.PASTE, Action.SELECT_ALL,
            Action.DELETE, Action.NUMBER_PAD, Action.SPACE, Action.SELECT_LINE);
        check(TextEditPanelModel.keys(order).size() == 15, "十五个编辑动作必须同屏可达");
        Action lineStart = Action.LINE_START, lineEnd = Action.LINE_END;
        checkGrid(order);
        java.util.ArrayList<Action> reversed = new java.util.ArrayList<>(order);
        java.util.Collections.reverse(reversed);
        checkGrid(reversed);
        Action[][] grid = grid(order);
        check(grid[0][0] == Action.COPY && grid[1][0] == Action.CUT && grid[2][0] == Action.PASTE
            && grid[3][0] == Action.SELECT_ALL && grid[0][8] == Action.DELETE
            && grid[1][8] == Action.NUMBER_PAD && grid[2][8] == Action.SPACE, "精简侧栏分组");
        check(grid[0][4] == Action.UP && grid[0][2] == Action.LEFT
            && grid[1][4] == Action.SELECT && grid[0][6] == Action.RIGHT && grid[2][4] == Action.DOWN,
            "方向键围绕选择，左右键填满中央区域");
        check(grid[3][2] == Action.DOCUMENT_START && grid[3][5] == Action.DOCUMENT_END
            && grid[3][8] == Action.RETURN, "文首尾与换行位于固定位置");
        check(TextEditPanelModel.key(Action.SELECT).label().equals("开始选择"), "选择入口说明动作");
        List<Action> legacy = List.of(Action.COPY, Action.SELECT_LINE, Action.PASTE, Action.SELECT_ALL,
            Action.CUT, Action.SPACE, Action.NUMBER_PAD, Action.DELETE);
        List<Action> visible = TextEditPanelModel.visibleOrder(legacy);
        check(visible.size() == 7 && !visible.contains(Action.SELECT_LINE), "旧动作不占可见排序位置");
        java.util.ArrayList<Action> changed = new java.util.ArrayList<>(visible);
        java.util.Collections.reverse(changed);
        List<Action> saved = TextEditPanelModel.mergeVisibleOrder(legacy, changed);
        check(saved.get(1) == Action.SELECT_LINE && saved.size() == 8
            && TextEditPanelModel.visibleOrder(saved).equals(changed), "保存新顺序保留旧身份及位置");
        try {
            TextEditPanelModel.mergeVisibleOrder(legacy, List.of(Action.COPY, Action.COPY));
            throw new AssertionError("重复和缺项排列必须被拒绝");
        } catch (IllegalArgumentException expected) { }
        for (int[] size : new int[][] {{240, 160}, {320, 199}, {402, 238}, {401, 239}, {600, 301}, {1200, 160}}) {
            List<Key> keys = TextEditPanelModel.keys(order);
            for (int index = 0; index < keys.size(); index++) {
                var bounds = TextEditPanelModel.bounds(keys.get(index), size[0], size[1], 3);
                check(bounds.left() >= 0 && bounds.top() >= 0 && bounds.right() <= size[0]
                    && bounds.bottom() <= size[1] && bounds.width() > 0 && bounds.height() > 0,
                    "窄宽与横屏布局保持有效点击范围");
                for (int previous = 0; previous < index; previous++) {
                    var other = TextEditPanelModel.bounds(keys.get(previous), size[0], size[1], 3);
                    check(Math.min(bounds.right(), other.right()) <= Math.max(bounds.left(), other.left())
                        || Math.min(bounds.bottom(), other.bottom()) <= Math.max(bounds.top(), other.top()),
                        "真实布局函数不能让点击范围相交");
                }
            }
            checkAlignment(keys, size[0], size[1], 3);
        }

        // 方向键和删除连发，其余不连发。
        for (Key key : TextEditPanelModel.keys(order)) {
            boolean repeats = EnumSet.of(Action.LEFT, Action.RIGHT, Action.UP, Action.DOWN, Action.DELETE)
                .contains(key.action());
            check(key.repeats() == repeats, "repeat flag of " + key.action());
        }

        check(TextEditPanelModel.keyCode(Action.LEFT) == KeyEvent.KEYCODE_DPAD_LEFT
            && TextEditPanelModel.keyCode(Action.RIGHT) == KeyEvent.KEYCODE_DPAD_RIGHT
            && TextEditPanelModel.keyCode(Action.UP) == KeyEvent.KEYCODE_DPAD_UP
            && TextEditPanelModel.keyCode(Action.DOWN) == KeyEvent.KEYCODE_DPAD_DOWN, "arrow key codes");
        check(TextEditPanelModel.keyCode(Action.DOCUMENT_START) == KeyEvent.KEYCODE_MOVE_HOME
            && TextEditPanelModel.keyCode(Action.DOCUMENT_END) == KeyEvent.KEYCODE_MOVE_END, "home and end");
        check(TextEditPanelModel.keyCode(Action.DELETE) == KeyEvent.KEYCODE_DEL, "delete is backspace");
        check(TextEditPanelModel.keyCode(Action.COPY) == KeyEvent.KEYCODE_UNKNOWN
            && TextEditPanelModel.keyCode(Action.SELECT) == KeyEvent.KEYCODE_UNKNOWN, "menu actions send no key");

        int shift = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        int ctrl = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        check(TextEditPanelModel.keyCode(lineStart) == KeyEvent.KEYCODE_MOVE_HOME
            && TextEditPanelModel.keyCode(lineEnd) == KeyEvent.KEYCODE_MOVE_END, "行边界使用 Home 和 End");
        check(TextEditPanelModel.metaState(lineStart, false) == 0
            && TextEditPanelModel.metaState(lineEnd, false) == 0, "行边界不能携带整篇文档的 Ctrl");
        check(TextEditPanelModel.metaState(lineStart, true) == shift
            && TextEditPanelModel.metaState(lineEnd, true) == shift, "选择模式延伸到当前行边界");
        check(TextEditPanelModel.metaState(Action.LEFT, false) == 0, "plain arrow moves the caret");
        check(TextEditPanelModel.metaState(Action.LEFT, true) == shift
            && TextEditPanelModel.metaState(Action.DOWN, true) == shift, "selecting arrows carry shift");
        check(TextEditPanelModel.metaState(Action.DOCUMENT_START, false) == ctrl
            && TextEditPanelModel.metaState(Action.DOCUMENT_END, false) == ctrl, "start and end use the whole document");
        check(TextEditPanelModel.metaState(Action.DOCUMENT_END, true) == (ctrl | shift),
            "selecting to the end extends the selection");
        check(TextEditPanelModel.metaState(Action.DELETE, true) == 0, "delete carries no modifier");

        check(TextEditPanelModel.contextMenuAction(Action.SELECT_ALL) == android.R.id.selectAll
            && TextEditPanelModel.contextMenuAction(Action.COPY) == android.R.id.copy
            && TextEditPanelModel.contextMenuAction(Action.CUT) == android.R.id.cut
            && TextEditPanelModel.contextMenuAction(Action.PASTE) == android.R.id.paste, "clipboard menu ids");
        check(TextEditPanelModel.contextMenuAction(Action.LEFT) == 0
            && TextEditPanelModel.contextMenuAction(Action.SELECT) == 0
            && TextEditPanelModel.contextMenuAction(Action.SELECT_LINE) == 0, "other keys are not menu actions");

        check(TextEditPanelModel.key(Action.NUMBER_PAD).label().equals("123")
            && TextEditPanelModel.key(Action.SPACE).label().equals("空格")
            && TextEditPanelModel.key(Action.RETURN).label().equals("换行"),
            "all editor shortcuts are visible");
        check(TextEditPanelModel.keyCode(Action.SPACE) == KeyEvent.KEYCODE_SPACE
            && TextEditPanelModel.keyCode(Action.RETURN) == KeyEvent.KEYCODE_ENTER
            && TextEditPanelModel.keyCode(Action.NUMBER_PAD) == KeyEvent.KEYCODE_UNKNOWN,
            "editor shortcut key codes");

        check(TextEditPanelModel.endsSelecting(Action.CUT) && TextEditPanelModel.endsSelecting(Action.PASTE)
            && TextEditPanelModel.endsSelecting(Action.DELETE), "cut, paste and delete end selecting");
        check(!TextEditPanelModel.endsSelecting(Action.COPY) && !TextEditPanelModel.endsSelecting(Action.LEFT)
            && !TextEditPanelModel.endsSelecting(Action.SELECT_ALL)
            && !TextEditPanelModel.endsSelecting(Action.SELECT_LINE), "copy and moves keep selecting");

        TextEditLineSelection.Range line = TextEditLineSelection.range("甲乙\n丙丁\n戊", 3, 3);
        check(line.start() == 3 && line.end() == 5, "caret selects the current line");
        line = TextEditLineSelection.range("甲乙\n丙丁\n戊", 2, 2);
        check(line.start() == 0 && line.end() == 2, "newline selects the preceding line");
        line = TextEditLineSelection.range("甲乙\n丙丁\n戊", 6, 6);
        check(line.start() == 6 && line.end() == 7, "last line is bounded");
        System.out.println("Android text edit panel: fixed cross layout, reordered side actions, key events and clipboard actions passed");
    }

    private static Action[][] grid(List<Action> order) {
        EnumSet<Action> seen = EnumSet.noneOf(Action.class);
        Action[][] grid = new Action[TextEditPanelModel.ROWS][TextEditPanelModel.COLUMNS];
        for (Key key : TextEditPanelModel.keys(order)) {
            check(seen.add(key.action()), "one key per action: " + key.action());
            check(!key.label().isEmpty() && !key.description().isEmpty(), "labelled: " + key.action());
            for (int row = key.row(); row < key.row() + key.rowSpan(); row++) {
                for (int column = key.column(); column < key.column() + key.columnSpan(); column++) {
                    check(row < grid.length && column < grid[row].length, "inside grid");
                    check(grid[row][column] == null, "no overlapping keys: " + key.action()
                        + " at " + row + "," + column + " with " + grid[row][column]);
                    grid[row][column] = key.action();
                }
            }
        }
        EnumSet<Action> expected = EnumSet.allOf(Action.class);
        expected.removeAll(EnumSet.of(Action.LINE_START, Action.LINE_END, Action.SELECT_LINE, Action.SYMBOLS));
        check(seen.equals(expected), "只显示精简设计中的动作，旧动作身份保留");
        return grid;
    }

    private static void checkGrid(List<Action> order) {
        Action[][] grid = grid(order);
        List<Action> visible = TextEditPanelModel.visibleOrder(order);
        for (int index = 0; index < visible.size(); index++) {
            int row = index < 4 ? index : index - 4;
            check(grid[row][index < 4 ? 0 : 8] == visible.get(index), "自定义顺序映射到七个可排序位置");
        }
        for (Action[] row : grid) for (Action action : row)
            check(action != null, "固定网格没有孤立空行或空格");
    }

    private static void checkAlignment(List<Key> keys, int width, int height, int margin) {
        TextEditPanelModel.Bounds[] side = new TextEditPanelModel.Bounds[4];
        for (Key key : keys)
            if (key.column() == 0) side[key.row()] = TextEditPanelModel.bounds(key, width, height, margin);
        var panel = TextEditPanelModel.centralBounds(width, height, margin);
        check(panel.top() == side[0].top() && panel.bottom() == side[3].bottom(), "中央外框与侧栏两端齐平");
        for (int row = 1; row <= 3; row++)
            check(TextEditPanelModel.seam(row, height, margin) == side[row].top(),
                "三条分隔线均对齐下一排侧键上边");
        var up = TextEditPanelModel.bounds(TextEditPanelModel.key(Action.UP), width, height, margin);
        var select = TextEditPanelModel.bounds(TextEditPanelModel.key(Action.SELECT), width, height, margin);
        var down = TextEditPanelModel.bounds(TextEditPanelModel.key(Action.DOWN), width, height, margin);
        int shortest = Math.min(up.height(), Math.min(select.height(), down.height()));
        int tallest = Math.max(up.height(), Math.max(select.height(), down.height()));
        check(tallest - shortest <= 1, "上下与选择等高，只允许整数像素舍入差");
        for (Key key : keys) {
            if (!TextEditPanelModel.isCentral(key)) continue;
            var bounds = TextEditPanelModel.bounds(key, width, height, margin);
            if (key.action() == Action.DOCUMENT_START || key.action() == Action.DOCUMENT_END)
                check(bounds.top() == side[3].top() && bounds.height() == side[3].height(),
                    "文首尾排对齐末排侧键上边，视觉高度不能多出半格间隙");
            if (key.action() == Action.LEFT || key.action() == Action.RIGHT)
                check(Math.abs((bounds.top() + bounds.bottom()) / 2f
                    - (select.top() + select.bottom()) / 2f) <= .5f, "左右与选择的单元格中心同轴");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
