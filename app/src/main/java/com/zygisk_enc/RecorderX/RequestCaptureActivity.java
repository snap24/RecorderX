package com.zygisk_enc.RecorderX;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A transparent activity that acts as a bridge to launch the 
 * MediaProjection permission dialog and runtime permissions (like Camera)
 * without showing the main app UI.
 */
public class RequestCaptureActivity extends Activity {
    public static final String ACTION_REQUEST_CAMERA = "com.zygisk_enc.RecorderX.ACTION_REQUEST_CAMERA";
    private static final int REQUEST_CODE = 2000;
    private static final int REQUEST_CODE_CAMERA = 2001;
    private static boolean isRequesting = false;

    public static boolean isRequesting() {
        return isRequesting;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.updateResources(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);

        String action = getIntent() != null ? getIntent().getAction() : null;
        if (ACTION_REQUEST_CAMERA.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
                    return;
                }
            }
            enableAndShowCamera();
            finish();
            return;
        }
        
        if (isRequesting || RecorderService.isRecording()) {
            finish();
            return;
        }

        isRequesting = true;
        
        // Immediately request permission
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            isRequesting = false;
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isRequesting = false;
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Permission granted, start the service
            RecorderService.setProjectionData(resultCode, data);
            Intent serviceIntent = new Intent(this, RecorderService.class);
            serviceIntent.setAction(RecorderService.ACTION_START);
            serviceIntent.putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(RecorderService.EXTRA_DATA, data);
            ContextCompat.startForegroundService(this, serviceIntent);
            finish();
        } else {
            // Cancelled or denied
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableAndShowCamera();
            } else {
                Toast.makeText(this, R.string.toast_camera_permission_required, Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }

    private void enableAndShowCamera() {
        SettingsManager settingsManager = new SettingsManager(this);
        settingsManager.setCameraOverlayEnabled(true);

        if (RecorderService.isRecording()) {
            CameraOverlayController.getInstance(this).show();
            RecorderService service = RecorderService.getInstance();
            if (service != null) {
                service.updateCameraState();
            }
            Toast.makeText(this, R.string.toast_camera_on, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.toast_camera_armed, Toast.LENGTH_SHORT).show();
        }
        ControlCenterWidgetProvider.updateAllWidgets(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRequesting = false;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
