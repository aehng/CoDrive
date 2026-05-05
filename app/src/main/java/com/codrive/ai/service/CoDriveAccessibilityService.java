package com.codrive.ai.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class CoDriveAccessibilityService extends AccessibilityService {
    private static final String TAG = "CoDriveAccessibility";
    private static volatile CoDriveAccessibilityService instance;
    private final Object rootLock = new Object();
    private AccessibilityNodeInfo latestExternalRoot;

    public static CoDriveAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility service connected.");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Accessibility service destroyed.");
        synchronized (rootLock) {
            if (latestExternalRoot != null) {
                latestExternalRoot.recycle();
                latestExternalRoot = null;
            }
        }
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
        if (currentRoot == null) {
            return;
        }
        try {
            if (isExternalPackage(currentRoot.getPackageName())) {
                AccessibilityNodeInfo snapshot = AccessibilityNodeInfo.obtain(currentRoot);
                synchronized (rootLock) {
                    if (latestExternalRoot != null) {
                        latestExternalRoot.recycle();
                    }
                    latestExternalRoot = snapshot;
                }
            }
        } finally {
            currentRoot.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        // Phase 0/1 scaffold only.
    }

    public AccessibilityNodeInfo getLatestRootNode() {
        return getLatestAutomationRootNode();
    }

    public AccessibilityNodeInfo getLatestAutomationRootNode() {
        AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
        if (currentRoot != null) {
            if (isExternalPackage(currentRoot.getPackageName())) {
                return currentRoot;
            }
            currentRoot.recycle();
        }

        synchronized (rootLock) {
            return latestExternalRoot == null ? null : AccessibilityNodeInfo.obtain(latestExternalRoot);
        }
    }

    public boolean dispatchMidpointTap(int[] bounds) {
        if (bounds == null || bounds.length != 4) {
            return false;
        }
        final int centerX = (bounds[0] + bounds[2]) / 2;
        final int centerY = (bounds[1] + bounds[3]) / 2;

        return dispatchTap(centerX, centerY);
    }

    public boolean dispatchDirectionalSwipeDown() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int centerX = dm.widthPixels / 2;
        int startY = Math.max(1, Math.round(dm.heightPixels * 0.05f));
        int endY = Math.round(dm.heightPixels * 0.78f);
        return dispatchSwipe(centerX, startY, centerX, endY, 220L);
    }

    public boolean dispatchDirectionalSwipeUp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int centerX = dm.widthPixels / 2;
        int startY = Math.round(dm.heightPixels * 0.78f);
        int endY = Math.max(1, Math.round(dm.heightPixels * 0.15f));
        return dispatchSwipe(centerX, startY, centerX, endY, 220L);
    }

    public boolean dispatchDirectionalSwipeLeft() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int centerY = dm.heightPixels / 2;
        int startX = Math.round(dm.widthPixels * 0.85f);
        int endX = Math.max(1, Math.round(dm.widthPixels * 0.15f));
        return dispatchSwipe(startX, centerY, endX, centerY, 220L);
    }

    public boolean dispatchDirectionalSwipeRight() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int centerY = dm.heightPixels / 2;
        int startX = Math.max(1, Math.round(dm.widthPixels * 0.15f));
        int endX = Math.round(dm.widthPixels * 0.85f);
        return dispatchSwipe(startX, centerY, endX, centerY, 220L);
    }

    private boolean dispatchTap(int x, int y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 60))
                .build();

        return dispatchGesture(gesture, null, null);
    }

    private boolean dispatchSwipe(int startX, int startY, int endX, int endY, long durationMs) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMs))
                .build();

        return dispatchGesture(gesture, null, null);
    }

    private boolean isExternalPackage(CharSequence packageName) {
        return packageName != null && !getPackageName().contentEquals(packageName);
    }
}

