package com.zygisk_enc.RecorderX;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaFormat;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class SettingsManager {
    private static final String PREFS_NAME = "recorderx_settings";
    private static final String KEY_RESOLUTION = "resolution";
    private static final String KEY_BITRATE = "bitrate";
    private static final String KEY_FPS = "fps";
    private static final String KEY_AUDIO_SOURCE = "audio_source";
    private static final String KEY_CODEC = "video_codec";
    private static final String KEY_BITRATE_MODE = "bitrate_mode";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_NAMING_TEMPLATE = "naming_template";
    private static final String KEY_ORIENTATION = "orientation";

    private final SharedPreferences prefs;
    private final Context context;

    public SettingsManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setOrientation(int index) { prefs.edit().putInt(KEY_ORIENTATION, index).apply(); }
    public int getOrientation() { return prefs.getInt(KEY_ORIENTATION, 0); } // 0=Auto, 1=Portrait, 2=Landscape

    public void setResolution(int index) { prefs.edit().putInt(KEY_RESOLUTION, index).apply(); }
    public int getResolution() { return prefs.getInt(KEY_RESOLUTION, 0); }

    public void setBitrate(int index) { prefs.edit().putInt(KEY_BITRATE, index).apply(); }
    public int getBitrate() { return prefs.getInt(KEY_BITRATE, 2); }

    public void setFps(int index) { prefs.edit().putInt(KEY_FPS, index).apply(); }
    public int getFps() { return prefs.getInt(KEY_FPS, 2); }

    public void setAudioSource(int index) { prefs.edit().putInt(KEY_AUDIO_SOURCE, index).apply(); }
    public int getAudioSource() { return prefs.getInt(KEY_AUDIO_SOURCE, 2); }

    public void setCodec(int index) { prefs.edit().putInt(KEY_CODEC, index).apply(); }
    public int getCodec() { return prefs.getInt(KEY_CODEC, 0); }

    public void setBitrateMode(int index) { prefs.edit().putInt(KEY_BITRATE_MODE, index).apply(); }
    public int getBitrateMode() { return prefs.getInt(KEY_BITRATE_MODE, 1); } // 0=VBR, 1=CBR

    public void setAudioQuality(int index) { prefs.edit().putInt(KEY_AUDIO_QUALITY, index).apply(); }
    public int getAudioQuality() { return prefs.getInt(KEY_AUDIO_QUALITY, 1); } // 0=Low, 1=High, 2=Extreme

    public void setNamingTemplate(String template) { prefs.edit().putString(KEY_NAMING_TEMPLATE, template).apply(); }
    
    public String getRawNamingTemplate() {
        return prefs.getString(KEY_NAMING_TEMPLATE, "");
    }

    public String getNamingTemplate() { 
        String template = prefs.getString(KEY_NAMING_TEMPLATE, ""); 
        if (template == null || template.trim().isEmpty()) {
            return "RecorderX_{timestamp}";
        }
        return template;
    }

    public int getResolutionWidth() {
        DisplayMetrics dm = getMetrics();
        int orientPref = getOrientation();
        boolean isLandscape;
        if (orientPref == 1) isLandscape = false;
        else if (orientPref == 2) isLandscape = true;
        else isLandscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        
        int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        int longSide = Math.max(dm.widthPixels, dm.heightPixels);
        
        int width;
        switch (getResolution()) {
            case 1: width = isLandscape ? 3840 : 2160; break; // 4K
            case 2: width = isLandscape ? 2560 : 1440; break; // 2K
            case 3: width = isLandscape ? 1920 : 1080; break; // 1080p
            case 4: width = isLandscape ? 1280 : 720; break;  // 720p
            case 5: width = isLandscape ? 854 : 480; break;   // 480p
            default: width = isLandscape ? longSide : shortSide; break;
        }
        return makeMultipleOf16(width);
    }

    public int getResolutionHeight() {
        DisplayMetrics dm = getMetrics();
        int orientPref = getOrientation();
        boolean isLandscape;
        if (orientPref == 1) isLandscape = false;
        else if (orientPref == 2) isLandscape = true;
        else isLandscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        int longSide = Math.max(dm.widthPixels, dm.heightPixels);

        int height;
        switch (getResolution()) {
            case 1: height = isLandscape ? 2160 : 3840; break; // 4K
            case 2: height = isLandscape ? 1440 : 2560; break; // 2K
            case 3: height = isLandscape ? 1080 : 1920; break; // 1080p
            case 4: height = isLandscape ? 720 : 1280; break;  // 720p
            case 5: height = isLandscape ? 480 : 854; break;   // 480p
            default: height = isLandscape ? shortSide : longSide; break;
        }
        return makeMultipleOf16(height);
    }

    private int makeMultipleOf16(int value) {
        return (value / 16) * 16;
    }

    private DisplayMetrics getMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        return metrics;
    }

    public int getBitrateValue() {
        int[] bitrates = {2000000, 4000000, 8000000, 12000000, 20000000, 40000000};
        int idx = getBitrate();
        return (idx >= 0 && idx < bitrates.length) ? bitrates[idx] : 8000000;
    }

    public int getFpsValue() {
        int[] fpsOptions = {24, 30, 60, 90, 120};
        int idx = getFps();
        return (idx >= 0 && idx < fpsOptions.length) ? fpsOptions[idx] : 60;
    }

    public String getVideoMimeType() {
        switch (getCodec()) {
            case 1: return MediaFormat.MIMETYPE_VIDEO_HEVC;
            case 2: return MediaFormat.MIMETYPE_VIDEO_AV1;
            default: return MediaFormat.MIMETYPE_VIDEO_AVC;
        }
    }
}
