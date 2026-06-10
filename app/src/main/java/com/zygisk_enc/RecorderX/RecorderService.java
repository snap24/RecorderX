package com.zygisk_enc.RecorderX;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RecorderService extends Service {
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String EXTRA_RESULT_CODE = "RESULT_CODE";
    public static final String EXTRA_DATA = "DATA";

    private static Intent staticDataBackup = null;
    private static int staticResultCodeBackup = Integer.MIN_VALUE;

    public static void setProjectionData(int resultCode, Intent data) {
        staticResultCodeBackup = resultCode;
        staticDataBackup = data;
    }

    private static final String CHANNEL_ID = "RecorderX_Channel";
    private static final int NOTIFICATION_ID = 1;

    private static boolean isRecording = false;
    private MediaProjection mediaProjection;
    private RecordingSession recordingSession;

    public static boolean isRecording() { return isRecording; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w("RecorderX_Service", "onStartCommand: intent is null");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d("RecorderX_Service", "onStartCommand: Action = " + action);
        
        if (ACTION_START.equals(action)) {
            createNotificationChannel();
            Notification notification = createNotification();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                startRecording(intent);
            } catch (Exception e) {
                Log.e("RecorderX_Service", "Failed to start foreground service", e);
                stopSelf();
            }
        } else if (ACTION_STOP.equals(action)) {
            stopRecording();
        }

        return START_NOT_STICKY;
    }

    private void startRecording(Intent intent) {
        if (isRecording) {
            Log.w("RecorderX_Service", "Start requested but already recording");
            return;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Integer.MIN_VALUE);
        Intent data = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
            } else {
                data = intent.getParcelableExtra(EXTRA_DATA);
            }
        } catch (Exception e) {
            Log.e("RecorderX_Service", "Error unparcelling Intent data", e);
        }

        // Static backup fallback
        if (resultCode == Integer.MIN_VALUE && staticResultCodeBackup != Integer.MIN_VALUE) {
            Log.w("RecorderX_Service", "ResultCode missing from intent, using backup");
            resultCode = staticResultCodeBackup;
        }
        if (data == null && staticDataBackup != null) {
            Log.w("RecorderX_Service", "Data missing from intent, using backup");
            data = staticDataBackup;
        }

        if (resultCode != Integer.MIN_VALUE && data != null) {
            try {
                Log.d("RecorderX_Service", "Acquiring MediaProjection with ResultCode=" + resultCode);
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection == null) {
                    Log.e("RecorderX_Service", "MediaProjection is null!");
                    stopSelf();
                    return;
                }

                SettingsManager settings = new SettingsManager(this);
                recordingSession = new RecordingSession(this, mediaProjection, settings);
                
                Log.i("RecorderX_Service", "Starting recording session...");
                recordingSession.start();
                isRecording = true;
                Log.i("RecorderX_Service", "Recording is now ACTIVE");

                // Clear backup
                staticDataBackup = null;
                staticResultCodeBackup = Integer.MIN_VALUE;
            } catch (Exception e) {
                Log.e("RecorderX_Service", "Error in startRecording", e);
                stopRecording();
            }
        } else {
            Log.e("RecorderX_Service", "Missing ResultCode or Data! ResultCode=" + resultCode + ", Data=" + (data != null));
            stopSelf();
        }
    }

    private void stopRecording() {
        if (recordingSession != null) {
            try {
                recordingSession.stop();
            } catch (Exception ignored) {}
            recordingSession = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }

        isRecording = false;
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Screen Recording", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecorderX")
            .setContentText("Recording screen...")
            .setSmallIcon(R.drawable.ic_record)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
