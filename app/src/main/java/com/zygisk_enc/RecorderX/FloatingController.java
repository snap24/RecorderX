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
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
    private ImageView btnCamera;
    private CameraOverlayController cameraController;
    private View cameraPopupView;
    private BrushController brushController;
    private boolean isExpanded = false;
    private boolean isShowing = false;

    private final android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
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
        if (rootLayout != null) {
            rootLayout.setVisibility(View.VISIBLE);
        }
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
                            R.string.toast_accessibility_permission_mic,
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
                if (isExpanded) {
                    dismissFacecamPopup();
                    timerHandler.removeCallbacks(timerRunnable);
                    isExpanded = false;
                    menuView.setVisibility(View.GONE);
                }
                hide();
                if (brushController == null) {
                    brushController = new BrushController(context, () -> {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (bubbleView != null) {
                                bubbleView.setVisibility(View.VISIBLE);
                                bubbleView.setScaleX(1f);
                                bubbleView.setScaleY(1f);
                                bubbleView.setAlpha(1f);
                            }
                            show();
                        });
                    });
                    brushController.setCameraTouchDelegate(new DrawingOverlayView.CameraTouchDelegate() {
                        @Override
                        public boolean isCameraShowing() {
                            return cameraController != null && cameraController.isShowing();
                        }

                        @Override
                        public boolean isCameraHit(float rawX, float rawY) {
                            return cameraController != null && cameraController.containsPoint(rawX, rawY);
                        }

                        @Override
                        public Path getCameraClipPath(float drawingViewScreenX, float drawingViewScreenY) {
                            return cameraController != null ? cameraController.getClipPath(drawingViewScreenX, drawingViewScreenY) : null;
                        }

                        @Override
                        public boolean onCameraTouchEvent(MotionEvent event) {
                            return cameraController != null && cameraController.handleTouchEvent(event);
                        }
                    });
                }
                brushController.show();
            });

            ImageView btnCollapse = createMenuButton(new CloseIconDrawable(), v -> {
                collapse();
            });
            
            boolean initialCamActive = CameraOverlayController.isOverlayShowing() ||
                    new SettingsManager(context).isCameraOverlayEnabled();
            btnCamera = createMenuButton(new CameraIconDrawable(initialCamActive, getAccentColor()), v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    try {
                        Intent intent = new Intent(context, RequestCaptureActivity.class);
                        intent.setAction(RequestCaptureActivity.ACTION_REQUEST_CAMERA);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        collapse();
                    } catch (Exception ignored) {}
                    return;
                }

                if (cameraController == null) {
                    cameraController = CameraOverlayController.getInstance(context);
                }

                if (cameraController.isShowing()) {
                    cameraController.dismiss();
                    ((ImageView) v).setImageDrawable(new CameraIconDrawable(false, getAccentColor()));
                } else {
                    cameraController.show();
                    ((ImageView) v).setImageDrawable(new CameraIconDrawable(true, getAccentColor()));
                }
                new SettingsManager(context).setCameraOverlayEnabled(cameraController.isShowing());
                ControlCenterWidgetProvider.updateAllWidgets(context);
            });

            btnCamera.setOnLongClickListener(v -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    context.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    try {
                        Intent intent = new Intent(context, RequestCaptureActivity.class);
                        intent.setAction(RequestCaptureActivity.ACTION_REQUEST_CAMERA);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        collapse();
                    } catch (Exception ignored) {}
                    return true;
                }
                if (cameraController == null) {
                    cameraController = CameraOverlayController.getInstance(context);
                }
                if (!cameraController.isShowing()) {
                    cameraController.show();
                    btnCamera.setImageDrawable(new CameraIconDrawable(true, getAccentColor()));
                    new SettingsManager(context).setCameraOverlayEnabled(true);
                    ControlCenterWidgetProvider.updateAllWidgets(context);
                }
                showFacecamPopup();
                return true;
            });

            menuView.addView(tvTimer);
            menuView.addView(btnPause);
            menuView.addView(btnStop);
            menuView.addView(btnMic);
            menuView.addView(btnCamera);
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
                                if (brushController != null && brushController.isMinimised()) {
                                    bubbleView.setVisibility(View.GONE);
                                    brushController.show();
                                } else {
                                    expand();
                                }
                            }
                            return true;
                    }
                    return false;
                }
            });
        }
        
        try {
            windowManager.addView(rootLayout, params);
        } catch (Exception e) {
            android.util.Log.e("FloatingController", "Overlay window rejected by the system", e);
            return;
        }
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
    
    private boolean isLandscapeMode() {
        SettingsManager settings = new SettingsManager(context);
        int orientPref = settings.getOrientation(); // 0=Auto, 1=Portrait, 2=Landscape
        if (orientPref == 1) return false;
        if (orientPref == 2) return true;
        return context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void expand() {
        if (isExpanded) return;
        
        boolean showMic = service.isAudioSourceSystem();
        
        if (menuView != null) {
            menuView.setOrientation(LinearLayout.HORIZONTAL);
            menuView.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        }

        int menuWidth = showMic ? dpToPx(352) : dpToPx(304);
        int menuHeight = dpToPx(56);
        
        if (btnMic != null) {
            btnMic.setVisibility(showMic ? View.VISIBLE : View.GONE);
            btnMic.setImageDrawable(new MicIconDrawable(service.isMicMuted()));
        }

        if (btnCamera != null) {
            boolean isCamActive = CameraOverlayController.isOverlayShowing() ||
                    (cameraController != null && cameraController.isShowing()) ||
                    new SettingsManager(context).isCameraOverlayEnabled();
            btnCamera.setImageDrawable(new CameraIconDrawable(isCamActive, getAccentColor()));
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
        
        dismissFacecamPopup();
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
        dismissFacecamPopup();
        timerHandler.removeCallbacks(timerRunnable);
        if (brushController != null) {
            brushController.dismiss();
            brushController = null;
        }
        if (cameraController != null) {
            cameraController.dismiss();
            cameraController = null;
        }
        if (rootLayout != null) {
            try {
                if (rootLayout.isAttachedToWindow()) {
                    windowManager.removeViewImmediate(rootLayout);
                }
            } catch (Exception ignored) {}
            rootLayout = null;
            isShowing = false;
        }
    }

    public void hide() {
        if (rootLayout != null) {
            rootLayout.setVisibility(View.GONE);
        }
    }

    public void updatePauseState(boolean isPaused) {
        if (btnPause != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                btnPause.setImageDrawable(new PauseIconDrawable(isPaused));
            });
        }
    }

    public void updateMicState() {
        if (btnMic != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                btnMic.setImageDrawable(new MicIconDrawable(service.isMicMuted()));
            });
        }
    }

    public void updateCameraState() {
        if (btnCamera != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                boolean isCamActive = CameraOverlayController.isOverlayShowing() ||
                        (cameraController != null && cameraController.isShowing()) ||
                        new SettingsManager(context).isCameraOverlayEnabled();
                btnCamera.setImageDrawable(new CameraIconDrawable(isCamActive, getAccentColor()));
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

    private int getAccentColor() {
        SharedPreferences themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        int index = themePrefs.getInt("accent_color_index", 1);
        int[] colors = {
            Color.parseColor("#9575CD"), Color.parseColor("#FBBF24"), Color.parseColor("#10B981"),
            Color.parseColor("#EF4444"), Color.parseColor("#3B82F6"), Color.parseColor("#EC4899"),
            Color.parseColor("#06B6D4"), Color.parseColor("#7C3AED"), Color.parseColor("#84CC16"),
            Color.parseColor("#F97316"), Color.parseColor("#14B8A6"), Color.parseColor("#6366F1")
        };
        if (index < 0 || index >= colors.length) index = 1;
        return colors[index];
    }

    private void showFacecamPopup() {
        dismissFacecamPopup();

        if (cameraController == null) {
            cameraController = CameraOverlayController.getInstance(context);
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6181818"));
        bg.setCornerRadius(dpToPx(24));
        bg.setStroke(dpToPx(1.5f), Color.parseColor("#66FFFFFF"));
        layout.setBackground(bg);
        layout.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        String frontStr = context.getString(R.string.camera_front);
        String backStr = context.getString(R.string.camera_back);
        TextView btnFlip = createPopupTextButton(cameraController.isFrontCamera() ? frontStr : backStr, v -> {
            cameraController.switchCamera();
            ((TextView) v).setText(cameraController.isFrontCamera() ? frontStr : backStr);
            android.widget.Toast.makeText(context, cameraController.isFrontCamera() ? R.string.toast_front_camera : R.string.toast_back_camera, android.widget.Toast.LENGTH_SHORT).show();
        });

        String circleStr = context.getString(R.string.camera_shape_circle);
        String rectStr = context.getString(R.string.camera_shape_rect);
        TextView btnShape = createPopupTextButton(cameraController.getCurrentShape() == CameraOverlayController.SHAPE_CIRCLE ? circleStr : rectStr, v -> {
            int nextShape = (cameraController.getCurrentShape() == CameraOverlayController.SHAPE_CIRCLE)
                    ? CameraOverlayController.SHAPE_ROUNDED_RECT
                    : CameraOverlayController.SHAPE_CIRCLE;
            cameraController.setShape(nextShape);
            ((TextView) v).setText(nextShape == CameraOverlayController.SHAPE_CIRCLE ? circleStr : rectStr);
        });

        String smallStr = context.getString(R.string.camera_size_small);
        String medStr = context.getString(R.string.camera_size_medium);
        String largeStr = context.getString(R.string.camera_size_large);
        String sizeLabel = cameraController.getCurrentSizeDp() == CameraOverlayController.SIZE_SMALL ? smallStr
                : (cameraController.getCurrentSizeDp() == CameraOverlayController.SIZE_MEDIUM ? medStr : largeStr);
        TextView btnSize = createPopupTextButton(sizeLabel, v -> {
            int nextSize = (cameraController.getCurrentSizeDp() == CameraOverlayController.SIZE_SMALL) ? CameraOverlayController.SIZE_MEDIUM
                    : ((cameraController.getCurrentSizeDp() == CameraOverlayController.SIZE_MEDIUM) ? CameraOverlayController.SIZE_LARGE : CameraOverlayController.SIZE_SMALL);
            cameraController.setSize(nextSize);
            String label = nextSize == CameraOverlayController.SIZE_SMALL ? smallStr : (nextSize == CameraOverlayController.SIZE_MEDIUM ? medStr : largeStr);
            ((TextView) v).setText(label);
        });

        String lockedStr = context.getString(R.string.camera_pos_locked);
        String moveStr = context.getString(R.string.camera_pos_move);
        TextView btnLock = createPopupTextButton(cameraController.isPositionLocked() ? lockedStr : moveStr, v -> {
            boolean nextLocked = !cameraController.isPositionLocked();
            cameraController.setPositionLocked(nextLocked);
            ((TextView) v).setText(nextLocked ? lockedStr : moveStr);
            android.widget.Toast.makeText(context, nextLocked ? R.string.toast_facecam_locked : R.string.toast_facecam_movable, android.widget.Toast.LENGTH_SHORT).show();
        });

        ImageView btnClosePopup = createMenuButton(new CloseIconDrawable(), v -> {
            dismissFacecamPopup();
        });

        layout.addView(btnFlip);
        layout.addView(btnShape);
        layout.addView(btnSize);
        layout.addView(btnLock);
        layout.addView(btnClosePopup);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams popupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        layout.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupWidth = layout.getMeasuredWidth();
        int popupHeight = layout.getMeasuredHeight();

        int menuWidth = (menuView != null && menuView.getWidth() > 0)
                ? menuView.getWidth()
                : (service.isAudioSourceSystem() ? dpToPx(352) : dpToPx(304));
        int menuHeight = (menuView != null && menuView.getHeight() > 0)
                ? menuView.getHeight()
                : dpToPx(56);

        // Center popup horizontally relative to the floating menu
        int menuCenterX = params.x + (menuWidth / 2);
        int popupX = menuCenterX - (popupWidth / 2);

        // Position popup DOWN below the floating menu
        int popupY = params.y + menuHeight + dpToPx(8);
        Point screenSize = getScreenSize();
        if (popupY + popupHeight > screenSize.y && params.y - popupHeight - dpToPx(8) >= 0) {
            popupY = params.y - popupHeight - dpToPx(8);
        }

        popupParams.gravity = Gravity.TOP | Gravity.START;
        popupParams.x = clampX(popupX, popupWidth);
        popupParams.y = clampY(popupY, popupHeight);

        layout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismissFacecamPopup();
                return true;
            }
            return false;
        });

        cameraPopupView = layout;
        try {
            windowManager.addView(cameraPopupView, popupParams);
        } catch (Exception e) {
            android.util.Log.e("FloatingController", "Failed to show camera popup", e);
        }
    }

    private void dismissFacecamPopup() {
        if (cameraPopupView != null) {
            try {
                if (cameraPopupView.isAttachedToWindow()) {
                    windowManager.removeViewImmediate(cameraPopupView);
                }
            } catch (Exception ignored) {}
            cameraPopupView = null;
        }
    }

    private TextView createPopupTextButton(String text, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
        );
        lp.setMargins(dpToPx(3), dpToPx(2), dpToPx(3), dpToPx(2));
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(8), 0, dpToPx(8), 0);

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setColor(Color.parseColor("#33FFFFFF"));
        itemBg.setCornerRadius(dpToPx(14));
        tv.setBackground(itemBg);

        tv.setOnClickListener(listener);
        return tv;
    }

    private static class CameraIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean isActive;
        private final int accentColor;

        public CameraIconDrawable(boolean isActive, int accentColor) {
            this.isActive = isActive;
            this.accentColor = accentColor;
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            paint.setColor(isActive ? accentColor : Color.WHITE);

            // Camera top viewfinder bump
            float bumpLeft = w * 0.38f;
            float bumpTop = h * 0.22f;
            float bumpRight = w * 0.62f;
            float bumpBottom = h * 0.32f;
            RectF bumpRect = new RectF(bumpLeft, bumpTop, bumpRight, bumpBottom);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(bumpRect, 4f, 4f, paint);

            // Camera main body
            float bodyLeft = w * 0.18f;
            float bodyTop = h * 0.30f;
            float bodyRight = w * 0.82f;
            float bodyBottom = h * 0.78f;
            RectF bodyRect = new RectF(bodyLeft, bodyTop, bodyRight, bodyBottom);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.5f);
            canvas.drawRoundRect(bodyRect, 8f, 8f, paint);

            // Lens circle
            paint.setStyle(isActive ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawCircle(w * 0.5f, h * 0.54f, w * 0.16f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
