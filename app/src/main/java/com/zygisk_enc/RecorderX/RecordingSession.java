package com.zygisk_enc.RecorderX;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingSession {
    private static final String TAG = "RecorderX_Session";
    
    private final Context context;
    private final MediaProjection mediaProjection;
    private final SettingsManager settings;
    
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaMuxer muxer;
    private AudioRecord audioRecord;
    private AudioRecord audioRecordSecondary;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    private ParcelFileDescriptor pfd;

    
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean muxerStarted = false;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    
    private Thread audioThread;
    private Thread videoThread;

    private String outputFilePath;
    private Uri outputUri;
    
    private int activeWidth;
    private int activeHeight;
    private MediaProjection.Callback projectionCallback;
    
    private volatile int videoFrameCount = 0;

    public interface SessionListener {
        void onSystemStop();
    }
    private SessionListener listener;

    public void setListener(SessionListener listener) {
        this.listener = listener;
    }

    public String getOutputFilePath() { return outputFilePath; }
    public Uri getOutputUri() { return outputUri; }

    public RecordingSession(Context context, MediaProjection mediaProjection, SettingsManager settings) {
        this.context = context;
        this.mediaProjection = mediaProjection;
        this.settings = settings;
    }

    public void start() throws IOException {
        Log.i(TAG, "start() called. Initializing session components...");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String template = settings.getNamingTemplate().replaceAll("[^a-zA-Z0-9_\\-{}, ]", "");
        String fileName = template.replace("{timestamp}", timestamp) + ".mp4";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RecorderX");

                outputUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) throw new IOException("Failed to create MediaStore entry");

                pfd = context.getContentResolver().openFileDescriptor(outputUri, "rw");
                if (pfd == null) throw new IOException("Failed to open FileDescriptor");
                muxer = new MediaMuxer(pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                File storageDir = new File(Environment.getExternalStorageDirectory(), "RecorderX");
                if (!storageDir.exists() && !storageDir.mkdirs()) {
                    storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RecorderX");
                    storageDir.mkdirs();
                }
                File file = new File(storageDir, fileName);
                outputFilePath = file.getAbsolutePath();
                muxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }

            setupVideoEncoder();
            
            if (settings.getAudioSource() != 0) {
                try {
                    setupAudioEncoder();
                } catch (Exception e) {
                    Log.e(TAG, "Audio setup failed, falling back to video-only", e);
                    audioEncoder = null;
                    audioRecord = null;
                }
            }
            
            isRecording.set(true);
            Log.d(TAG, "Starting encoders...");
            videoEncoder.start();
            if (audioEncoder != null) audioEncoder.start();
            
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) wm.getDefaultDisplay().getRealMetrics(metrics);
            int density = metrics.densityDpi > 0 ? metrics.densityDpi : 300;

            Log.d(TAG, "Registering MediaProjection callback (required for Android 14+)...");
            if (projectionCallback != null) {
                mediaProjection.unregisterCallback(projectionCallback);
            }
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "MediaProjection onStop() triggered by system");
                    if (listener != null) {
                        listener.onSystemStop();
                    } else {
                        stop();
                    }
                }
            };
            mediaProjection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));

            Log.d(TAG, "Creating VirtualDisplay (" + activeWidth + "x" + activeHeight + ")...");
            virtualDisplay = mediaProjection.createVirtualDisplay("RecorderX",
                    activeWidth, activeHeight, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                    inputSurface, null, null);
            
            if (virtualDisplay == null) {
                throw new IOException("Failed to create virtual display");
            }

            startEncodingThreads();
            Log.i(TAG, "Recording session started successfully");
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Failed to start recording session", e);
            stop();
            throw e;
        }
    }

    private boolean tryConfigureVideoEncoder(String mime, int width, int height, int fps, int bitrate, int bitrateMode, boolean useHighProfile) {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, (float) fps);
            }
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / fps);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode);

            if (useHighProfile && MediaFormat.MIMETYPE_VIDEO_AVC.equals(mime)) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            }

            videoEncoder = MediaCodec.createEncoderByType(mime);
            
            // Proactive hardware capabilities check to prevent silent encoder failures
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaCodecInfo.CodecCapabilities caps = videoEncoder.getCodecInfo().getCapabilitiesForType(mime);
                if (caps != null) {
                    MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
                    if (videoCaps != null) {
                        if (!videoCaps.getBitrateRange().contains(bitrate)) {
                            int clampedBitrate = Math.max(videoCaps.getBitrateRange().getLower(), Math.min(bitrate, videoCaps.getBitrateRange().getUpper()));
                            Log.w(TAG, "Bitrate " + bitrate + " not supported. Clamping to " + clampedBitrate);
                            bitrate = clampedBitrate;
                            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        }
                    }
                }
            }

            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            
            this.activeWidth = width;
            this.activeHeight = height;
            
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Encoder fallback: Rejected " + width + "x" + height + "@" + fps + "fps (" + mime + ")");
            if (videoEncoder != null) {
                try { videoEncoder.release(); } catch (Exception ignored) {}
                videoEncoder = null;
            }
            return false;
        }
    }

    private void setupVideoEncoder() throws IOException {
        String originalMime = settings.getVideoMimeType();
        int originalWidth = settings.getResolutionWidth();
        int originalHeight = settings.getResolutionHeight();
        int originalFps = settings.getFpsValue();
        int originalBitrate = settings.getBitrateValue();
        int originalBitrateMode = settings.getBitrateMode() == 0 ? 
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR : 
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

        String safeMime = MediaFormat.MIMETYPE_VIDEO_AVC;

        String[] mimesToTry = originalMime.equals(safeMime) ? 
                              new String[]{originalMime} : 
                              new String[]{originalMime, safeMime};

        for (String mime : mimesToTry) {
            boolean isOriginal = mime.equals(originalMime);
            int mode = originalBitrateMode; // Always keep user's VBR/CBR preference

            // Step 1: Request with requested/safe settings
            if (tryConfigureVideoEncoder(mime, originalWidth, originalHeight, originalFps, originalBitrate, mode, isOriginal)) {
                return;
            }

            boolean isLandscape = originalWidth > originalHeight;

            // Dimensions for 1080p and 720p
            int w1080 = isLandscape ? 1920 : 1080;
            int h1080 = isLandscape ? 1080 : 1920;
            int w720 = isLandscape ? 1280 : 720;
            int h720 = isLandscape ? 720 : 1280;

            // Step 2: 1080p @ Max 60 FPS (12 Mbps)
            if (tryConfigureVideoEncoder(mime, w1080, h1080, Math.min(originalFps, 60), 12000000, mode, false)) {
                return;
            }

            // Step 3: 720p @ Max 60 FPS (12 Mbps)
            if (tryConfigureVideoEncoder(mime, w720, h720, Math.min(originalFps, 60), 12000000, mode, false)) {
                return;
            }

            // Step 4: 1080p @ Max 30 FPS (12 Mbps)
            if (tryConfigureVideoEncoder(mime, w1080, h1080, Math.min(originalFps, 30), 12000000, mode, false)) {
                return;
            }

            // Step 5: 720p @ Max 30 FPS (12 Mbps)
            if (tryConfigureVideoEncoder(mime, w720, h720, Math.min(originalFps, 30), 12000000, mode, false)) {
                return;
            }
        }

        // Rock Bottom (720p 30fps safe fallback with safeMime)
        int safeWidth = originalWidth > originalHeight ? 1280 : 720;
        int safeHeight = originalWidth > originalHeight ? 720 : 1280;
        if (tryConfigureVideoEncoder(safeMime, safeWidth, safeHeight, 30, 4000000, originalBitrateMode, false)) {
            return;
        }

        throw new IOException("Failed to configure video encoder. Your device does not support screen recording.");
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void setupAudioEncoder() throws IOException {
        int sampleRate;
        int bitRate;

        switch (settings.getAudioQuality()) {
            case 0: // Low
                sampleRate = 32000;
                bitRate = 64000;
                break;
            case 2: // Extreme
                sampleRate = 48000;
                bitRate = 320000;
                break;
            case 1: // High
            default:
                sampleRate = 44100;
                bitRate = 128000;
                break;
        }

        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4;

        if (settings.getAudioSource() == 2 || settings.getAudioSource() == 3) {
            AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            audioRecord = new AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();
                    
            if (settings.getAudioSource() == 3) {
                audioRecordSecondary = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
            }
        } else {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        }

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        
        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void startEncodingThreads() {
        videoFrameCount = 0;
        
        videoThread = new Thread(() -> {
            drainEncoder(videoEncoder, true);
        }, "VideoEncoderThread");
        videoThread.start();
        
        // WATCHDOG TIMER
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isRecording.get() && videoFrameCount == 0) {
                Log.e(TAG, "WATCHDOG: 0 frames produced in 2.5s. Hardware choked!");
                triggerWatchdogFallback();
            }
        }, 2500);
        
        if (audioEncoder != null) {
            audioThread = new Thread(() -> {
                try {
                    if (audioEncoder != null && audioRecord != null) {
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                            throw new IOException("AudioRecord failed to initialize");
                        }
                        if (audioRecordSecondary != null && audioRecordSecondary.getState() != AudioRecord.STATE_INITIALIZED) {
                            Log.w(TAG, "Secondary AudioRecord failed. Falling back to single audio.");
                            audioRecordSecondary.release();
                            audioRecordSecondary = null;
                        }
                        
                        audioRecord.startRecording();
                        if (audioRecordSecondary != null) {
                            audioRecordSecondary.startRecording();
                        }
                        
                        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                            throw new IOException("AudioRecord failed to start");
                        }
                        drainAudio();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fatal audio thread error", e);
                    synchronized (muxer) {
                        audioEncoder = null;
                    }
                }
            }, "AudioEncoderThread");
            audioThread.start();
        }
    }

    private long videoStartTimeUs = -1;

    private void drainEncoder(MediaCodec encoder, boolean isVideo) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int frameCount = 0;
        try {
            while (isRecording.get()) {
                int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (muxer) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        Log.d(TAG, (isVideo ? "Video" : "Audio") + " format changed: " + newFormat);
                        if (isVideo) videoTrackIndex = muxer.addTrack(newFormat);
                        else audioTrackIndex = muxer.addTrack(newFormat);
                        
                        if (videoTrackIndex != -1 && (audioEncoder == null || audioTrackIndex != -1)) {
                            if (!muxerStarted) {
                                Log.i(TAG, "Starting muxer... (Video: " + videoTrackIndex + ", Audio: " + audioTrackIndex + ")");
                                try {
                                    muxer.start();
                                    muxerStarted = true;
                                } catch (Exception e) {
                                    Log.e(TAG, "Muxer start failed", e);
                                }
                            }
                        }
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);
                    
                    if (muxerStarted && bufferInfo.size > 0) {
                        // NORMALIZE PTS: Most hardware encoders use system uptime which causes huge offsets
                        if (isVideo) {
                            if (videoStartTimeUs == -1) videoStartTimeUs = bufferInfo.presentationTimeUs;
                            bufferInfo.presentationTimeUs -= videoStartTimeUs;
                        }

                        if (bufferInfo.presentationTimeUs < 0) bufferInfo.presentationTimeUs = 0;
                        
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    }
                    
                    if (isVideo) {
                        frameCount++;
                        videoFrameCount++;
                    }
                    encoder.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isVideo && !muxerStarted && audioEncoder != null && frameCount > 300) {
                        Log.w(TAG, "Video frames Produced: " + frameCount + " but Audio track not ready. Forcing video-only muxer start.");
                        synchronized (muxer) {
                            if (!muxerStarted) {
                                try {
                                    muxer.start();
                                    muxerStarted = true;
                                } catch (Exception e) {
                                    Log.e(TAG, "Force-start muxer failed", e);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Drain " + (isVideo ? "video" : "audio") + " error", e);
        }
    }

    private void drainAudio() {
        int sampleRate = audioRecord.getSampleRate();
        int bufferSize = 4096; 
        ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(bufferSize);
        ByteBuffer secBuffer = null;
        if (audioRecordSecondary != null) {
            secBuffer = ByteBuffer.allocateDirect(bufferSize);
        }
        
        // Use system time for audio PTS sync if possible, but keep simple for now
        long totalSamples = 0;

        try {
            while (isRecording.get()) {
                pcmBuffer.clear();
                int read = audioRecord.read(pcmBuffer, bufferSize);
                
                int readSec = 0;
                if (audioRecordSecondary != null && secBuffer != null) {
                    secBuffer.clear();
                    readSec = audioRecordSecondary.read(secBuffer, bufferSize);
                }

                if (read > 0 || readSec > 0) {
                    // Mix audio if both are present
                    if (read > 0 && readSec > 0) {
                        byte[] primaryBytes = new byte[read];
                        pcmBuffer.get(primaryBytes);
                        
                        byte[] secondaryBytes = new byte[readSec];
                        secBuffer.get(secondaryBytes);
                        
                        int minLen = Math.min(read, readSec);
                        for (int i = 0; i < minLen - 1; i += 2) {
                            short sample1 = (short) ((primaryBytes[i] & 0xFF) | (primaryBytes[i+1] << 8));
                            short sample2 = (short) ((secondaryBytes[i] & 0xFF) | (secondaryBytes[i+1] << 8));
                            
                            int mixed = sample1 + sample2;
                            if (mixed > 32767) mixed = 32767;
                            if (mixed < -32768) mixed = -32768;
                            
                            primaryBytes[i] = (byte) (mixed & 0xFF);
                            primaryBytes[i+1] = (byte) ((mixed >> 8) & 0xFF);
                        }
                        pcmBuffer.clear();
                        pcmBuffer.put(primaryBytes);
                        pcmBuffer.position(0);
                    } else if (read <= 0 && readSec > 0) {
                        // Only secondary has data
                        pcmBuffer.clear();
                        byte[] secondaryBytes = new byte[readSec];
                        secBuffer.get(secondaryBytes);
                        pcmBuffer.put(secondaryBytes);
                        pcmBuffer.position(0);
                        read = readSec;
                    }

                    int inputIndex = audioEncoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputIndex);
                        inputBuffer.clear();
                        
                        int bytesToCopy = Math.min(read, inputBuffer.remaining());
                        pcmBuffer.limit(bytesToCopy);
                        inputBuffer.put(pcmBuffer);
                        // Calculate Audio PTS based on total samples read to ensure it starts exactly at 0
                        long pts = (totalSamples * 1000000L) / sampleRate;
                        audioEncoder.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0);
                        totalSamples += (bytesToCopy / 4);
                    }
                }
                drainAudioOutput();
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio capture error", e);
        }
    }

    private void drainAudioOutput() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (muxer) {
                    if (muxerStarted) return;
                    MediaFormat format = audioEncoder.getOutputFormat();
                    try {
                        audioTrackIndex = muxer.addTrack(format);
                        if (videoTrackIndex != -1 && !muxerStarted) {
                            muxer.start();
                            muxerStarted = true;
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputIndex);
                if (muxerStarted && bufferInfo.size > 0 && audioTrackIndex != -1) {
                    // Audio PTS is already normalized (starts at 0) from drainAudio()
                    muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                }
                audioEncoder.releaseOutputBuffer(outputIndex, false);
            }
            outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    public void stop() {
        isRecording.set(false);
        try {
            if (videoThread != null) videoThread.join(1000);
            if (audioThread != null) audioThread.join(1000);
        } catch (InterruptedException ignored) {}

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        try { if (videoEncoder != null) { videoEncoder.stop(); videoEncoder.release(); } } catch (Exception ignored) {}
        try { if (audioEncoder != null) { audioEncoder.stop(); audioEncoder.release(); } } catch (Exception ignored) {}
        try { if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); } } catch (Exception ignored) {}
        try { if (audioRecordSecondary != null) { audioRecordSecondary.stop(); audioRecordSecondary.release(); } } catch (Exception ignored) {}
        
        if (muxer != null) { 
            try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) {}
            try { muxer.release(); } catch (Exception ignored) {}
            muxer = null;
        }

        try { if (pfd != null) { pfd.close(); pfd = null; } } catch (Exception ignored) {}

        // Finalize MediaStore or notify Scanner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (outputUri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                context.getContentResolver().update(outputUri, values, null, null);
            }
        } else if (outputFilePath != null) {
            MediaScannerConnection.scanFile(context, new String[]{outputFilePath}, null, null);
        }
        Log.d(TAG, "Session stopped and saved.");
    }
    
    private void triggerWatchdogFallback() {
        int currentRes = settings.getResolution();
        if (currentRes >= 4) { // Don't go below 720p (index 4)
            if (listener != null) listener.onSystemStop();
            return;
        }

        // Drop resolution down a notch (e.g. from 4K to 2K, or 1080p to 720p)
        settings.setResolution(currentRes + 1);
        new Handler(Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context, "Hardware overloaded. Auto-downgrading...", android.widget.Toast.LENGTH_LONG).show();
        });

        new Thread(() -> {
            Log.i(TAG, "Performing cleanup for fallback...");
            
            // 1. Stop components without killing MediaProjection directly
            isRecording.set(false);
            try { if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; } } catch (Exception ignored) {}
            try { if (videoEncoder != null) { videoEncoder.stop(); videoEncoder.release(); videoEncoder = null; } } catch (Exception ignored) {}
            try { if (audioEncoder != null) { audioEncoder.stop(); audioEncoder.release(); audioEncoder = null; } } catch (Exception ignored) {}
            try {
                if (muxerStarted && muxer != null) muxer.stop();
                if (muxer != null) muxer.release();
                muxer = null;
            } catch (Exception ignored) {}
            muxerStarted = false;
            try { if (pfd != null) { pfd.close(); pfd = null; } } catch (Exception ignored) {}

            // 2. Delete the 0-byte file
            try {
                if (outputFilePath != null) new java.io.File(outputFilePath).delete();
                else if (outputUri != null) context.getContentResolver().delete(outputUri, null, null);
            } catch (Exception ignored) {}
            
            outputFilePath = null;
            outputUri = null;

            if (Build.VERSION.SDK_INT >= 34) { // Android 14+ (UPSIDE_DOWN_CAKE)
                Log.i(TAG, "Android 14+ detected. Re-prompting for MediaProjection...");
                
                // Stop current service so it can be restarted cleanly
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onSystemStop());
                }
                
                // Fire standard clickable Notification to bypass background restrictions smoothly
                Intent promptIntent = new Intent(context, RequestCaptureActivity.class);
                promptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, promptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "saved_channel")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Hardware Overloaded")
                        .setContentText("Recording failed. Tap to restart with safe settings.")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(pendingIntent) // Tap to open RequestCaptureActivity
                        .setAutoCancel(true);
                
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(9999, builder.build());
                }
                
            } else {
                Log.i(TAG, "Performing internal live-reboot for pre-A14...");
                // 3. Wait a moment for hardware to reset
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                // 4. Re-run start logic natively
                try {
                    start();
                } catch (Exception e) {
                    Log.e(TAG, "Watchdog live-reboot failed", e);
                    if (listener != null) listener.onSystemStop();
                }
            }
        }).start();
    }
}
