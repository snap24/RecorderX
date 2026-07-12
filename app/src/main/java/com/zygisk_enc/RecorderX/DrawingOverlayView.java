package com.zygisk_enc.RecorderX;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class DrawingOverlayView extends View {
    public enum Mode {
        FREEHAND, RECTANGLE, CIRCLE, ARROW, LINE
    }

    private Mode currentMode = Mode.FREEHAND;
    private int brushColor = Color.RED;
    private float strokeWidth = 12f;

    private final Paint paint = new Paint();
    private final List<DrawnItem> drawnItems = new ArrayList<>();
    
    private Path currentPath;
    private float startX, startY;
    private float currentX, currentY;
    private boolean isDrawing = false;

    public DrawingOverlayView(Context context) {
        super(context);
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
    }

    public Mode getMode() {
        return currentMode;
    }

    public void setBrushColor(int color) {
        this.brushColor = color;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
    }

    public void undo() {
        if (!drawnItems.isEmpty()) {
            drawnItems.remove(drawnItems.size() - 1);
            invalidate();
        }
    }

    public void clear() {
        drawnItems.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        for (DrawnItem item : drawnItems) {
            item.draw(canvas, paint);
        }

        if (isDrawing) {
            paint.setColor(brushColor);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);

            switch (currentMode) {
                case FREEHAND:
                    if (currentPath != null) {
                        canvas.drawPath(currentPath, paint);
                    }
                    break;
                case RECTANGLE:
                    canvas.drawRect(
                        Math.min(startX, currentX), Math.min(startY, currentY),
                        Math.max(startX, currentX), Math.max(startY, currentY),
                        paint
                    );
                    break;
                case CIRCLE:
                    canvas.drawOval(new RectF(
                        Math.min(startX, currentX), Math.min(startY, currentY),
                        Math.max(startX, currentX), Math.max(startY, currentY)
                    ), paint);
                    break;
                case LINE:
                    canvas.drawLine(startX, startY, currentX, currentY, paint);
                    break;
                case ARROW:
                    drawArrow(canvas, startX, startY, currentX, currentY, paint);
                    break;
            }
        }
    }

    private void drawArrow(Canvas canvas, float x1, float y1, float x2, float y2, Paint paint) {
        canvas.drawLine(x1, y1, x2, y2, paint);
        float angle = (float) Math.atan2(y2 - y1, x2 - x1);
        float arrowLength = strokeWidth * 3f + 16f;
        float arrowAngle = (float) Math.toRadians(30);
        float x3 = x2 - arrowLength * (float) Math.cos(angle + arrowAngle);
        float y3 = y2 - arrowLength * (float) Math.sin(angle + arrowAngle);
        float x4 = x2 - arrowLength * (float) Math.cos(angle - arrowAngle);
        float y4 = y2 - arrowLength * (float) Math.sin(angle - arrowAngle);
        canvas.drawLine(x2, y2, x3, y3, paint);
        canvas.drawLine(x2, y2, x4, y4, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrawing = true;
                startX = x;
                startY = y;
                currentX = x;
                currentY = y;
                if (currentMode == Mode.FREEHAND) {
                    currentPath = new Path();
                    currentPath.moveTo(x, y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                currentX = x;
                currentY = y;
                if (currentMode == Mode.FREEHAND && currentPath != null) {
                    currentPath.lineTo(x, y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (isDrawing) {
                    commitShape();
                    isDrawing = false;
                    currentPath = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void commitShape() {
        switch (currentMode) {
            case FREEHAND:
                if (currentPath != null) {
                    FreehandItem item = new FreehandItem();
                    item.path = currentPath;
                    item.color = brushColor;
                    item.strokeWidth = strokeWidth;
                    drawnItems.add(item);
                }
                break;
            case RECTANGLE:
                RectangleItem rect = new RectangleItem();
                rect.left = Math.min(startX, currentX);
                rect.top = Math.min(startY, currentY);
                rect.right = Math.max(startX, currentX);
                rect.bottom = Math.max(startY, currentY);
                rect.color = brushColor;
                rect.strokeWidth = strokeWidth;
                drawnItems.add(rect);
                break;
            case CIRCLE:
                CircleItem circ = new CircleItem();
                circ.left = Math.min(startX, currentX);
                circ.top = Math.min(startY, currentY);
                circ.right = Math.max(startX, currentX);
                circ.bottom = Math.max(startY, currentY);
                circ.color = brushColor;
                circ.strokeWidth = strokeWidth;
                drawnItems.add(circ);
                break;
            case LINE:
                LineItem line = new LineItem();
                line.x1 = startX;
                line.y1 = startY;
                line.x2 = currentX;
                line.y2 = currentY;
                line.color = brushColor;
                line.strokeWidth = strokeWidth;
                drawnItems.add(line);
                break;
            case ARROW:
                ArrowItem arrow = new ArrowItem();
                arrow.x1 = startX;
                arrow.y1 = startY;
                arrow.x2 = currentX;
                arrow.y2 = currentY;
                arrow.color = brushColor;
                arrow.strokeWidth = strokeWidth;
                drawnItems.add(arrow);
                break;
        }
    }

    public interface DrawnItem {
        void draw(Canvas canvas, Paint paint);
    }

    public static class FreehandItem implements DrawnItem {
        public Path path;
        public int color;
        public float strokeWidth;
        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }
    }

    public static class RectangleItem implements DrawnItem {
        public float left, top, right, bottom;
        public int color;
        public float strokeWidth;
        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    public static class CircleItem implements DrawnItem {
        public float left, top, right, bottom;
        public int color;
        public float strokeWidth;
        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawOval(new RectF(left, top, right, bottom), paint);
        }
    }

    public static class LineItem implements DrawnItem {
        public float x1, y1, x2, y2;
        public int color;
        public float strokeWidth;
        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    public static class ArrowItem implements DrawnItem {
        public float x1, y1, x2, y2;
        public int color;
        public float strokeWidth;
        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x1, y1, x2, y2, paint);
            
            float angle = (float) Math.atan2(y2 - y1, x2 - x1);
            float arrowLength = strokeWidth * 3f + 16f;
            float arrowAngle = (float) Math.toRadians(30);
            float x3 = x2 - arrowLength * (float) Math.cos(angle + arrowAngle);
            float y3 = y2 - arrowLength * (float) Math.sin(angle + arrowAngle);
            float x4 = x2 - arrowLength * (float) Math.cos(angle - arrowAngle);
            float y4 = y2 - arrowLength * (float) Math.sin(angle - arrowAngle);
            canvas.drawLine(x2, y2, x3, y3, paint);
            canvas.drawLine(x2, y2, x4, y4, paint);
        }
    }
}
