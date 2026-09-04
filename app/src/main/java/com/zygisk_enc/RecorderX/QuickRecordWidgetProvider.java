package com.zygisk_enc.RecorderX;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

public class QuickRecordWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_UPDATE = "com.zygisk_enc.RecorderX.ACTION_WIDGET_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Context localizedContext = LocaleManager.updateResources(context);
        for (int appWidgetId : appWidgetIds) {
            updateWidget(localizedContext, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_WIDGET_UPDATE.equals(action) ||
            AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            updateAllWidgets(LocaleManager.updateResources(context));
        }
    }

    public static void updateAllWidgets(Context context) {
        Context localizedContext = LocaleManager.updateResources(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(localizedContext);
        ComponentName thisWidget = new ComponentName(localizedContext, QuickRecordWidgetProvider.class);
        int[] appWidgetIds = manager.getAppWidgetIds(thisWidget);
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            for (int appWidgetId : appWidgetIds) {
                updateWidget(localizedContext, manager, appWidgetId);
            }
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Context localizedContext = LocaleManager.updateResources(context);
        RemoteViews views = new RemoteViews(localizedContext.getPackageName(), R.layout.widget_quick_record);
        boolean isRecording = RecorderService.isRecording();

        if (isRecording) {
            views.setImageViewResource(R.id.widgetQuickRecordIcon, R.drawable.ic_widget_stop);
            views.setTextViewText(R.id.widgetQuickRecordText, localizedContext.getString(R.string.widget_btn_stop));
            views.setTextColor(R.id.widgetQuickRecordText, Color.parseColor("#FF3B30"));

            Intent stopIntent = new Intent(localizedContext, RecorderService.class);
            stopIntent.setAction(RecorderService.ACTION_STOP);
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pendingIntent = PendingIntent.getForegroundService(localizedContext, 101, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getService(localizedContext, 101, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            views.setOnClickPendingIntent(R.id.widgetQuickRecordRoot, pendingIntent);
        } else {
            views.setImageViewResource(R.id.widgetQuickRecordIcon, R.drawable.ic_widget_record);
            views.setTextViewText(R.id.widgetQuickRecordText, localizedContext.getString(R.string.widget_btn_rec));
            views.setTextColor(R.id.widgetQuickRecordText, Color.parseColor("#FF3B30"));

            Intent startIntent = new Intent(localizedContext, RequestCaptureActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(localizedContext, 102, startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetQuickRecordRoot, pendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
