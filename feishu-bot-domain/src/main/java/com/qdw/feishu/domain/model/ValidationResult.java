package com.qdw.feishu.domain.model;

/**
 * 命令验证结果
 */
public class ValidationResult {

    private final boolean allowed;
    private final String message;

    private ValidationResult(boolean allowed, String message) {
        this.allowed = allowed;
        this.message = message;
    }

    /**
     * 创建允许的验证结果
     * 
     * @return 验证结果实例
     */
    public static ValidationResult allowed() {
        return new ValidationResult(true, null);
    }

    /**
     * 创建受限的验证结果
     * 
     * @param message 限制消息
     * @return 验证结果实例
     */
    public static ValidationResult restricted(String message) {
        return new ValidationResult(false, message);
    }

    /**
     * 检查是否允许
     * 
     * @return 如果允许返回 true，否则返回 false
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 获取消息
     * 
     * @return 消息内容，如果允许则为 null
     */
    public String getMessage() {
        return message;
    }
}
