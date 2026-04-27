package com.codrive.ai.overlay;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import com.codrive.ai.R;
import com.codrive.ai.accessibility.PruningOutcome;
import com.codrive.ai.accessibility.UiTreePruner;
import com.codrive.ai.execution.AccessibilityActionExecutor;
import com.codrive.ai.execution.AccessibilityRuntimeAdapter;
import com.codrive.ai.launcher.ChatLauncherEntryPoint;
import com.codrive.ai.llm.LlmClientFactory;
import com.codrive.ai.memory.IdentityDatabase;
import com.codrive.ai.memory.MemorySearchTool;
import com.codrive.ai.model.ActionType;
import com.codrive.ai.model.AgentDecision;
import com.codrive.ai.model.ExecutionResult;
import com.codrive.ai.orchestration.ActiveSessionManager;
import com.codrive.ai.orchestration.ChatTracerBulletOrchestrator;
import com.codrive.ai.orchestration.InferenceLoopRunner;
import com.codrive.ai.orchestration.IncrementalRequestManager;
import com.codrive.ai.orchestration.Tier1NavigationDirective;
import com.codrive.ai.orchestration.Tier1NavigationRouter;
import com.codrive.ai.orchestration.TracerBulletResult;
import com.codrive.ai.overlay.stt.ContinuousSpeechRecognizer;
import com.codrive.ai.overlay.stt.SpeechCommandEndpointer;
import com.codrive.ai.settings.LlmSettingsStore;
import com.codrive.ai.service.CoDriveAccessibilityService;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

public class OverlayBubbleService extends Service {
    private static final String TAG = "OverlayBubbleService";

    private WindowManager windowManager;
    private View overlayRoot;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout panel;
    private ScrollView transcriptScroll;
    private TextView transcriptText;
    private TextView listeningStatusText;
    private EditText inputText;
    private Button sendButton;
    private ToggleButton micToggleButton;
    private Button pushToTalkButton;

    private int startX;
    private int startY;
    private float startTouchX;
    private float startTouchY;
    private boolean moved;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService backgroundExecutor;
    private IdentityDatabase identityDatabase;
    private LlmSettingsStore llmSettingsStore;
    private ActiveSessionManager activeSessionManager;
    private ContinuousSpeechRecognizer continuousSpeechRecognizer;
    private UiTreePruner uiTreePruner;
    private IncrementalRequestManager incrementalRequestManager;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        identityDatabase = Room.databaseBuilder(
                getApplicationContext(),
                IdentityDatabase.class,
                "codrive_identity.db"
        ).build();
        llmSettingsStore = LlmSettingsStore.create(getApplicationContext());
        activeSessionManager = new ActiveSessionManager();
        uiTreePruner = new UiTreePruner();
        incrementalRequestManager = new IncrementalRequestManager(backgroundExecutor);
        continuousSpeechRecognizer = new ContinuousSpeechRecognizer(
                getApplicationContext(),
                new ContinuousSpeechRecognizer.Callbacks() {
                    @Override
                    public void onListeningStateChanged(String message) {
                        mainHandler.post(() -> updateListeningStatus(message));
                    }

                    @Override
                    public void onSpeechDetected() {
                        // Previously we interrupted the active request as soon as speech was detected.
                        // That caused frequent "Interrupting current request" messages for brief noises
                        // or when the user simply started speaking. Instead, only update UI state here.
                        mainHandler.post(() -> updateListeningStatus(getString(R.string.overlay_listening_speech_detected)));
                    }

                    @Override
                    public void onCommandReady(String command) {
                        mainHandler.post(() -> submitOverlayCommand(command, true));
                    }

                    @Override
                    public void onPartialTranscript(String partial) {
                        mainHandler.post(() -> {
                            if (inputText != null && partial != null) {
                                inputText.setText(partial);
                            }
                        });
                    }

                    @Override
                    public void onCommandRejected(String reason) {
                        mainHandler.post(() -> {
                            updateListeningStatus(getString(R.string.overlay_ignored_noise));
                            if (!"empty transcript".equals(reason)) {
                                appendLine(getString(R.string.chat_codrive_prefix), getString(R.string.overlay_ignored_noise));
                                scrollTranscriptToBottom();
                            }
                        });
                    }
                },
                new SpeechCommandEndpointer()
        );
        incrementalRequestManager.registerCallback(result -> mainHandler.post(() -> {
            if (sendButton != null) {
                sendButton.setEnabled(true);
            }
            if (inputText != null) {
                inputText.setText("");
            }
            renderResult(result);

            if (!result.getDidExecute()) {
                Toast.makeText(this, result.getFinalFeedback(), Toast.LENGTH_LONG).show();
            }

            if (isContinuousListeningEnabled()) {
                updateListeningStatus(getString(R.string.overlay_listening_starting));
            }
        }));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ChatLauncherEntryPoint.ACTION_START_OVERLAY;
        if (ChatLauncherEntryPoint.ACTION_STOP_OVERLAY.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            promptOverlayPermission();
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureOverlayAttached();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (incrementalRequestManager != null) {
            incrementalRequestManager.endSession();
        }
        if (continuousSpeechRecognizer != null) {
            continuousSpeechRecognizer.stop();
        }
        if (identityDatabase != null) {
            identityDatabase.close();
        }
        detachOverlay();
        super.onDestroy();
    }

