# OpenCode 卡片功能实施计划 v2.0

> **创建日期**: 2026-02-15
> **分支**: card-implementation-clean
> **目标**: 在飞书机器人中集成卡片消息功能

---

## 📊 当前状态评估

### ✅ 好消息
- 代码已在干净分支 `card-implementation-clean`
- 所有之前的修改已清除
- 基础功能（文本模式）正常工作

### ⚠️ 发现的问题
1. **项目存在预先的编译错误**（与卡片功能无关）
   - Lombok 注解处理器未正确配置
   - 多个类缺少 `log` 变量
   - 估计错误数：数百个

2. **环境影响**
   - Git worktree 环境可能导致编译器问题
   - Maven 依赖可能不完整

---

## 🎯 新的实施策略

### 策略原则
1. **避免修改Lombok相关代码** - 绕过而不是修复
2. **增量添加** - 每次只添加1-2个文件
3. **手动测试优先** - 不完全依赖编译通过
4. **使用现有结构** - 最小化改动

### 技术方案调整

#### 方案A：最小化方案（推荐）
**核心思路**：不编译整个项目，只添加必要的卡片类，使用反射或动态加载

**优点**：
- 避免大规模编译错误
- 快速验证卡片功能
- 风险最低

**缺点**：
- 需要运行时测试
- IDE 可能不提示语法错误

**实施步骤**：
1. 创建 `domain/card/` 包，添加3个核心类
2. 创建 `infrastructure/card/` 实现包
3. 在 `OpenCodeApp` 中使用卡片（运行时集成）
4. 手动复制编译好的class文件到target目录

#### 方案B：快速修复方案
**核心思路**：手动修复关键的Lombok问题，然后正常编译

**优点**：
- 完整的编译支持
- IDE 正常工作
- 代码质量高

**缺点**：
- 需要修复数十个文件
- 耗时较长（30-60分钟）

**实施步骤**：
1. 修复Lombok配置或手动添加log字段
2. 修复SendResult构造函数
3. 修复Message类的@Data问题
4. 然后按原计划添加卡片功能

#### 方案C：独立模块方案
**核心思路**：创建独立的card-module，单独编译

**优点**：
- 完全隔离
- 不影响现有代码
- 易于测试

**缺点**：
- 需要重新组织项目结构
- Maven配置复杂

---

## 📝 推荐计划：方案A（最小化方案）

### Phase 1: 准备阶段（10分钟）

#### 1.1 验证基础功能
```bash
# 确认当前应用可以运行
ps aux | grep feishu
# 测试文本模式
# 发送: /opencode help
```

#### 1.2 创建卡片包结构
```bash
mkdir -p feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card
mkdir -p feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card
```

### Phase 2: 核心卡片类（15分钟）

#### 2.1 创建CardElement.java
**位置**: `domain/card/CardElement.java`
**内容**: 卡片元素基础类
**不使用Lombok** - 手动写getter/setter

#### 2.2 创建CardBuilder.java
**位置**: `domain/card/CardBuilder.java`
**内容**: 卡片构建器
**不使用Lombok** - 手动写方法

#### 2.3 创建OpenCodeCardTemplate.java
**位置**: `domain/card/OpenCodeCardTemplate.java`
**内容**: OpenCode专用卡片模板
**使用标准SLF4J** - 手动创建Logger

### Phase 3: Gateway扩展（10分钟）

#### 3.1 修改FeishuGateway接口
**添加方法**:
```java
boolean isCardContent(String content);
SendResult sendCard(String receiveOpenId, String cardContent);
```

#### 3.2 修改FeishuGatewayImpl实现
**关键**: 避免修改Lombok相关的类

### Phase 4: OpenCode集成（10分钟）

#### 4.1 创建OpenCodeCardService
**位置**: `domain/opencode/OpenCodeCardService.java`
**策略**: 使用手动Logger，避免@Slf4j

#### 4.2 修改OpenCodeApp（可选）
**如果编译允许**: 集成卡片服务
**如果编译失败**: 使用反射或配置类集成

### Phase 5: 测试验证（15分钟）

#### 5.1 编译验证
```bash
# 只编译新添加的类
javac -cp "target/classes:..." domain/card/CardElement.java
```

#### 5.2 运行时测试
```bash
# 重启应用
./start-feishu.sh

# 在飞书中测试
/opencode projects  # 应该返回项目列表卡片
```

#### 5.3 回退测试
如果卡片发送失败，验证文本模式仍然工作

---

## 🔧 应急预案

### 如果方案A也失败

#### Plan B-1: 使用main分支
```bash
git checkout main
# 在main分支尝试卡片功能
```

#### Plan B-2: 创建独立演示项目
```bash
# 创建一个新的简单项目
# 只包含卡片功能演示
# 不依赖现有feishu-bot代码
```

#### Plan B-3: 延迟实现
- 记录当前问题和尝试的方案
- 等待环境修复后再实施
- 先完善文档和测试用例

---

## 📊 成功标准

### 最小成功标准（MVP）
1. ✅ 能够创建CardBuilder并生成JSON
2. ✅ 能够通过FeishuGateway发送卡片
3. ✅ 至少1个OpenCode命令返回卡片（如 /opencode help）

### 完整成功标准
1. ✅ 所有管理命令支持卡片模式
2. ✅ 卡片与文本模式可配置切换
3. ✅ 单元测试覆盖
4. ✅ 文档完善

---

## ⏱️ 时间估算

| 阶段 | 方案A（最小化） | 方案B（修复） | 方案C（独立模块） |
|------|----------------|--------------|------------------|
| 准备 | 10分钟 | 30分钟 | 45分钟 |
| 实施 | 30分钟 | 60分钟 | 90分钟 |
| 测试 | 20分钟 | 30分钟 | 30分钟 |
| **总计** | **60分钟** | **120分钟** | **165分钟** |

---

## 🎯 下一步行动

请选择实施方案：

**A. 方案A - 最小化方案**（推荐）
- 我立即开始实施
- 预计1小时内完成
- 风险最低

**B. 方案B - 修复Lombok**
- 我先修复编译错误
- 然后按原计划实施
- 预计2小时

**C. 方案C - 独立模块**
- 创建独立的card模块
- 完全隔离实施
- 预计2.5小时

**D. 暂停实施**
- 记录当前状态和问题
- 等待更好的时机
- 先做其他工作

请回复 A, B, C 或 D
