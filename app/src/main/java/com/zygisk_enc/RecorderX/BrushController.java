package com.zygisk_enc.RecorderX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private FrameLayout rootContainer;
    private LinearLayout toolbarView;
    private FrameLayout dockHandleView;
    private WindowManager.LayoutParams drawingParams;
    private WindowManager.LayoutParams toolbarParams;

    private boolean isShowing = false;
    private boolean isDocked = false;
    private boolean isDockedOnRight = false;
    
    // Track selected button state for visual feedback
    private final List<ImageView> shapeButtons = new ArrayList<>();
    private ImageView btnBrush;
    private ImageView btnRect;
    private ImageView btnCircle;
    private ImageView btnArrow;
    private ImageView btnLine;
    private ImageView btnMove;

    private DrawingOverlayView.CameraTouchDelegate cameraTouchDelegate;

    public BrushController(Context context, Runnable onDismissCallback) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.onDismissCallback = onDismissCallback;
    }

    public void setCameraTouchDelegate(DrawingOverlayView.CameraTouchDelegate delegate) {
        this.cameraTouchDelegate = delegate;
        if (drawingView != null) {
            drawingView.setCameraTouchDelegate(delegate);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void show() {
        if (isShowing) {
            if (isDocked) {
                undockToolbar();
            }
            return;
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                drawingParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            drawingView = new DrawingOverlayView(context);
            drawingView.setBrushColor(Color.RED); // default red brush
            drawingView.setStrokeWidth(12f);
            if (cameraTouchDelegate != null) {
                drawingView.setCameraTouchDelegate(cameraTouchDelegate);
            }
            drawingView.setOnDrawingTouchListener(() -> {
                if (isShowing && !isDocked) {
                    minimise();
                }
            });
            windowManager.addView(drawingView, drawingParams);
        } else {
            setDrawingTouchable(true);
        }

        // 2. Translucent floating toolbar window
        Point screen = getScreenSize();
        if (toolbarParams == null) {
            toolbarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );
            toolbarParams.gravity = Gravity.TOP | Gravity.START;
            toolbarParams.x = Math.max(0, (screen.x - dpToPx(160)) / 2);
            toolbarParams.y = Math.max(0, screen.y - dpToPx(260));
        }

        rootContainer = new FrameLayout(context);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        initDockHandle();
        dockHandleView.setVisibility(View.GONE);

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
        ImageView btnClear = createToolbarButton(new ClearIconDrawable(), v -> clearAndDock());
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
            private boolean isMoving;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = toolbarParams.x;
                        initialY = toolbarParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoving = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isMoving = true;
                        }
                        toolbarParams.x = initialX + dx;
                        toolbarParams.y = initialY + dy;
                        try {
                            windowManager.updateViewLayout(rootContainer, toolbarParams);
                        } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        Point curScreen = getScreenSize();
                        int tw = toolbarView.getWidth() > 0 ? toolbarView.getWidth() : dpToPx(160);
                        int th = toolbarView.getHeight() > 0 ? toolbarView.getHeight() : dpToPx(180);

                        int leftThreshold = dpToPx(40);
                        int rightThreshold = curScreen.x - tw - dpToPx(40);

                        if (toolbarParams.x <= leftThreshold) {
                            dockToolbar(false);
                        } else if (toolbarParams.x >= rightThreshold) {
                            dockToolbar(true);
                        } else {
                            toolbarParams.x = clampX(toolbarParams.x, tw);
                            toolbarParams.y = clampY(toolbarParams.y, th);
                            try {
                                windowManager.updateViewLayout(rootContainer, toolbarParams);
                            } catch (Exception ignored) {}
                        }
                        return true;
                }
                return false;
            }
        });

        rootContainer.addView(toolbarView);
        rootContainer.addView(dockHandleView);

        toolbarView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int measuredW = toolbarView.getMeasuredWidth();
        int measuredH = toolbarView.getMeasuredHeight();
        toolbarParams.x = clampX(toolbarParams.x, measuredW);
        toolbarParams.y = clampY(toolbarParams.y, measuredH);

        windowManager.addView(rootContainer, toolbarParams);
        isShowing = true;
        isDocked = false;
    }

    private void initDockHandle() {
        dockHandleView = new FrameLayout(context);
        dockHandleView.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(28), dpToPx(56)));

        ImageView icon = new ImageView(context);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dpToPx(18), dpToPx(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageDrawable(new BrushIconDrawable());
        dockHandleView.addView(icon);

        updateDockHandleStyle(isDockedOnRight);

        dockHandleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = toolbarParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        if (Math.abs(diffX) > 8 || Math.abs(diffY) > 8) {
                            isDragging = true;
                        }

                        // Pull inward away from edge by > 20dp triggers undock
                        boolean pulledInward = isDockedOnRight ? (diffX < -dpToPx(20)) : (diffX > dpToPx(20));
                        if (pulledInward) {
                            undockToolbar();
                            return true;
                        }

                        if (isDragging) {
                            int handleHeight = dpToPx(56);
                            toolbarParams.y = clampY(initialY + (int) diffY, handleHeight);
                            try {
                                windowManager.updateViewLayout(rootContainer, toolbarParams);
                            } catch (Exception ignored) {}
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            undockToolbar();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void updateDockHandleStyle(boolean onRight) {
        if (dockHandleView == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E61F1F1F"));
        bg.setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF"));
        float r = dpToPx(16);
        if (onRight) {
            bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        } else {
            bg.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        }
        dockHandleView.setBackground(bg);
    }

    public void dockToolbar(boolean onRight) {
        if (!isShowing || rootContainer == null || toolbarView == null) return;

        isDocked = true;
        isDockedOnRight = onRight;

        toolbarView.setVisibility(View.GONE);
        dockHandleView.setVisibility(View.VISIBLE);
        updateDockHandleStyle(onRight);

        Point screen = getScreenSize();
        int handleWidth = dpToPx(28);
        int handleHeight = dpToPx(56);

        if (onRight) {
            toolbarParams.x = screen.x - handleWidth;
        } else {
            toolbarParams.x = 0;
        }

        toolbarParams.y = clampY(toolbarParams.y, handleHeight);

        try {
            windowManager.updateViewLayout(rootContainer, toolbarParams);
        } catch (Exception ignored) {}
    }

    public void undockToolbar() {
        if (!isShowing || rootContainer == null || toolbarView == null) return;

        isDocked = false;

        dockHandleView.setVisibility(View.GONE);
        toolbarView.setVisibility(View.VISIBLE);

        Point screen = getScreenSize();
        int tw = toolbarView.getWidth() > 0 ? toolbarView.getWidth() : dpToPx(160);
        int th = toolbarView.getHeight() > 0 ? toolbarView.getHeight() : dpToPx(180);

        if (isDockedOnRight) {
            toolbarParams.x = screen.x - tw - dpToPx(12);
        } else {
            toolbarParams.x = dpToPx(12);
        }

        toolbarParams.y = clampY(toolbarParams.y, th);

        try {
            windowManager.updateViewLayout(rootContainer, toolbarParams);
        } catch (Exception ignored) {}
    }

    public void dismiss() {
        if (!isShowing && drawingView == null) return;

        if (drawingView != null) {
            try { windowManager.removeView(drawingView); } catch (Exception ignored) {}
            drawingView = null;
        }

        if (rootContainer != null) {
            try { windowManager.removeView(rootContainer); } catch (Exception ignored) {}
            rootContainer = null;
            toolbarView = null;
            dockHandleView = null;
        }

        isShowing = false;
        isDocked = false;

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

    // White X erases all drawings and tucks the toolbar away into the side hint tab
    public void clearAndDock() {
        if (drawingView != null) {
            drawingView.clear();
        }
        minimise();
    }

    // Puts the toolbar away into the side hint tab
    public void minimise() {
        if (!isShowing) return;
        Point screen = getScreenSize();
        int tw = toolbarView != null && toolbarView.getWidth() > 0 ? toolbarView.getWidth() : dpToPx(160);
        boolean closerToRight = (toolbarParams != null && (toolbarParams.x + tw / 2) > screen.x / 2);
        dockToolbar(closerToRight);
    }

    public boolean isMinimised() {
        return isDocked || (!isShowing && drawingView != null);
    }

    private Point getScreenSize() {
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return size;
    }

    private int clampX(int x, int viewWidth) {
        Point size = getScreenSize();
        int maxX = size.x - viewWidth;
        return Math.max(0, Math.min(x, maxX));
    }

    private int clampY(int y, int viewHeight) {
        Point size = getScreenSize();
        int maxY = size.y - viewHeight;
        return Math.max(0, Math.min(y, maxY));
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
