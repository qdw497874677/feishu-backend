package com.qdw.feishu.domain.feishu;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.ImContextRef;

/**
 * Resolves Feishu message to IM context reference.
 * 
 * Follows the Feishu context resolution rule:
 * - prefer thread_id (topicId) for topic/thread discussions
 * - fall back to chat_id for P2P or flat group chat
 * 
 * This ensures stable context identification:
 * - thread_id (topicId) is the stable identifier for topic discussions
 * - chat_id is the stable identifier for P2P/flat conversations
 * - root_id/parent_id are message-level, not conversation-level
 * 
 * Note: In our domain model, Feishu's thread_id is stored as Message.topicId
 */
public final class FeishuContextResolver {
    
    private FeishuContextResolver() {
        // Utility class, no instantiation
    }
    
    /**
     * Resolve IM context from Feishu message.
     * 
     * Rule: thread_id (topicId) first, chat_id fallback
     * 
     * @param message the Feishu message
     * @return ImContextRef identifying the conversation context
     * @throws IllegalArgumentException if message has neither topicId nor chatId
     */
    public static ImContextRef resolve(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        // topicId in our model = thread_id in Feishu (topic discussions)
        String topicId = message.getTopicId();
        String chatId = message.getChatId();
        
        // Prefer topicId (thread_id) for topic discussions
        if (topicId != null && !topicId.isEmpty()) {
            return ImContextRef.feishuThread(topicId);
        }
        
        // Fall back to chatId for P2P or flat group chat
        if (chatId != null && !chatId.isEmpty()) {
            return ImContextRef.feishuChat(chatId);
        }
        
        throw new IllegalArgumentException(
            "Message has neither topicId nor chatId - cannot resolve IM context"
        );
    }
    
    /**
     * Try to resolve IM context from Feishu message.
     * 
     * Unlike {@link #resolve(Message)}, this returns null instead of throwing.
     * 
     * @param message the Feishu message
     * @return ImContextRef identifying the conversation context, or null if unavailable
     */
    public static ImContextRef tryResolve(Message message) {
        if (message == null) {
            return null;
        }
        
        String topicId = message.getTopicId();
        String chatId = message.getChatId();
        
        if (topicId != null && !topicId.isEmpty()) {
            return ImContextRef.feishuThread(topicId);
        }
        
        if (chatId != null && !chatId.isEmpty()) {
            return ImContextRef.feishuChat(chatId);
        }
        
        return null;
    }
    
    /**
     * Extract the context ID (topicId or chatId) from a Feishu message.
     * 
     * @param message the Feishu message
     * @return the context ID, or null if unavailable
     */
    public static String extractContextId(Message message) {
        if (message == null) {
            return null;
        }
        
        String topicId = message.getTopicId();
        if (topicId != null && !topicId.isEmpty()) {
            return topicId;
        }
        
        String chatId = message.getChatId();
        if (chatId != null && !chatId.isEmpty()) {
            return chatId;
        }
        
        return null;
    }
    
    /**
     * Check if a message has a resolvable IM context.
     * 
     * @param message the message to check
     * @return true if the message has topicId or chatId
     */
    public static boolean hasContext(Message message) {
        if (message == null) {
            return false;
        }
        
        String topicId = message.getTopicId();
        String chatId = message.getChatId();
        
        return (topicId != null && !topicId.isEmpty()) 
            || (chatId != null && !chatId.isEmpty());
    }
}
