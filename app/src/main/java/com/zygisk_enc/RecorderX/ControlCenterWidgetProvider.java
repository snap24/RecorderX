package com.zygisk_enc.RecorderX;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.RemoteViews;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

public class ControlCenterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_UPDATE = "com.zygisk_enc.RecorderX.ACTION_WIDGET_UPDATE";
    public static final String ACTION_TOGGLE_BUBBLE = "com.zygisk_enc.RecorderX.ACTION_TOGGLE_BUBBLE";
    public static final String ACTION_TOGGLE_CAM = "com.zygisk_enc.RecorderX.ACTION_TOGGLE_CAM";
    public static final String ACTION_TOGGLE_ORIENT = "com.zygisk_enc.RecorderX.ACTION_TOGGLE_ORIENT";
    public static final String ACTION_TOGGLE_AUDIO = "com.zygisk_enc.RecorderX.ACTION_TOGGLE_AUDIO";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Context localizedContext = LocaleManager.updateResources(context);
        for (int appWidgetId : appWidgetIds) {
            updateWidget(localizedContext, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context localizedContext = LocaleManager.updateResources(context);
        super.onReceive(localizedContext, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        SettingsManager settingsManager = new SettingsManager(localizedContext);

        if (ACTION_TOGGLE_BUBBLE.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(localizedContext)) {
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + localizedContext.getPackageName()));
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                localizedContext.startActivity(permIntent);
                Toast.makeText(localizedContext, R.string.dialog_overlay_permission_title, Toast.LENGTH_SHORT).show();
            } else {
                boolean nextState = !settingsManager.isFloatingControlEnabled();
                settingsManager.setFloatingControlEnabled(nextState);
                Toast.makeText(localizedContext, nextState ? R.string.toast_bubble_on : R.string.toast_bubble_off, Toast.LENGTH_SHORT).show();
            }
            updateAllWidgets(localizedContext);
        } else if (ACTION_TOGGLE_CAM.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(localizedContext)) {
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + localizedContext.getPackageName()));
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                localizedContext.startActivity(permIntent);
                Toast.makeText(localizedContext, R.string.dialog_overlay_permission_title, Toast.LENGTH_SHORT).show();
            } else if (ContextCompat.checkSelfPermission(localizedContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Intent appIntent = new Intent(localizedContext, MainActivity.class);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                localizedContext.startActivity(appIntent);
                Toast.makeText(localizedContext, R.string.toast_camera_permission_required, Toast.LENGTH_SHORT).show();
            } else {
                boolean nextState = !settingsManager.isCameraOverlayEnabled();
                settingsManager.setCameraOverlayEnabled(nextState);

                if (RecorderService.isRecording()) {
                    if (nextState) {
                        CameraOverlayController.getInstance(localizedContext).show();
                    } else {
                        CameraOverlayController.getInstance(localizedContext).dismiss();
                    }
                    RecorderService service = RecorderService.getInstance();
                    if (service != null) {
                        service.updateCameraState();
                    }
                    Toast.makeText(localizedContext, nextState ? R.string.toast_camera_on : R.string.toast_camera_off, Toast.LENGTH_SHORT).show();
                } else {
                    if (CameraOverlayController.isOverlayShowing()) {
                        CameraOverlayController.getInstance(localizedContext).dismiss();
                    }
                    Toast.makeText(localizedContext, nextState ? R.string.toast_camera_armed : R.string.toast_camera_off, Toast.LENGTH_SHORT).show();
                }
            }
            updateAllWidgets(localizedContext);
        } else if (ACTION_TOGGLE_ORIENT.equals(action)) {
            int current = settingsManager.getOrientation();
            int next = (current == 1) ? 2 : 1;
            settingsManager.setOrientation(next);
            Toast.makeText(localizedContext, next == 1 ? R.string.toast_orientation_portrait : R.string.toast_orientation_landscape, Toast.LENGTH_SHORT).show();
            updateAllWidgets(localizedContext);
        } else if (ACTION_TOGGLE_AUDIO.equals(action)) {
            int current = settingsManager.getAudioSource();
            int next;
            if (current == 3) {
                next = 2; // SYS
            } else if (current == 2) {
                next = 3; // SYS+MIC
            } else {
                next = 2; // Default to SYS
            }

            if (next == 3) {
                if (ContextCompat.checkSelfPermission(localizedContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Intent appIntent = new Intent(localizedContext, MainActivity.class);
                    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    localizedContext.startActivity(appIntent);
                    Toast.makeText(localizedContext, R.string.toast_audio_permission_required, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            settingsManager.setAudioSource(next);

            if (RecorderService.isRecording()) {
                RecorderService service = RecorderService.getInstance();
                if (service != null) {
                    boolean micMuted = (next == 2);
                    service.setMicMuted(micMuted);
                }
            }

            Toast.makeText(localizedContext, next == 3 ? R.string.toast_audio_sys_mic : R.string.toast_audio_sys, Toast.LENGTH_SHORT).show();
            updateAllWidgets(localizedContext);
        } else if (ACTION_WIDGET_UPDATE.equals(action) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            updateAllWidgets(localizedContext);
        }
    }

    public static void updateAllWidgets(Context context) {
        Context localizedContext = LocaleManager.updateResources(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(localizedContext);
        ComponentName thisWidget = new ComponentName(localizedContext, ControlCenterWidgetProvider.class);
        int[] appWidgetIds = manager.getAppWidgetIds(thisWidget);
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            for (int appWidgetId : appWidgetIds) {
                updateWidget(localizedContext, manager, appWidgetId);
            }
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Context localizedContext = LocaleManager.updateResources(context);
        RemoteViews views = new RemoteViews(localizedContext.getPackageName(), R.layout.widget_control_center);
        SettingsManager settingsManager = new SettingsManager(localizedContext);
        boolean isRecording = RecorderService.isRecording();

        // 1. Record / Stop button
        if (isRecording) {
            views.setImageViewResource(R.id.widgetRecIcon, R.drawable.ic_widget_stop);
            views.setTextViewText(R.id.widgetRecLabel, localizedContext.getString(R.string.widget_btn_stop));
            views.setTextColor(R.id.widgetRecLabel, Color.parseColor("#FF3B30"));
            views.setInt(R.id.widgetBtnRec, "setBackgroundResource", R.drawable.bg_widget_btn_active);

            Intent stopIntent = new Intent(localizedContext, RecorderService.class);
            stopIntent.setAction(RecorderService.ACTION_STOP);
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pendingIntent = PendingIntent.getForegroundService(localizedContext, 201, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getService(localizedContext, 201, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            views.setOnClickPendingIntent(R.id.widgetBtnRec, pendingIntent);
        } else {
            views.setImageViewResource(R.id.widgetRecIcon, R.drawable.ic_widget_record);
            views.setTextViewText(R.id.widgetRecLabel, localizedContext.getString(R.string.widget_btn_rec));
            views.setTextColor(R.id.widgetRecLabel, Color.parseColor("#FF3B30"));
            views.setInt(R.id.widgetBtnRec, "setBackgroundResource", R.drawable.bg_widget_btn);

            Intent startIntent = new Intent(localizedContext, RequestCaptureActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(localizedContext, 202, startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetBtnRec, pendingIntent);
        }

        // 2. Open Last Rec button
        Intent lastRecIntent = new Intent(localizedContext, WidgetActionActivity.class);
        lastRecIntent.setAction(WidgetActionActivity.ACTION_OPEN_LAST_REC);
        lastRecIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        PendingIntent lastRecPendingIntent = PendingIntent.getActivity(localizedContext, 203, lastRecIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnPlayLast, lastRecPendingIntent);
        views.setTextViewText(R.id.widgetPlayLastLabel, localizedContext.getString(R.string.widget_btn_last));
        views.setTextColor(R.id.widgetPlayLastLabel, Color.parseColor("#E0E0E0"));

        // 3. Open App button
        Intent appIntent = new Intent(localizedContext, MainActivity.class);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent appPendingIntent = PendingIntent.getActivity(localizedContext, 204, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnOpenApp, appPendingIntent);
        views.setTextViewText(R.id.widgetOpenAppLabel, localizedContext.getString(R.string.widget_btn_app));
        views.setTextColor(R.id.widgetOpenAppLabel, Color.parseColor("#E0E0E0"));

        // 4. Audio Source toggle button (SYS vs SYS+MIC)
        int audioSource = settingsManager.getAudioSource();
        int audioIconRes;
        String audioText;
        boolean audioActive = (audioSource != 0);

        if (audioSource == 3) {
            audioIconRes = R.drawable.ic_widget_audio_mix;
            audioText = localizedContext.getString(R.string.widget_btn_audio_mix);
        } else if (audioSource == 1) {
            audioIconRes = R.drawable.ic_widget_audio_mic;
            audioText = localizedContext.getString(R.string.widget_btn_audio_mic);
        } else if (audioSource == 0) {
            audioIconRes = R.drawable.ic_widget_audio_off;
            audioText = localizedContext.getString(R.string.widget_btn_audio_off);
        } else {
            audioIconRes = R.drawable.ic_widget_audio_sys;
            audioText = localizedContext.getString(R.string.widget_btn_audio_sys);
        }

        views.setImageViewResource(R.id.widgetAudioIcon, audioIconRes);
        views.setTextViewText(R.id.widgetAudioLabel, audioText);
        views.setInt(R.id.widgetBtnAudio, "setBackgroundResource",
                audioActive ? R.drawable.bg_widget_btn_active : R.drawable.bg_widget_btn);
        views.setTextColor(R.id.widgetAudioLabel,
                audioActive ? Color.parseColor("#FFFFFF") : Color.parseColor("#8E8E93"));

        Intent audioIntent = new Intent(localizedContext, ControlCenterWidgetProvider.class);
        audioIntent.setAction(ACTION_TOGGLE_AUDIO);
        PendingIntent audioPendingIntent = PendingIntent.getBroadcast(localizedContext, 208, audioIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnAudio, audioPendingIntent);

        // 5. Camera overlay toggle button
        boolean camOn = settingsManager.isCameraOverlayEnabled();
        views.setInt(R.id.widgetBtnCam, "setBackgroundResource",
                camOn ? R.drawable.bg_widget_btn_active : R.drawable.bg_widget_btn);
        views.setTextViewText(R.id.widgetCamLabel, localizedContext.getString(R.string.widget_btn_cam));
        views.setTextColor(R.id.widgetCamLabel, camOn ? Color.parseColor("#FFFFFF") : Color.parseColor("#8E8E93"));

        Intent camIntent = new Intent(localizedContext, ControlCenterWidgetProvider.class);
        camIntent.setAction(ACTION_TOGGLE_CAM);
        PendingIntent camPendingIntent = PendingIntent.getBroadcast(localizedContext, 206, camIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnCam, camPendingIntent);

        // 6. Floating Bubble toggle button
        boolean bubbleOn = settingsManager.isFloatingControlEnabled();
        views.setInt(R.id.widgetBtnBubble, "setBackgroundResource",
                bubbleOn ? R.drawable.bg_widget_btn_active : R.drawable.bg_widget_btn);
        views.setTextViewText(R.id.widgetBubbleLabel, localizedContext.getString(R.string.widget_btn_bubble));
        views.setTextColor(R.id.widgetBubbleLabel, bubbleOn ? Color.parseColor("#FFFFFF") : Color.parseColor("#8E8E93"));

        Intent bubbleIntent = new Intent(localizedContext, ControlCenterWidgetProvider.class);
        bubbleIntent.setAction(ACTION_TOGGLE_BUBBLE);
        PendingIntent bubblePendingIntent = PendingIntent.getBroadcast(localizedContext, 205, bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnBubble, bubblePendingIntent);

        // 7. Orientation toggle button (Portrait vs Landscape)
        int orientation = settingsManager.getOrientation();
        boolean isPortrait = (orientation == 1);
        views.setImageViewResource(R.id.widgetOrientIcon,
                isPortrait ? R.drawable.ic_widget_portrait : R.drawable.ic_widget_landscape);
        views.setTextViewText(R.id.widgetOrientLabel,
                isPortrait ? localizedContext.getString(R.string.widget_btn_portrait) : localizedContext.getString(R.string.widget_btn_landscape));
        views.setInt(R.id.widgetBtnOrient, "setBackgroundResource", R.drawable.bg_widget_btn_active);
        views.setTextColor(R.id.widgetOrientLabel, Color.parseColor("#FFFFFF"));

        Intent orientIntent = new Intent(localizedContext, ControlCenterWidgetProvider.class);
        orientIntent.setAction(ACTION_TOGGLE_ORIENT);
        PendingIntent orientPendingIntent = PendingIntent.getBroadcast(localizedContext, 207, orientIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetBtnOrient, orientPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
