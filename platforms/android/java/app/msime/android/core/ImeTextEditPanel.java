package app.msime.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** 文本编辑的四键侧栏、无缝方向区、选择和文档边界；编辑动作交给当前编辑器。 */
final class ImeTextEditPanel {
    private final MSIMEInputService s;
    private Button selectionButton;
    private boolean selecting;
    private Runnable repeatTask;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private TextView previewText;
    private Button dismissPreview;
    private Button previewAction;
    private final List<KeyboardShortcutButton> shortcuts = new ArrayList<>();
    private Button undoHistoryButton;
    private Button redoHistoryButton;
    private LinearLayout header;
    private int headerHeight;
    private int headerIconPercent = 100;
    private int headerButtonWidth;

    ImeTextEditPanel(MSIMEInputService service) { s = service; }

    LinearLayout build() {
        LinearLayout panel = LayoutPolicy.column(s);
        panel.setContentDescription("文本编辑面板");
        ViewPolicy.setClickable(panel, true);
        populate(panel);
        ViewPolicy.hide(panel);
        return panel;
    }

    private void populate(LinearLayout panel) {
        stopRepeat();
        selectionButton = null;
        shortcuts.clear();
        undoHistoryButton = null;
        redoHistoryButton = null;
        panel.removeAllViews();
        KeyboardSkin skin = s.imeStyler.themed(s.skin);
        header = LayoutPolicy.row(s);
        header.setContentDescription("文本编辑标题栏");
        header.addView(icon(KeyboardIconPaths.Icon.CHEVRON, 90, "返回键盘", this::backToKeyboard), headerCell());
        TextView title = ViewPolicy.textLabel(s, "文本编辑", 16);
        title.setSingleLine(true);
        ViewPolicy.clearFontPadding(title);
        KeyboardTypographyPolicy.setFittedKeyTextSize(title, 10, 16);
        ViewPolicy.setCenteredVertically(title);
        ViewPolicy.setTextColor(title, Color.parseColor(skin.keyForeground()));
        header.addView(title, LayoutPolicy.weightedMatchParentParams(1));
        header.addView(history(false), headerCell());
        header.addView(history(true), headerCell());
        int padding = s.pixels(4);
        LinearLayout.LayoutParams headerParams = LayoutPolicy.matchWidthHeightPx(s.pixels(44));
        headerParams.setMargins(padding, padding, padding, 0);
        panel.addView(header, headerParams);
        resizeHeader(headerHeight > 0 ? headerHeight : s.pixels(44), headerIconPercent,
            headerButtonWidth > 0 ? headerButtonWidth : s.pixels(44));

        TextEditPanelGrid grid = new TextEditPanelGrid(s);
        grid.setSkin(skin);
        org.json.JSONObject preferences = s.preferencesSnapshot == null
            ? null : s.preferencesSnapshot.optJSONObject("preferences");
        for (TextEditPanelModel.Key spec : TextEditPanelModel.keys(TextEditButtonOrder.fromPreferences(preferences)))
            grid.addKey(spec, key(spec));
        LinearLayout.LayoutParams gridParams = LayoutPolicy.weightedWidthParams(1);
        gridParams.setMargins(padding, 0, padding, 0);
        panel.addView(grid, gridParams);

        Button switchIme = shortcut(KeyboardShortcutIconPolicy.Icon.GLOBE, "切换输入法", () -> {
            InputMethodManager manager = s.getSystemService(InputMethodManager.class);
            if (manager != null) manager.showInputMethodPicker();
        });
        LinearLayout preview = LayoutPolicy.row(s);
        previewText = ViewPolicy.textLabel(s, null, 12);
        ViewPolicy.setCentered(previewText);
        ViewPolicy.setTextColor(previewText, Color.parseColor(skin.keyForeground()));
        previewText.setMaxLines(2);
        preview.addView(previewText, LayoutPolicy.weightedMatchParentParams(1));
        previewAction = s.keyboardKey("", "", s::runRecentClipQuickAction);
        KeyboardTypographyPolicy.setFittedKeyTextSize(previewAction, 10, 12);
        preview.addView(previewAction, headerCell());
        dismissPreview = s.keyboardKey("×", "不再显示这条复制的内容", s::dismissRecentClip);
        dismissPreview.setContentDescription("不再显示这条复制的内容");
        preview.addView(dismissPreview, headerCell());
        Button clipboard = shortcut(KeyboardShortcutIconPolicy.Icon.CLIPBOARD, "剪贴板",
            s.imeToolbar.panelToggle(() -> s.clipboardSurface, s.imePanels::showClipboardHistory));
        LinearLayout footer = new KeyboardBottomBarLayout(s, switchIme, preview, clipboard);
        footer.setContentDescription("文本编辑底栏");
        panel.addView(footer, LayoutPolicy.matchWidthHeightPx(s.pixels(KeyboardBottomBarPolicy.BAR_HEIGHT_DP)));
        styleShortcuts(Color.parseColor(s.skin.toolbarIcon()), Color.parseColor(s.skin.toolbarActiveIcon()),
            Color.parseColor(s.skin.toolbarActiveBackground()));
    }

