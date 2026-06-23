package com.zygisk_enc.RecorderX;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1000;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE_START = 1002;
    
    private SettingsManager settingsManager;
    private Button btnRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        initUI();
        
        if (!checkPermissions()) {
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
            permissions.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE_START);
        }
    }

    private void initUI() {
        btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> toggleRecording());

        findViewById(R.id.btnGuide).setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle(R.string.guide_title);
            builder.setMessage(R.string.guide_message);
            builder.setPositiveButton(R.string.guide_understood, null);
            builder.show();
        });
            
        findViewById(R.id.btnViewSource).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/snap24/RecorderX"));
            startActivity(intent);
        });

        // Video Settings
        setupSlider(R.id.codecSlider, R.array.codec_options, settingsManager.getCodec(), 
            index -> settingsManager.setCodec(index));
        setupSlider(R.id.orientationSlider, R.array.orientation_options, settingsManager.getOrientation(), 
            index -> settingsManager.setOrientation(index));
        setupSlider(R.id.bitrateModeSlider, R.array.bitrate_mode_options, settingsManager.getBitrateMode(), 
            index -> settingsManager.setBitrateMode(index));

        setupSlider(R.id.resolutionSlider, R.array.resolution_options, settingsManager.getResolution(), 
            index -> settingsManager.setResolution(index));
        setupSlider(R.id.fpsSlider, R.array.fps_options, settingsManager.getFps(), 
            index -> settingsManager.setFps(index));
        setupSlider(R.id.bitrateSlider, R.array.bitrate_options, settingsManager.getBitrate(), 
            index -> settingsManager.setBitrate(index));

        // Audio Settings
        setupSlider(R.id.audioSlider, R.array.audio_options, settingsManager.getAudioSource(), 
            index -> settingsManager.setAudioSource(index));
        setupSlider(R.id.audioQualitySlider, R.array.audio_quality_options, settingsManager.getAudioQuality(), 
            index -> settingsManager.setAudioQuality(index));

        // Output Settings
        TextInputEditText namingInput = findViewById(R.id.namingTemplateEditText);
        namingInput.setText(settingsManager.getRawNamingTemplate());
        namingInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s != null) settingsManager.setNamingTemplate(s.toString());
            }
        });
    }

    private void setupSlider(int viewId, int arrayId, int initialSelection, OnSelectionChanged listener) {
        Slider slider = findViewById(viewId);
        String[] options = getResources().getStringArray(arrayId);
        
        slider.setValue(initialSelection);
        slider.setLabelFormatter(value -> {
            int index = (int) value;
            if (index >= 0 && index < options.length) {
                return options[index];
            }
            return String.valueOf(value);
        });
        
        slider.addOnChangeListener((slider1, value, fromUser) -> {
            if (fromUser) {
                listener.onChanged((int) value);
            }
        });
    }

    private void toggleRecording() {
        if (RecorderService.isRecording()) {
            stopRecording();
        } else {
            if (checkPermissions()) {
                startProjectionRequest();
            } else {
                requestPermissions();
            }
        }
    }

    private boolean checkPermissions() {
        boolean granted = true;
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            granted = false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                granted = false;
            }
        }
        
        return granted;
    }

    private void requestPermissions() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        
        permissions.add(Manifest.permission.RECORD_AUDIO);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startProjectionRequest();
            } else {
                Toast.makeText(this, "Audio permission required for Mic", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startProjectionRequest() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                startRecording(resultCode, data);
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startRecording(int resultCode, Intent data) {
        android.util.Log.d("RecorderX_Main", "startRecording: ResultCode=" + resultCode + ", Data=" + data);
        
        RecorderService.setProjectionData(resultCode, data);

        Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(RecorderService.ACTION_START);
        intent.putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(RecorderService.EXTRA_DATA, data);
        
        try {
            ContextCompat.startForegroundService(this, intent);
            btnRecord.setText(R.string.stop_recording);
        } catch (Exception e) {
            android.util.Log.e("RecorderX_Main", "Failed to start service", e);
            Toast.makeText(this, "Failed to start recorder", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(RecorderService.ACTION_STOP);
        startService(intent);
        btnRecord.setText(R.string.start_recording);
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnRecord.setText(RecorderService.isRecording() ? R.string.stop_recording : R.string.start_recording);
        
        if (getIntent() != null && getIntent().getBooleanExtra("AUTO_START", false)) {
            getIntent().removeExtra("AUTO_START");
            if (!RecorderService.isRecording()) {
                toggleRecording();
            }
        }
    }

    interface OnSelectionChanged {
        void onChanged(int index);
    }
}