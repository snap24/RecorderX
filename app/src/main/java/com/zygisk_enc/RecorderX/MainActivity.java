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
    private boolean isWarningDialogShowing = false;
    private android.os.Handler titleToggleHandler;
    private Runnable titleToggleRunnable;
    private boolean isShowingTitleText = true;

    private static final int[] ACCENT_COLORS = {
        android.graphics.Color.parseColor("#9575CD"), // 0: Lavender (Default)
        android.graphics.Color.parseColor("#FBBF24"), // 1: Amber Yellow (2nd color)
        android.graphics.Color.parseColor("#10B981"), // 2: Emerald Green
        android.graphics.Color.parseColor("#EF4444"), // 3: Sunset Red
        android.graphics.Color.parseColor("#3B82F6"), // 4: Ocean Blue
        android.graphics.Color.parseColor("#EC4899"), // 5: Sakura Pink
        android.graphics.Color.parseColor("#06B6D4"), // 6: Cyan/Teal
        android.graphics.Color.parseColor("#7C3AED"), // 7: Deep Violet
        android.graphics.Color.parseColor("#84CC16"), // 8: Lime Green
        android.graphics.Color.parseColor("#F97316"), // 9: Vibrant Orange
        android.graphics.Color.parseColor("#6366F1"), // 10: Indigo
        android.graphics.Color.parseColor("#F43F5E")  // 11: Rose Pink
    };

    private final RecorderService.RecordingStateListener recordingStateListener = new RecorderService.RecordingStateListener() {
        @Override
        public void onStateChanged(boolean isRecording) {
            if (btnRecord != null) {
                btnRecord.setText(isRecording ? R.string.stop_recording : R.string.start_recording);
            }
        }
    };

    @Override
    protected void onDestroy() {
        RecorderService.unregisterStateListener(recordingStateListener);
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        // First launch auto-detection of system theme
        if (!themePrefs.contains("theme_mode")) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            int defaultMode = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? 1 : 0;
            themePrefs.edit().putInt("theme_mode", defaultMode).apply();
        }
        int themeMode = themePrefs.getInt("theme_mode", 1);
        if (themeMode != 0 && themeMode != 1) {
            themeMode = 1;
            themePrefs.edit().putInt("theme_mode", 1).apply();
        }
        if (themeMode == 0) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        initUI();
        RecorderService.registerStateListener(recordingStateListener);
        
        if (!checkPermissions()) {
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
            permissions.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE_START);
        }

        // Initialize swipe customizer onboarding hint next to the title
        android.widget.ImageView swipeHintIcon = findViewById(R.id.swipeHintIcon);
        android.widget.TextView titleMain = findViewById(R.id.titleMain);
        android.widget.TextView titleX = findViewById(R.id.titleX);
        
        boolean shown = getSharedPreferences("ui_prefs", MODE_PRIVATE).getBoolean("swipe_color_onboarding_shown", false);
        if (!shown) {
            if (titleX != null) titleX.setVisibility(android.view.View.GONE);
            
            if (swipeHintIcon != null) {
                swipeHintIcon.setVisibility(android.view.View.VISIBLE);
                swipeHintIcon.setImageTintList(android.content.res.ColorStateList.valueOf(getActiveAccentColor()));
                
                // Play horizontal translate/slide animation
                android.view.animation.TranslateAnimation anim = new android.view.animation.TranslateAnimation(
                    android.view.animation.Animation.RELATIVE_TO_SELF, -0.15f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.15f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.0f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.0f
                );
                anim.setDuration(800);
                anim.setRepeatCount(android.view.animation.Animation.INFINITE);
                anim.setRepeatMode(android.view.animation.Animation.REVERSE);
                anim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                swipeHintIcon.startAnimation(anim);
            }
            
            // Loop title text: RECORDER X <-> SWIPE HERE
            titleToggleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            titleToggleRunnable = new Runnable() {
                @Override
                public void run() {
                    if (titleMain != null) {
                        if (isShowingTitleText) {
                            titleMain.setText("SWIPE HERE");
                        } else {
                            titleMain.setText("RECORDER X");
                        }
                        isShowingTitleText = !isShowingTitleText;
                    }
                    if (titleToggleHandler != null) {
                        titleToggleHandler.postDelayed(this, 1000);
                    }
                }
            };
            titleToggleHandler.postDelayed(titleToggleRunnable, 1000);
        } else {
            if (swipeHintIcon != null) swipeHintIcon.setVisibility(android.view.View.GONE);
            if (titleMain != null) titleMain.setText("RECORDER");
            if (titleX != null) {
                titleX.setVisibility(android.view.View.VISIBLE);
                startXPulseAnimation();
            }
        }
    }

    private void initUI() {
        btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> toggleRecording());

        android.widget.TextView btnThemeToggle = findViewById(R.id.btnThemeToggle);
        android.content.SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        
        // Auto-detect system preference if not set
        if (!themePrefs.contains("theme_mode")) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            int defaultMode = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) ? 1 : 0;
            themePrefs.edit().putInt("theme_mode", defaultMode).apply();
        }
        
        int initialMode = themePrefs.getInt("theme_mode", 1);
        if (initialMode != 0 && initialMode != 1) {
            initialMode = 1;
        }
        
        // In Light mode (0), show D. In Dark mode (1), show L.
        btnThemeToggle.setText(initialMode == 0 ? "D" : "L");
        
        btnThemeToggle.setOnClickListener(v -> {
            int currentMode = themePrefs.getInt("theme_mode", 1);
            if (currentMode != 0 && currentMode != 1) {
                currentMode = 1;
            }
            int nextMode = 1 - currentMode; // Toggle directly between 0 (Light) and 1 (Dark)
            themePrefs.edit().putInt("theme_mode", nextMode).apply();
            
            if (nextMode == 0) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            }
            recreate();
        });

        findViewById(R.id.btnGuide).setOnClickListener(v -> {
            android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            
            android.view.View contentView = getLayoutInflater().inflate(R.layout.dialog_guide, null);
            if (contentView instanceof com.google.android.material.card.MaterialCardView) {
                com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) contentView;
                float density = getResources().getDisplayMetrics().density;
                card.setStrokeWidth((int)(2 * density));
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(getActiveAccentColor()));
            }
            
            android.widget.FrameLayout container = new android.widget.FrameLayout(this);
            int margin = (int)(24 * getResources().getDisplayMetrics().density);
            container.setPadding(margin, margin, margin, margin);
            container.addView(contentView);
            dialog.setContentView(container);
            
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.view.WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
                    attrs.setBlurBehindRadius((int)(16 * getResources().getDisplayMetrics().density));
                    dialog.getWindow().setAttributes(attrs);
                }
                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                lp.copyFrom(dialog.getWindow().getAttributes());
                lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
                dialog.getWindow().setAttributes(lp);
            }
            
            applyDynamicColorsToView(dialog.getWindow().getDecorView(), getActiveAccentColor());
            
            // Apply blur to activity background
            android.view.View rootView = findViewById(android.R.id.content);
            if (rootView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                rootView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP));
            }
            
            dialog.setOnDismissListener(d -> {
                if (rootView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    rootView.setRenderEffect(null);
                }
            });
            
            dialog.findViewById(R.id.btnUnderstood).setOnClickListener(view -> dialog.dismiss());
            dialog.show();
        });

        findViewById(R.id.btnOpenRecordings).setOnClickListener(v -> {
            try {
                java.io.File folder = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "RecorderX");
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", folder);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "resource/folder"); // Triggers third-party file managers (ZArchiver, Cx, etc.)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent); // Allows user to select "Always" or "Just Once"
            } catch (Exception e) {
                // Fallback 1: Try system document viewer
                try {
                    java.io.File folder = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "RecorderX");
                    android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", folder);
                    Intent systemIntent = new Intent(Intent.ACTION_VIEW);
                    systemIntent.setDataAndType(uri, "vnd.android.document/directory");
                    systemIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(systemIntent);
                } catch (Exception ex) {
                    // Fallback 2: General document tree picker
                    try {
                        Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        startActivity(fallback);
                    } catch (Exception fatal) {
                        Toast.makeText(this, "Could not open File Manager", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
            
        findViewById(R.id.btnViewSource).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/snap24/RecorderX"));
            startActivity(intent);
        });

        // Initialize invisible color slider over RECORDER X title area overlay
        android.view.View colorTouchArea = findViewById(R.id.colorTouchArea);
        if (colorTouchArea != null) {
            colorTouchArea.setOnTouchListener(new android.view.View.OnTouchListener() {
                private float startX;
                
                @android.annotation.SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    switch (event.getAction()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            return true;
                            
                        case android.view.MotionEvent.ACTION_UP:
                            float deltaX = event.getRawX() - startX;
                            float density = v.getResources().getDisplayMetrics().density;
                            float threshold = 8 * density; // Super sensitive 8dp threshold for quick little swipes!
                            
                            if (Math.abs(deltaX) > threshold) {
                                int currentIdx = themePrefs.getInt("accent_color_index", 1);
                                int newIdx;
                                if (deltaX > 0) {
                                    // Swipe Right -> Next Color
                                    newIdx = (currentIdx + 1) % ACCENT_COLORS.length;
                                } else {
                                    // Swipe Left -> Previous Color
                                    newIdx = (currentIdx - 1 + ACCENT_COLORS.length) % ACCENT_COLORS.length;
                                }
                                themePrefs.edit().putInt("accent_color_index", newIdx).apply();
                                applyAccentColor(ACCENT_COLORS[newIdx]);
                                dismissSwipeHint();
                            } else {
                                v.performClick();
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        // Apply saved accent color on startup (default to Yellow: index 1)
        int savedColorIndex = themePrefs.getInt("accent_color_index", 1);
        applyAccentColor(ACCENT_COLORS[savedColorIndex]);

        // Video Settings
        setupSlider(R.id.codecSlider, R.array.codec_options, settingsManager.getCodec(), 
            index -> settingsManager.setCodec(index));
        // Orientation slider — with first-time AUTO mode warning
        Slider orientationSlider = findViewById(R.id.orientationSlider);
        String[] orientOptions = getResources().getStringArray(R.array.orientation_options);
        orientationSlider.setValue(settingsManager.getOrientation());
        orientationSlider.setLabelFormatter(value -> {
            int idx = (int) value;
            return (idx >= 0 && idx < orientOptions.length) ? orientOptions[idx] : String.valueOf(value);
        });
        orientationSlider.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            int selected = (int) value;
            if (selected == 0) {
                android.content.SharedPreferences warnPrefs = getSharedPreferences("ui_prefs", MODE_PRIVATE);
                boolean warned = warnPrefs.getBoolean("auto_orient_warned", false);
                if (!warned && !isWarningDialogShowing) {
                    isWarningDialogShowing = true;
                    // Use plain Dialog — no AlertDialog internal handler conflicts, single clean tap guaranteed
                    android.app.Dialog warningDialog = new android.app.Dialog(this);
                    warningDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                    warningDialog.setCancelable(false);
                    warningDialog.setCanceledOnTouchOutside(false);

                    // Build layout programmatically
                    android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
                    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    int pad = (int)(24 * getResources().getDisplayMetrics().density);
                    layout.setPadding(pad, pad, pad, pad);
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(getResources().getColor(R.color.bg_main, getTheme()));
                    bg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
                    bg.setStroke((int)(2 * getResources().getDisplayMetrics().density), getActiveAccentColor());
                    layout.setBackground(bg);

                    android.widget.TextView tvTitle = new android.widget.TextView(this);
                    tvTitle.setText("⚠  Auto Orientation Warning");
                    tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                    tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tvTitle.setTextColor(getActiveAccentColor());
                    android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    titleParams.bottomMargin = (int)(12 * getResources().getDisplayMetrics().density);
                    layout.addView(tvTitle, titleParams);

                    android.widget.TextView tvMsg = new android.widget.TextView(this);
                    tvMsg.setText("Auto Orientation is a gimmick and will degrade your recording quality.\n\nAndroid dynamically rotates the virtual display mid-recording, often causing resolution mismatches, black bars, and encoder instability.\n\nWe strongly recommend manually selecting Portrait or Landscape to match the primary orientation of the app you intend to record — this produces the best, most consistent output.");
                    tvMsg.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                    tvMsg.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                    android.widget.LinearLayout.LayoutParams msgParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    msgParams.bottomMargin = (int)(20 * getResources().getDisplayMetrics().density);
                    layout.addView(tvMsg, msgParams);

                    android.widget.Button btnOk = new android.widget.Button(this);
                    btnOk.setText("I Understand (5)");
                    btnOk.setEnabled(false);
                    android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
                    btnBg.setColor(getActiveAccentColor());
                    btnBg.setCornerRadius(4 * getResources().getDisplayMetrics().density);
                    btnOk.setBackground(btnBg);
                    btnOk.setTextColor(android.graphics.Color.BLACK);
                    btnOk.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    // Single clean listener — plain Dialog has no internal handler, one tap = one call
                    btnOk.setOnClickListener(v -> {
                        isWarningDialogShowing = false;
                        warnPrefs.edit().putBoolean("auto_orient_warned", true).apply();
                        settingsManager.setOrientation(0);
                        orientationSlider.setValue(0);
                        warningDialog.dismiss();
                    });
                    layout.addView(btnOk, new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            (int)(48 * getResources().getDisplayMetrics().density)));

                    android.widget.FrameLayout container = new android.widget.FrameLayout(this);
                    int margin = (int)(24 * getResources().getDisplayMetrics().density);
                    container.setPadding(margin, margin, margin, margin);
                    container.addView(layout);
                    warningDialog.setContentView(container);

                    // Apply blur to activity background
                    android.view.View rootView = findViewById(android.R.id.content);
                    if (rootView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        rootView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP));
                    }
                    
                    warningDialog.setOnDismissListener(d -> {
                        isWarningDialogShowing = false;
                        if (rootView != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            rootView.setRenderEffect(null);
                        }
                    });

                    if (warningDialog.getWindow() != null) {
                        warningDialog.getWindow().setBackgroundDrawable(
                                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        warningDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            android.view.WindowManager.LayoutParams attrs = warningDialog.getWindow().getAttributes();
                            attrs.setBlurBehindRadius((int)(16 * getResources().getDisplayMetrics().density));
                            warningDialog.getWindow().setAttributes(attrs);
                        }
                        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                        lp.copyFrom(warningDialog.getWindow().getAttributes());
                        lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                        warningDialog.getWindow().setAttributes(lp);
                    }
                    warningDialog.show();

                    // Timer only updates text and re-enables — never touches click listener
                    new android.os.CountDownTimer(5000, 1000) {
                        @Override public void onTick(long ms) {
                            btnOk.setText("I Understand (" + (ms / 1000 + 1) + ")");
                        }
                        @Override public void onFinish() {
                            btnOk.setText("I Understand");
                            btnOk.setEnabled(true);
                        }
                    }.start();

                    // Revert slider visually until user explicitly confirms
                    sl.setValue(settingsManager.getOrientation());
                    return;
                }
            }
            settingsManager.setOrientation(selected);
        });
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

        // Floating Controls Toggle
        com.google.android.material.switchmaterial.SwitchMaterial switchFloating = findViewById(R.id.switchFloatingControl);
        switchFloating.setChecked(settingsManager.isFloatingControlEnabled());
        switchFloating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(MainActivity.this)) {
                    switchFloating.setChecked(false);
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Overlay Permission Required")
                        .setMessage("To display the floating controller bubble, RecorderX needs permission to display over other apps. Please grant it on the next screen.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            settingsManager.setFloatingControlEnabled(true);
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    settingsManager.setFloatingControlEnabled(true);
                }
            } else {
                settingsManager.setFloatingControlEnabled(false);
            }
        });

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

        // Clickable copy helper for {timestamp}
        setupNamingTemplateHelper();
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

        com.google.android.material.switchmaterial.SwitchMaterial switchFloating = findViewById(R.id.switchFloatingControl);
        if (switchFloating != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                boolean hasPermission = android.provider.Settings.canDrawOverlays(this);
                if (hasPermission) {
                    if (settingsManager.isFloatingControlEnabled()) {
                        switchFloating.setChecked(true);
                    }
                } else {
                    settingsManager.setFloatingControlEnabled(false);
                    switchFloating.setChecked(false);
                }
            } else {
                switchFloating.setChecked(settingsManager.isFloatingControlEnabled());
            }
        }
        
        if (getIntent() != null && getIntent().getBooleanExtra("AUTO_START", false)) {
            getIntent().removeExtra("AUTO_START");
            if (!RecorderService.isRecording()) {
                toggleRecording();
            }
        }
    }

    private void applyAccentColor(int color) {
        // 0. Make colorTouchArea fully invisible (no border)
        android.view.View colorTouchArea = findViewById(R.id.colorTouchArea);
        if (colorTouchArea != null) {
            colorTouchArea.setBackground(null);
        }
        
        android.widget.ImageView swipeHintIcon = findViewById(R.id.swipeHintIcon);
        if (swipeHintIcon != null && swipeHintIcon.getVisibility() == android.view.View.VISIBLE) {
            swipeHintIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }

        // 1. Title
        android.widget.TextView titleMain = findViewById(R.id.titleMain);
        if (titleMain != null) titleMain.setTextColor(color);
        android.widget.TextView titleX = findViewById(R.id.titleX);
        if (titleX != null) titleX.setTextColor(color);
        
        // 2. Headers
        android.widget.TextView headerVideo = findViewById(R.id.headerVideoSubsystem);
        if (headerVideo != null) headerVideo.setTextColor(color);
        android.widget.TextView headerAudio = findViewById(R.id.headerAudioSubsystem);
        if (headerAudio != null) headerAudio.setTextColor(color);
        
        // 3. Button
        if (btnRecord != null) {
            btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
        
        // 4. Sliders
        int[] sliderIds = {
            R.id.codecSlider, R.id.orientationSlider, R.id.resolutionSlider,
            R.id.fpsSlider, R.id.bitrateSlider, R.id.bitrateModeSlider,
            R.id.audioSlider, R.id.audioQualitySlider
        };
        for (int id : sliderIds) {
            com.google.android.material.slider.Slider slider = findViewById(id);
            if (slider != null) {
                slider.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(color));
            }
        }
        
        // 5. Switch
        com.google.android.material.switchmaterial.SwitchMaterial switchFloating = findViewById(R.id.switchFloatingControl);
        if (switchFloating != null) {
            switchFloating.setTrackTintList(android.content.res.ColorStateList.valueOf(color));
        }
        
        // 6. TextInputLayout
        com.google.android.material.textfield.TextInputLayout layoutTemplate = findViewById(R.id.namingTemplateLayout);
        if (layoutTemplate != null) {
            layoutTemplate.setHintTextColor(android.content.res.ColorStateList.valueOf(color));
            layoutTemplate.setBoxStrokeColor(color);
        }

        // 7. Dynamic text spans
        setupNamingTemplateHelper();
    }

    private int getActiveAccentColor() {
        android.content.SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        int index = themePrefs.getInt("accent_color_index", 1); // Default to Yellow (index 1)
        if (index < 0 || index >= ACCENT_COLORS.length) {
            index = 1;
        }
        return ACCENT_COLORS[index];
    }

    private void setupNamingTemplateHelper() {
        android.widget.TextView tvHelper = findViewById(R.id.tvNamingTemplateHelper);
        if (tvHelper != null) {
            String text = "Type your preferred prefix in the template box to personalize your saved video titles.\nAdd the {timestamp} placeholder to ensure every file is organized sequentially by date.\nInvalid characters are stripped automatically, defaulting to a clean timestamp if empty.\nFormat: yyyyMMdd_HHmmss (e.g. 20260712_143000).";
            android.text.SpannableString spannable = new android.text.SpannableString(text);
            String target = "{timestamp}";
            int startIdx = text.indexOf(target);
            if (startIdx != -1) {
                int endIdx = startIdx + target.length();
                spannable.setSpan(new android.text.style.ClickableSpan() {
                    @Override
                    public void onClick(android.view.View widget) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("timestamp_placeholder", "{timestamp}");
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(MainActivity.this, "Copied {timestamp} to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                    
                    @Override
                    public void updateDrawState(android.text.TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(false);
                        ds.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        ds.setColor(getActiveAccentColor());
                    }
                }, startIdx, endIdx, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvHelper.setText(spannable);
            tvHelper.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
    }

    private void applyDynamicColorsToView(android.view.View view, int color) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyDynamicColorsToView(group.getChildAt(i), color);
            }
        } else if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            // If the text color matches the default accent_lavender, recolor it!
            if (tv.getCurrentTextColor() == getResources().getColor(R.color.accent_lavender, getTheme())) {
                tv.setTextColor(color);
            }
        }
        // Tint underlay/understood button if it's the target R.id.btnUnderstood
        if (view.getId() == R.id.btnUnderstood && view instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) view).setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void dismissSwipeHint() {
        android.widget.ImageView swipeHintIcon = findViewById(R.id.swipeHintIcon);
        if (swipeHintIcon != null && swipeHintIcon.getVisibility() == android.view.View.VISIBLE) {
            android.view.animation.AlphaAnimation fadeOut = new android.view.animation.AlphaAnimation(1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                @Override public void onAnimationStart(android.view.animation.Animation animation) {}
                @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
                @Override
                public void onAnimationEnd(android.view.animation.Animation animation) {
                    swipeHintIcon.clearAnimation();
                    swipeHintIcon.setVisibility(android.view.View.GONE);
                }
            });
            swipeHintIcon.startAnimation(fadeOut);
            
            // Stop the title looping text handler
            if (titleToggleHandler != null && titleToggleRunnable != null) {
                titleToggleHandler.removeCallbacks(titleToggleRunnable);
                titleToggleHandler = null;
                titleToggleRunnable = null;
            }
            
            // Show custom pulsing X letter
            android.widget.TextView titleMain = findViewById(R.id.titleMain);
            android.widget.TextView titleX = findViewById(R.id.titleX);
            if (titleMain != null) titleMain.setText("RECORDER");
            if (titleX != null) {
                titleX.setVisibility(android.view.View.VISIBLE);
                startXPulseAnimation();
            }
            
            getSharedPreferences("ui_prefs", MODE_PRIVATE)
                .edit().putBoolean("swipe_color_onboarding_shown", true).apply();
        }
    }

    private void startXPulseAnimation() {
        android.widget.TextView titleX = findViewById(R.id.titleX);
        if (titleX != null) {
            titleX.clearAnimation();
            android.view.animation.ScaleAnimation pulse = new android.view.animation.ScaleAnimation(
                1.0f, 1.8f, 1.0f, 1.8f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            );
            pulse.setDuration(600);
            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
            pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            titleX.startAnimation(pulse);
        }
    }

    interface OnSelectionChanged {
        void onChanged(int index);
    }
}