package com.codrive.ai;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.codrive.ai.execution.AccessibilityActionExecutor;
import com.codrive.ai.execution.AccessibilityRuntimeAdapter;
import com.codrive.ai.llm.LlmClientFactory;
import com.codrive.ai.memory.IdentityDatabase;
import com.codrive.ai.memory.MemorySearchTool;
import com.codrive.ai.model.ActionType;
import com.codrive.ai.model.AgentDecision;
import com.codrive.ai.orchestration.ChatTracerBulletOrchestrator;
import com.codrive.ai.orchestration.InferenceLoopRunner;
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

    private IdentityDatabase identityDatabase;
    private ExecutorService backgroundExecutor;
    private LlmSettingsStore llmSettingsStore;

    private static final String TAG = "ChatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        outputText = findViewById(R.id.chatOutputText);
        // Make the output scrollable so long messages (errors/debug) are visible.
        outputText.setMovementMethod(new ScrollingMovementMethod());
        outputText.setScrollBarSize(8);
        inputText = findViewById(R.id.chatInputText);
        sendButton = findViewById(R.id.chatSendButton);

        outputText.setText(R.string.chat_ready_message);

        identityDatabase = Room.databaseBuilder(
                getApplicationContext(),
                IdentityDatabase.class,
                "codrive_identity.db"
        ).build();
        llmSettingsStore = LlmSettingsStore.create(getApplicationContext());
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
            outputText.setText(R.string.chat_enter_command_first);
            return;
        }

        sendButton.setEnabled(false);
        outputText.setText(R.string.chat_running_command);

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
                String feedback = finalResult.getFinalFeedback();
                outputText.setText(feedback);
                // ensure the content is visible (scroll to top)
                outputText.post(() -> outputText.scrollTo(0, 0));

                // show a short Toast for failures so it's visible even if layout clipping occurs
                if (!finalResult.getDidExecute()) {
                    Toast.makeText(this, feedback, Toast.LENGTH_LONG).show();
                }
            });
        });
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

        return orchestrator.run(command, pruner.prune(service.getLatestRootNode(), System.currentTimeMillis()));
    }
}
