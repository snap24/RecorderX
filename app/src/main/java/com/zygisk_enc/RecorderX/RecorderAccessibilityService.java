package com.zygisk_enc.RecorderX;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class RecorderAccessibilityService extends AccessibilityService {
    private static RecorderAccessibilityService instance;

    public static RecorderAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No event handling needed for screenshot triggers
    }

    @Override
    public void onInterrupt() {
        // No interrupt handling needed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }
}
