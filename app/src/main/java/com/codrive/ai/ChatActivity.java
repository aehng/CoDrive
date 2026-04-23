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
import com.codrive.ai.orchestration.Tier1NavigationDirective;
import com.codrive.ai.orchestration.Tier1NavigationRouter;
import com.codrive.ai.orchestration.TracerBulletResult;
import com.codrive.ai.settings.LlmSettingsStore;
import com.codrive.ai.service.CoDriveAccessibilityService;
import com.codrive.ai.accessibility.UiTreePruner;

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
    private ActiveSessionManager activeSessionManager;

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
        activeSessionManager = new ActiveSessionManager();
        backgroundExecutor = Executors.newSingleThreadExecutor();

        sendButton.setOnClickListener(v -> submitCommand());
    }

    @Override
    protected void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        if (identityDatabase != null) {
            identityDatabase.close();
        }
        super.onDestroy();
    }

    private void submitCommand() {
        final String command = inputText.getText().toString().trim();
        if (TextUtils.isEmpty(command)) {
            Toast.makeText(this, R.string.chat_enter_command_first, Toast.LENGTH_SHORT).show();
            return;
        }

        appendLine(getString(R.string.chat_you_prefix), command);
        sendButton.setEnabled(false);

        backgroundExecutor.execute(() -> {
            TracerBulletResult result;
            try {
                result = runTracerBullet(command);
            } catch (Exception error) {
                Log.e(TAG, "Tracer bullet failed", error);
                result = unexpectedFailureResult(error);
            }
            final TracerBulletResult finalResult = result;
            runOnUiThread(() -> {
                sendButton.setEnabled(true);
                inputText.setText("");
                renderResult(finalResult);

                String feedback = finalResult.getFinalFeedback();
                if (!finalResult.getDidExecute()) {
                    Toast.makeText(this, feedback, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void renderResult(TracerBulletResult result) {
        appendLine(getString(R.string.chat_codrive_prefix), result.getFinalFeedback());
        if (result.getExecutionResult() != null) {
            String actionSummary = result.getExecutionResult().getMessage();
            appendLine(getString(R.string.chat_action_prefix), actionSummary);
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

    private TracerBulletResult runTracerBullet(String command) {
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

        Tier1NavigationRouter tier1NavigationRouter = new Tier1NavigationRouter();
        Tier1NavigationDirective navigationDirective = tier1NavigationRouter.match(command);
        if (navigationDirective != null) {
            boolean success = service.performGlobalAction(navigationDirective.getGlobalAction());
            String message = success ? navigationDirective.getVoiceFeedback() : "Failed to perform the requested navigation.";
            if (success) {
                activeSessionManager.clear();
            }
            return new TracerBulletResult(
                    message,
                    new AgentDecision(
                            ActionType.FINISH,
                            -1,
                            "",
                            "",
                            message,
                            success ? 1.0 : 0.0
                    ),
                    new ExecutionResult(
                            success,
                            message,
                            ActionType.FINISH,
                            -1
                    ),
                    success
            );
        }

        UiTreePruner pruner = new UiTreePruner();
        AccessibilityRuntimeAdapter runtimeAdapter = new AccessibilityRuntimeAdapter(service);
        AccessibilityActionExecutor actionExecutor = new AccessibilityActionExecutor(runtimeAdapter);

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
                pruner.prune(service.getLatestRootNode(), System.currentTimeMillis())
        );
        activeSessionManager.onDecision(result.getDecision(), result.getDidExecute());
        return result;
    }
}
