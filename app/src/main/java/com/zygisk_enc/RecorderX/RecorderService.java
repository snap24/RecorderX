package com.zygisk_enc.RecorderX;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import java.io.File;

public class RecorderService extends Service {
    private static final String TAG = "RecorderX_Service";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAVED_NOTIFICATION_ID = 2;
    private static final String CHANNEL_ID = "recorder_channel";
    private static final String SAVED_CHANNEL_ID = "saved_channel";

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";

    private static boolean isRecording = false;
    private static int resultCode;
    private static Intent projectionData;

    private RecordingSession recordingSession;

    public static boolean isRecording() { return isRecording; }

    public static void setProjectionData(int code, Intent data) {
        resultCode = code;
        projectionData = data;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand: Action = " + action);

        if (ACTION_START.equals(action)) {
            createNotificationChannels();
            Notification notification = createNotification();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SettingsManager settings = new SettingsManager(this);
                        if (settings.getAudioSource() == 1 || settings.getAudioSource() == 3) {
                            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                        }
                    }
                    startForeground(NOTIFICATION_ID, notification, type);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
                startRecording(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground service", e);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopRecording();
        }

        return START_NOT_STICKY;
    }

    private void startRecording(Intent intent) {
        if (isRecording) return;
        
        int currentResultCode = intent != null ? intent.getIntExtra(EXTRA_RESULT_CODE, resultCode) : resultCode;
        Intent currentData = intent != null ? intent.getParcelableExtra(EXTRA_DATA) : null;
        if (currentData == null) currentData = projectionData;

        Log.d(TAG, "Acquiring MediaProjection with ResultCode=" + currentResultCode);
        android.media.projection.MediaProjectionManager projectionManager = (android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        android.media.projection.MediaProjection mediaProjection = projectionManager.getMediaProjection(currentResultCode, currentData);

        if (mediaProjection != null) {
            Log.i(TAG, "Starting recording session...");
            SettingsManager settings = new SettingsManager(this);
            recordingSession = new RecordingSession(this, mediaProjection, settings);
            recordingSession.setListener(() -> {
                Log.i(TAG, "Listener: System stopped projection. Terminating service...");
                stopRecording();
            });
            try {
                recordingSession.start();
                isRecording = true;
            } catch (Exception e) {
                Log.e(TAG, "Error in startRecording", e);
                stopForeground(true);
                stopSelf();
            }
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        Log.i(TAG, "Stopping recording service...");
        
        if (recordingSession != null) {
            String lastPath = recordingSession.getOutputFilePath();
            Uri lastUri = recordingSession.getOutputUri();
            recordingSession.stop();
            
            showSavedNotification(lastPath, lastUri);
        }
        
        isRecording = false;
        stopForeground(true);
        stopSelf();
    }

    private void showSavedNotification(String path, Uri uri) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        Bitmap thumbnail = null;

        try {
            if (uri != null) {
                openIntent.setDataAndType(uri, "video/mp4");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        thumbnail = getContentResolver().loadThumbnail(uri, new android.util.Size(512, 384), null);
                    } catch (Exception e) {
                        // MediaStore might not have indexed it yet. Fallback to Retriever.
                        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(this, uri);
                            thumbnail = retriever.getFrameAtTime();
                        } catch (Exception ignored) {
                            Log.w(TAG, "Thumbnail generation skipped (video likely too short or empty).");
                        } finally {
                            try { retriever.release(); } catch (Exception ignored) {}
                        }
                    }
                }
            } else if (path != null) {
                File file = new File(path);
                Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                openIntent.setDataAndType(fileUri, "video/mp4");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                thumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to attach thumbnail to notification: " + e.getMessage());
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SAVED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Recording Saved")
            .setContentText("Tap to view your recording in /Movies/RecorderX")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail);
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(thumbnail)
                .bigLargeIcon(null));
        }

        manager.notify(SAVED_NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Recorder Service", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(serviceChannel);
            
            NotificationChannel savedChannel = new NotificationChannel(SAVED_CHANNEL_ID, "Recording Saved", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(savedChannel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecorderX is active")
            .setContentText("Recording the screen...")
            .setSmallIcon(R.drawable.ic_record)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
