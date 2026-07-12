package com.zygisk_enc.RecorderX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
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

public class FloatingController {
    private final Context context;
    private final WindowManager windowManager;
    private final RecorderService service;
    private final int bubbleSize;
    
    private FrameLayout rootLayout;
    private WindowManager.LayoutParams params;
    
    private FrameLayout bubbleView;
    private LinearLayout menuView;
    private android.widget.TextView tvTimer;
    private ImageView btnMic;
    private ImageView btnPause;
    private BrushController brushController;
    private boolean isExpanded = false;
    private boolean isShowing = false;

    private final android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isExpanded && tvTimer != null) {
                long durationMs = service.getActiveRecordingDurationMs();
                long seconds = (durationMs / 1000) % 60;
                long minutes = (durationMs / (1000 * 60)) % 60;
                long hours = (durationMs / (1000 * 60 * 60)) % 24;
                
                String timeStr;
                if (hours > 0) {
                    timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
                } else {
                    timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
                }
                tvTimer.setText(timeStr);
            }
            timerHandler.postDelayed(this, 1000);
        }
    };
    
    public FloatingController(RecorderService service) {
        this.service = service;
        this.context = service;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.bubbleSize = dpToPx(48); // Reduced size from 56dp to 48dp
    }
    
    @SuppressLint("ClickableViewAccessibility")
    public void show() {
        if (isShowing) return;
        
        if (rootLayout == null) {
            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );
            
            // Restore saved position or default to the right side
            android.content.SharedPreferences prefs = context.getSharedPreferences("floating_bubble_prefs", Context.MODE_PRIVATE);
            boolean hasSaved = prefs.getBoolean("bubble_has_saved", false);
            if (hasSaved) {
                params.gravity = prefs.getInt("bubble_gravity", Gravity.TOP | Gravity.START);
                params.x = prefs.getInt("bubble_x", 100);
                params.y = prefs.getInt("bubble_y", 500);
            } else {
                params.gravity = Gravity.TOP | Gravity.END;
                params.x = 0; // Snap to the right edge
                params.y = dpToPx(150); // Initial vertical position
            }
            
            rootLayout = new FrameLayout(context);
            
            // 1. Bubble View (App Logo Only)
            bubbleView = new FrameLayout(context);
            FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(bubbleSize, bubbleSize);
            bubbleView.setLayoutParams(bubbleParams);
            bubbleView.setBackground(null);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bubbleView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        int w = view.getWidth();
                        int h = view.getHeight();
                        if (w > 0 && h > 0) {
                            outline.setOval(0, 0, w, h);
                        }
                    }
                });
                bubbleView.setElevation(dpToPx(6));
            }
            
            ImageView icon = new ImageView(context);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            icon.setLayoutParams(iconParams);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(R.mipmap.ic_launcher);
            bubbleView.addView(icon);
            
            // 2. Menu View (Controls)
            menuView = new LinearLayout(context);
            menuView.setOrientation(LinearLayout.HORIZONTAL);
            menuView.setVisibility(View.GONE);
            
            GradientDrawable menuBg = new GradientDrawable();
            menuBg.setCornerRadius(dpToPx(28));
            menuBg.setColor(Color.parseColor("#B31F1F1F")); // 70% transparent dark grey
            menuBg.setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF")); // 40% transparent white border
            menuView.setBackground(menuBg);
            menuView.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            
            // Create Timer TextView
            tvTimer = new android.widget.TextView(context);
            LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            );
            timerParams.gravity = Gravity.CENTER_VERTICAL;
            timerParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
            tvTimer.setLayoutParams(timerParams);
            tvTimer.setTextColor(Color.WHITE);
            tvTimer.setTextSize(14f);
            tvTimer.setGravity(Gravity.CENTER);
            tvTimer.setTypeface(android.graphics.Typeface.MONOSPACE);
            tvTimer.setText("00:00");
            
            btnPause = createMenuButton(new PauseIconDrawable(service.isPaused()), v -> {
                if (service.isPaused()) {
                    service.resumeRecording();
                    ((ImageView) v).setImageDrawable(new PauseIconDrawable(false));
                } else {
                    service.pauseRecording();
                    ((ImageView) v).setImageDrawable(new PauseIconDrawable(true));
                }
            });
            
            ImageView btnStop = createMenuButton(new StopIconDrawable(), v -> {
                service.stopRecordingExternally();
            });
            
            ImageView btnScreenshot = createMenuButton(new ScreenshotIconDrawable(), v -> {
                collapse();
                if (rootLayout != null) {
                    rootLayout.setVisibility(View.GONE);
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    service.takeScreenshot(() -> {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (rootLayout != null) {
                                rootLayout.setVisibility(View.VISIBLE);
                                bubbleView.setScaleX(0.5f);
                                bubbleView.setScaleY(0.5f);
                                bubbleView.setAlpha(0f);
                                bubbleView.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .alpha(1f)
                                    .setDuration(150)
                                    .start();
                            }
                        });
                    });
                }, 250);
            });
            
            btnMic = createMenuButton(new MicIconDrawable(service.isMicMuted()), v -> {
                RecorderAccessibilityService accessService = RecorderAccessibilityService.getInstance();
                if (accessService != null) {
                    // Accessibility service active — toggle mic normally
                    boolean nextMuted = !service.isMicMuted();
                    service.setMicMuted(nextMuted);
                    ((ImageView) v).setImageDrawable(new MicIconDrawable(nextMuted));
                } else {
                    // Accessibility service not enabled — guide user to enable it
                    android.widget.Toast.makeText(context,
                            "Please enable Accessibility permission for RecorderX to control mic",
                            android.widget.Toast.LENGTH_LONG).show();
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        android.util.Log.e("FloatingController", "Could not open accessibility settings", e);
                    }
                }
            });
            
            ImageView btnBrush = createMenuButton(new BrushController.BrushIconDrawable(), v -> {
                collapse();
                bubbleView.setVisibility(View.GONE);
                brushController = new BrushController(context, () -> {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        bubbleView.setVisibility(View.VISIBLE);
                        bubbleView.setScaleX(0.5f);
                        bubbleView.setScaleY(0.5f);
                        bubbleView.setAlpha(0f);
                        bubbleView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(150)
                            .start();
                        brushController = null;
                    });
                });
                brushController.show();
            });

            ImageView btnCollapse = createMenuButton(new CloseIconDrawable(), v -> {
                collapse();
            });
            
            menuView.addView(tvTimer);
            menuView.addView(btnPause);
            menuView.addView(btnStop);
            menuView.addView(btnMic);
            menuView.addView(btnScreenshot);
            menuView.addView(btnBrush);
            menuView.addView(btnCollapse);
            
            rootLayout.addView(bubbleView);
            rootLayout.addView(menuView);
            
            // Listen for touch outside window to collapse automatically
            rootLayout.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    if (isExpanded) {
                        collapse();
                    }
                    return true;
                }
                return false;
            });
            
            bubbleView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;
                private long touchStartTime;
                
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // Convert back to START-relative coordinates if currently END-aligned
                            if (params.gravity == (Gravity.TOP | Gravity.END)) {
                                Point screenSize = getScreenSize();
                                params.x = screenSize.x - params.x - bubbleSize;
                                params.gravity = Gravity.TOP | Gravity.START;
                                windowManager.updateViewLayout(rootLayout, params);
                              }
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            touchStartTime = System.currentTimeMillis();
                            return true;
                            
                        case MotionEvent.ACTION_MOVE:
                            int targetX = initialX + (int) (event.getRawX() - initialTouchX);
                            int targetY = initialY + (int) (event.getRawY() - initialTouchY);
                            params.x = clampX(targetX, bubbleSize);
                            params.y = clampY(targetY, bubbleSize);
                            windowManager.updateViewLayout(rootLayout, params);
                            return true;
                            
                        case MotionEvent.ACTION_UP:
                            long duration = System.currentTimeMillis() - touchStartTime;
                            float diffX = Math.abs(event.getRawX() - initialTouchX);
                            float diffY = Math.abs(event.getRawY() - initialTouchY);
                            
                            // Decide side of screen and snap gravity configuration
                            Point screenSize = getScreenSize();
                            boolean isRightHalf = (params.x + bubbleSize / 2) > (screenSize.x / 2);
                            
                            if (isRightHalf) {
                                params.gravity = Gravity.TOP | Gravity.END;
                                params.x = screenSize.x - params.x - bubbleSize;
                            } else {
                                params.gravity = Gravity.TOP | Gravity.START;
                            }
                            windowManager.updateViewLayout(rootLayout, params);
                            
                            // Persist bubble coordinates and gravity
                            context.getSharedPreferences("floating_bubble_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putInt("bubble_x", params.x)
                                .putInt("bubble_y", params.y)
                                .putInt("bubble_gravity", params.gravity)
                                .putBoolean("bubble_has_saved", true)
                                .apply();
                            
                            if (duration < 200 && diffX < 10 && diffY < 10) {
                                expand();
                            }
                            return true;
                    }
                    return false;
                }
            });
        }
        
        windowManager.addView(rootLayout, params);
        isShowing = true;
        if (isExpanded) {
            timerHandler.post(timerRunnable);
        }
    }
    
    private ImageView createMenuButton(Drawable iconDrawable, View.OnClickListener listener) {
        ImageView button = new ImageView(context);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
        btnParams.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        button.setLayoutParams(btnParams);
        button.setImageDrawable(iconDrawable);
        button.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        button.setOnClickListener(listener);
        return button;
    }
    
    private void expand() {
        if (isExpanded) return;
        
        boolean showMic = service.isAudioSourceSystem();
        int menuWidth = showMic ? dpToPx(304) : dpToPx(256);
        int menuHeight = dpToPx(56);
        
        if (btnMic != null) {
            btnMic.setVisibility(showMic ? View.VISIBLE : View.GONE);
            btnMic.setImageDrawable(new MicIconDrawable(service.isMicMuted()));
        }
        
        // Adjust coordinate so the expanded menu stays completely on screen
        params.x = clampX(params.x, menuWidth);
        params.y = clampY(params.y, menuHeight);
        
        isExpanded = true;
        
        // Animate bubbleView shrinking and fading out
        bubbleView.animate()
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0f)
            .setDuration(120)
            .withEndAction(() -> {
                bubbleView.setVisibility(View.GONE);
                
                // Animate menuView expanding and fading in
                menuView.setVisibility(View.VISIBLE);
                menuView.setScaleX(0.5f);
                menuView.setScaleY(0.5f);
                menuView.setAlpha(0f);
                menuView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(150)
                    .start();
            })
            .start();
            
        windowManager.updateViewLayout(rootLayout, params);
        
        // Start duration timer updates
        timerHandler.post(timerRunnable);
    }
    
    public void collapse() {
        if (!isExpanded) return;
        
        // Stop duration timer updates
        timerHandler.removeCallbacks(timerRunnable);
        isExpanded = false;
        
        // Animate menuView shrinking and fading out
        menuView.animate()
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0f)
            .setDuration(120)
            .withEndAction(() -> {
                menuView.setVisibility(View.GONE);
                
                // Animate bubbleView expanding and fading in
                bubbleView.setVisibility(View.VISIBLE);
                bubbleView.setScaleX(0.5f);
                bubbleView.setScaleY(0.5f);
                bubbleView.setAlpha(0f);
                bubbleView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(150)
                    .start();
            })
            .start();
            
        windowManager.updateViewLayout(rootLayout, params);
    }
    
    public void dismiss() {
        timerHandler.removeCallbacks(timerRunnable);
        if (brushController != null) {
            brushController.dismiss();
            brushController = null;
        }
        if (isShowing && rootLayout != null) {
            try {
                windowManager.removeView(rootLayout);
            } catch (Exception ignored) {}
            isShowing = false;
        }
    }

    public void updatePauseState(boolean isPaused) {
        if (btnPause != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                btnPause.setImageDrawable(new PauseIconDrawable(isPaused));
            });
        }
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
    
    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // --- Custom Drawables ---

    private static class HexagonDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            
            float radius = Math.min(w, h) / 2f - 4f;
            float cx = w / 2f;
            float cy = h / 2f;
            
            path.reset();
            for (int i = 0; i < 6; i++) {
                double angle = Math.toRadians(90 + i * 60);
                float x = (float) (cx + radius * Math.cos(angle));
                float y = (float) (cy + radius * Math.sin(angle));
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
            
            // Draw transparent glass fill
            paint.setColor(Color.parseColor("#4DFFFFFF")); // 30% transparent white
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            
            // Draw sharp frosted white stroke
            paint.setColor(Color.parseColor("#B3FFFFFF")); // 70% transparent white border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class PauseIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isPlay;

        public PauseIconDrawable(boolean isPlay) {
            this.isPlay = isPlay;
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            
            if (isPlay) {
                Path path = new Path();
                path.moveTo(w * 0.35f, h * 0.25f);
                path.lineTo(w * 0.75f, h * 0.5f);
                path.lineTo(w * 0.35f, h * 0.75f);
                path.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(w * 0.3f, h * 0.25f, w * 0.42f, h * 0.75f, paint);
                canvas.drawRect(w * 0.58f, h * 0.25f, w * 0.7f, h * 0.75f, paint);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class StopIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public StopIconDrawable() {
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class CloseIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CloseIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(5f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            canvas.drawLine(w * 0.35f, h * 0.35f, w * 0.65f, h * 0.65f, paint);
            canvas.drawLine(w * 0.65f, h * 0.35f, w * 0.35f, h * 0.65f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class ScreenshotIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ScreenshotIconDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            
            float left = w * 0.25f;
            float right = w * 0.75f;
            float top = h * 0.25f;
            float bottom = h * 0.75f;
            float length = w * 0.15f; // length of each bracket arm
            
            // Top-Left corner
            canvas.drawLine(left, top, left + length, top, paint);
            canvas.drawLine(left, top, left, top + length, paint);
            
            // Top-Right corner
            canvas.drawLine(right, top, right - length, top, paint);
            canvas.drawLine(right, top, right, top + length, paint);
            
            // Bottom-Left corner
            canvas.drawLine(left, bottom, left + length, bottom, paint);
            canvas.drawLine(left, bottom, left, bottom - length, paint);
            
            // Bottom-Right corner
            canvas.drawLine(right, bottom, right - length, bottom, paint);
            canvas.drawLine(right, bottom, right, bottom - length, paint);
            
            // Central dot (fill)
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.08f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class MicIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isMuted;

        public MicIconDrawable(boolean isMuted) {
            this.isMuted = isMuted;
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            
            paint.setStyle(Paint.Style.FILL);
            if (!isMuted) {
                paint.setColor(Color.RED);
            } else {
                paint.setColor(Color.WHITE);
            }
            
            // 1. Microphone body (rounded rect)
            float left = w * 0.38f;
            float right = w * 0.62f;
            float top = h * 0.25f;
            float bottom = h * 0.58f;
            canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, paint);
            
            // 2. Microphone U-stand (stroke)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            
            float uLeft = w * 0.3f;
            float uRight = w * 0.7f;
            float uTop = h * 0.42f;
            float uBottom = h * 0.64f;
            
            android.graphics.RectF oval = new android.graphics.RectF(uLeft, uTop, uRight, uBottom);
            canvas.drawArc(oval, 0, 180, false, paint);
            
            // 3. Stand stem & base plate
            canvas.drawLine(w * 0.5f, uBottom, w * 0.5f, h * 0.78f, paint);
            canvas.drawLine(w * 0.38f, h * 0.78f, w * 0.62f, h * 0.78f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
