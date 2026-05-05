package com.codrive.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

import com.codrive.ai.R;
import com.codrive.ai.accessibility.PruningOutcome;
import com.codrive.ai.accessibility.UiTreePruner;
import com.codrive.ai.execution.AccessibilityActionExecutor;
import com.codrive.ai.execution.AccessibilityRuntimeAdapter;
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
import com.codrive.ai.settings.LlmSettingsStore;
import com.codrive.ai.settings.VoiceSettingsStore;
import com.codrive.ai.service.CoDriveAccessibilityService;
import com.codrive.ai.contracts.TtsEngine;
import com.codrive.ai.voice.AndroidTextToSpeechEngine;
import com.codrive.ai.voice.SherpaTtsEngine;
import com.codrive.ai.voice.VoiceEngineFactory;
import com.codrive.ai.modeldownload.ModelStorage;
import com.codrive.ai.vlm.InternVlModelLoader;
import com.codrive.ai.vlm.InternVlRuntime;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

public class ChatActivity extends AppCompatActivity {
    private TextView outputText;
    private EditText inputText;
    private Button sendButton;
    private ScrollView outputScroll;

    private IdentityDatabase identityDatabase;
    private ExecutorService backgroundExecutor;
    private LlmSettingsStore llmSettingsStore;
    private VoiceSettingsStore voiceSettingsStore;
    private ActiveSessionManager activeSessionManager;
    private IncrementalRequestManager incrementalRequestManager;
    private UiTreePruner uiTreePruner;
    private TtsEngine ttsEngine;
    private InternVlRuntime vlmRuntime;

    private static final String TAG = "ChatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        View chatRoot = findViewById(R.id.chatRoot);
        View composerContainer = findViewById(R.id.chatComposerContainer);
        outputScroll = findViewById(R.id.chatOutputScroll);
        outputText = findViewById(R.id.chatOutputText);
        inputText = findViewById(R.id.chatInputText);
        sendButton = findViewById(R.id.chatSendButton);
        ImageButton menuButton = findViewById(R.id.chatMenuButton);

        configureInsets(chatRoot, composerContainer);
        menuButton.setOnClickListener(this::showNavigationMenu);

        outputText.setText(R.string.chat_ready_message);

        identityDatabase = Room.databaseBuilder(
                getApplicationContext(),
                IdentityDatabase.class,
                "codrive_identity.db"
        ).build();
        llmSettingsStore = LlmSettingsStore.create(getApplicationContext());
        voiceSettingsStore = VoiceSettingsStore.create(getApplicationContext());
        activeSessionManager = new ActiveSessionManager();
        backgroundExecutor = Executors.newSingleThreadExecutor();
        incrementalRequestManager = new IncrementalRequestManager(backgroundExecutor);
        uiTreePruner = new UiTreePruner();
        ModelStorage storage = new ModelStorage(new File(getApplicationContext().getNoBackupFilesDir(), "models"));
        vlmRuntime = new InternVlRuntime(new InternVlModelLoader(storage));
        ttsEngine = VoiceEngineFactory.createTtsEngine(this, voiceSettingsStore);

        incrementalRequestManager.registerCallback(result -> runOnUiThread(() -> {
            sendButton.setEnabled(true);
            inputText.setText("");
            renderResult(result);

            String feedback = result.getFinalFeedback();
            if (!result.getDidExecute()) {
                Toast.makeText(this, feedback, Toast.LENGTH_LONG).show();
            }
        }));

        sendButton.setOnClickListener(v -> submitCommand());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTtsEngine();
    }

    @Override
    protected void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (incrementalRequestManager != null) {
            incrementalRequestManager.endSession();
        }
        releaseTtsEngine();
        if (identityDatabase != null) {
            identityDatabase.close();
        }
        super.onDestroy();
    }

    private void refreshTtsEngine() {
        boolean shouldUseSherpa = VoiceEngineFactory.shouldUseSherpaTts(this, voiceSettingsStore);
        if (ttsEngine == null) {
            ttsEngine = VoiceEngineFactory.createTtsEngine(this, voiceSettingsStore);
            return;
        }
        boolean isSherpa = ttsEngine instanceof SherpaTtsEngine;
        if (isSherpa != shouldUseSherpa) {
            releaseTtsEngine();
            ttsEngine = VoiceEngineFactory.createTtsEngine(this, voiceSettingsStore);
        }
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

    private void submitCommand() {
        if (ttsEngine != null) {
            ttsEngine.stop();
        }
        final String command = inputText.getText().toString().trim();
        if (TextUtils.isEmpty(command)) {
            Toast.makeText(this, R.string.chat_enter_command_first, Toast.LENGTH_SHORT).show();
            return;
        }

        appendLine(getString(R.string.chat_you_prefix), command);
        sendButton.setEnabled(false);
        incrementalRequestManager.startSession(
                command,
                capturePruningOutcome(),
                this::runTracerBullet
        );
    }

    private void renderResult(TracerBulletResult result) {
        appendLine(getString(R.string.chat_codrive_prefix), result.getFinalFeedback());
        if (result.getExecutionResult() != null) {
            String actionSummary = result.getExecutionResult().getMessage();
            appendLine(getString(R.string.chat_action_prefix), actionSummary);
        }
        if (ttsEngine != null && !TextUtils.isEmpty(result.getFinalFeedback())) {
            ttsEngine.speak(result.getFinalFeedback());
        }
        scrollTranscriptToBottom();
    }

    private void appendLine(String prefix, String message) {
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) {
            safeMessage = getString(R.string.chat_generic_error_message);
        }
        if (outputText.length() > 0) {
            outputText.append("\n\n");
        }
        outputText.append(prefix);
        outputText.append(" ");
        outputText.append(safeMessage);
    }

    private void scrollTranscriptToBottom() {
        outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void configureInsets(View root, View composerContainer) {
        final int baseRootBottom = root.getPaddingBottom();
        final int baseComposerBottom = composerContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    baseRootBottom
            );
            composerContainer.setPadding(
                    composerContainer.getPaddingLeft(),
                    composerContainer.getPaddingTop(),
                    composerContainer.getPaddingRight(),
                    baseComposerBottom + bars.bottom
            );
            return insets;
        });
    }

    private void showNavigationMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.chat_navigation_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::onNavigationItemClicked);
        popupMenu.show();
    }

    private boolean onNavigationItemClicked(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_chat_home) {
            startActivity(new Intent(this, MainActivity.class));
            return true;
        }
        if (itemId == R.id.menu_chat_chat) {
            return true;
        }
        if (itemId == R.id.menu_chat_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return false;
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
            ExecutionResult executionResult = actionExecutor.execute(decision, new com.codrive.ai.model.PrunedUiMap(0L, java.util.Collections.emptyList()));
            boolean success = executionResult.getSuccess();
            String message = success ? navigationDirective.getVoiceFeedback() : "Failed to perform the requested navigation.";
            if (success) {
                activeSessionManager.clear();
            }
            return new TracerBulletResult(
                    message,
                    decision,
                    executionResult,
                    success
            );
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
                runtimeAdapter::bindRegistry,
                vlmRuntime
        );

        TracerBulletResult result = orchestrator.run(
                sessionAwareCommand,
                pruningOutcome
        );
        activeSessionManager.onDecision(result.getDecision(), result.getDidExecute());
        return result;
    }

    private PruningOutcome capturePruningOutcome() {
        CoDriveAccessibilityService service = CoDriveAccessibilityService.getInstance();
        return uiTreePruner.prune(
                service != null ? service.getLatestRootNode() : null,
                System.currentTimeMillis()
        );
    }
}