    private LinearLayout.LayoutParams headerCell() {
        return LayoutPolicy.linearParamsPx(s.pixels(44), LinearLayout.LayoutParams.MATCH_PARENT);
    }

    /** 复用普通工具栏已计算的行高和图标比例；面板重建后仍沿用当前尺寸。 */
    void resizeHeader(int height, int iconPercent, int buttonWidth) {
        headerHeight = height;
        headerIconPercent = iconPercent;
        headerButtonWidth = buttonWidth;
        if (header == null) return;
        ViewPolicy.setFixedHeight(header, height);
        for (int index : new int[] {0, 2, 3}) {
            View button = header.getChildAt(index);
            if (button.getLayoutParams().width != buttonWidth) {
                button.getLayoutParams().width = buttonWidth;
                button.requestLayout();
            }
            if (button instanceof KeyboardShortcutButton shortcut) shortcut.setSizePercent(iconPercent);
            if (button instanceof TextEditIconButton back) back.setGlyphSize(24 * iconPercent / 100f);
        }
    }

    private Button icon(KeyboardIconPaths.Icon glyph, float rotation, String description, Runnable action) {
        TextEditIconButton button = new TextEditIconButton(s, glyph, rotation);
        button.setText(description);
        button.setContentDescription(description);
        button.setKeyboardRole(KeyboardKeyRole.PLAIN);
        s.imeStyler.styleButton(button, false);
        s.bindCountedAction(button, action);
        return button;
    }

    private Button history(boolean redo) {
        Button button = shortcut(redo ? KeyboardShortcutIconPolicy.Icon.REDO : KeyboardShortcutIconPolicy.Icon.UNDO,
            redo ? "重做" : "撤销", () -> s.imeToolbar.performHistory(redo));
        if (redo) redoHistoryButton = button;
        else undoHistoryButton = button;
        return button;
    }

    private Button shortcut(KeyboardShortcutIconPolicy.Icon glyph, String description, Runnable action) {
        KeyboardShortcutButton button = new KeyboardShortcutButton(s, glyph);
        button.setText(description);
        button.setContentDescription(description);
        s.imeStyler.styleButton(button, false);
        s.bindCountedAction(button, action);
        shortcuts.add(button);
        return button;
    }

    /** 与普通工具栏和底栏共用图标色、激活色和柔和底块。 */
    void styleShortcuts(int idle, int active, int fill) {
        for (KeyboardShortcutButton button : shortcuts) {
            button.setIconColors(idle, active);
            button.setActiveFill(fill);
        }
        boolean enabled = s.imeToolbar.canDispatchHistory();
        if (undoHistoryButton != null) ViewPolicy.setEnabled(undoHistoryButton, enabled);
        if (redoHistoryButton != null) ViewPolicy.setEnabled(redoHistoryButton, enabled);
    }

