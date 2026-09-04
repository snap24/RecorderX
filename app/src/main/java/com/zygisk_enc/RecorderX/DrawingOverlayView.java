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
        FREEHAND, RECTANGLE, CIRCLE, ARROW, LINE, MOVE
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

    private DrawnItem selectedItem = null;
    private float lastTouchX, lastTouchY;

    public interface OnDrawingTouchListener {
        void onDrawingStarted();
    }
    private OnDrawingTouchListener drawingTouchListener;

    public interface CameraTouchDelegate {
        boolean isCameraShowing();
        boolean isCameraHit(float rawX, float rawY);
        Path getCameraClipPath(float drawingViewScreenX, float drawingViewScreenY);
        boolean onCameraTouchEvent(MotionEvent event);
    }
    private CameraTouchDelegate cameraTouchDelegate;
    private boolean isInteractingWithCamera = false;

    public void setOnDrawingTouchListener(OnDrawingTouchListener listener) {
        this.drawingTouchListener = listener;
    }

    public void setCameraTouchDelegate(CameraTouchDelegate delegate) {
        this.cameraTouchDelegate = delegate;
    }

    public DrawingOverlayView(Context context) {
        super(context);
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        this.selectedItem = null;
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
        selectedItem = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int saveCount = canvas.save();
        if (cameraTouchDelegate != null && cameraTouchDelegate.isCameraShowing()) {
            int[] drawLoc = new int[2];
            getLocationOnScreen(drawLoc);
            Path clipPath = cameraTouchDelegate.getCameraClipPath(drawLoc[0], drawLoc[1]);
            if (clipPath != null) {
                canvas.clipOutPath(clipPath);
            }
        }

        for (DrawnItem item : drawnItems) {
            item.draw(canvas, paint);
        }

        if (isDrawing && currentMode != Mode.MOVE) {
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

        canvas.restoreToCount(saveCount);
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
        int action = event.getActionMasked();

        // 1. If currently interacting with/dragging camera, forward all events
        if (isInteractingWithCamera) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isInteractingWithCamera = false;
            }
            if (cameraTouchDelegate != null) {
                cameraTouchDelegate.onCameraTouchEvent(event);
            }
            invalidate();
            return true;
        }

        // 2. On ACTION_DOWN, check if touch lands on camera overlay
        if (action == MotionEvent.ACTION_DOWN) {
            if (cameraTouchDelegate != null && cameraTouchDelegate.isCameraShowing()) {
                if (cameraTouchDelegate.isCameraHit(event.getRawX(), event.getRawY())) {
                    isInteractingWithCamera = true;
                    cameraTouchDelegate.onCameraTouchEvent(event);
                    return true;
                }
            }
        }

        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN && drawingTouchListener != null) {
            drawingTouchListener.onDrawingStarted();
        }

        if (currentMode == Mode.MOVE) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    selectedItem = null;
                    for (int i = drawnItems.size() - 1; i >= 0; i--) {
                        DrawnItem item = drawnItems.get(i);
                        if (item.contains(x, y)) {
                            selectedItem = item;
                            lastTouchX = x;
                            lastTouchY = y;
                            break;
                        }
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (selectedItem != null) {
                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;
                        selectedItem.translate(dx, dy);
                        lastTouchX = x;
                        lastTouchY = y;
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    selectedItem = null;
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }

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
        boolean contains(float x, float y);
        void translate(float dx, float dy);
    }

    public static class FreehandItem implements DrawnItem {
        public Path path;
        public int color;
        public float strokeWidth;
        private final RectF bounds = new RectF();

        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean contains(float x, float y) {
            if (path == null) return false;
            path.computeBounds(bounds, true);
            float pad = Math.max(30f, strokeWidth);
            bounds.inset(-pad, -pad);
            return bounds.contains(x, y);
        }

        @Override
        public void translate(float dx, float dy) {
            if (path != null) {
                path.offset(dx, dy);
            }
        }
    }

    public static class RectangleItem implements DrawnItem {
        public float left, top, right, bottom;
        public int color;
        public float strokeWidth;
        private final RectF bounds = new RectF();

        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(left, top, right, bottom, paint);
        }

        @Override
        public boolean contains(float x, float y) {
            float pad = Math.max(30f, strokeWidth);
            bounds.set(left - pad, top - pad, right + pad, bottom + pad);
            return bounds.contains(x, y);
        }

        @Override
        public void translate(float dx, float dy) {
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        }
    }

    public static class CircleItem implements DrawnItem {
        public float left, top, right, bottom;
        public int color;
        public float strokeWidth;
        private final RectF bounds = new RectF();

        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawOval(new RectF(left, top, right, bottom), paint);
        }

        @Override
        public boolean contains(float x, float y) {
            float pad = Math.max(30f, strokeWidth);
            bounds.set(left - pad, top - pad, right + pad, bottom + pad);
            return bounds.contains(x, y);
        }

        @Override
        public void translate(float dx, float dy) {
            left += dx;
            right += dx;
            top += dy;
            bottom += dy;
        }
    }

    public static class LineItem implements DrawnItem {
        public float x1, y1, x2, y2;
        public int color;
        public float strokeWidth;
        private final RectF bounds = new RectF();

        @Override
        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        @Override
        public boolean contains(float x, float y) {
            float pad = Math.max(30f, strokeWidth);
            bounds.set(Math.min(x1, x2) - pad, Math.min(y1, y2) - pad, Math.max(x1, x2) + pad, Math.max(y1, y2) + pad);
            return bounds.contains(x, y);
        }

        @Override
        public void translate(float dx, float dy) {
            x1 += dx;
            x2 += dx;
            y1 += dy;
            y2 += dy;
        }
    }

    public static class ArrowItem implements DrawnItem {
        public float x1, y1, x2, y2;
        public int color;
        public float strokeWidth;
        private final RectF bounds = new RectF();

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

        @Override
        public boolean contains(float x, float y) {
            float pad = Math.max(30f, strokeWidth);
            bounds.set(Math.min(x1, x2) - pad, Math.min(y1, y2) - pad, Math.max(x1, x2) + pad, Math.max(y1, y2) + pad);
            return bounds.contains(x, y);
        }

        @Override
        public void translate(float dx, float dy) {
            x1 += dx;
            x2 += dx;
            y1 += dy;
            y2 += dy;
        }
    }
}
