package com.qdw.feishu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 飞书机器人应用启动类
 * 
 * COLA 架构设计：
 * - Adapter: 接口适配层，处理 Webhook、HTTP 请求
 * - App: 应用层，编排用例，参数校验
 * - Domain: 领域层，核心业务逻辑，实体
 * - Infrastructure: 基础设施层，外部服务集成，数据访问
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.qdw.feishu",
        "com.alibaba.cola",
        "com.qdw.feishu.domain"
    }
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("""

╔════════════════════════════════════════╗
║                                               ║
║   🤖 Feishu Bot Backend Started!               ║
║   COLA Architecture - JDK 17 - Spring Boot 3.x  ║
║                                               ║
╚════════════════════════════════════════╝

        """);
    }
}