    private Button key(TextEditPanelModel.Key spec) {
        TextEditPanelModel.Action action = spec.action();
        Runnable run = () -> perform(action);
        Button button;
        if (action == TextEditPanelModel.Action.DELETE) {
            button = s.backspaceKey(run);
        } else if (action == TextEditPanelModel.Action.RETURN) {
            button = s.iconKey(KeyboardIconKey.Kind.RETURN, spec.label(), spec.description(), run);
            button.setOnLongClickListener(ignored -> { openSymbols(); return true; });
        } else if (action == TextEditPanelModel.Action.SELECT) {
            TextEditPanelButton select = new TextEditPanelButton(s);
            KeyboardTypographyPolicy.normalizeKeyCap(select);
            ViewPolicy.setAllCapsFalse(select);
            select.setText(spec.label());
            select.setKeyboardRole(KeyboardKeyRole.PLAIN);
            s.imeStyler.styleButton(select, false);
            s.bindCountedAction(select, run);
            KeyboardTypographyPolicy.setFittedKeyTextSize(select, 11, 16);
            selectionButton = select;
            button = select;
        } else if (action == TextEditPanelModel.Action.DOCUMENT_START
                || action == TextEditPanelModel.Action.DOCUMENT_END) {
            button = icon(KeyboardIconPaths.Icon.EDIT_DOCUMENT_START,
                action == TextEditPanelModel.Action.DOCUMENT_END ? 180 : 0, spec.description(), run);
            ((TextEditIconButton) button).setGlyphSize(28);
        } else if (isCursorMove(action)) {
            float rotation = switch (action) {
                case UP -> 180;
                case LEFT -> 90;
                case RIGHT -> -90;
                default -> 0;
            };
            button = icon(KeyboardIconPaths.Icon.CHEVRON, rotation, spec.description(), run);
            ((TextEditIconButton) button).setGlyphSize(28);
        } else {
            button = s.keyboardKey(spec.label(), spec.description(), run);
            KeyboardTypographyPolicy.setFittedKeyTextSize(button, 11, 16);
        }
        if (spec.repeats()) {
            if (action == TextEditPanelModel.Action.DELETE) s.imeLetterRows.bindBackspaceRepeat(button, run);
            else bindRepeat(button, run);
        }
        ViewPolicy.clearPadding(button);
        button.setContentDescription(spec.description());
        return button;
    }

    private static boolean isCursorMove(TextEditPanelModel.Action action) {
        return action == TextEditPanelModel.Action.LEFT || action == TextEditPanelModel.Action.UP
            || action == TextEditPanelModel.Action.DOWN || action == TextEditPanelModel.Action.RIGHT;
    }

    private void openSymbols() {
        if (!SymbolPanelEntryPolicy.opens(s.connection != null, s.symbolPanel != null)
                || s.textEditPanel == null || !s.textEditPanel.isShown()) return;
        close();
        s.imePanels.showSymbolPanel();
    }

    private void backToKeyboard() {
        s.closeToolbarPanels();
        s.closeCandidatePanel();
        s.render();
    }

