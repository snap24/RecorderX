package com.zygisk_enc.RecorderX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

public class BrushController {
    private final Context context;
    private final WindowManager windowManager;
    private final Runnable onDismissCallback;

    private DrawingOverlayView drawingView;
    private LinearLayout toolbarView;
    private WindowManager.LayoutParams drawingParams;
    private WindowManager.LayoutParams toolbarParams;

    private boolean isShowing = false;
    
    // Track selected button state for visual feedback
    private final List<ImageView> shapeButtons = new ArrayList<>();
    private ImageView btnBrush;
    private ImageView btnRect;
    private ImageView btnCircle;
    private ImageView btnArrow;
    private ImageView btnLine;

    public BrushController(Context context, Runnable onDismissCallback) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.onDismissCallback = onDismissCallback;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void show() {
        if (isShowing) return;

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE;

        // 1. Full-screen drawing overlay window
        drawingParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        drawingView = new DrawingOverlayView(context);
        drawingView.setBrushColor(Color.RED); // default red brush
        drawingView.setStrokeWidth(12f);
        windowManager.addView(drawingView, drawingParams);

        // 2. Translucent floating toolbar window
        toolbarParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        toolbarParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        toolbarParams.y = dpToPx(80); // Positioned above navigation bar

        toolbarView = new LinearLayout(context);
        toolbarView.setOrientation(LinearLayout.HORIZONTAL);
        
        GradientDrawable toolbarBg = new GradientDrawable();
        toolbarBg.setCornerRadius(dpToPx(24));
        toolbarBg.setColor(Color.parseColor("#E61F1F1F")); // Glassmorphic translucent dark grey
        toolbarBg.setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF")); // Frosted white stroke
        toolbarView.setBackground(toolbarBg);
        toolbarView.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        // Create buttons
        btnBrush = createToolbarButton(new BrushIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.FREEHAND, btnBrush));
        btnRect = createToolbarButton(new RectangleIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.RECTANGLE, btnRect));
        btnCircle = createToolbarButton(new CircleIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.CIRCLE, btnCircle));
        btnArrow = createToolbarButton(new ArrowIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.ARROW, btnArrow));
        btnLine = createToolbarButton(new LineIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.LINE, btnLine));
        
        shapeButtons.add(btnBrush);
        shapeButtons.add(btnRect);
        shapeButtons.add(btnCircle);
        shapeButtons.add(btnArrow);
        shapeButtons.add(btnLine);

        // Set default selection state visually
        highlightButton(btnBrush);

        ImageView btnUndo = createToolbarButton(new UndoIconDrawable(), v -> drawingView.undo());
        ImageView btnClear = createToolbarButton(new ClearIconDrawable(), v -> drawingView.clear());
        ImageView btnExit = createToolbarButton(new ExitIconDrawable(), v -> dismiss());

        toolbarView.addView(btnBrush);
        toolbarView.addView(btnRect);
        toolbarView.addView(btnCircle);
        toolbarView.addView(btnArrow);
        toolbarView.addView(btnLine);
        toolbarView.addView(btnUndo);
        toolbarView.addView(btnClear);
        toolbarView.addView(btnExit);

        // Make the toolbar draggable
        toolbarView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = toolbarParams.x;
                        initialY = toolbarParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        toolbarParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        // Invert Y delta since gravity is BOTTOM
                        toolbarParams.y = initialY - (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(toolbarView, toolbarParams);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(toolbarView, toolbarParams);
        isShowing = true;
    }

    public void dismiss() {
        if (!isShowing) return;

        if (drawingView != null) {
            windowManager.removeView(drawingView);
            drawingView = null;
        }

        if (toolbarView != null) {
            windowManager.removeView(toolbarView);
            toolbarView = null;
        }

        isShowing = false;
        
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }

    private void selectMode(DrawingOverlayView.Mode mode, ImageView selectedButton) {
        if (drawingView != null) {
            drawingView.setMode(mode);
            highlightButton(selectedButton);
        }
    }

    private void highlightButton(ImageView selectedButton) {
        for (ImageView btn : shapeButtons) {
            if (btn == selectedButton) {
                // Highlight selected button (solid translucent white circle)
                GradientDrawable selectedBg = new GradientDrawable();
                selectedBg.setShape(GradientDrawable.OVAL);
                selectedBg.setColor(Color.parseColor("#4DFFFFFF"));
                btn.setBackground(selectedBg);
            } else {
                btn.setBackground(null);
            }
        }
    }

    private ImageView createToolbarButton(Drawable iconDrawable, View.OnClickListener listener) {
        ImageView button = new ImageView(context);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
        btnParams.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        button.setLayoutParams(btnParams);
        button.setImageDrawable(iconDrawable);
        button.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        button.setOnClickListener(listener);
        return button;
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    // --- Programmatic Icon Drawables ---

    static class BrushIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public BrushIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            Path p = new Path();
            p.moveTo(w * 0.25f, h * 0.75f);
            p.quadTo(w * 0.45f, h * 0.35f, w * 0.75f, h * 0.25f);
            canvas.drawPath(p, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class RectangleIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public RectangleIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawRect(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class CircleIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public CircleIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawOval(new RectF(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f), paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class ArrowIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ArrowIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float x1 = w * 0.25f, y1 = h * 0.75f;
            float x2 = w * 0.75f, y2 = h * 0.25f;
            canvas.drawLine(x1, y1, x2, y2, paint);
            float angle = (float) Math.atan2(y2 - y1, x2 - x1);
            float len = w * 0.20f;
            float arrowAngle = (float) Math.toRadians(30);
            float x3 = x2 - len * (float) Math.cos(angle + arrowAngle);
            float y3 = y2 - len * (float) Math.sin(angle + arrowAngle);
            float x4 = x2 - len * (float) Math.cos(angle - arrowAngle);
            float y4 = y2 - len * (float) Math.sin(angle - arrowAngle);
            canvas.drawLine(x2, y2, x3, y3, paint);
            canvas.drawLine(x2, y2, x4, y4, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class LineIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public LineIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawLine(w * 0.25f, h * 0.75f, w * 0.75f, h * 0.25f, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class UndoIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public UndoIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            
            // Draw a beautiful curved tail curving from right to left
            Path p = new Path();
            p.moveTo(w * 0.7f, h * 0.65f); // Start bottom-right
            p.quadTo(w * 0.7f, h * 0.35f, w * 0.4f, h * 0.35f); // Curve to top-left
            canvas.drawPath(p, paint);
            
            // Arrowhead at (w * 0.4f, h * 0.35f) pointing left/down-left
            canvas.drawLine(w * 0.4f, h * 0.35f, w * 0.52f, h * 0.23f, paint);
            canvas.drawLine(w * 0.4f, h * 0.35f, w * 0.52f, h * 0.47f, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class ClearIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ClearIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawLine(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, paint);
            canvas.drawLine(w * 0.7f, h * 0.3f, w * 0.3f, h * 0.7f, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class ExitIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ExitIconDrawable() {
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawLine(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, paint);
            canvas.drawLine(w * 0.7f, h * 0.3f, w * 0.3f, h * 0.7f, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
