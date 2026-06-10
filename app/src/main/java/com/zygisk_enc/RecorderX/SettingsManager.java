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

    private final SharedPreferences prefs;
    private final Context context;

    public SettingsManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setResolution(int index) { prefs.edit().putInt(KEY_RESOLUTION, index).apply(); }
    public int getResolution() { return prefs.getInt(KEY_RESOLUTION, 0); }

    public void setBitrate(int index) { prefs.edit().putInt(KEY_BITRATE, index).apply(); }
    public int getBitrate() { return prefs.getInt(KEY_BITRATE, 2); } // Default 8 Mbps

    public void setFps(int index) { prefs.edit().putInt(KEY_FPS, index).apply(); }
    public int getFps() { return prefs.getInt(KEY_FPS, 2); } // Default 60 FPS

    public void setAudioSource(int index) { prefs.edit().putInt(KEY_AUDIO_SOURCE, index).apply(); }
    public int getAudioSource() { return prefs.getInt(KEY_AUDIO_SOURCE, 1); } // Default Mic

    public void setCodec(int index) { prefs.edit().putInt(KEY_CODEC, index).apply(); }
    public int getCodec() { return prefs.getInt(KEY_CODEC, 0); } // Default H.264

    public int getResolutionWidth() {
        switch (getResolution()) {
            case 1: return 1920;
            case 2: return 1280;
            case 3: return 854;
            default:
                DisplayMetrics dm = getMetrics();
                return makeMultipleOf16(dm.widthPixels);
        }
    }

    public int getResolutionHeight() {
        switch (getResolution()) {
            case 1: return 1080;
            case 2: return 720;
            case 3: return 480;
            default:
                DisplayMetrics dm = getMetrics();
                return makeMultipleOf16(dm.heightPixels);
        }
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
        return getCodec() == 1 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
    }
}
