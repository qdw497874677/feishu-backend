package com.qdw.feishu.domain.opencode;

import lombok.Builder;
import lombok.Data;

/**
 * 项目信息值对象（结构化数据，用于卡片渲染）。
 *
 * 由 OpenCodeGateway.listProjectsStructured() 返回，
 * 供 OpenCodeApp 构建项目列表卡片。
 */
@Data
@Builder
public class ProjectInfo {

    /** 项目名称（从路径提取） */
    private final String name;

    /** 项目完整路径 */
    private final String path;

    /** 版本控制系统（如 git），可能为 null */
    private final String vcs;
}
