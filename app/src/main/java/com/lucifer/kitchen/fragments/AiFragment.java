package com.lucifer.kitchen.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.lucifer.kitchen.R;
import com.lucifer.kitchen.utils.AiClient;
import com.lucifer.kitchen.utils.AiClient.ChatMessage;
import com.lucifer.kitchen.utils.AiClient.ChatResponse;
import com.lucifer.kitchen.utils.ConversationManager;
import com.lucifer.kitchen.utils.ConversationManager.Conversation;
import com.lucifer.kitchen.utils.KaiToolHandler;
import com.lucifer.kitchen.utils.ProjectManager;
import com.lucifer.kitchen.utils.ShellExecutor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiFragment extends Fragment {
    // PLACEHOLDER: remaining methods below
    private LinearLayout chatContainer;
    private ScrollView scrollChat;
    private EditText etMessage;
    private MaterialButton btnSend;
    private MaterialCardView cardSettings;
    private Spinner spinnerProvider;
    private EditText etApiKey, etEndpoint, etModel;
    private MaterialButton btnSaveSettings;
    private TextView tvContextInfo, tvProviderInfo, tvToolStatus;
    private ImageButton btnToggleSettings, btnNewChat, btnHistory;

    private AiClient aiClient;
    private ProjectManager projectManager;
    private ConversationManager conversationManager;
    private KaiToolHandler toolHandler;
    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String activeConversationId;
    private int toolCallCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai, container, false);
        initViews(view);
        initManagers();
        setupProviderSpinner();
        loadSettings();
        setupListeners();
        loadOrCreateConversation();
        updateContextInfo();
        return view;
    }

    private void initViews(View view) {
        chatContainer = view.findViewById(R.id.chatContainer);
        scrollChat = view.findViewById(R.id.scrollChat);
        etMessage = view.findViewById(R.id.etMessage);
        btnSend = view.findViewById(R.id.btnSend);
        cardSettings = view.findViewById(R.id.cardSettings);
        spinnerProvider = view.findViewById(R.id.spinnerProvider);
        etApiKey = view.findViewById(R.id.etApiKey);
        etEndpoint = view.findViewById(R.id.etEndpoint);
        etModel = view.findViewById(R.id.etModel);
        btnSaveSettings = view.findViewById(R.id.btnSaveSettings);
        tvContextInfo = view.findViewById(R.id.tvContextInfo);
        tvProviderInfo = view.findViewById(R.id.tvProviderInfo);
        tvToolStatus = view.findViewById(R.id.tvToolStatus);
        btnToggleSettings = view.findViewById(R.id.btnToggleSettings);
        btnNewChat = view.findViewById(R.id.btnNewChat);
        btnHistory = view.findViewById(R.id.btnHistory);
    }

    private void initManagers() {
        aiClient = new AiClient(requireContext());
        projectManager = new ProjectManager(requireContext());
        conversationManager = new ConversationManager(requireContext());
        toolHandler = new KaiToolHandler(requireContext());
    }

    private void setupProviderSpinner() {
        String[] providers = AiClient.getProviderNames();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_item, providers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);

        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean needsKey = position != AiClient.PROVIDER_FREE
                    && position != AiClient.PROVIDER_OLLAMA
                    && position != AiClient.PROVIDER_LMSTUDIO
                    && position != AiClient.PROVIDER_JANAI;
                etApiKey.setVisibility(needsKey ? View.VISIBLE : View.GONE);
                etEndpoint.setHint(AiClient.getDefaultEndpoint(position));
                etModel.setHint(AiClient.getDefaultModel(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSettings() {
        spinnerProvider.setSelection(aiClient.getProvider());
        etApiKey.setText(aiClient.getApiKey());
        etEndpoint.setText(aiClient.getEndpoint());
        etModel.setText(aiClient.getModel());
        updateProviderInfo();
    }

    private void updateProviderInfo() {
        String providerName = AiClient.getProviderName(aiClient.getProvider());
        String modelName = aiClient.getModel();
        if (modelName == null || modelName.isEmpty()) {
            modelName = AiClient.getDefaultModel(aiClient.getProvider());
        }
        tvProviderInfo.setText(providerName + " / " + modelName);
    }

    private void setupListeners() {
        btnToggleSettings.setOnClickListener(v -> {
            cardSettings.setVisibility(
                cardSettings.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        btnSaveSettings.setOnClickListener(v -> {
            aiClient.save(
                spinnerProvider.getSelectedItemPosition(),
                etApiKey.getText().toString().trim(),
                etEndpoint.getText().toString().trim(),
                etModel.getText().toString().trim()
            );
            cardSettings.setVisibility(View.GONE);
            updateProviderInfo();
            Toast.makeText(requireContext(), R.string.ai_settings_saved, Toast.LENGTH_SHORT).show();
        });

        btnSend.setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        btnNewChat.setOnClickListener(v -> startNewChat());

        btnHistory.setOnClickListener(v -> showHistoryDialog());
    }

    private void loadOrCreateConversation() {
        activeConversationId = conversationManager.getActiveConversationId();
        if (activeConversationId != null) {
            Conversation conv = conversationManager.getConversation(activeConversationId);
            if (conv != null) {
                conversationHistory.clear();
                conversationHistory.addAll(conv.messages);
                restoreChat();
                return;
            }
        }
        startNewChat();
    }

    private void startNewChat() {
        conversationHistory.clear();
        chatContainer.removeAllViews();
        toolCallCount = 0;
        activeConversationId = conversationManager.createConversation(
            getString(R.string.ai_new_chat));
        showWelcomeMessage();
    }

    private void restoreChat() {
        chatContainer.removeAllViews();
        toolCallCount = 0;
        for (ChatMessage msg : conversationHistory) {
            if ("system".equals(msg.role)) continue;
            if ("user".equals(msg.role)) {
                addMessageBubble(msg.content, true);
            } else if ("assistant".equals(msg.role)) {
                addMessageBubble(msg.content, false);
            } else if ("tool".equals(msg.role)) {
                addToolResultBubble(msg.content);
                toolCallCount++;
            }
        }
        if (conversationHistory.isEmpty()) {
            showWelcomeMessage();
        }
        updateToolStatus();
    }

    private void showHistoryDialog() {
        List<Conversation> conversations = conversationManager.getAllConversations();
        if (conversations.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ai_no_history, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] titles = new String[conversations.size()];
        for (int i = 0; i < conversations.size(); i++) {
            Conversation c = conversations.get(i);
            titles[i] = c.title + " (" + c.messages.size() + " msgs)";
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_conversations)
            .setItems(titles, (dialog, which) -> {
                Conversation selected = conversations.get(which);
                activeConversationId = selected.id;
                conversationManager.setActiveConversation(selected.id);
                conversationHistory.clear();
                conversationHistory.addAll(selected.messages);
                restoreChat();
            })
            .setNeutralButton(R.string.ai_clear_all, (dialog, which) -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ai_clear_all)
                    .setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        conversationManager.clearAll();
                        startNewChat();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void updateContextInfo() {
        String projectPath = projectManager.getCurrentProjectPath();
        if (projectPath != null) {
            int fileCount = countFilesRecursive(new File(projectPath));
            tvContextInfo.setText(getString(R.string.ai_context_files, fileCount));
        } else {
            tvContextInfo.setText(getString(R.string.ai_error_no_project));
        }
    }

    private void updateToolStatus() {
        if (toolCallCount > 0) {
            tvToolStatus.setText(getString(R.string.ai_tools_used, toolCallCount));
        } else {
            tvToolStatus.setText(getString(R.string.ai_tool_calling));
        }
    }

    private int countFilesRecursive(File dir) {
        int count = 0;
        if (!dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isFile()) count++;
            else if (f.isDirectory()) count += countFilesRecursive(f);
        }
        return count;
    }

    private void showWelcomeMessage() {
        addMessageBubble(getString(R.string.ai_welcome), false);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        etMessage.setText("");
        addMessageBubble(text, true);

        // Handle local commands
        if (text.startsWith("/")) {
            handleLocalCommand(text);
            return;
        }

        if (!aiClient.isConfigured()) {
            addMessageBubble(getString(R.string.ai_error_no_key), false);
            return;
        }

        // Add thinking indicator
        final View thinkingView = addThinkingIndicator();

        // Build system prompt on first message
        if (conversationHistory.isEmpty() || !"system".equals(conversationHistory.get(0).role)) {
            conversationHistory.add(0, new ChatMessage("system", buildSystemPrompt()));
        }

        ChatMessage userMsg = new ChatMessage("user", text);
        conversationHistory.add(userMsg);
        conversationManager.addMessage(activeConversationId, userMsg);

        sendToAiWithToolLoop(thinkingView);
    }

    private void sendToAiWithToolLoop(View thinkingView) {
        executor.execute(() -> {
            try {
                JSONArray tools = toolHandler.getToolDefinitions();
                int maxToolRounds = 10;
                int round = 0;

                while (round < maxToolRounds) {
                    round++;
                    ChatResponse response = aiClient.chatWithTools(conversationHistory, tools);

                    if (response.hasToolCalls()) {
                        // Process tool calls
                        ChatMessage assistantMsg = new ChatMessage("assistant",
                            response.content != null ? response.content : "");
                        conversationHistory.add(assistantMsg);

                        for (int i = 0; i < response.toolCalls.length(); i++) {
                            JSONObject tc = response.toolCalls.getJSONObject(i);
                            String toolId = tc.optString("id", "call_" + i);
                            String toolName = tc.getString("name");
                            JSONObject toolArgs = tc.getJSONObject("arguments");

                            // Show tool execution in UI
                            final String execMsg = getString(R.string.ai_tool_executing, toolName);
                            mainHandler.post(() -> addToolCallBubble(execMsg));

                            // Execute tool
                            String result = toolHandler.executeTool(toolName, toolArgs);
                            toolCallCount++;

                            // Show result
                            final String resultPreview = result.length() > 200
                                ? result.substring(0, 200) + "..." : result;
                            mainHandler.post(() -> {
                                addToolResultBubble(resultPreview);
                                updateToolStatus();
                            });

                            // Add tool result to conversation
                            // For OpenAI-compatible: role=tool, tool_call_id=id
                            ChatMessage toolMsg = new ChatMessage("tool", result);
                            conversationHistory.add(toolMsg);
                            conversationManager.addMessage(activeConversationId, toolMsg);
                        }

                        // Continue loop to get AI's final response after tool results
                        continue;
                    }

                    // No tool calls - got final text response
                    String finalContent = response.content != null ? response.content : "";
                    ChatMessage assistantMsg = new ChatMessage("assistant", finalContent);
                    conversationHistory.add(assistantMsg);
                    conversationManager.addMessage(activeConversationId, assistantMsg);

                    // Also process legacy action blocks
                    String processedContent = processAiActions(finalContent);

                    mainHandler.post(() -> {
                        chatContainer.removeView(thinkingView);
                        addMessageBubble(processedContent, false);
                        scrollToBottom();
                    });
                    return;
                }

                // Max rounds reached
                mainHandler.post(() -> {
                    chatContainer.removeView(thinkingView);
                    addMessageBubble("Tool call limit reached.", false);
                    scrollToBottom();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    chatContainer.removeView(thinkingView);
                    addMessageBubble("Error: " + e.getMessage(), false);
                    scrollToBottom();
                });
            }
        });
    }

    private void handleLocalCommand(String text) {
        String projectPath = projectManager.getCurrentProjectPath();

        if (text.equals("/tree")) {
            if (projectPath == null) {
                addMessageBubble(getString(R.string.ai_error_no_project), false);
                return;
            }
            String tree = buildProjectTree(new File(projectPath), "", 0);
            addMessageBubble(tree, false);
        } else if (text.equals("/help")) {
            showWelcomeMessage();
        } else if (text.startsWith("/read ")) {
            String filePath = resolveFilePath(text.substring(6).trim());
            String content = readFileContent(filePath);
            addMessageBubble(content, false);
        } else if (text.startsWith("/exec ")) {
            String cmd = text.substring(6).trim();
            executor.execute(() -> {
                String result = ShellExecutor.execute(cmd);
                mainHandler.post(() -> {
                    addMessageBubble("$ " + cmd + "\n\n" + result, false);
                    scrollToBottom();
                });
            });
        } else {
            addMessageBubble("Unknown command. Type /help", false);
        }
        scrollToBottom();
    }

    private String processAiActions(String response) {
        StringBuilder result = new StringBuilder(response);

        int execStart;
        while ((execStart = result.indexOf("```exec\n")) != -1) {
            int execEnd = result.indexOf("```", execStart + 8);
            if (execEnd == -1) break;
            String cmd = result.substring(execStart + 8, execEnd).trim();
            String output = ShellExecutor.execute(cmd);
            result.replace(execStart, execEnd + 3,
                "[Executed: " + cmd + "]\n" + output);
        }

        int writeStart;
        while ((writeStart = result.indexOf("```write\n")) != -1) {
            int writeEnd = result.indexOf("```", writeStart + 9);
            if (writeEnd == -1) break;
            String block = result.substring(writeStart + 9, writeEnd).trim();
            int nl = block.indexOf('\n');
            if (nl > 0) {
                String path = resolveFilePath(block.substring(0, nl).trim());
                String content = block.substring(nl + 1);
                boolean ok = writeFileContent(path, content);
                result.replace(writeStart, writeEnd + 3,
                    "[" + (ok ? "Written" : "Failed") + ": " + path + "]");
            }
        }

        int readStart;
        while ((readStart = result.indexOf("```read\n")) != -1) {
            int readEnd = result.indexOf("```", readStart + 8);
            if (readEnd == -1) break;
            String path = resolveFilePath(result.substring(readStart + 8, readEnd).trim());
            String content = readFileContent(path);
            result.replace(readStart, readEnd + 3,
                "[File: " + path + "]\n" + content);
        }

        return result.toString();
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.ai_system_prompt));
        sb.append("\n\n");

        String projectPath = projectManager.getCurrentProjectPath();
        if (projectPath != null) {
            sb.append("Current project path: ").append(projectPath).append("\n");
            sb.append("Project structure:\n");
            sb.append(buildProjectTree(new File(projectPath), "", 0));
            sb.append("\n\n");
            sb.append("You have tools available to interact with the project:\n");
            sb.append("- read_file: Read file contents\n");
            sb.append("- write_file: Create or modify files\n");
            sb.append("- list_files: Browse project directories\n");
            sb.append("- execute_command: Run shell commands\n");
            sb.append("- search_files: Search for text in files\n");
            sb.append("- file_info: Get file metadata\n");
            sb.append("Use these tools to help the user with their ROM project.\n");
        } else {
            sb.append("No project is currently open.\n");
        }

        return sb.toString();
    }

    private String buildProjectTree(File dir, String prefix, int depth) {
        if (depth > 5) return prefix + "...\n";
        StringBuilder sb = new StringBuilder();
        File[] files = dir.listFiles();
        if (files == null) return "";

        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareTo(b.getName());
        });

        int count = 0;
        for (File f : files) {
            count++;
            if (count > 50) {
                sb.append(prefix).append("... (").append(files.length - 50).append(" more)\n");
                break;
            }
            if (f.isDirectory()) {
                sb.append(prefix).append("[").append(f.getName()).append("/]\n");
                sb.append(buildProjectTree(f, prefix + "  ", depth + 1));
            } else {
                sb.append(prefix).append(f.getName()).append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveFilePath(String path) {
        if (path.startsWith("/")) return path;
        String projectPath = projectManager.getCurrentProjectPath();
        if (projectPath != null) return projectPath + "/" + path;
        return path;
    }

    private String readFileContent(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return "File not found: " + path;
            if (file.length() > 512 * 1024) return "File too large: " + file.length() + " bytes";
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                sb.append(lineNum++).append(": ").append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private boolean writeFileContent(String path, String content) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- UI Bubble Methods ---

    private TextView addMessageBubble(String text, boolean isUser) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, 4, 0, 4);

        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);

        if (isUser) {
            params.gravity = Gravity.END;
            wrapper.setGravity(Gravity.END);
            tv.setBackgroundColor(0xFF1B5E20);
            tv.setTextColor(Color.WHITE);
        } else {
            params.gravity = Gravity.START;
            wrapper.setGravity(Gravity.START);
            tv.setBackgroundColor(0xFF1E1E1E);
            tv.setTextColor(0xFFE0E0E0);
        }

        tv.setLayoutParams(params);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(16, 12, 16, 12);
        tv.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.85f));
        tv.setTextIsSelectable(true);

        if (!isUser && (text.contains("```") || text.contains("  "))) {
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(11);
        }

        wrapper.addView(tv);
        chatContainer.addView(wrapper);
        scrollToBottom();
        return tv;
    }

    private void addToolCallBubble(String text) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, 2, 0, 2);
        wrapper.setGravity(Gravity.START);

        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setText(">> " + text);
        tv.setTextSize(11);
        tv.setTextColor(0xFFFF9800); // orange
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(16, 6, 16, 6);
        tv.setBackgroundColor(0xFF2A2000);

        wrapper.addView(tv);
        chatContainer.addView(wrapper);
        scrollToBottom();
    }

    private void addToolResultBubble(String text) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, 2, 0, 2);
        wrapper.setGravity(Gravity.START);

        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTextColor(0xFF81C784); // green
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(16, 4, 16, 4);
        tv.setBackgroundColor(0xFF1A2E1A);
        tv.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.9f));
        tv.setTextIsSelectable(true);

        wrapper.addView(tv);
        chatContainer.addView(wrapper);
        scrollToBottom();
    }

    private View addThinkingIndicator() {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, 4, 0, 4);
        wrapper.setGravity(Gravity.START);

        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setText(getString(R.string.ai_thinking));
        tv.setTextSize(13);
        tv.setTextColor(0xFF9E9E9E);
        tv.setPadding(16, 12, 16, 12);
        tv.setBackgroundColor(0xFF1E1E1E);

        wrapper.addView(tv);
        chatContainer.addView(wrapper);
        scrollToBottom();
        return wrapper;
    }

    private void scrollToBottom() {
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }
}
