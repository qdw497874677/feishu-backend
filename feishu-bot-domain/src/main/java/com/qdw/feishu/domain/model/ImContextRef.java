package com.qdw.feishu.domain.model;

import lombok.Value;

/**
 * External IM conversation context identifier.
 * 
 * Identifies a conversation context in an IM platform (Feishu, Discord, etc.)
 * without being tied to any specific IM implementation details.
 * 
 * For Feishu:
 * - thread context: use thread_id (preferred for topic discussions)
 * - chat context: use chat_id (fallback for P2P or flat group chat)
 */
@Value
public class ImContextRef {
    
    /** Platform identifier: "feishu", "discord", "slack", etc. */
    String platform;
    
    /** Context type: "thread", "chat", "channel", etc. */
    String contextType;
    
    /** Platform-specific unique identifier */
    String contextId;
    
    /**
     * Create a Feishu thread context reference.
     * 
     * @param threadId the Feishu thread/topic ID
     * @return ImContextRef for the thread
     */
    public static ImContextRef feishuThread(String threadId) {
        return new ImContextRef("feishu", "thread", threadId);
    }
    
    /**
     * Create a Feishu chat context reference.
     * 
     * @param chatId the Feishu chat ID
     * @return ImContextRef for the chat
     */
    public static ImContextRef feishuChat(String chatId) {
        return new ImContextRef("feishu", "chat", chatId);
    }
    
    /**
     * Generate unique storage key for this context.
     * Format: platform:contextType:contextId
     * 
     * @return unique storage key
     */
    public String toStorageKey() {
        return platform + ":" + contextType + ":" + contextId;
    }
    
    /**
     * Parse from storage key.
     * 
     * @param storageKey the storage key in format "platform:contextType:contextId"
     * @return ImContextRef instance
     * @throws IllegalArgumentException if key format is invalid
     */
    public static ImContextRef fromStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isEmpty()) {
            throw new IllegalArgumentException("Storage key cannot be null or empty");
        }
        
        String[] parts = storageKey.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid storage key format: " + storageKey);
        }
        
        return new ImContextRef(parts[0], parts[1], parts[2]);
    }
    
    /**
     * Check if this is a Feishu context.
     * 
     * @return true if platform is "feishu"
     */
    public boolean isFeishu() {
        return "feishu".equals(platform);
    }
    
    /**
     * Check if this is a thread/topic context.
     * 
     * @return true if contextType is "thread"
     */
    public boolean isThread() {
        return "thread".equals(contextType);
    }
    
    /**
     * Check if this is a chat context.
     * 
     * @return true if contextType is "chat"
     */
    public boolean isChat() {
        return "chat".equals(contextType);
    }
}
