package com.qdw.feishu.domain.opencode;

import lombok.Builder;
import lombok.Data;

/**
 * 会话信息值对象（结构化数据，用于卡片渲染）。
 *
 * 由 OpenCodeGateway.listRecentSessionsStructured() 返回，
 * 供 OpenCodeCommandHandler 构建会话列表卡片。
 */
@Data
@Builder
public class SessionInfo {

    /** 会话 ID，如 ses_xxx */
    private final String sessionId;

    /** 会话名称/标题 */
    private final String title;

    /** 最后提示词摘要（可能为 null 或空），截断到 50 字符 */
    private final String lastPrompt;

    /** 相对时间，如 "5分钟前"、"2小时前"、"3天前" */
    private final String relativeTime;

    /** 所属项目名称 */
    private final String projectName;

    /** 会话所属项目目录 */
    private final String projectDirectory;
}
