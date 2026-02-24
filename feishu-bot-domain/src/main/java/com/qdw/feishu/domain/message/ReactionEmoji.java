package com.qdw.feishu.domain.message;

/**
 * 飞书消息表情类型枚举
 * 
 * 用于消息反应（Reaction）功能
 */
public enum ReactionEmoji {
    
    /** 点赞（默认反应） */
    THUMBSUP("THUMBSUP"),
    
    /** 爱心（任务开始） */
    HEART("HEART"),
    
    /** 鼓掌（任务完成） */
    CLAP("CLAP");
    
    private final String emojiType;
    
    ReactionEmoji(String emojiType) {
        this.emojiType = emojiType;
    }
    
    public String getEmojiType() {
        return emojiType;
    }
}
