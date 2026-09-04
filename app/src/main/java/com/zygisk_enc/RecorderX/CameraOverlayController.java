package com.zygisk_enc.RecorderX;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Collections;

public class CameraOverlayController {
    private static final String TAG = "CameraOverlay";
    private static final String PREFS_NAME = "facecam_prefs";

    private static CameraOverlayController sInstance;

    public static synchronized CameraOverlayController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CameraOverlayController(context.getApplicationContext());
        }
        return sInstance;
    }

    public static synchronized boolean isOverlayShowing() {
        return sInstance != null && sInstance.isShowing();
    }

    public static synchronized void toggle(Context context) {
        CameraOverlayController controller = getInstance(context);
        if (controller.isShowing()) {
            controller.dismiss();
        } else {
            controller.show();
        }
    }

    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_ROUNDED_RECT = 1;

    public static final int SIZE_SMALL = 120;
    public static final int SIZE_MEDIUM = 160;
    public static final int SIZE_LARGE = 200;

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;

    private FrameLayout rootLayout;
    private TextureView textureView;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;

    private boolean isFrontCamera;
    private int currentShape;
    private int currentSizeDp;
    private boolean isPositionLocked;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Size previewSize;
    private int sensorOrientation = 0;

    public CameraOverlayController(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.isFrontCamera = prefs.getBoolean("is_front_camera", true);
        this.currentShape = prefs.getInt("shape", SHAPE_CIRCLE);
        this.currentSizeDp = prefs.getInt("size_dp", SIZE_SMALL);
        this.isPositionLocked = prefs.getBoolean("is_locked", false);

        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        if (isShowing) return;

        startBackgroundThread();
        startOrientationListener();
        initWindowViews();

        try {
            windowManager.addView(rootLayout, params);
            isShowing = true;
            if (RecorderService.getInstance() != null) {
                RecorderService.getInstance().updateCameraState();
            }
            ControlCenterWidgetProvider.updateAllWidgets(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add camera window overlay", e);
        }
    }

    public void dismiss() {
        if (!isShowing) return;

        stopOrientationListener();
        closeCamera();
        stopBackgroundThread();

        if (rootLayout != null) {
            try {
                if (rootLayout.isAttachedToWindow()) {
                    windowManager.removeViewImmediate(rootLayout);
                }
            } catch (Exception ignored) {}
            rootLayout = null;
        }
        isShowing = false;
        if (RecorderService.getInstance() != null) {
            RecorderService.getInstance().updateCameraState();
        }
        ControlCenterWidgetProvider.updateAllWidgets(context);
    }

    private void initWindowViews() {
        int sizePx = dpToPx(currentSizeDp);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("cam_x", dpToPx(24));
        params.y = prefs.getInt("cam_y", dpToPx(120));

        rootLayout = new FrameLayout(context);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));

        updateOverlayBorder();

        textureView = new TextureView(context);
        textureView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        applyShapeClipping();

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera(width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });

        rootLayout.addView(textureView);
        setupTouchListener();
    }

    private void updateOverlayBorder() {
        if (rootLayout == null) return;
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setStroke(dpToPx(2f), Color.WHITE);
        if (currentShape == SHAPE_CIRCLE) {
            border.setShape(GradientDrawable.OVAL);
        } else {
            border.setCornerRadius(dpToPx(16));
        }
        rootLayout.setForeground(border);
    }

    private void applyShapeClipping() {
        if (rootLayout == null) return;
        rootLayout.setClipToOutline(true);
        rootLayout.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (currentShape == SHAPE_CIRCLE) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                } else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dpToPx(16));
                }
            }
        });
    }

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;

    public int getVisibleX() {
        if (isDragging || rootLayout == null || !rootLayout.isAttachedToWindow() || rootLayout.getWidth() == 0) {
            return params != null ? params.x : 0;
        }
        int[] loc = new int[2];
        rootLayout.getLocationOnScreen(loc);
        return loc[0];
    }

    public int getVisibleY() {
        if (isDragging || rootLayout == null || !rootLayout.isAttachedToWindow() || rootLayout.getHeight() == 0) {
            return params != null ? params.y : 0;
        }
        int[] loc = new int[2];
        rootLayout.getLocationOnScreen(loc);
        return loc[1];
    }

    public int getVisibleWidth() {
        if (rootLayout != null && rootLayout.getWidth() > 0) {
            return rootLayout.getWidth();
        }
        return params != null ? params.width : dpToPx(currentSizeDp);
    }

    public int getVisibleHeight() {
        if (rootLayout != null && rootLayout.getHeight() > 0) {
            return rootLayout.getHeight();
        }
        return params != null ? params.height : dpToPx(currentSizeDp);
    }

    public Rect getOverlayBounds() {
        if (!isShowing || params == null) return new Rect();
        int vx = getVisibleX();
        int vy = getVisibleY();
        int vw = getVisibleWidth();
        int vh = getVisibleHeight();
        return new Rect(vx, vy, vx + vw, vy + vh);
    }

    public boolean containsPoint(float rawX, float rawY) {
        if (!isShowing || rootLayout == null || params == null) return false;
        int vx = getVisibleX();
        int vy = getVisibleY();
        int vw = getVisibleWidth();
        int vh = getVisibleHeight();

        // 6dp touch slop / tolerance around boundary so finger taps near edge reliably grab camera
        float pad = dpToPx(6);

        if (rawX < vx - pad || rawX > vx + vw + pad ||
            rawY < vy - pad || rawY > vy + vh + pad) {
            return false;
        }

        if (currentShape == SHAPE_CIRCLE) {
            float cx = vx + vw / 2f;
            float cy = vy + vh / 2f;
            float r = (vw / 2f) + pad;
            float dx = rawX - cx;
            float dy = rawY - cy;
            return (dx * dx + dy * dy) <= (r * r);
        }
        return true;
    }

    public Path getClipPath(float drawingViewScreenX, float drawingViewScreenY) {
        if (!isShowing || params == null) return null;
        int vx = getVisibleX();
        int vy = getVisibleY();
        int vw = getVisibleWidth();
        int vh = getVisibleHeight();

        // 1.5dp expansion so anti-aliased brush strokes can never bleed over the camera border
        float pad = dpToPx(1.5f);
        float left = vx - drawingViewScreenX - pad;
        float top = vy - drawingViewScreenY - pad;
        float right = left + vw + (pad * 2f);
        float bottom = top + vh + (pad * 2f);

        Path path = new Path();
        if (currentShape == SHAPE_CIRCLE) {
            path.addOval(left, top, right, bottom, Path.Direction.CW);
        } else {
            float r = dpToPx(16) + pad;
            path.addRoundRect(left, top, right, bottom, r, r, Path.Direction.CW);
        }
        return path;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        if (!isShowing || rootLayout == null || params == null) return false;
        if (isPositionLocked) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                initialX = getVisibleX();
                initialY = getVisibleY();
                params.x = initialX;
                params.y = initialY;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                int newX = initialX + (int) (event.getRawX() - initialTouchX);
                int newY = initialY + (int) (event.getRawY() - initialTouchY);
                params.x = clampX(newX, params.width);
                params.y = clampY(newY, params.height);
                try {
                    windowManager.updateViewLayout(rootLayout, params);
                } catch (Exception ignored) {}
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                prefs.edit().putInt("cam_x", params.x).putInt("cam_y", params.y).apply();
                return true;
        }
        return false;
    }

    private void setupTouchListener() {
        rootLayout.setOnTouchListener((v, event) -> handleTouchEvent(event));
    }

    public void switchCamera() {
        isFrontCamera = !isFrontCamera;
        prefs.edit().putBoolean("is_front_camera", isFrontCamera).apply();
        closeCamera();
        if (textureView != null && textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    public void setShape(int shape) {
        this.currentShape = shape;
        prefs.edit().putInt("shape", currentShape).apply();
        updateOverlayBorder();
        applyShapeClipping();
        if (rootLayout != null) {
            rootLayout.invalidateOutline();
        }
    }

    public void setSize(int sizeDp) {
        this.currentSizeDp = sizeDp;
        prefs.edit().putInt("size_dp", currentSizeDp).apply();
        int sizePx = dpToPx(currentSizeDp);
        if (rootLayout != null && params != null) {
            params.width = sizePx;
            params.height = sizePx;
            params.x = clampX(params.x, sizePx);
            params.y = clampY(params.y, sizePx);
            try {
                windowManager.updateViewLayout(rootLayout, params);
            } catch (Exception ignored) {}
            configureTransform(sizePx, sizePx);
        }
    }

    public void setPositionLocked(boolean locked) {
        this.isPositionLocked = locked;
        prefs.edit().putBoolean("is_locked", isPositionLocked).apply();
    }

    public boolean isPositionLocked() {
        return isPositionLocked;
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public int getCurrentShape() {
        return currentShape;
    }

    public int getCurrentSizeDp() {
        return currentSizeDp;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        try {
            String targetCameraId = null;
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    targetCameraId = cameraId;
                    Integer orient = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = (orient != null) ? orient : 0;
                    break;
                } else if (!isFrontCamera && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = cameraId;
                    Integer orient = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    sensorOrientation = (orient != null) ? orient : 0;
                    break;
                }
            }

            if (targetCameraId == null && cameraManager.getCameraIdList().length > 0) {
                targetCameraId = cameraManager.getCameraIdList()[0];
            }

            if (targetCameraId == null) {
                Log.e(TAG, "No camera found on device");
                return;
            }

            CameraCharacteristics targetChars = cameraManager.getCameraCharacteristics(targetCameraId);
            android.hardware.camera2.params.StreamConfigurationMap map = targetChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));
            } else {
                previewSize = new Size(720, 720);
            }
            configureTransform(width, height);

            cameraManager.openCamera(targetCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "CameraDevice error: " + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error opening Camera2 device", e);
        }
    }

    private void startPreview() {
        if (cameraDevice == null || textureView == null || !textureView.isAvailable()) return;

        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting preview session", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Failed creating camera preview session configuration");
                }
            }, backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "Failed creating camera preview session", e);
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null || previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;

        int rotation = windowManager.getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        int pw = previewSize.getWidth();
        int ph = previewSize.getHeight();

        float scaleX = 1.0f;
        float scaleY = 1.0f;

        if (pw == ph) {
            // Native 1:1 stream (e.g. 720x720, 1080x1080)
            // Pixel aspect ratio is natively 1:1, centered from sensor center point
            scaleX = 1.0f;
            scaleY = 1.0f;
        } else {
            // For rectangular preview stream (e.g. 640x480):
            // In portrait (ROTATION_0, ROTATION_180), the camera sensor is landscape,
            // so the buffer's height maps to view width, and buffer's width maps to view height.
            // In landscape (ROTATION_90, ROTATION_270), buffer's width maps to view width.
            boolean isPortrait = (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180);
            float effW = isPortrait ? ph : pw;
            float effH = isPortrait ? pw : ph;

            float aspectEff = effW / effH;
            float aspectView = (float) viewWidth / (float) viewHeight;

            // True Center-Crop: expand shorter dimension to fill square without squishing
            if (aspectEff < aspectView) {
                scaleX = 1.0f;
                scaleY = 1.0f / aspectEff;
            } else {
                scaleX = aspectEff / aspectView;
                scaleY = 1.0f;
            }
        }

        matrix.setScale(scaleX, scaleY, centerX, centerY);

        if (rotation == Surface.ROTATION_90) {
            matrix.postRotate(270, centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        } else if (rotation == Surface.ROTATION_270) {
            matrix.postRotate(90, centerX, centerY);
        }

        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, centerX, centerY);
        }

        textureView.setTransform(matrix);
    }

    private Size chooseOptimalSize(Size[] choices) {
        if (choices == null || choices.length == 0) return new Size(720, 720);

        // 1. Prefer native 1:1 square preview sizes (e.g. 720x720, 1080x1080, 704x704)
        // Camera ISP will natively center-crop from sensor, zero squish/stretch
        Size bestSquare = null;
        int bestSquareDiff = Integer.MAX_VALUE;
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight()) {
                int diff = Math.abs(size.getWidth() - 720);
                if (diff < bestSquareDiff && size.getWidth() <= 1080 && size.getWidth() >= 480) {
                    bestSquareDiff = diff;
                    bestSquare = size;
                }
            }
        }
        if (bestSquare != null) {
            return bestSquare;
        }

        // 2. Fallback: 4:3 preview size (e.g. 640x480, 800x600, 960x720, 1280x960)
        Size best43 = null;
        int best43Diff = Integer.MAX_VALUE;
        for (Size size : choices) {
            float aspect = (float) size.getWidth() / (float) size.getHeight();
            if (Math.abs(aspect - (4.0f / 3.0f)) < 0.05f) {
                int diff = Math.abs(size.getWidth() - 640) + Math.abs(size.getHeight() - 480);
                if (diff < best43Diff && size.getWidth() <= 1280 && size.getHeight() <= 960) {
                    best43Diff = diff;
                    best43 = size;
                }
            }
        }
        if (best43 != null) {
            return best43;
        }

        // 3. Fallback: Any supported size <= 1280x960
        Size bestAny = choices[0];
        int minDiff = Integer.MAX_VALUE;
        for (Size size : choices) {
            int diff = Math.abs(size.getWidth() - 640) + Math.abs(size.getHeight() - 480);
            if (diff < minDiff && size.getWidth() <= 1280 && size.getHeight() <= 960) {
                minDiff = diff;
                bestAny = size;
            }
        }
        return bestAny;
    }

    private android.view.OrientationEventListener orientationListener;

    private void startOrientationListener() {
        if (orientationListener == null) {
            orientationListener = new android.view.OrientationEventListener(context) {
                private int lastRotation = -1;
                @Override
                public void onOrientationChanged(int orientation) {
                    if (!isShowing || rootLayout == null || params == null) return;
                    int curRotation = windowManager.getDefaultDisplay().getRotation();
                    if (curRotation != lastRotation) {
                        lastRotation = curRotation;
                        new Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (isShowing && rootLayout != null && params != null) {
                                configureTransform(params.width, params.height);
                            }
                        });
                    }
                }
            };
            if (orientationListener.canDetectOrientation()) {
                orientationListener.enable();
            }
        }
    }

    private void stopOrientationListener() {
        if (orientationListener != null) {
            orientationListener.disable();
            orientationListener = null;
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.close();
            } catch (Exception ignored) {}
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Exception ignored) {}
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(500);
            } catch (Exception ignored) {}
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private int clampX(int x, int width) {
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return Math.max(0, Math.min(x, size.x - width));
    }

    private int clampY(int y, int height) {
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        return Math.max(0, Math.min(y, size.y - height));
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
