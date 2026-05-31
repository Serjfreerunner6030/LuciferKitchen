package com.lucifer.kitchen.utils;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConversationManager {

    private static final String PREFS = "kai_conversations";
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final String KEY_ACTIVE = "active_conversation";
    private static final int MAX_CONVERSATIONS = 50;

    private final SharedPreferences prefs;

    public ConversationManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static class Conversation {
        public final String id;
        public String title;
        public final List<AiClient.ChatMessage> messages;
        public long lastUpdated;

        public Conversation(String id, String title) {
            this.id = id;
            this.title = title;
            this.messages = new ArrayList<>();
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public String createConversation(String title) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Conversation conv = new Conversation(id, title);
        saveConversation(conv);
        setActiveConversation(id);
        return id;
    }

    public void setActiveConversation(String id) {
        prefs.edit().putString(KEY_ACTIVE, id).apply();
    }

    public String getActiveConversationId() {
        return prefs.getString(KEY_ACTIVE, null);
    }

    public Conversation getConversation(String id) {
        try {
            JSONArray all = getAllConversationsJson();
            for (int i = 0; i < all.length(); i++) {
                JSONObject obj = all.getJSONObject(i);
                if (obj.getString("id").equals(id)) {
                    return jsonToConversation(obj);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Conversation getActiveConversation() {
        String id = getActiveConversationId();
        if (id == null) return null;
        return getConversation(id);
    }

    public List<Conversation> getAllConversations() {
        List<Conversation> list = new ArrayList<>();
        try {
            JSONArray all = getAllConversationsJson();
            for (int i = 0; i < all.length(); i++) {
                list.add(jsonToConversation(all.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // Sort by lastUpdated descending
        list.sort((a, b) -> Long.compare(b.lastUpdated, a.lastUpdated));
        return list;
    }

    public void saveConversation(Conversation conv) {
        try {
            conv.lastUpdated = System.currentTimeMillis();
            JSONArray all = getAllConversationsJson();

            // Find existing and replace, or add new
            boolean found = false;
            for (int i = 0; i < all.length(); i++) {
                if (all.getJSONObject(i).getString("id").equals(conv.id)) {
                    all.put(i, conversationToJson(conv));
                    found = true;
                    break;
                }
            }
            if (!found) {
                all.put(conversationToJson(conv));
            }

            // Trim old conversations
            while (all.length() > MAX_CONVERSATIONS) {
                all.remove(0);
            }

            prefs.edit().putString(KEY_CONVERSATIONS, all.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void addMessage(String conversationId, AiClient.ChatMessage message) {
        Conversation conv = getConversation(conversationId);
        if (conv != null) {
            conv.messages.add(message);
            // Auto-generate title from first user message
            if (conv.title.equals("New Chat") && "user".equals(message.role)) {
                String title = message.content;
                if (title.length() > 40) title = title.substring(0, 40) + "...";
                conv.title = title;
            }
            saveConversation(conv);
        }
    }

    public void deleteConversation(String id) {
        try {
            JSONArray all = getAllConversationsJson();
            JSONArray updated = new JSONArray();
            for (int i = 0; i < all.length(); i++) {
                if (!all.getJSONObject(i).getString("id").equals(id)) {
                    updated.put(all.getJSONObject(i));
                }
            }
            prefs.edit().putString(KEY_CONVERSATIONS, updated.toString()).apply();

            if (id.equals(getActiveConversationId())) {
                prefs.edit().remove(KEY_ACTIVE).apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    private JSONArray getAllConversationsJson() {
        String json = prefs.getString(KEY_CONVERSATIONS, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private JSONObject conversationToJson(Conversation conv) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", conv.id);
        obj.put("title", conv.title);
        obj.put("lastUpdated", conv.lastUpdated);

        JSONArray msgs = new JSONArray();
        for (AiClient.ChatMessage msg : conv.messages) {
            JSONObject m = new JSONObject();
            m.put("role", msg.role);
            m.put("content", msg.content);
            msgs.put(m);
        }
        obj.put("messages", msgs);
        return obj;
    }

    private Conversation jsonToConversation(JSONObject obj) throws JSONException {
        Conversation conv = new Conversation(
            obj.getString("id"),
            obj.optString("title", "Chat")
        );
        conv.lastUpdated = obj.optLong("lastUpdated", 0);

        JSONArray msgs = obj.optJSONArray("messages");
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.getJSONObject(i);
                conv.messages.add(new AiClient.ChatMessage(
                    m.getString("role"),
                    m.optString("content", "")
                ));
            }
        }
        return conv;
    }
}
