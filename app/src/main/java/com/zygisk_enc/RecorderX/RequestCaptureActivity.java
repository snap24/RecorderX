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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Immediately request permission
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Permission granted, start the service
            RecorderService.setProjectionData(resultCode, data);
            Intent serviceIntent = new Intent(this, RecorderService.class);
            serviceIntent.setAction(RecorderService.ACTION_START);
            serviceIntent.putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(RecorderService.EXTRA_DATA, data);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
        // Always finish after the dialog is dismissed
        finish();
    }
}
