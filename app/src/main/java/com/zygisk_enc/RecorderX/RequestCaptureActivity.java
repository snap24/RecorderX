package com.zygisk_enc.RecorderX;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A transparent activity that acts as a bridge to launch the 
 * MediaProjection permission dialog from the Quick Settings Tile
 * without showing the main app UI.
 */
public class RequestCaptureActivity extends Activity {
    private static final int REQUEST_CODE = 2000;
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
        ControlCenterWidgetProvider.cancelPendingTermination();
        
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
            exitProcessIfIdle();
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
            exitProcessIfIdle();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRequesting = false;
        if (!RecorderService.isRecording()) {
            exitProcessIfIdle();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void exitProcessIfIdle() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!MainActivity.isActivityVisible() && !RecorderService.isRecording() && !CameraOverlayController.isOverlayShowing()) {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }
        }, 300);
    }
}
