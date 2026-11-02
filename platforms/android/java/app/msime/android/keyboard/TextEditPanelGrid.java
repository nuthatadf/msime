package app.msime.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** 模型边界同时驱动测量和摆放，跨行方向键不会再依赖另一套行高比例。 */
public final class TextEditPanelGrid extends ViewGroup {
    private final List<TextEditPanelModel.Key> keys = new ArrayList<>();
    private final int margin;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path seams = new Path();
    private int background;
    private int lineColor;
    private int accent;
    private float radius;
    private final float lineWidth;

    public TextEditPanelGrid(Context context) {
        super(context);
        margin = DimensionPolicy.pixels(context, 3);
        lineWidth = Math.max(1, DimensionPolicy.floatPixels(context, .5f));
        setClickable(true);
        setWillNotDraw(false);
    }

    public void addKey(TextEditPanelModel.Key key, View button) {
        keys.add(key);
        if (TextEditPanelModel.isCentral(key) && button instanceof TextEditPanelButton central)
            central.setSeamless(accent);
        addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setSkin(KeyboardSkin skin) {
        background = Color.parseColor(skin.keyBackground());
        lineColor = ColorPolicy.withAlpha(Color.parseColor(skin.keyForeground()), .18f);
        accent = Color.parseColor(skin.accent());
        radius = DimensionPolicy.floatPixels(getContext(), Math.max(4, skin.cornerRadius()));
        for (int index = 0; index < keys.size(); index++)
            if (TextEditPanelModel.isCentral(keys.get(index))
                    && getChildAt(index) instanceof TextEditPanelButton central)
                central.setSeamless(accent);
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int index = 0; index < keys.size(); index++) {
            TextEditPanelModel.Bounds bounds = TextEditPanelModel.bounds(keys.get(index), width, height, margin);
            getChildAt(index).measure(MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < keys.size(); index++) {
            TextEditPanelModel.Bounds bounds = TextEditPanelModel.bounds(keys.get(index), right - left, bottom - top, margin);
            getChildAt(index).layout(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            if (getChildAt(index) instanceof TextEditIconButton icon && TextEditPanelModel.isCentral(keys.get(index)))
                icon.setCenterOffset(TextEditPanelModel.contentCenterY(keys.get(index), bottom - top)
                    - (bounds.top() + bounds.bottom()) / 2f);
        }
    }

    private void prepareOutline() {
        TextEditPanelModel.Bounds bounds = TextEditPanelModel.centralBounds(getWidth(), getHeight(), margin);
        outline.reset();
        outline.addRoundRect(new RectF(bounds.left(), bounds.top(), bounds.right(), bounds.bottom()),
            radius, radius, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas canvas) {
        prepareOutline();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(background);
        canvas.drawPath(outline, paint);
    }

    @Override protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int index = indexOfChild(child);
        if (index < 0 || !TextEditPanelModel.isCentral(keys.get(index)))
            return super.drawChild(canvas, child, drawingTime);
        int saved = canvas.save();
        canvas.clipPath(outline);
        boolean drawn = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(saved);
        return drawn;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        TextEditPanelModel.Bounds bounds = TextEditPanelModel.centralBounds(getWidth(), getHeight(), margin);
        float half = lineWidth / 2;
        float edge = TextEditPanelModel.seam(3, getHeight(), margin);
        seams.reset();
        seams.addRoundRect(new RectF(bounds.left() + half, bounds.top() + half,
            bounds.right() - half, bounds.bottom() - half), radius, radius, Path.Direction.CW);
        for (int column : new int[] {4, 6}) {
            float x = getWidth() * column / (float) TextEditPanelModel.COLUMNS;
            seams.moveTo(x, bounds.top());
            seams.lineTo(x, edge);
        }
        for (int row : new int[] {1, 2}) {
            float y = TextEditPanelModel.seam(row, getHeight(), margin);
            seams.moveTo(getWidth() * 4f / TextEditPanelModel.COLUMNS, y);
            seams.lineTo(getWidth() * 6f / TextEditPanelModel.COLUMNS, y);
        }
        seams.moveTo(bounds.left(), edge);
        seams.lineTo(bounds.right(), edge);
        seams.moveTo(getWidth() / 2f, edge);
        seams.lineTo(getWidth() / 2f, bounds.bottom());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        paint.setColor(lineColor);
        canvas.drawPath(seams, paint);
    }
}