    private void ensureOverlayAttached() {
        if (overlayRoot != null) {
            return;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 30;
        layoutParams.y = 220;

        overlayRoot = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null);
        ImageButton bubbleButton = overlayRoot.findViewById(R.id.overlayBubbleButton);
        panel = overlayRoot.findViewById(R.id.overlayPanel);
        transcriptScroll = overlayRoot.findViewById(R.id.overlayTranscriptScroll);
        transcriptText = overlayRoot.findViewById(R.id.overlayTranscriptText);
        listeningStatusText = overlayRoot.findViewById(R.id.overlayListeningStatusText);
        inputText = overlayRoot.findViewById(R.id.overlayInputText);
        sendButton = overlayRoot.findViewById(R.id.overlaySendButton);
        micToggleButton = overlayRoot.findViewById(R.id.overlayMicToggleButton);
        pushToTalkButton = overlayRoot.findViewById(R.id.overlayPushToTalkButton);
        Button openChatButton = overlayRoot.findViewById(R.id.overlayOpenChatButton);
        Button closeBubbleButton = overlayRoot.findViewById(R.id.overlayCloseBubbleButton);

        bubbleButton.setOnTouchListener(this::onBubbleTouch);
        bubbleButton.setOnClickListener(v -> togglePanel());
        sendButton.setOnClickListener(v -> submitOverlayCommand(inputText.getText().toString().trim(), false));
        if (micToggleButton != null) {
            micToggleButton.setChecked(true);
            micToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startContinuousVoiceMode();
                } else {
                    stopContinuousVoiceMode(getString(R.string.overlay_listening_muted));
                }
            });
        }
        if (pushToTalkButton != null) {
            pushToTalkButton.setOnTouchListener((view, event) -> handlePushToTalkTouch(event));
        }

        openChatButton.setOnClickListener(v -> {
            startActivity(ChatLauncherEntryPoint.newChatIntent(this));
            setPanelExpanded(false);
        });

        closeBubbleButton.setOnClickListener(v -> stopSelf());

        windowManager.addView(overlayRoot, layoutParams);
        startContinuousVoiceMode();
    }

    private boolean onBubbleTouch(View view, MotionEvent event) {
        if (layoutParams == null) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = layoutParams.x;
                startY = layoutParams.y;
                startTouchX = event.getRawX();
                startTouchY = event.getRawY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - startTouchX);
                int dy = Math.round(event.getRawY() - startTouchY);
                if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                    moved = true;
                }
                layoutParams.x = startX + dx;
                layoutParams.y = startY + dy;
                windowManager.updateViewLayout(overlayRoot, layoutParams);
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    view.performClick();
                }
                return true;
            default:
                return false;
        }
    }

    private void togglePanel() {
        if (panel == null) {
            return;
        }
        setPanelExpanded(panel.getVisibility() != View.VISIBLE);
    }

    private void setPanelExpanded(boolean expanded) {
        if (panel == null || layoutParams == null) {
            return;
        }

        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        int nextFlags = expanded
                ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

        if (layoutParams.flags != nextFlags) {
            layoutParams.flags = nextFlags;
            windowManager.updateViewLayout(overlayRoot, layoutParams);
        }
    }

    private void submitOverlayCommand(String command, boolean fromVoice) {
        if (sendButton == null) {
            return;
        }

        if (TextUtils.isEmpty(command)) {
            Toast.makeText(this, R.string.chat_enter_command_first, Toast.LENGTH_SHORT).show();
            return;
        }

        appendLine(getString(R.string.chat_you_prefix), command);
        appendLine(getString(R.string.chat_codrive_prefix), getString(R.string.overlay_running_command));
        sendButton.setEnabled(false);

        if (fromVoice) {
            updateListeningStatus(getString(R.string.overlay_running_command));
        }

        PruningOutcome pruningOutcome = capturePruningOutcome();
        BiFunction<String, PruningOutcome, TracerBulletResult> runner = this::runTracerBullet;
        if (fromVoice) {
            incrementalRequestManager.appendToActiveRequest(command, pruningOutcome, runner);
        } else {
            incrementalRequestManager.startSession(command, pruningOutcome, runner);
        }
    }

    private void renderResult(TracerBulletResult result) {
        appendLine(getString(R.string.chat_codrive_prefix), result.getFinalFeedback());
        if (result.getExecutionResult() != null) {
            appendLine(getString(R.string.chat_action_prefix), result.getExecutionResult().getMessage());
        }
        scrollTranscriptToBottom();
    }

    private TracerBulletResult runTracerBullet(String command, PruningOutcome pruningOutcome) {
        CoDriveAccessibilityService service = CoDriveAccessibilityService.getInstance();
        if (service == null) {
            return new TracerBulletResult(
                    getString(R.string.chat_service_not_connected),
                    new AgentDecision(ActionType.FINISH, -1, "", "", "", 0.0),
                    null,
                    false
            );
        }

        String sessionAwareCommand = activeSessionManager.beginTurn(command);

        AccessibilityRuntimeAdapter runtimeAdapter = new AccessibilityRuntimeAdapter(service);
        AccessibilityActionExecutor actionExecutor = new AccessibilityActionExecutor(runtimeAdapter);

        Tier1NavigationRouter tier1NavigationRouter = new Tier1NavigationRouter();
        Tier1NavigationDirective navigationDirective = tier1NavigationRouter.match(command);
        if (navigationDirective != null) {
            AgentDecision decision = new AgentDecision(
                    navigationDirective.getActionType(),
                    0,
                    "",
                    "",
                    navigationDirective.getVoiceFeedback(),
                    1.0
            );
            ExecutionResult executionResult = actionExecutor.execute(
                    decision,
                    new com.codrive.ai.model.PrunedUiMap(0L, Collections.emptyList())
            );
            boolean success = executionResult.getSuccess();
            String message = success
                    ? navigationDirective.getVoiceFeedback()
                    : getString(R.string.overlay_failed_navigation);

            if (success) {
                activeSessionManager.clear();
            }
            return new TracerBulletResult(message, decision, executionResult, success);
        }

        MemorySearchTool memorySearchTool = new MemorySearchTool(
                () -> identityDatabase.identityDao(),
                () -> identityDatabase.sessionContextDao(),
                System::currentTimeMillis
        );
        InferenceLoopRunner loopRunner = new InferenceLoopRunner(
                LlmClientFactory.create(llmSettingsStore),
                memorySearchTool
        );
        BiFunction<String, com.codrive.ai.model.PrunedUiMap, AgentDecision> decisionRunner = loopRunner::run;
        ChatTracerBulletOrchestrator orchestrator = new ChatTracerBulletOrchestrator(
                decisionRunner,
                actionExecutor,
                runtimeAdapter::bindRegistry
        );

        TracerBulletResult result = orchestrator.run(
                sessionAwareCommand,
                pruningOutcome
        );
        activeSessionManager.onDecision(result.getDecision(), result.getDidExecute());
        return result;
    }

    private TracerBulletResult unexpectedFailureResult(Exception error) {
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = getString(R.string.chat_generic_error_message);
        }
        return new TracerBulletResult(
                message,
                new AgentDecision(ActionType.FINISH, -1, "", "", "", 0.0),
                null,
                false
        );
    }

    private void appendLine(String prefix, String message) {
        if (transcriptText == null) {
            return;
        }
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) {
            safeMessage = getString(R.string.chat_generic_error_message);
        }
        if (transcriptText.length() > 0) {
            transcriptText.append("\n\n");
        }
        transcriptText.append(prefix);
        transcriptText.append(" ");
        transcriptText.append(safeMessage);
    }

    private void scrollTranscriptToBottom() {
        if (transcriptScroll != null) {
            transcriptScroll.post(() -> transcriptScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void promptOverlayPermission() {
        Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startContinuousVoiceMode() {
        if (continuousSpeechRecognizer == null) {
            return;
        }
        if (!isContinuousListeningEnabled()) {
            updateListeningStatus(getString(R.string.overlay_listening_muted));
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateListeningStatus(getString(R.string.overlay_listening_no_mic_permission));
            return;
        }
        updateListeningStatus(getString(R.string.overlay_listening_starting));
        continuousSpeechRecognizer.start();
    }

    private void stopContinuousVoiceMode(String statusMessage) {
        if (continuousSpeechRecognizer != null) {
            continuousSpeechRecognizer.stop();
        }
        updateListeningStatus(statusMessage);
    }

    private void updateListeningStatus(String message) {
        if (listeningStatusText != null) {
            listeningStatusText.setText(message);
        }
    }

    private void interruptActiveCommand(String optionalStatusMessage) {
        if (incrementalRequestManager != null) {
            incrementalRequestManager.cancelActiveRequest();
        }
        if (sendButton != null) {
            sendButton.setEnabled(true);
        }
        if (!TextUtils.isEmpty(optionalStatusMessage)) {
            appendLine(getString(R.string.chat_codrive_prefix), optionalStatusMessage);
            scrollTranscriptToBottom();
        }
    }

    private boolean handlePushToTalkTouch(MotionEvent event) {
        if (continuousSpeechRecognizer == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            updateListeningStatus(getString(R.string.overlay_listening_push_to_talk_active));
            continuousSpeechRecognizer.start();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Always stop the recognizer on release so we get final results and submit the command.
            continuousSpeechRecognizer.stop();
            updateListeningStatus(getString(R.string.overlay_listening_push_to_talk_idle));

            // If continuous listening is enabled, resume it shortly after releasing the PTT button.
            if (isContinuousListeningEnabled()) {
                mainHandler.postDelayed(() -> startContinuousVoiceMode(), 150L);
            }
            return true;
        }
        return false;
    }

    private boolean isContinuousListeningEnabled() {
        return micToggleButton == null || micToggleButton.isChecked();
    }

    private PruningOutcome capturePruningOutcome() {
        CoDriveAccessibilityService service = CoDriveAccessibilityService.getInstance();
        return uiTreePruner.prune(
                service != null ? service.getLatestAutomationRootNode() : null,
                System.currentTimeMillis()
        );
    }

    private void detachOverlay() {
        if (overlayRoot != null && windowManager != null) {
            windowManager.removeView(overlayRoot);
            overlayRoot = null;
            panel = null;
            transcriptScroll = null;
            transcriptText = null;
            listeningStatusText = null;
            inputText = null;
            sendButton = null;
            micToggleButton = null;
            pushToTalkButton = null;
            layoutParams = null;
        }
    }
}





