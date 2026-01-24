# 飞书机器人开发经验

本目录包含飞书机器人长连接实现过程中的经验沉淀。

## 经验文档

- **[飞书机器人长连接实现经验](./feishu-long-connection-implementation.md)**

    涵盖内容：
    - SDK 依赖和版本问题
    - 消息格式处理
    - Spring Bean 注册
    - 配置属性管理
    - 条件化 Bean
    - 字符编码处理
    - 接口签名匹配
    - 长连接 WebSocket 集成
    - 常见错误及解决方案
    - 调试技巧

---

## 快速参考

### 问题快速查找

| 问题类型 | 文档位置 |
|---------|---------|
| SDK 依赖问题 | 长连接实现经验.md #1 |
| 消息格式错误 | 长连接实现经验.md #2 |
| Bean 注册失败 | 长连接实现经验.md #3 |
| 编码问题 | 长连接实现经验.md #6 |

### 关键命令

```bash
# 查看经验文档
cat /root/workspace/feishu-backend/experience/feishu-long-connection-implementation.md

# 搜索特定问题
grep "错误1" /root/workspace/feishu-backend/experience/feishu-long-connection-implementation.md
```

---

## 贡献指南

当你在开发过程中遇到新问题时，请：

1. 记录问题和解决方案
2. 添加到相应的经验文档中
3. 更新本 README.md 的快速参考表格
4. 遵循现有的文档格式

---

## 相关资源

- [飞书 IM SDK 文档](https://open.feishu.cn/document/serverSdk/im sdk)
- [飞书 WebSocket 文档](https://open.feishu.cn/document/serverSdk/event-sdk)
- [COLA 框架](https://github.com/alibaba/COLA)
- [飞书 SDK GitHub](https://github.com/larksuite/oapi-sdk-java)
