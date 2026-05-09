package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.opencode.ProjectInfo;
import com.qdw.feishu.domain.opencode.SessionInfo;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import com.qdw.feishu.infrastructure.gateway.opencode.ChatApi;
import com.qdw.feishu.infrastructure.gateway.opencode.HealthApi;
import com.qdw.feishu.infrastructure.gateway.opencode.OpenCodeHttpHelper;
import com.qdw.feishu.infrastructure.gateway.opencode.ProjectApi;
import com.qdw.feishu.infrastructure.gateway.opencode.SessionApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * OpenCode Gateway 实现（Facade）
 *
 * 薄层门面，委托给 API 资源子类处理。
 * 子类按职责划分：SessionApi、ProjectApi、ChatApi、HealthApi。
 */
@Slf4j
@Component
public class OpenCodeGatewayImpl implements OpenCodeGateway {

    private final SessionApi sessionApi;
    private final ProjectApi projectApi;
    private final ChatApi chatApi;
    private final HealthApi healthApi;

    @Autowired
    public OpenCodeGatewayImpl(OpenCodeProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
                .build();

        OpenCodeHttpHelper httpHelper = new OpenCodeHttpHelper(properties, httpClient);

        this.projectApi = new ProjectApi(httpHelper);
        this.sessionApi = new SessionApi(httpHelper, projectApi);
        this.chatApi = new ChatApi(httpHelper);
        this.healthApi = new HealthApi(httpHelper);

        log.info("OpenCode Gateway 初始化完成，服务端: {}", properties.getServerUrl());
    }

    /** 用于测试的构造函数，注入子类实例 */
    public OpenCodeGatewayImpl(SessionApi sessionApi, ProjectApi projectApi,
                                ChatApi chatApi, HealthApi healthApi) {
        this.sessionApi = sessionApi;
        this.projectApi = projectApi;
        this.chatApi = chatApi;
        this.healthApi = healthApi;
    }

    @Override
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception {
        return chatApi.executeCommand(prompt, sessionId, timeoutSeconds);
    }

    @Override
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds, String directory) throws Exception {
        return chatApi.executeCommand(prompt, sessionId, timeoutSeconds, directory);
    }

    @Override
    public String createSession() throws Exception {
        return sessionApi.createSession();
    }

    @Override
    public String createSession(String initialDirectory) throws Exception {
        return sessionApi.createSession(initialDirectory);
    }

    @Override
    public String listSessions() {
        return sessionApi.listSessions();
    }

    @Override
    public String listRecentSessions(String project, int limit) {
        return sessionApi.listRecentSessions(project, limit);
    }

    @Override
    public List<SessionInfo> listRecentSessionsStructured(String project, int limit) {
        return sessionApi.listRecentSessionsStructured(project, limit);
    }

    @Override
    public String listProjects() {
        return projectApi.listProjects();
    }

    @Override
    public List<ProjectInfo> listProjectsStructured() {
        return projectApi.listProjectsStructured();
    }

    @Override
    public String listCommands() {
        return projectApi.listCommands();
    }

    @Override
    public String getServerStatus() {
        return healthApi.getServerStatus();
    }

    /**
     * 检查服务健康状态（公开方法供外部使用）
     */
    public boolean isServerHealthy() {
        return healthApi.isServerHealthy();
    }
}
