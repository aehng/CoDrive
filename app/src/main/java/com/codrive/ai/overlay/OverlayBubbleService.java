package com.codrive.ai.overlay;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
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
import com.codrive.ai.contracts.TtsEngine;
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
import com.codrive.ai.overlay.stt.OverlaySpeechRecognizer;
import com.codrive.ai.overlay.stt.SpeechCommandEndpointer;
import com.codrive.ai.settings.LlmSettingsStore;
import com.codrive.ai.settings.VoiceSettingsStore;
import com.codrive.ai.service.CoDriveAccessibilityService;
import com.codrive.ai.voice.AndroidTextToSpeechEngine;
import com.codrive.ai.voice.SherpaTtsEngine;
import com.codrive.ai.voice.VoiceEngineFactory;
import com.codrive.ai.modeldownload.ModelStorage;
import com.codrive.ai.vlm.InternVlModelLoader;
import com.codrive.ai.vlm.InternVlRuntime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    private TextView liveTranscriptText;
    private EditText inputText;
    private Button sendButton;
    private ToggleButton micToggleButton;
    private ToggleButton audioRouteToggleButton;
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
    private VoiceSettingsStore voiceSettingsStore;
    private ActiveSessionManager activeSessionManager;
    private OverlaySpeechRecognizer continuousSpeechRecognizer;
    private UiTreePruner uiTreePruner;
    private IncrementalRequestManager incrementalRequestManager;
    private final SpeechCommandEndpointer overlayEndpointer = new SpeechCommandEndpointer();
    private final SpeechCommandEndpointer recognizerEndpointer = new SpeechCommandEndpointer();
    private ContinuousSpeechRecognizer.Callbacks speechCallbacks;
    private TtsEngine ttsEngine;
    private InternVlRuntime vlmRuntime;
    private boolean micSuppressedForTts = false;
    private AudioManager audioManager;
    private boolean commAudioModeApplied = false;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphoneOn = false;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            audioFocusGranted = false;
        }
    };
    private boolean audioFocusGranted = false;
    private boolean routeToSpeaker = true;
    private String lastAssistantSpokenNormalized = "";
    private final Runnable autoSubmitRunnable = new Runnable() {
        @Override
        public void run() {
            if (inputText == null) return;
            final String candidate = inputText.getText().toString().trim();
            if (candidate.isEmpty()) return;
            SpeechCommandEndpointer.Verdict verdict = overlayEndpointer.evaluateRelaxed(candidate);
            if (verdict.getShouldSubmit()) {
                mainHandler.post(() -> submitOverlayCommand(verdict.getCommand(), true));
            } else {
                mainHandler.post(() -> updateListeningStatus(getString(R.string.overlay_ignored_noise)));
            }
        }
    };
    private static final long PARTIAL_IDLE_MS = 300L;
    private static final long BARGE_IN_COMMAND_DELAY_MS = 1200L;
    private static final double ECHO_SIMILARITY_THRESHOLD = 0.72;
    private static final int ECHO_MIN_ORDERED_COMMON_WORDS = 3;
    private static final long TTS_SUPPRESS_MIN_MS = 1200L;
    private static final long TTS_SUPPRESS_MAX_MS = 10000L;
    private static final long TTS_SUPPRESS_PADDING_MS = 300L;
    private static final long TTS_MS_PER_CHAR = 55L;
    private static final long AUDIO_ROUTE_WATCHDOG_MS = 1500L;
    private final Runnable audioRouteWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            ensureCommunicationAudioMode();
            mainHandler.postDelayed(this, AUDIO_ROUTE_WATCHDOG_MS);
        }
    };
    private final Runnable resumeAfterTtsRunnable = new Runnable() {
        @Override
        public void run() {
            micSuppressedForTts = false;
            if (isContinuousListeningEnabled()) {
                startContinuousVoiceMode();
            } else {
                updateListeningStatus(getString(R.string.overlay_listening_muted));
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        identityDatabase = Room.databaseBuilder(
                getApplicationContext(),
                IdentityDatabase.class,
                "codrive_identity.db"
        ).build();
        llmSettingsStore = LlmSettingsStore.create(getApplicationContext());
        voiceSettingsStore = VoiceSettingsStore.create(getApplicationContext());
        // Hard default every overlay session: main loudspeaker.
        routeToSpeaker = true;
        activeSessionManager = new ActiveSessionManager(
                30_000L,
                llmSettingsStore.isHistoryEnabled(),
                llmSettingsStore.getHistoryDepth(),
                System::currentTimeMillis
        );
        uiTreePruner = new UiTreePruner();
        ModelStorage storage = new ModelStorage(new File(getApplicationContext().getNoBackupFilesDir(), "models"));
        vlmRuntime = new InternVlRuntime(new InternVlModelLoader(storage));
        incrementalRequestManager = new IncrementalRequestManager(backgroundExecutor);
        speechCallbacks = new ContinuousSpeechRecognizer.Callbacks() {
                    @Override
                    public void onListeningStateChanged(String message) {
                        mainHandler.post(() -> updateListeningStatus(message));
                    }

                    @Override
                    public void onSpeechDetected() {
                        if (micSuppressedForTts) {
                            return;
                        }
                        mainHandler.post(() -> clearLiveTranscript());
                        mainHandler.post(() -> updateListeningStatus(getString(R.string.overlay_listening_speech_detected)));
                    }

                    @Override
                    public void onCommandReady(String command) {
                        if (micSuppressedForTts) {
                            return;
                        }
                        if (shouldDropAsEcho(command)) {
                            mainHandler.post(() -> updateListeningStatus(getString(R.string.overlay_ignored_noise)));
                            return;
                        }
                        mainHandler.removeCallbacks(autoSubmitRunnable);
                        mainHandler.post(() -> {
                            clearLiveTranscript();
                            updateListeningStatus("Waiting...");
                            submitOverlayCommand(command, true);
                        });
                    }

                    @Override
                    public void onPartialTranscript(String partial) {
                        if (micSuppressedForTts) {
                            return;
                        }
                        mainHandler.post(() -> {
                            if (partial != null) {
                                updateLiveTranscript(partial);
                            }
                            if (inputText != null && partial != null) {
                                inputText.setText(partial);
                                // Reset auto-submit timer whenever a partial updates
                                mainHandler.removeCallbacks(autoSubmitRunnable);
                                mainHandler.postDelayed(autoSubmitRunnable, PARTIAL_IDLE_MS);
                            }
                        });
                    }

                    @Override
                    public void onCommandRejected(String reason) {
                        if (micSuppressedForTts) {
                            return;
                        }
                        mainHandler.post(() -> {
                            // Cancel auto-submit when explicit rejection occurs
                            mainHandler.removeCallbacks(autoSubmitRunnable);
                            clearLiveTranscript();
                            updateListeningStatus(getString(R.string.overlay_ignored_noise));
                            if (!"empty transcript".equals(reason)) {
                                appendLine(getString(R.string.chat_codrive_prefix), getString(R.string.overlay_ignored_noise));
                                scrollTranscriptToBottom();
                            }
                        });
                    }
                };
        refreshVoiceEngines(true);
        ensureCommunicationAudioMode();
        mainHandler.postDelayed(audioRouteWatchdogRunnable, AUDIO_ROUTE_WATCHDOG_MS);
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
                restartContinuousVoiceMode();
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

        refreshVoiceEngines(false);

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
        mainHandler.removeCallbacks(audioRouteWatchdogRunnable);
        mainHandler.removeCallbacks(resumeAfterTtsRunnable);
        if (continuousSpeechRecognizer != null) {
            continuousSpeechRecognizer.stop();
        }
        restoreAudioMode();
        releaseTtsEngine();
        if (identityDatabase != null) {
            identityDatabase.close();
        }
        detachOverlay();
        super.onDestroy();
    }

    private void refreshVoiceEngines(boolean force) {
        boolean shouldUseSherpaTts = VoiceEngineFactory.shouldUseSherpaTts(this, voiceSettingsStore);
        boolean shouldUseSherpaStt = VoiceEngineFactory.shouldUseSherpaStt(getApplicationContext(), voiceSettingsStore);

        if (force || shouldRecreateTts(shouldUseSherpaTts)) {
            releaseTtsEngine();
            ttsEngine = VoiceEngineFactory.createTtsEngine(this, voiceSettingsStore);
        }

        if (force || shouldRecreateStt(shouldUseSherpaStt)) {
            if (continuousSpeechRecognizer != null) {
                continuousSpeechRecognizer.stop();
            }
            continuousSpeechRecognizer = VoiceEngineFactory.createOverlaySpeechRecognizer(
                    getApplicationContext(),
                    speechCallbacks,
                    recognizerEndpointer,
                    voiceSettingsStore.getSttLocaleTag(),
                    voiceSettingsStore
            );
        }

        if (overlayRoot != null && isContinuousListeningEnabled()) {
            startContinuousVoiceMode();
        }
    }

    private boolean shouldRecreateTts(boolean shouldUseSherpa) {
        if (ttsEngine == null) {
            return true;
        }
        boolean isSherpa = ttsEngine instanceof SherpaTtsEngine;
        return isSherpa != shouldUseSherpa;
    }

    private boolean shouldRecreateStt(boolean shouldUseSherpa) {
        if (continuousSpeechRecognizer == null) {
            return true;
        }
        boolean isSherpa = continuousSpeechRecognizer instanceof com.codrive.ai.overlay.stt.SherpaSpeechRecognizer;
        return isSherpa != shouldUseSherpa;
    }

    private void releaseTtsEngine() {
        if (ttsEngine == null) {
            return;
        }
        ttsEngine.stop();
        if (ttsEngine instanceof AndroidTextToSpeechEngine) {
            ((AndroidTextToSpeechEngine) ttsEngine).shutdown();
        } else if (ttsEngine instanceof SherpaTtsEngine) {
            ((SherpaTtsEngine) ttsEngine).shutdown();
        }
        ttsEngine = null;
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
        liveTranscriptText = overlayRoot.findViewById(R.id.overlayLiveTranscriptText);
        inputText = overlayRoot.findViewById(R.id.overlayInputText);
        sendButton = overlayRoot.findViewById(R.id.overlaySendButton);
        micToggleButton = overlayRoot.findViewById(R.id.overlayMicToggleButton);
        pushToTalkButton = overlayRoot.findViewById(R.id.overlayPushToTalkButton);
        audioRouteToggleButton = overlayRoot.findViewById(R.id.overlayAudioRouteToggleButton);
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
        if (audioRouteToggleButton != null) {
            audioRouteToggleButton.setChecked(true);
            audioRouteToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                routeToSpeaker = isChecked;
                ensureCommunicationAudioMode();
            });
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

        clearLiveTranscript();
        cancelTtsMicSuppression();
        if (ttsEngine != null) {
            ttsEngine.stop();
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
        long delayMs = voiceSettingsStore.getCommandDelayMs();
        if (fromVoice) {
            if (isBargeInEnabled()) {
                delayMs = Math.max(delayMs, BARGE_IN_COMMAND_DELAY_MS);
            }
            incrementalRequestManager.appendToActiveRequest(command, pruningOutcome, delayMs, runner);
        } else {
            incrementalRequestManager.startSession(command, pruningOutcome, 0, runner);
        }
    }

    private void renderResult(TracerBulletResult result) {
        appendLine(getString(R.string.chat_codrive_prefix), result.getFinalFeedback());
        if (result.getExecutionResult() != null) {
            appendLine(getString(R.string.chat_action_prefix), result.getExecutionResult().getMessage());
        }
        if (ttsEngine != null && !TextUtils.isEmpty(result.getFinalFeedback())) {
            ensureCommunicationAudioMode();
            rememberAssistantSpeech(result.getFinalFeedback());
            if (!isBargeInEnabled()) {
                suppressMicForAssistantSpeech(result.getFinalFeedback());
            }
            ttsEngine.speak(result.getFinalFeedback());
        }
        scrollTranscriptToBottom();
    }

    private TracerBulletResult runTracerBullet(String command, PruningOutcome pruningOutcome) {
        CoDriveAccessibilityService service = CoDriveAccessibilityService.getInstance();
        if (service == null) {
            Log.w(TAG, "Accessibility service instance is null; verify it is enabled in system settings.");
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
        // Create LLM client and orchestrator. We attach a watchdog that will call
        // `llmClient.cancel()` if the worker thread is interrupted so in-flight
        // OkHttp calls (or other cancellable transports) are aborted promptly.
        final com.codrive.ai.contracts.LlmClient llmClientRef = LlmClientFactory.create(llmSettingsStore);
        InferenceLoopRunner loopRunner = new InferenceLoopRunner(
                llmClientRef,
                memorySearchTool,
                3,
                query -> {
                    mainHandler.post(() -> {
                        appendLine(getString(R.string.chat_codrive_prefix), getString(R.string.overlay_searching_memory));
                        updateListeningStatus(getString(R.string.overlay_searching_memory));
                        scrollTranscriptToBottom();
                    });
                }
        );
        BiFunction<String, com.codrive.ai.model.PrunedUiMap, AgentDecision> decisionRunner = loopRunner::run;
        ChatTracerBulletOrchestrator orchestratorLocal = new ChatTracerBulletOrchestrator(
                decisionRunner,
                actionExecutor,
                runtimeAdapter::bindRegistry,
                vlmRuntime
        );

        TracerBulletResult result;
        final Thread workerThread = Thread.currentThread();
        Thread watchdog = new Thread(() -> {
            try {
                while (!workerThread.isInterrupted()) {
                    Thread.sleep(50);
                }
                try {
                    llmClientRef.cancel();
                } catch (Exception ex) {
                    // ignore
                }
            } catch (InterruptedException ie) {
                // exit
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        try {
            if (!llmSettingsStore.isAgenticBetaEnabled()) {
                result = orchestratorLocal.run(sessionAwareCommand, pruningOutcome);
            } else {
                result = runAgenticLoop(orchestratorLocal, command, sessionAwareCommand, pruningOutcome);
            }
        } finally {
            watchdog.interrupt();
        }

        activeSessionManager.onDecision(result.getDecision(), result.getDidExecute());
        return result;
    }

    private TracerBulletResult runAgenticLoop(
            ChatTracerBulletOrchestrator orchestrator,
            String originalCommand,
            String sessionAwareCommand,
            PruningOutcome firstOutcome
    ) {
        int maxIterations = llmSettingsStore.getAgenticMaxIterations();
        List<String> conversation = new ArrayList<>();
        conversation.add("USER: " + originalCommand);
        if (!TextUtils.equals(originalCommand, sessionAwareCommand)) {
            conversation.add("USER_CONTEXT: " + sessionAwareCommand);
        }
        String loopPrompt = buildAgenticPrompt(conversation, null, 0, maxIterations);
        PruningOutcome currentOutcome = firstOutcome;
        TracerBulletResult lastResult = null;

        for (int i = 0; i < maxIterations; i++) {
            TracerBulletResult stepResult = orchestrator.run(loopPrompt, currentOutcome);
            lastResult = stepResult;
            conversation.add("ASSISTANT: " + sanitizeForPrompt(stepResult.getFinalFeedback()));
            trimConversationIfNeeded(conversation);
            if (!stepResult.getDidExecute()) {
                if (stepResult.getDecision().getActionType() == ActionType.RESPOND && i < maxIterations - 1) {
                    loopPrompt = buildAgenticPrompt(
                            conversation,
                            stepResult.getFinalFeedback(),
                            i + 1,
                            maxIterations
                    );
                    currentOutcome = capturePruningOutcome();
                    continue;
                }
                return stepResult;
            }

            String outcome = stepResult.getExecutionResult() != null
                    ? stepResult.getExecutionResult().getMessage()
                    : stepResult.getFinalFeedback();
            loopPrompt = buildAgenticPrompt(
                    conversation,
                    outcome,
                    i + 1,
                    maxIterations
            );
            currentOutcome = capturePruningOutcome();
        }

        if (lastResult != null) {
            return new TracerBulletResult(
                    "Agentic run reached max iterations. " + lastResult.getFinalFeedback(),
                    lastResult.getDecision(),
                    lastResult.getExecutionResult(),
                    lastResult.getDidExecute()
            );
        }
        return unexpectedFailureResult(new IllegalStateException("Agentic loop produced no result."));
    }

    private String buildAgenticPrompt(
            List<String> conversation,
            String lastOutcome,
            int completed,
            int maxIterations
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("AGENTIC_BETA_MODE=true\n");
        builder.append(String.format(Locale.US, "COMPLETED_ITERATIONS=%d/%d\n", completed, maxIterations));
        builder.append("CONVERSATION_MESSAGES (no prior ui_map snapshots):\n");
        for (String message : conversation) {
            builder.append("- ").append(sanitizeForPrompt(message)).append('\n');
        }
        if (!TextUtils.isEmpty(lastOutcome)) {
            builder.append("LAST_OUTCOME=").append(sanitizeForPrompt(lastOutcome)).append('\n');
        }
        builder.append("Use only the current ui_map for grounding.\n");
        builder.append("If this needs multiple steps, return one executable action at a time.\n");
        builder.append("If task is complete, return FINISH or RESPOND. Return strict JSON only.");
        return builder.toString();
    }

    private String sanitizeForPrompt(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    private void trimConversationIfNeeded(List<String> conversation) {
        int maxMessages = llmSettingsStore.isHistoryEnabled()
                ? Math.max(1, llmSettingsStore.getHistoryDepth() * 2)
                : 1;
        while (conversation.size() > maxMessages) {
            conversation.remove(0);
        }
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
        ensureCommunicationAudioMode();
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

    private void restartContinuousVoiceMode() {
        if (continuousSpeechRecognizer == null) {
            return;
        }
        continuousSpeechRecognizer.stop();
        mainHandler.postDelayed(this::startContinuousVoiceMode, 120L);
    }

    private void stopContinuousVoiceMode(String statusMessage) {
        if (continuousSpeechRecognizer != null) {
            continuousSpeechRecognizer.stop();
        }
        clearLiveTranscript();
        updateListeningStatus(statusMessage);
    }

    private void suppressMicForAssistantSpeech(String spokenText) {
        micSuppressedForTts = true;
        if (continuousSpeechRecognizer != null) {
            continuousSpeechRecognizer.stop();
        }
        clearLiveTranscript();
        updateListeningStatus(getString(R.string.overlay_speaking_response));
        long delayMs = estimateSpeechDurationMs(spokenText);
        mainHandler.removeCallbacks(resumeAfterTtsRunnable);
        mainHandler.postDelayed(resumeAfterTtsRunnable, delayMs);
    }

    private void cancelTtsMicSuppression() {
        micSuppressedForTts = false;
        mainHandler.removeCallbacks(resumeAfterTtsRunnable);
    }

    private boolean isBargeInEnabled() {
        return voiceSettingsStore != null && voiceSettingsStore.isBargeInEnabled();
    }

    private void rememberAssistantSpeech(String text) {
        lastAssistantSpokenNormalized = normalizeForEcho(text);
    }

    private boolean shouldDropAsEcho(String recognized) {
        String heard = normalizeForEcho(recognized);
        String spoken = lastAssistantSpokenNormalized;
        if (heard.isEmpty() || spoken.isEmpty()) {
            return false;
        }
        if (spoken.contains(heard) || heard.contains(spoken)) {
            return true;
        }
        if (hasOrderedWordOverlap(heard, spoken, ECHO_MIN_ORDERED_COMMON_WORDS)) {
            return true;
        }
        double similarity = diceCoefficient(heard, spoken);
        return similarity >= ECHO_SIMILARITY_THRESHOLD;
    }

    private boolean hasOrderedWordOverlap(String heard, String spoken, int minWords) {
        String[] heardWords = heard.split(" ");
        String[] spokenWords = spoken.split(" ");
        if (heardWords.length < minWords || spokenWords.length < minWords) {
            return false;
        }
        int[][] lcs = new int[heardWords.length + 1][spokenWords.length + 1];
        for (int i = heardWords.length - 1; i >= 0; i--) {
            for (int j = spokenWords.length - 1; j >= 0; j--) {
                if (heardWords[i].equals(spokenWords[j])) {
                    lcs[i][j] = 1 + lcs[i + 1][j + 1];
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        return lcs[0][0] >= minWords;
    }

    private String normalizeForEcho(String text) {
        if (text == null) {
            return "";
        }
        return text
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double diceCoefficient(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() < 2 || b.length() < 2) {
            return 0.0;
        }
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            String gram = a.substring(i, i + 2);
            counts.put(gram, counts.getOrDefault(gram, 0) + 1);
        }
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String gram = b.substring(i, i + 2);
            Integer count = counts.get(gram);
            if (count != null && count > 0) {
                overlap++;
                counts.put(gram, count - 1);
            }
        }
        int total = (a.length() - 1) + (b.length() - 1);
        return total == 0 ? 0.0 : (2.0 * overlap) / total;
    }

    private long estimateSpeechDurationMs(String text) {
        if (TextUtils.isEmpty(text)) {
            return TTS_SUPPRESS_MIN_MS;
        }
        long estimated = (text.length() * TTS_MS_PER_CHAR) + TTS_SUPPRESS_PADDING_MS;
        return Math.max(TTS_SUPPRESS_MIN_MS, Math.min(TTS_SUPPRESS_MAX_MS, estimated));
    }

    private void updateListeningStatus(String message) {
        if (listeningStatusText != null) {
            listeningStatusText.setText(message);
        }
    }

    private void updateLiveTranscript(String partial) {
        if (liveTranscriptText == null) {
            return;
        }
        String trimmed = partial == null ? "" : partial.trim();
        if (trimmed.isEmpty()) {
            liveTranscriptText.setText("");
            liveTranscriptText.setVisibility(View.GONE);
        } else {
            liveTranscriptText.setText(getString(R.string.overlay_live_transcript_format, trimmed));
            liveTranscriptText.setVisibility(View.VISIBLE);
        }
    }

    private void clearLiveTranscript() {
        updateLiveTranscript("");
    }

    private void interruptActiveRequest(String optionalStatusMessage) {
        if (incrementalRequestManager != null) {
            incrementalRequestManager.cancelActiveRequest();
        }
        if (sendButton != null) {
            sendButton.setEnabled(true);
        }
        if (ttsEngine != null) {
            ttsEngine.stop();
        }
        if (!TextUtils.isEmpty(optionalStatusMessage)) {
            appendLine(getString(R.string.chat_codrive_prefix), optionalStatusMessage);
            scrollTranscriptToBottom();
        }
    }

    private void interruptActiveCommand(String optionalStatusMessage) {
        interruptActiveRequest(optionalStatusMessage);
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

    private void ensureCommunicationAudioMode() {
        if (audioManager == null) {
            return;
        }
        try {
            requestVoiceAudioFocusIfNeeded();
            if (!commAudioModeApplied) {
                previousAudioMode = audioManager.getMode();
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                commAudioModeApplied = true;
                Log.d(TAG, "Applied communication audio mode for full-duplex voice.");
            }
            // Force loudspeaker for overlay assistant responses in communication mode.
            audioManager.setSpeakerphoneOn(routeToSpeaker);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to apply communication audio mode", ex);
        }
    }

    private void restoreAudioMode() {
        if (audioManager == null) {
            return;
        }
        try {
            // Explicit teardown for call-like session lifecycle.
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
            Log.d(TAG, "Restored MODE_NORMAL and speakerphone off.");
        } catch (Exception ex) {
            Log.w(TAG, "Failed to restore previous audio mode", ex);
        } finally {
            commAudioModeApplied = false;
            abandonVoiceAudioFocusIfNeeded();
        }
    }

    private void requestVoiceAudioFocusIfNeeded() {
        if (audioManager == null || audioFocusGranted) {
            return;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            );
        }
        audioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        Log.d(TAG, "Audio focus request result=" + result + " granted=" + audioFocusGranted);
    }

    private void abandonVoiceAudioFocusIfNeeded() {
        if (audioManager == null || !audioFocusGranted) {
            return;
        }
        final int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                return;
            }
            result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            result = audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        audioFocusGranted = false;
        Log.d(TAG, "Audio focus abandoned result=" + result);
    }

    private void detachOverlay() {
        if (overlayRoot != null && windowManager != null) {
            windowManager.removeView(overlayRoot);
            overlayRoot = null;
            panel = null;
            transcriptScroll = null;
            transcriptText = null;
            listeningStatusText = null;
            liveTranscriptText = null;
            inputText = null;
            sendButton = null;
            micToggleButton = null;
            audioRouteToggleButton = null;
            pushToTalkButton = null;
            layoutParams = null;
        }
    }
}









