package com.codrive.ai.launcher;

import android.content.Context;
import android.content.Intent;

import com.codrive.ai.ChatActivity;
import com.codrive.ai.overlay.OverlayBubbleService;

public final class ChatLauncherEntryPoint {
    public static final String ACTION_START_OVERLAY = "com.codrive.ai.action.START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "com.codrive.ai.action.STOP_OVERLAY";

    private ChatLauncherEntryPoint() {
    }

    public static Intent newChatIntent(Context context) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static Intent newStartOverlayIntent(Context context) {
        Intent intent = new Intent(context, OverlayBubbleService.class);
        intent.setAction(ACTION_START_OVERLAY);
        return intent;
    }

    public static Intent newStopOverlayIntent(Context context) {
        Intent intent = new Intent(context, OverlayBubbleService.class);
        intent.setAction(ACTION_STOP_OVERLAY);
        return intent;
    }
}

