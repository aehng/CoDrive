package com.codrive.ai.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class CoDriveAccessibilityService extends AccessibilityService {
    private static volatile CoDriveAccessibilityService instance;

    public static CoDriveAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Phase 0/1 scaffold only.
    }

    @Override
    public void onInterrupt() {
        // Phase 0/1 scaffold only.
    }

    public AccessibilityNodeInfo getLatestRootNode() {
        return getRootInActiveWindow();
    }

    public boolean dispatchMidpointTap(int[] bounds) {
        if (bounds == null || bounds.length != 4) {
            return false;
        }
        final int centerX = (bounds[0] + bounds[2]) / 2;
        final int centerY = (bounds[1] + bounds[3]) / 2;

        Path tapPath = new Path();
        tapPath.moveTo(centerX, centerY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 60))
                .build();

        return dispatchGesture(gesture, null, null);
    }
}

