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
    private ImageView btnMove;

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

        // 1. Full-screen drawing overlay window, kept alive across minimise so strokes persist
        if (drawingView == null) {
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
        } else {
            setDrawingTouchable(true);
        }

        // 2. Translucent floating toolbar window
        if (toolbarParams == null) {
            toolbarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );
            toolbarParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            toolbarParams.y = dpToPx(80); // Positioned above navigation bar
        }

        toolbarView = new LinearLayout(context);
        toolbarView.setOrientation(LinearLayout.VERTICAL);
        
        GradientDrawable toolbarBg = new GradientDrawable();
        toolbarBg.setCornerRadius(dpToPx(18));
        toolbarBg.setColor(Color.parseColor("#E61F1F1F")); // Glassmorphic translucent dark grey
        toolbarBg.setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF")); // Frosted white stroke
        toolbarView.setBackground(toolbarBg);
        toolbarView.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        // Create buttons
        btnBrush = createToolbarButton(new BrushIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.FREEHAND, btnBrush));
        btnRect = createToolbarButton(new RectangleIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.RECTANGLE, btnRect));
        btnCircle = createToolbarButton(new CircleIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.CIRCLE, btnCircle));
        btnArrow = createToolbarButton(new ArrowIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.ARROW, btnArrow));
        btnLine = createToolbarButton(new LineIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.LINE, btnLine));
        btnMove = createToolbarButton(new MoveIconDrawable(), v -> selectMode(DrawingOverlayView.Mode.MOVE, btnMove));
        
        shapeButtons.clear();
        shapeButtons.add(btnBrush);
        shapeButtons.add(btnRect);
        shapeButtons.add(btnCircle);
        shapeButtons.add(btnArrow);
        shapeButtons.add(btnLine);
        shapeButtons.add(btnMove);

        // Set default selection state visually
        highlightButton(btnBrush);

        ImageView btnUndo = createToolbarButton(new UndoIconDrawable(), v -> drawingView.undo());
        ImageView btnClear = createToolbarButton(new ClearIconDrawable(), v -> minimise());
        ImageView btnExit = createToolbarButton(new ExitIconDrawable(), v -> clearAndDismiss());

        // Sleek top drag header (Generous touch target area, small visual pill)
        LinearLayout dragHeader = new LinearLayout(context);
        dragHeader.setOrientation(LinearLayout.HORIZONTAL);
        dragHeader.setGravity(Gravity.CENTER);
        dragHeader.setPadding(0, dpToPx(8), 0, dpToPx(8)); // Generous touch target area for easy finger drag

        ImageView dragHandle = new ImageView(context);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(4)); // Visually sleek small pill
        handleParams.gravity = Gravity.CENTER;
        dragHandle.setLayoutParams(handleParams);

        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setCornerRadius(dpToPx(2));
        handleDrawable.setColor(Color.parseColor("#B3FFFFFF")); // Translucent frosted white pill
        dragHandle.setImageDrawable(handleDrawable);
        dragHeader.addView(dragHandle);

        boolean isLandscape = isLandscapeMode();
        toolbarView.addView(dragHeader);

        if (isLandscape) {
            // Landscape layout (2 wide horizontal rows: 5 top, 4 bottom)
            LinearLayout row1 = new LinearLayout(context);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER);
            row1.addView(btnBrush);
            row1.addView(btnRect);
            row1.addView(btnCircle);
            row1.addView(btnArrow);
            row1.addView(btnLine);

            LinearLayout row2 = new LinearLayout(context);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setGravity(Gravity.CENTER);
            row2.addView(btnMove);
            row2.addView(btnUndo);
            row2.addView(btnClear);
            row2.addView(btnExit);

            toolbarView.addView(row1);
            toolbarView.addView(row2);
        } else {
            // Portrait layout (3 compact square rows: 3 per row)
            LinearLayout row1 = new LinearLayout(context);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER);
            row1.addView(btnBrush);
            row1.addView(btnRect);
            row1.addView(btnCircle);

            LinearLayout row2 = new LinearLayout(context);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setGravity(Gravity.CENTER);
            row2.addView(btnArrow);
            row2.addView(btnLine);
            row2.addView(btnMove);

            LinearLayout row3 = new LinearLayout(context);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setGravity(Gravity.CENTER);
            row3.addView(btnUndo);
            row3.addView(btnClear);
            row3.addView(btnExit);

            toolbarView.addView(row1);
            toolbarView.addView(row2);
            toolbarView.addView(row3);
        }

        // Make ONLY the drag header handle move the box (prevents accidental button clicks)
        dragHeader.setOnTouchListener(new View.OnTouchListener() {
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

    // A toolbar position dragged in one orientation lands off-screen in the other, so re-seat it
    public void onConfigurationChanged() {
        if (toolbarView == null || toolbarParams == null) return;
        toolbarParams.x = 0;
        toolbarParams.y = dpToPx(80);
        try { windowManager.updateViewLayout(toolbarView, toolbarParams); } catch (Exception ignored) {}
    }

    public void dismiss() {
        if (!isShowing && drawingView == null) return;

        if (drawingView != null) {
            try { windowManager.removeView(drawingView); } catch (Exception ignored) {}
            drawingView = null;
        }

        if (toolbarView != null) {
            try { windowManager.removeView(toolbarView); } catch (Exception ignored) {}
            toolbarView = null;
        }

        isShowing = false;

        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }

    // Red exit erases all strokes on screen and closes drawing overlay completely
    public void clearAndDismiss() {
        if (drawingView != null) {
            drawingView.clear();
        }
        dismiss();
    }

    // White X puts the toolbar away but leaves the strokes on screen
    public void minimise() {
        if (!isShowing) return;

        if (toolbarView != null) {
            try { windowManager.removeView(toolbarView); } catch (Exception ignored) {}
            toolbarView = null;
        }

        setDrawingTouchable(false);
        isShowing = false;

        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }

    public boolean isMinimised() {
        return !isShowing && drawingView != null;
    }

    private boolean isLandscapeMode() {
        SettingsManager settings = new SettingsManager(context);
        int orientPref = settings.getOrientation(); // 0=Auto, 1=Portrait, 2=Landscape
        if (orientPref == 1) return false;
        if (orientPref == 2) return true;
        return context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setDrawingTouchable(boolean touchable) {
        if (drawingView == null || drawingParams == null) return;
        if (touchable) {
            drawingParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            drawingParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try { windowManager.updateViewLayout(drawingView, drawingParams); } catch (Exception ignored) {}
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
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(34), dpToPx(34));
        btnParams.setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2));
        button.setLayoutParams(btnParams);
        button.setImageDrawable(iconDrawable);
        button.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
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

    private static class MoveIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public MoveIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float cx = w / 2f;
            float cy = h / 2f;
            float len = w * 0.22f;

            canvas.drawLine(cx - len, cy, cx + len, cy, paint);
            canvas.drawLine(cx, cy - len, cx, cy + len, paint);

            float arr = w * 0.08f;
            canvas.drawLine(cx - len, cy, cx - len + arr, cy - arr, paint);
            canvas.drawLine(cx - len, cy, cx - len + arr, cy + arr, paint);
            canvas.drawLine(cx + len, cy, cx + len - arr, cy - arr, paint);
            canvas.drawLine(cx + len, cy, cx + len - arr, cy + arr, paint);
            canvas.drawLine(cx, cy - len, cx - arr, cy - len + arr, paint);
            canvas.drawLine(cx, cy - len, cx + arr, cy - len + arr, paint);
            canvas.drawLine(cx, cy + len, cx - arr, cy + len - arr, paint);
            canvas.drawLine(cx, cy + len, cx + arr, cy + len - arr, paint);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
