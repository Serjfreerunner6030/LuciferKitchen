package com.lucifer.kitchen.utils;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AiClient {

    // Provider IDs
    public static final int PROVIDER_OPENAI = 0;
    public static final int PROVIDER_ANTHROPIC = 1;
    public static final int PROVIDER_GEMINI = 2;
    public static final int PROVIDER_DEEPSEEK = 3;
    public static final int PROVIDER_MISTRAL = 4;
    public static final int PROVIDER_XAI = 5;
    public static final int PROVIDER_COHERE = 6;
    public static final int PROVIDER_OPENROUTER = 7;
    public static final int PROVIDER_TOGETHER = 8;
    public static final int PROVIDER_FIREWORKS = 9;
    public static final int PROVIDER_PERPLEXITY = 10;
    public static final int PROVIDER_GROQ = 11;
    public static final int PROVIDER_OLLAMA = 12;
    public static final int PROVIDER_LMSTUDIO = 13;
    public static final int PROVIDER_JANAI = 14;
    public static final int PROVIDER_CEREBRAS = 15;
    public static final int PROVIDER_SAMBANOVA = 16;
    public static final int PROVIDER_HYPERBOLIC = 17;
    public static final int PROVIDER_AI21 = 18;
    public static final int PROVIDER_MOONSHOT = 19;
    public static final int PROVIDER_ZHIPU = 20;
    public static final int PROVIDER_BAICHUAN = 21;
    public static final int PROVIDER_FREE = 22;
    public static final int PROVIDER_CUSTOM = 23;

    public static final int PROVIDER_COUNT = 24;

    private static final String[] PROVIDER_NAMES = {
        "OpenAI",           // 0
        "Anthropic",        // 1
        "Google Gemini",    // 2
        "DeepSeek",         // 3
        "Mistral",          // 4
        "xAI (Grok)",       // 5
        "Cohere",           // 6
        "OpenRouter",       // 7
        "Together AI",      // 8
        "Fireworks AI",     // 9
        "Perplexity",       // 10
        "Groq",             // 11
        "Ollama",           // 12
        "LM Studio",        // 13
        "Jan AI",           // 14
        "Cerebras",         // 15
        "SambaNova",        // 16
        "Hyperbolic",       // 17
        "AI21",             // 18
        "Moonshot",         // 19
        "Zhipu (GLM)",      // 20
        "Baichuan",         // 21
        "Free (no key)",    // 22
        "Custom"            // 23
    };

    private static final String[] DEFAULT_ENDPOINTS = {
        "https://api.openai.com/v1/chat/completions",                          // 0  OpenAI
        "https://api.anthropic.com/v1/messages",                               // 1  Anthropic
        "https://generativelanguage.googleapis.com",                           // 2  Gemini
        "https://api.deepseek.com/v1/chat/completions",                        // 3  DeepSeek
        "https://api.mistral.ai/v1/chat/completions",                          // 4  Mistral
        "https://api.x.ai/v1/chat/completions",                               // 5  xAI
        "https://api.cohere.com/v2/chat",                                      // 6  Cohere
        "https://openrouter.ai/api/v1/chat/completions",                       // 7  OpenRouter
        "https://api.together.xyz/v1/chat/completions",                        // 8  Together
        "https://api.fireworks.ai/inference/v1/chat/completions",              // 9  Fireworks
        "https://api.perplexity.ai/chat/completions",                          // 10 Perplexity
        "https://api.groq.com/openai/v1/chat/completions",                     // 11 Groq
        "http://localhost:11434",                                               // 12 Ollama
        "http://localhost:1234/v1/chat/completions",                           // 13 LM Studio
        "http://localhost:1337/v1/chat/completions",                           // 14 Jan AI
        "https://api.cerebras.ai/v1/chat/completions",                         // 15 Cerebras
        "https://api.sambanova.ai/v1/chat/completions",                        // 16 SambaNova
        "https://api.hyperbolic.xyz/v1/chat/completions",                      // 17 Hyperbolic
        "https://api.ai21.com/studio/v1/chat/completions",                     // 18 AI21
        "https://api.moonshot.cn/v1/chat/completions",                         // 19 Moonshot
        "https://open.bigmodel.cn/api/paas/v4/chat/completions",               // 20 Zhipu
        "https://api.baichuan-ai.com/v1/chat/completions",                     // 21 Baichuan
        "https://kai-proxy.vercel.app/api/chat",                               // 22 Free
        ""                                                                      // 23 Custom
    };

    private static final String[] DEFAULT_MODELS = {
        "gpt-4o",                                              // 0  OpenAI
        "claude-sonnet-4-20250514",                            // 1  Anthropic
        "gemini-2.0-flash",                                    // 2  Gemini
        "deepseek-chat",                                       // 3  DeepSeek
        "mistral-large-latest",                                // 4  Mistral
        "grok-2",                                              // 5  xAI
        "command-r-plus",                                      // 6  Cohere
        "openai/gpt-4o",                                       // 7  OpenRouter
        "meta-llama/Llama-3-70b-chat-hf",                     // 8  Together
        "accounts/fireworks/models/llama-v3p1-70b-instruct",  // 9  Fireworks
        "sonar-pro",                                           // 10 Perplexity
        "llama-3.1-70b-versatile",                             // 11 Groq
        "llama3",                                              // 12 Ollama
        "local-model",                                         // 13 LM Studio
        "llama3",                                              // 14 Jan AI
        "llama3.1-70b",                                        // 15 Cerebras
        "Meta-Llama-3.1-70B-Instruct",                        // 16 SambaNova
        "meta-llama/Llama-3.1-70B-Instruct",                  // 17 Hyperbolic
        "jamba-1.5-large",                                     // 18 AI21
        "moonshot-v1-8k",                                      // 19 Moonshot
        "glm-4-flash",                                         // 20 Zhipu
        "Baichuan4",                                           // 21 Baichuan
        "free",                                                // 22 Free
        "gpt-4o"                                               // 23 Custom
    };

    private static final String PREFS = "ai_prefs";
    private static final String KEY_PROVIDER = "ai_provider";
    private static final String KEY_API_KEY = "ai_api_key";
    private static final String KEY_ENDPOINT = "ai_endpoint";
    private static final String KEY_MODEL = "ai_model";

    private final SharedPreferences prefs;

    private int provider;
    private String apiKey;
    private String endpoint;
    private String model;

    public AiClient(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public void load() {
        provider = prefs.getInt(KEY_PROVIDER, PROVIDER_OPENAI);
        apiKey = prefs.getString(KEY_API_KEY, "");
        endpoint = prefs.getString(KEY_ENDPOINT, "");
        model = prefs.getString(KEY_MODEL, "");
    }

    public void save(int provider, String apiKey, String endpoint, String model) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.model = model;
        prefs.edit()
            .putInt(KEY_PROVIDER, provider)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_ENDPOINT, endpoint)
            .putString(KEY_MODEL, model)
            .apply();
    }

    public int getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getEndpoint() { return endpoint; }
    public String getModel() { return model; }

    public boolean isConfigured() {
        switch (provider) {
            case PROVIDER_FREE:
            case PROVIDER_OLLAMA:
            case PROVIDER_LMSTUDIO:
            case PROVIDER_JANAI:
                return true;
            default:
                return apiKey != null && !apiKey.isEmpty();
        }
    }

    public String getDefaultModel() {
        if (provider >= 0 && provider < DEFAULT_MODELS.length) {
            return DEFAULT_MODELS[provider];
        }
        return "gpt-4o";
    }

    public String getDefaultEndpoint() {
        if (provider >= 0 && provider < DEFAULT_ENDPOINTS.length) {
            return DEFAULT_ENDPOINTS[provider];
        }
        return "";
    }

    public static String getProviderName(int id) {
        if (id >= 0 && id < PROVIDER_NAMES.length) {
            return PROVIDER_NAMES[id];
        }
        return "Unknown";
    }

    public static String[] getProviderNames() {
        return PROVIDER_NAMES.clone();
    }

    public static String getDefaultEndpoint(int providerId) {
        if (providerId >= 0 && providerId < DEFAULT_ENDPOINTS.length) {
            return DEFAULT_ENDPOINTS[providerId];
        }
        return "";
    }

    public static String getDefaultModel(int providerId) {
        if (providerId >= 0 && providerId < DEFAULT_MODELS.length) {
            return DEFAULT_MODELS[providerId];
        }
        return "gpt-4o";
    }

    private String resolveEndpoint() {
        return (endpoint != null && !endpoint.isEmpty()) ? endpoint : getDefaultEndpoint();
    }

    private String resolveModel() {
        return (model != null && !model.isEmpty()) ? model : getDefaultModel();
    }

    /**
     * Send chat request. Returns the AI response text.
     * Backward-compatible method that does not support tool calling.
     */
    public String chat(List<ChatMessage> messages) throws Exception {
        ChatResponse response = chatWithTools(messages, null);
        return response.content != null ? response.content : "";
    }

    // --- chatWithTools dispatch ---

    /**
     * Send chat request with optional tool definitions.
     * Dispatches to the correct API format based on the current provider.
     *
     * @param messages the conversation messages
     * @param tools    optional JSONArray of tool definitions (OpenAI format), or null
     * @return ChatResponse with content and/or tool calls
     */
    public ChatResponse chatWithTools(List<ChatMessage> messages, JSONArray tools) throws Exception {
        switch (provider) {
            case PROVIDER_ANTHROPIC:
                return chatAnthropic(messages, tools);
            case PROVIDER_GEMINI:
                return chatGemini(messages, tools);
            case PROVIDER_OLLAMA:
                return chatOllama(messages);
            default:
                return chatOpenAICompatible(messages, tools);
        }
    }

    // --- OpenAI-compatible ---

    private ChatResponse chatOpenAICompatible(List<ChatMessage> messages, JSONArray tools)
            throws Exception {
        String url = resolveEndpoint();
        String mdl = resolveModel();

        JSONArray msgArray = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject m = new JSONObject();
            m.put("role", msg.role);
            m.put("content", msg.content);
            msgArray.put(m);
        }

        JSONObject body = new JSONObject();
        body.put("model", mdl);
        body.put("messages", msgArray);
        body.put("max_tokens", 4096);

        if (tools != null && tools.length() > 0) {
            body.put("tools", tools);
        }

        HttpURLConnection conn = openConnection(url, "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        // Free and local providers need no auth header
        if (provider != PROVIDER_FREE && provider != PROVIDER_OLLAMA
                && provider != PROVIDER_LMSTUDIO && provider != PROVIDER_JANAI) {
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
        }

        String responseBody = executeRequest(conn, body.toString());
        JSONObject resp = new JSONObject(responseBody);

        JSONObject message = resp.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message");

        if (message.has("tool_calls")) {
            JSONArray toolCalls = message.getJSONArray("tool_calls");
            JSONArray standardized = new JSONArray();
            for (int i = 0; i < toolCalls.length(); i++) {
                JSONObject tc = toolCalls.getJSONObject(i);
                JSONObject fn = tc.getJSONObject("function");
                JSONObject std = new JSONObject();
                std.put("id", tc.getString("id"));
                std.put("name", fn.getString("name"));
                std.put("arguments", fn.getString("arguments"));
                standardized.put(std);
            }
            String content = message.optString("content", null);
            return new ChatResponse(content, standardized);
        }

        return new ChatResponse(message.getString("content"), null);
    }

    // --- Anthropic ---

    private ChatResponse chatAnthropic(List<ChatMessage> messages, JSONArray tools)
            throws Exception {
        String url = resolveEndpoint();
        String mdl = resolveModel();

        String systemText = null;
        JSONArray msgArray = new JSONArray();
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.role)) {
                systemText = msg.content;
            } else {
                JSONObject m = new JSONObject();
                m.put("role", msg.role);
                m.put("content", msg.content);
                msgArray.put(m);
            }
        }

        JSONObject body = new JSONObject();
        body.put("model", mdl);
        body.put("max_tokens", 4096);
        body.put("messages", msgArray);
        if (systemText != null) {
            body.put("system", systemText);
        }

        if (tools != null && tools.length() > 0) {
            // Convert OpenAI tool format to Anthropic format
            JSONArray anthropicTools = new JSONArray();
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.getJSONObject(i);
                JSONObject fn = tool.optJSONObject("function");
                if (fn != null) {
                    JSONObject at = new JSONObject();
                    at.put("name", fn.getString("name"));
                    if (fn.has("description")) {
                        at.put("description", fn.getString("description"));
                    }
                    if (fn.has("parameters")) {
                        at.put("input_schema", fn.getJSONObject("parameters"));
                    }
                    anthropicTools.put(at);
                }
            }
            body.put("tools", anthropicTools);
        }

        HttpURLConnection conn = openConnection(url, "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");

        String responseBody = executeRequest(conn, body.toString());
        JSONObject resp = new JSONObject(responseBody);

        JSONArray contentArray = resp.getJSONArray("content");

        // Check for tool_use blocks
        JSONArray toolCalls = new JSONArray();
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < contentArray.length(); i++) {
            JSONObject block = contentArray.getJSONObject(i);
            String type = block.getString("type");
            if ("tool_use".equals(type)) {
                JSONObject std = new JSONObject();
                std.put("id", block.getString("id"));
                std.put("name", block.getString("name"));
                std.put("arguments", block.getJSONObject("input").toString());
                toolCalls.put(std);
            } else if ("text".equals(type)) {
                textContent.append(block.getString("text"));
            }
        }

        if (toolCalls.length() > 0) {
            String text = textContent.length() > 0 ? textContent.toString() : null;
            return new ChatResponse(text, toolCalls);
        }

        return new ChatResponse(textContent.toString(), null);
    }

    // --- Gemini ---

    private ChatResponse chatGemini(List<ChatMessage> messages, JSONArray tools)
            throws Exception {
        String mdl = resolveModel();
        String baseUrl = resolveEndpoint();
        String url = baseUrl + "/v1beta/models/" + mdl + ":generateContent?key=" + apiKey;

        String systemText = null;
        List<ChatMessage> chatMsgs = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.role)) {
                systemText = msg.content;
            } else {
                chatMsgs.add(msg);
            }
        }

        JSONArray contents = new JSONArray();
        for (ChatMessage msg : chatMsgs) {
            JSONObject content = new JSONObject();
            content.put("role", "user".equals(msg.role) ? "user" : "model");
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", msg.content);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
        }

        JSONObject body = new JSONObject();
        body.put("contents", contents);

        if (systemText != null) {
            JSONObject systemInstruction = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", systemText);
            parts.put(part);
            systemInstruction.put("parts", parts);
            body.put("systemInstruction", systemInstruction);
        }

        JSONObject genConfig = new JSONObject();
        genConfig.put("maxOutputTokens", 4096);
        body.put("generationConfig", genConfig);

        if (tools != null && tools.length() > 0) {
            // Convert OpenAI tool format to Gemini functionDeclarations
            JSONArray functionDeclarations = new JSONArray();
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.getJSONObject(i);
                JSONObject fn = tool.optJSONObject("function");
                if (fn != null) {
                    JSONObject decl = new JSONObject();
                    decl.put("name", fn.getString("name"));
                    if (fn.has("description")) {
                        decl.put("description", fn.getString("description"));
                    }
                    if (fn.has("parameters")) {
                        decl.put("parameters", fn.getJSONObject("parameters"));
                    }
                    functionDeclarations.put(decl);
                }
            }
            JSONArray toolsArray = new JSONArray();
            JSONObject toolObj = new JSONObject();
            toolObj.put("functionDeclarations", functionDeclarations);
            toolsArray.put(toolObj);
            body.put("tools", toolsArray);
        }

        HttpURLConnection conn = openConnection(url, "POST");
        conn.setRequestProperty("Content-Type", "application/json");

        String responseBody = executeRequest(conn, body.toString());
        JSONObject resp = new JSONObject(responseBody);

        JSONObject candidate = resp.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content");
        JSONArray partsArray = candidate.getJSONArray("parts");

        // Check for functionCall parts
        JSONArray toolCalls = new JSONArray();
        StringBuilder textContent = new StringBuilder();

        for (int i = 0; i < partsArray.length(); i++) {
            JSONObject p = partsArray.getJSONObject(i);
            if (p.has("functionCall")) {
                JSONObject fc = p.getJSONObject("functionCall");
                JSONObject std = new JSONObject();
                std.put("id", "call_" + i);
                std.put("name", fc.getString("name"));
                std.put("arguments", fc.optJSONObject("args") != null
                    ? fc.getJSONObject("args").toString() : "{}");
                toolCalls.put(std);
            } else if (p.has("text")) {
                textContent.append(p.getString("text"));
            }
        }

        if (toolCalls.length() > 0) {
            String text = textContent.length() > 0 ? textContent.toString() : null;
            return new ChatResponse(text, toolCalls);
        }

        return new ChatResponse(textContent.toString(), null);
    }

    // --- Ollama ---

    private ChatResponse chatOllama(List<ChatMessage> messages) throws Exception {
        String baseUrl = resolveEndpoint();
        String url = baseUrl + "/api/chat";
        String mdl = resolveModel();

        JSONArray msgArray = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject m = new JSONObject();
            m.put("role", msg.role);
            m.put("content", msg.content);
            msgArray.put(m);
        }

        JSONObject body = new JSONObject();
        body.put("model", mdl);
        body.put("messages", msgArray);
        body.put("stream", false);

        HttpURLConnection conn = openConnection(url, "POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setReadTimeout(300000); // Ollama can be slow

        String responseBody = executeRequest(conn, body.toString());
        JSONObject resp = new JSONObject(responseBody);

        return new ChatResponse(
            resp.getJSONObject("message").getString("content"), null);
    }

    // --- HTTP helpers ---

    private HttpURLConnection openConnection(String url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        return conn;
    }

    private String executeRequest(HttpURLConnection conn, String body) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("API error " + code + ": " + sb.toString());
        }

        return sb.toString();
    }

    // --- Inner classes ---

    public static class ChatMessage {
        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class ChatResponse {
        public final String content;
        public final JSONArray toolCalls;

        public ChatResponse(String content, JSONArray toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && toolCalls.length() > 0;
        }
    }
}
