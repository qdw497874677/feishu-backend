# Feishu Bot 经验管理

## 使用方法

本项目使用 experience-manager skill 来系统地记录和检索开发经验。

### 查询经验

```bash
# 列出所有经验
cat .experience/experiences.json | jq '.experiences[] | {id, title, category, status}'

# 按类别过滤
cat .experience/experiences.json | jq '.experiences[] | select(.category == "安全")'

# 按标签过滤
cat .experience/experiences.json | jq '.experiences[] | select(.tags | index("webhook"))'
```

### 添加新经验

编辑 `.experience/experiences.json` 文件，按照以下格式添加新经验：

```json
{
  "experiences": [
    {
      "id": "exp_YYYYMMDD_HHMMSS",
      "title": "经验标题",
      "content": "详细的问题描述和解决方案",
      "category": "分类名称",
      "tags": ["标签1", "标签2"],
      "context": "上下文描述",
      "scope": "项目经验",
      "source": "codereview|debugging|development|user-feedback|主动总结",
      "status": "已解决|临时方案|待解决|持续观察",
      "resolution_level": "根本解决|临时绕过|部分解决|未解决",
      "reproducibility": "复现|偶现|环境特定|数据相关",
      "created_at": "2026-01-17T17:00:00.000Z",
      "updated_at": "2026-01-17T17:00:00.000Z"
    }
  ],
  "categories": ["分类1", "分类2"]
}
```

## 已记录的经验（P0 改进）

| ID | 标题 | 类别 | 状态 |
|----|------|------|------|
| exp_20260117_170001 | 飞书 Webhook HMAC-SHA256 签名验证实现 | 安全 | 已解决 |
| exp_20260117_170002 | COLA 架构依赖倒置优化 | 架构 | 已解决 |
| exp_20260117_170003 | 单元测试框架搭建与测试实现 | 代码质量 | 已解决 |
| exp_20260117_170004 | 飞书 SDK 集成与最佳实践 | 第三方集成 | 已解决 |

## 经验分类

### 安全
- Webhook 签名验证
- HMAC-SHA256 算法实现
- 时间戳验证
- Nonce 处理

### 架构
- COLA 分层原则
- 依赖倒置
- 领域服务设计
- Gateway 模式

### 代码质量
- 单元测试
- 测试覆盖率
- 测试框架配置
- 代码规范

### 第三方集成
- 飞书 SDK 集成
- API 调用最佳实践
- 错误处理

## 关键要点

### Webhook 安全
- ✅ 使用 HMAC-SHA256 算法
- ✅ 5 分钟时间窗口验证
- ✅ Nonce 随机值防止重放攻击
- ✅ Base64 编码签名结果

### COLA 架构
- ✅ 遵循分层原则：Adapter → App → Domain → Infrastructure
- ✅ 依赖倒置：Domain 定义接口，Infrastructure 实现
- ✅ 领域服务：封装业务逻辑
- ✅ 扩展点：支持插件化

### 单元测试
- ✅ 使用 JUnit 5 + Mockito + AssertJ
- ✅ @DisplayName 提供清晰描述
- ✅ @ExtendWith(MockitoExtension.class) 简化 Mock
- ✅ AssertJ 流式 API 提高可读性
- ✅ 覆盖率目标：> 80%

---

**文档版本**: 1.0
**更新时间**: 2026-01-17
