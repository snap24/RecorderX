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
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_DELETE = "ACTION_DELETE";
    public static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";

    private static boolean isRecording = false;
    private static int resultCode;
    private static Intent projectionData;

    private RecordingSession recordingSession;
    private FloatingController floatingController;

    public interface RecordingStateListener {
        void onStateChanged(boolean isRecording);
    }

    private static final java.util.List<RecordingStateListener> stateListeners = new java.util.ArrayList<>();

    public static void registerStateListener(RecordingStateListener listener) {
        synchronized (stateListeners) {
            stateListeners.add(listener);
            listener.onStateChanged(isRecording);
        }
    }

    public static void unregisterStateListener(RecordingStateListener listener) {
        synchronized (stateListeners) {
            stateListeners.remove(listener);
        }
    }

    private static void notifyStateChanged() {
        synchronized (stateListeners) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                synchronized (stateListeners) {
                    for (RecordingStateListener listener : stateListeners) {
                        listener.onStateChanged(isRecording);
                    }
                }
            });
        }
    }

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
        } else if (ACTION_PAUSE.equals(action)) {
            pauseRecording();
        } else if (ACTION_RESUME.equals(action)) {
            resumeRecording();
        } else if (ACTION_DELETE.equals(action)) {
            String deleteUriStr = intent.getStringExtra("delete_uri");
            String deletePath = intent.getStringExtra("delete_path");
            deleteRecordingFile(deleteUriStr, deletePath);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(SAVED_NOTIFICATION_ID);
            }
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
                notifyStateChanged();

                if (settings.isFloatingControlEnabled()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                        Log.w(TAG, "Cannot show floating control: overlay permission not granted");
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            floatingController = new FloatingController(this);
                            floatingController.show();
                        });
                    }
                }
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

        if (floatingController != null) {
            floatingController.dismiss();
            floatingController = null;
        }
        
        if (recordingSession != null) {
            String lastPath = recordingSession.getOutputFilePath();
            Uri lastUri = recordingSession.getOutputUri();
            recordingSession.stop();
            
            if (lastPath != null || lastUri != null) {
                showSavedNotification(lastPath, lastUri);
            }
        }
        
        isRecording = false;
        notifyStateChanged();
        stopForeground(true);
        stopSelf();
    }

    public boolean isPaused() {
        return recordingSession != null && recordingSession.isPaused();
    }

    public long getActiveRecordingDurationMs() {
        return recordingSession != null ? recordingSession.getActiveDurationMs() : 0;
    }

    public void takeScreenshot(Runnable onCompleted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            RecorderAccessibilityService serviceInstance = RecorderAccessibilityService.getInstance();
            if (serviceInstance != null) {
                boolean success = serviceInstance.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                if (success) {
                    Log.i(TAG, "System screenshot triggered via accessibility service");
                } else {
                    Log.e(TAG, "Failed to trigger system screenshot via accessibility service");
                }
                if (onCompleted != null) onCompleted.run();
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(this, "Please enable Accessibility permission for RecorderX to take screenshots", android.widget.Toast.LENGTH_LONG).show();
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not launch accessibility settings", e);
                    }
                });
                if (onCompleted != null) onCompleted.run();
            }
        } else {
            if (recordingSession != null) {
                recordingSession.takeScreenshot(onCompleted);
            } else {
                if (onCompleted != null) onCompleted.run();
            }
        }
    }

    public boolean isMicMuted() {
        return recordingSession != null ? recordingSession.isMicMuted() : true;
    }

    public void setMicMuted(boolean muted) {
        if (recordingSession != null) {
            recordingSession.setMicMuted(muted);
        }
    }

    public boolean isAudioSourceSystem() {
        int source = new SettingsManager(this).getAudioSource();
        return source == 2 || source == 3;
    }

    public void pauseRecording() {
        if (recordingSession != null) {
            recordingSession.pause();
            updateNotificationPaused(true);
            if (floatingController != null) {
                floatingController.updatePauseState(true);
            }
        }
    }

    public void resumeRecording() {
        if (recordingSession != null) {
            recordingSession.resume();
            updateNotificationPaused(false);
            if (floatingController != null) {
                floatingController.updatePauseState(false);
            }
        }
    }

    public void stopRecordingExternally() {
        stopRecording();
    }

    private void updateNotificationPaused(boolean paused) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = createActiveNotification(paused);
            manager.notify(NOTIFICATION_ID, notification);
        }
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
        
        Intent deleteIntent = new Intent(this, RecorderService.class);
        deleteIntent.setAction(ACTION_DELETE);
        if (uri != null) {
            deleteIntent.putExtra("delete_uri", uri.toString());
        }
        if (path != null) {
            deleteIntent.putExtra("delete_path", path);
        }
        PendingIntent deletePendingIntent = PendingIntent.getService(
            this,
            1,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SAVED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle("Recording Saved")
            .setContentText("Tap to view your recording in /Movies/RecorderX")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Delete", deletePendingIntent);

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail);
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(thumbnail)
                .bigLargeIcon(null));
        }

        manager.notify(SAVED_NOTIFICATION_ID, builder.build());
    }

    private void deleteRecordingFile(String uriStr, String path) {
        try {
            if (uriStr != null) {
                Uri uri = Uri.parse(uriStr);
                getContentResolver().delete(uri, null, null);
                Log.i(TAG, "Deleted recording via MediaStore Uri: " + uri);
            } else if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    file.delete();
                    Log.i(TAG, "Deleted recording file: " + path);
                }
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(getApplicationContext(), "Recording deleted", android.widget.Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete recording file", e);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Recorder Service", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(serviceChannel);
            
            NotificationChannel savedChannel = new NotificationChannel(SAVED_CHANNEL_ID, "Recording Saved", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(savedChannel);
        }
    }

    private Notification createNotification() {
        return createActiveNotification(false);
    }

    private Notification createActiveNotification(boolean paused) {
        Intent stopIntent = new Intent(this, RecorderService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 
            0, 
            stopIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent pauseIntent = new Intent(this, RecorderService.class);
        pauseIntent.setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getService(
            this, 
            0, 
            pauseIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        String title = paused ? "RecorderX is paused" : "RecorderX is active";
        String actionLabel = paused ? "Resume Recording" : "Pause Recording";

        android.graphics.drawable.Icon pauseIcon = createTextIcon(paused ? "RESUME" : "PAUSE");
        android.graphics.drawable.Icon stopIcon = createTextIcon("STOP");

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_record)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(
                pauseIcon, actionLabel, pausePendingIntent
            ).build())
            .addAction(new Notification.Action.Builder(
                stopIcon, "Stop Recording", stopPendingIntent
            ).build())
            .setStyle(new Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1))
            .build();
    }

    private android.graphics.drawable.Icon createTextIcon(String text) {
        int width = 96;
        int height = 96;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.WHITE);
        
        // Dynamically adjust text size based on length to prevent horizontal clipping
        float textSize = 26f;
        if (text.length() > 5) {
            textSize = 21f; // Scaled down for longer words like "RESUME"
        }
        paint.setTextSize(textSize);
        
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        
        android.graphics.Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float fontHeight = fontMetrics.descent - fontMetrics.ascent;
        float y = (height - fontHeight) / 2f - fontMetrics.ascent;
        
        canvas.drawText(text, width / 2f, y, paint);
        
        return android.graphics.drawable.Icon.createWithBitmap(bitmap);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
