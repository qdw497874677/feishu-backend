package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
import com.qdw.feishu.domain.model.TopicMetadata;
import com.qdw.feishu.domain.model.opencode.OpenCodeMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

/**
 * OpenCode 会话管理实现（基于 TopicMapping.metadata）
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "opencode.session.storage",
    havingValue = "topic-mapping",
    matchIfMissing = true
)
public class OpenCodeSessionGatewayImpl implements OpenCodeSessionGateway {

    private final TopicMappingGateway topicMappingGateway;

    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_LAST_COMMAND = "lastCommand";
    private static final String KEY_COMMAND_COUNT = "commandCount";
    private static final String KEY_SESSION_CREATED = "sessionCreatedAt";
    private static final String KEY_LAST_ACTIVE = "lastActiveAt";
    private static final String KEY_EXPLICITLY_INITIALIZED = "explicitlyInitialized";

    public OpenCodeSessionGatewayImpl(TopicMappingGateway topicMappingGateway) {
        this.topicMappingGateway = topicMappingGateway;
    }

    @Override
    public void saveSession(String topicId, String sessionId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            log.warn("话题映射不存在，无法保存会话: topicId={}", topicId);
            return;
        }

        TopicMapping mapping = mappingOpt.get();

        // 使用 TopicMetadata 工具类修改 metadata
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.set(KEY_SESSION_ID, sessionId);
        metadata.set(KEY_LAST_ACTIVE, System.currentTimeMillis());

        // 保存修改
        topicMappingGateway.save(metadata.save());

        log.info("保存会话: topicId={}, sessionId={}", topicId, sessionId);
    }

    @Override
    public Optional<String> getSessionId(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);

        return metadata.getString(KEY_SESSION_ID);
    }

    @Override
    public void updateSession(String topicId, String sessionId) {
        saveSession(topicId, sessionId);
    }

    @Override
    public void deleteSession(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.remove(KEY_SESSION_ID);

        topicMappingGateway.save(metadata.save());

        log.info("删除会话: topicId={}", topicId);
    }

    @Override
    public void clearSession(String topicId) {
        deleteSession(topicId);
    }

    @Override
    public Optional<OpenCodeMetadata> getMetadata(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);

        // 从 metadata 中提取所有字段
        OpenCodeMetadata result = new OpenCodeMetadata();

        metadata.getString(KEY_SESSION_ID).ifPresent(result::setSessionId);
        metadata.getString(KEY_LAST_COMMAND).ifPresent(result::setLastCommand);
        metadata.getInt(KEY_COMMAND_COUNT).ifPresentOrElse(
            result::setCommandCount,
            () -> result.setCommandCount(0)
        );
        metadata.getLong(KEY_SESSION_CREATED).ifPresentOrElse(
            result::setSessionCreatedAt,
            () -> result.setSessionCreatedAt(System.currentTimeMillis())
        );
        metadata.getLong(KEY_LAST_ACTIVE).ifPresentOrElse(
            result::setLastActiveAt,
            () -> result.setLastActiveAt(System.currentTimeMillis())
        );

        return Optional.of(result);
    }

    @Override
    public void saveMetadata(String topicId, OpenCodeMetadata metadata) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            log.warn("话题映射不存在，无法保存元数据: topicId={}", topicId);
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata topicMetadata = TopicMetadata.of(mapping);

        // 保存所有字段
        topicMetadata.set(KEY_SESSION_ID, metadata.getSessionId());
        topicMetadata.set(KEY_LAST_COMMAND, metadata.getLastCommand());
        topicMetadata.set(KEY_COMMAND_COUNT, metadata.getCommandCount());
        topicMetadata.set(KEY_SESSION_CREATED, metadata.getSessionCreatedAt());
        topicMetadata.set(KEY_LAST_ACTIVE, metadata.getLastActiveAt());

        topicMappingGateway.save(topicMetadata.save());

        log.info("保存元数据: topicId={}, metadata={}", topicId, metadata);
    }

    @Override
    public boolean isExplicitlyInitialized(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return false;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);

        return metadata.getBoolean(KEY_EXPLICITLY_INITIALIZED).orElse(false);
    }

    @Override
    public void setExplicitlyInitialized(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            log.warn("话题映射不存在，无法设置显式初始化: topicId={}", topicId);
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.set(KEY_EXPLICITLY_INITIALIZED, "true");

        topicMappingGateway.save(metadata.save());

        log.info("设置话题为已显式初始化: topicId={}", topicId);
    }

    @Override
    public void clearExplicitlyInitialized(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.remove(KEY_EXPLICITLY_INITIALIZED);

        topicMappingGateway.save(metadata.save());

        log.info("清除话题的显式初始化状态: topicId={}", topicId);
    }
}