    /** 按下立即执行一次，按住按 {@link BackspaceRepeatPolicy} 的节奏连发，松手或滑出键停止。 */
    private void bindRepeat(Button button, Runnable action) {
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    stopRepeat();
                    button.setPressed(true);
                    s.imeKeyFeedback.playFeedback(button);
                    action.run();
                    Runnable task = new Runnable() {
                        private int repeats;

                        @Override public void run() {
                            if (repeatTask != this || !button.isPressed()) return;
                            s.imeKeyFeedback.playFeedback(button);
                            action.run();
                            repeats++;
                            s.main.postDelayed(this, BackspaceRepeatPolicy.repeatInterval(repeats));
                        }
                    };
                    repeatTask = task;
                    s.main.postDelayed(task, BackspaceRepeatPolicy.INITIAL_DELAY_MS);
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (event.getX() < 0 || event.getY() < 0
                            || event.getX() >= button.getWidth() || event.getY() >= button.getHeight()) {
                        stopRepeat();
                        button.setPressed(false);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    stopRepeat();
                    button.setPressed(false);
                    return true;
                }
                default -> { return true; }
            }
        });
    }

    private void stopRepeat() {
        if (repeatTask != null) s.main.removeCallbacks(repeatTask);
        repeatTask = null;
    }

    /** 打开前完成组字；关闭与返回只清理面板状态，不改编辑器选区。 */
    void show() {
        if (s.connection == null || s.textEditPanel == null) {
            s.notice("当前没有可编辑的输入框");
            return;
        }
        if (s.session != 0 && s.hasEngineComposition()) s.command(2);
        s.closeToolbarPanels();
        s.closeCandidatePanel();
        selecting = false;
        populate(s.textEditPanel);
        s.imeStyler.applySkinBackground(s.textEditPanel);
        ViewPolicy.show(s.textEditPanel);
        renderState();
    }

    void close() {
        stopRepeat();
        selecting = false;
        if (s.textEditPanel != null) ViewPolicy.hide(s.textEditPanel);
        editingAccessibility(false);
    }

    private void renderState() {
        Button select = selectionButton;
        if (select == null) return;
        select.setText(selecting ? "取消选择" : "开始选择");
        select.setContentDescription(selecting ? "取消选择" : "选择模式");
        ViewPolicy.setSelected(select, selecting);
        select.setActivated(selecting);
        s.imeStyler.styleButton(select, KeyboardKeyRole.PLAIN, s.skin);
        if (android.os.Build.VERSION.SDK_INT >= 30)
            select.setStateDescription(selecting ? "已开启，方向键延伸选区" : "已关闭");
        refreshFooter();
    }

    void refreshFooter() {
        if (previewText == null) return;
        boolean visible = ViewPolicy.isVisible(s.textEditPanel);
        boolean preview = visible && ViewPolicy.isVisible(s.recentClipRow);
        previewText.setText(preview ? s.recentClipButton.getText() : null);
        previewText.setContentDescription(preview ? s.recentClipButton.getContentDescription() : null);
        previewText.setVisibility(preview ? View.VISIBLE : View.INVISIBLE);
        previewText.setImportantForAccessibility(preview ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
            : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        previewText.setOnClickListener(preview ? ignored -> s.recentClipButton.performClick() : null);
        previewText.setClickable(preview);
        ViewPolicy.setVisible(dismissPreview, preview);
        String action = preview ? s.recentClipQuickAction(s.recentImageSendable(System.currentTimeMillis())) : null;
        ViewPolicy.setVisible(previewAction, action != null);
        if (action != null) {
            previewAction.setText(RecentClipboardSuggestion.quickActionLabel(action));
            previewAction.setContentDescription(RecentClipboardSuggestion.quickActionDescription(action));
        }
        editingAccessibility(visible);
    }

    /** 覆盖的键盘不能仍被读屏选中；预览在底栏提供同一入口。 */
    private void editingAccessibility(boolean editing) {
        int mode = editing ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        s.imeFrame.setKeysAccessibility(mode);
        if (s.shortcutToolbar != null) s.shortcutToolbar.setImportantForAccessibility(mode);
        if (s.recentClipRow != null) s.recentClipRow.setImportantForAccessibility(mode);
    }

    private void perform(TextEditPanelModel.Action action) {
        InputConnection target = s.connection;
        if (target == null) return;
        s.selectionEcho.invalidate();
        if (action == TextEditPanelModel.Action.SELECT) {
            selecting = !selecting;
        } else if (action == TextEditPanelModel.Action.SYMBOLS) {
            openSymbols();
            return;
        } else if (action == TextEditPanelModel.Action.SELECT_LINE) {
            selectLine(target);
            selecting = false;
        } else if (action == TextEditPanelModel.Action.NUMBER_PAD) {
            selecting = false;
            s.showNumberKeyboardFromTextEdit();
            return;
        } else if (action == TextEditPanelModel.Action.SPACE) {
            InputConnectionGuard.commitText(target, " ", 1);
            selecting = false;
        } else if (action == TextEditPanelModel.Action.RETURN) {
            InputConnectionGuard.commitText(target, "\n", 1);
            selecting = false;
        } else {
            int menu = TextEditPanelModel.contextMenuAction(action);
            if (menu != 0) {
                boolean done = InputConnectionGuard.performContextMenuAction(target, menu);
                // 有些编辑器（部分 WebView、跨平台框架的输入框）不实现上下文菜单动作；粘贴还能退回到直接上屏剪贴板文字。复制和剪切不退回：那要宿主自己读选中的文字，会绕过密码框禁止复制的规则。
                if (!done && action == TextEditPanelModel.Action.PASTE) pasteClipboardText(target);
            } else if (!isCursorMove(action) || cursorSteps(target, action, 1, selecting) > 0) {
                sendKey(target, TextEditPanelModel.keyCode(action),
                    TextEditPanelModel.metaState(action, selecting));
            }
            if (TextEditPanelModel.endsSelecting(action)) selecting = false;
        }
        renderState();
    }

    private void selectLine(InputConnection target) {
        try {
            android.view.inputmethod.ExtractedTextRequest request =
                new android.view.inputmethod.ExtractedTextRequest();
            android.view.inputmethod.ExtractedText extracted = target.getExtractedText(request, 0);
            if (extracted == null || extracted.text == null || extracted.startOffset != 0
                    || extracted.partialStartOffset != -1) {
                s.notice("当前输入框不支持选择行");
                return;
            }
            TextEditLineSelection.Range range = TextEditLineSelection.range(
                extracted.text, extracted.selectionStart, extracted.selectionEnd);
            if (range == null || !target.setSelection(range.start(), range.end()))
                s.notice("当前输入框不支持选择行");
        } catch (RuntimeException ignored) {
            s.notice("当前输入框不支持选择行");
        }
    }

    boolean canSwipeCopyAll() {
        return s.connection != null && !EditorPolicy.password(s.editorInputType) && !s.hasEngineComposition()
            && (s.handwritingCanvas == null || !s.handwritingCanvas.hasInk());
    }

    /** 复制始终交给编辑器；提取结果只保留选区元数据，用于核对全选和恢复原位置。 */
    void copyAllFromGesture() {
        if (!canSwipeCopyAll()) return;
        InputConnection target = s.connection;
        s.selectionEcho.invalidate();
        boolean copied = EditorCopyAll.copy(new EditorCopyAll.Editor() {
            @Override public Object target() { return s.connection; }
            @Override public EditorCopyAll.Selection read() {
                try {
                    var request = new android.view.inputmethod.ExtractedTextRequest();
                    var text = target.getExtractedText(request, 0);
                    if (text == null || text.text == null || text.startOffset != 0 || text.partialStartOffset != -1)
                        return null;
                    var selection = new EditorCopyAll.Selection(text.selectionStart, text.selectionEnd, text.text.length());
                    return selection.valid() ? selection : null;
                } catch (RuntimeException ignored) { return null; }
            }
            @Override public boolean selectAll() {
                return InputConnectionGuard.performContextMenuAction(target, android.R.id.selectAll);
            }
            @Override public boolean copy() {
                return InputConnectionGuard.performContextMenuAction(target, android.R.id.copy);
            }
            @Override public boolean select(int start, int end) {
                try { return target.setSelection(start, end); }
                catch (RuntimeException ignored) { return false; }
            }
        });
        if (!copied) s.notice("当前输入框不支持复制全文");
    }

    /**
     * 发一次按键。带 Shift 时先按下、最后松开左 Shift 键：EditText 只在文字缓冲里记着 Shift 按下时才延伸选区（ArrowKeyMovementMethod.isSelecting 看的是缓冲的修饰键状态，不看事件自己的 meta），WebView 则看事件的 meta，两者都给。
     */
    private boolean sendKey(InputConnection target, int keyCode, int metaState) {
        boolean shift = (metaState & KeyEvent.META_SHIFT_ON) != 0;
        long down = SystemClock.uptimeMillis();
        boolean done = !shift || InputConnectionGuard.sendKeyEvent(target, event(down, down, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON));
        if (done) done = InputConnectionGuard.sendKeyEvent(target, event(down, down, KeyEvent.ACTION_DOWN, keyCode, metaState));
        boolean released = InputConnectionGuard.sendKeyEvent(target, event(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, metaState));
        if (shift) InputConnectionGuard.sendKeyEvent(target, event(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_SHIFT_LEFT, 0));
        return done && released;
    }

    /**
     * 这一批方向键里实际发几个（{@link TextCursorEdgePolicy}）：光标已在文档首尾时往外的键不发，免得编辑器把它当作焦点导航、把焦点交给相邻的控件。回读在主线程同步进行，每批只读一次、只读要走的那一侧；密码框不读。空格拖动也经这里。
     */
    TextCursorEdgePolicy.Plan cursorPlan(InputConnection target, int horizontal, int vertical, boolean extend) {
        return TextCursorEdgePolicy.plan(horizontal, vertical, extend, selectionStart, selectionEnd,
            s.editorInputType, new TextCursorEdgePolicy.Editor() {
                @Override public CharSequence before(int limit) { return InputConnectionGuard.textBeforeCursor(target, limit); }
                @Override public CharSequence after(int limit) { return InputConnectionGuard.textAfterCursor(target, limit); }
                @Override public CharSequence selected() { return InputConnectionGuard.selectedText(target); }
                @Override public boolean confirmsEmpty() { return extractedTextEmpty(target); }
            });
    }

    /** 编辑器经 getExtractedText 报的全文是不是空的。不维护文字、只收按键的编辑器（BaseInputConnection 的默认实现）不提供抽取文字，确认不了；调用失败同样算确认不了。 */
    private static boolean extractedTextEmpty(InputConnection target) {
        try {
            android.view.inputmethod.ExtractedTextRequest request =
                new android.view.inputmethod.ExtractedTextRequest();
            request.hintMaxChars = 1;
            android.view.inputmethod.ExtractedText extracted = target.getExtractedText(request, 0);
            return extracted != null && extracted.text != null && extracted.text.length() == 0
                && extracted.startOffset == 0 && extracted.partialStartOffset == -1;
        } catch (RuntimeException disconnected) {
            return false;
        }
    }

    private int cursorSteps(InputConnection target, TextEditPanelModel.Action action, int count, boolean extend) {
        int horizontal = action == TextEditPanelModel.Action.LEFT ? -count : action == TextEditPanelModel.Action.RIGHT ? count : 0;
        int vertical = action == TextEditPanelModel.Action.UP ? -count : action == TextEditPanelModel.Action.DOWN ? count : 0;
        TextCursorEdgePolicy.Plan plan = cursorPlan(target, horizontal, vertical, extend);
        return Math.abs(plan.horizontal()) + Math.abs(plan.vertical());
    }

    void editorChanged(int start, int end) { close(); selectionChanged(start, end); }

    /** 同一输入框重启只更新连接报告，保留面板与选择开关。 */
    void editorRestarted(InputConnection previous, int start, int end) { selectionChanged(start, end); }

    boolean reportedSelection(int start, int end) {
        return start >= 0 && end >= 0 && selectionStart == start && selectionEnd == end;
    }

    void selectionChanged(int start, int end) { selectionStart = start; selectionEnd = end; }

    void shutdown() { close(); }

    private static KeyEvent event(long downTime, long eventTime, int action, int keyCode, int metaState) {
        return new KeyEvent(downTime, eventTime, action, keyCode, 0, metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    private void pasteClipboardText(InputConnection target) {
        ClipboardManager clipboard = s.getSystemService(ClipboardManager.class);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(s);
        if (text != null && text.length() > 0) InputConnectionGuard.commitText(target, text, 1);
    }
}
