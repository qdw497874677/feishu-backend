package com.qdw.feishu.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import java.util.Optional;

import java.util.Optional;

/**
 * TopicMapping Metadata 操作工具类
 *
 * 提供类型安全的 metadata 访问接口
 */
@Slf4j
public class TopicMetadata {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TopicMapping mapping;
    private JsonNode metadataNode;

    /**
     * 从 TopicMapping 创建 TopicMetadata
     *
     * @param mapping 话题映射实体
     * @return TopicMetadata 工具实例
     */
    public static TopicMetadata of(TopicMapping mapping) {
        return new TopicMetadata(mapping);
    }

    /**
     * 私有构造函数
     */
    private TopicMetadata(TopicMapping mapping) {
        this.mapping = mapping;
        this.metadataNode = parseMetadata(mapping.getMetadata());
    }

    /**
     * 解析 metadata JSON 字符串
     */
    private JsonNode parseMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(metadata);
        } catch (Exception e) {
            log.warn("Failed to parse metadata: {}", metadata, e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 获取当前应用的命名空间节点
     */
    private ObjectNode getAppNode() {
        String appId = mapping.getAppId();

        if (metadataNode.isObject()) {
            ObjectNode root = (ObjectNode) metadataNode;

            if (!root.has(appId)) {
                root.putObject(appId);
            }

            return (ObjectNode) root.get(appId);
        }

        return objectMapper.createObjectNode();
    }

    /**
     * 设置字符串值
     *
     * @param key 键（在当前应用命名空间下）
     * @param value 值
     * @return this（支持链式调用）
     */
    public TopicMetadata set(String key, String value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置整数值
     *
     * @param key 键
     * @param value 值
     * @return this
     */
    public TopicMetadata set(String key, int value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置长整型值
     *
     * @param key 键
     * @param value 值
     * @return this
     */
    public TopicMetadata set(String key, long value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置布尔值
     *
     * @param key 键
     * @param value 值
     * @return this
     */
    public TopicMetadata set(String key, boolean value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置任意对象（序列化为 JSON）
     *
     * @param key 键
     * @param value 值
     * @return this
     */
    public TopicMetadata set(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            getAppNode().set(key, objectMapper.readTree(json));
            return this;
        } catch (Exception e) {
            log.error("Failed to serialize value for key: {}", key, e);
            return this;
        }
    }

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，如果不存在返回 Optional.empty()
     */
    public Optional<String> getString(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isTextual()) {
            return Optional.of(appNode.get(key).asText());
        }
        return Optional.empty();
    }

    /**
     * 获取整数值
     *
     * @param key 键
     * @return 值，如果不存在返回 Optional.empty()
     */
    public Optional<Integer> getInt(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isInt()) {
            return Optional.of(appNode.get(key).asInt());
        }
        return Optional.empty();
    }

    /**
     * 获取长整型值
     *
     * @param key 键
     * @return 值，如果不存在返回 Optional.empty()
     */
    public Optional<Long> getLong(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isLong()) {
            return Optional.of(appNode.get(key).asLong());
        }
        return Optional.empty();
    }

    /**
     * 获取布尔值
     *
     * @param key 键
     * @return 值，如果不存在返回 Optional.empty()
     */
    public Optional<Boolean> getBoolean(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isBoolean()) {
            return Optional.of(appNode.get(key).asBoolean());
        }
        return Optional.empty();
    }

    /**
     * 获取对象（反序列化）
     *
     * @param key 键
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 对象，如果不存在或解析失败返回 Optional.empty()
     */
    public <T> Optional<T> getObject(String key, Class<T> clazz) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key)) {
            try {
                return Optional.of(objectMapper.treeToValue(appNode.get(key), clazz));
            } catch (Exception e) {
                log.error("Failed to deserialize value for key: {}", key, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 移除键
     *
     * @param key 键
     * @return this
     */
    public TopicMetadata remove(String key) {
        getAppNode().remove(key);
        return this;
    }

    /**
     * 检查键是否存在
     *
     * @param key 键
     * @return 如果键存在返回 true
     */
    public boolean has(String key) {
        return getAppNode().has(key);
    }

    /**
     * 清空当前应用的所有 metadata
     *
     * @return this
     */
    public TopicMetadata clear() {
        getAppNode().removeAll();
        return this;
    }

    /**
     * 将修改保存回 TopicMapping
     *
     * 重要：修改后必须调用此方法，否则不会持久化
     *
     * @return 更新后的 TopicMapping
     */
    public TopicMapping save() {
        try {
            String json = objectMapper.writeValueAsString(metadataNode);
            mapping.setMetadata(json);
            return mapping;
        } catch (Exception e) {
            log.error("Failed to serialize metadata", e);
            return mapping;
        }
    }

    /**
     * 获取原始 metadata JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        return mapping.getMetadata();
    }

    /**
     * 获取底层 JsonNode（高级用法）
     *
     * @return JsonNode
     */
    public JsonNode getJsonNode() {
        return metadataNode;
    }
}
