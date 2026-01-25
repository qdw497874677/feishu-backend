package com.qdw.feishu.domain.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMapping {

    private String topicId;
    private String appId;
    private Instant createdAt;
    private Instant lastActiveAt;
}
