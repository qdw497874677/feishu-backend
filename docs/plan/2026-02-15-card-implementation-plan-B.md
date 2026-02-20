# OpenCode 卡片功能实施计划 - 方案B

> **创建日期**: 2026-02-15
> **分支**: card-implementation-clean
> **方案**: B - 修复Lombok后正常实施
> **预计时间**: 120分钟

---

## 📋 方案概述

### 核心策略
**先修复Lombok配置和所有编译错误，然后在正常的开发环境中实施卡片功能。**

### 为什么选择方案B
1. **代码质量高** - 编译器正常工作，IDE有完整提示
2. **便于调试** - 可以在开发时就发现问题
3. **可持续** - 修复基础问题后，后续开发更顺利
4. **文档价值** - 为团队留下Lombok配置的经验

---

## 📊 阶段规划

### Phase 0: 环境诊断 (15分钟)

#### 0.1 确认Lombok问题范围
```bash
# 统计所有Lombok相关错误
mvn clean compile 2>&1 | grep "cannot find symbol" | wc -l

# 查看具体错误
mvn clean compile 2>&1 | grep "variable log" | head -10
```

#### 0.2 检查Maven配置
```bash
# 检查pom.xml中的Lombok配置
grep -A3 "lombok" pom.xml

# 检查IDEA/Maven设置
ls -la ~/.m2/repository/org/projectlombok/lombok/
```

#### 0.3 尝试简单修复
选项1: 修复pom.xml添加annotationProcessorPaths
选项2: 直接手动修复所有Lombok类（更快但工作量大）

**决策点**: 如果pom.xml修复有效，继续；否则转到手动修复

---

### Phase 1: Lombok修复 (45分钟)

#### 1.1 修复pom.xml (5分钟)
在feishu-bot-domain/pom.xml中添加：
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>17</source>
                <target>17</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### 1.2 测试pom修复 (5分钟)
```bash
mvn clean compile -pl feishu-bot-domain -DskipTests
# 如果成功，跳转到Phase 2
# 如果失败，继续1.3
```

#### 1.3 手动修复所有Lombok类 (35分钟)
如果pom修复无效，手动修复：

**批量替换脚本**:
```bash
# 1. 查找所有@Slf4j类
find feishu-bot-domain -name "*.java" -exec grep -l "@Slf4j" {} \; > /tmp/slf4j-classes.txt

# 2. 为每个类添加手动Logger
while read file; do
    # 添加import
    sed -i '1a import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;' "$file"
    # 删除@Slf4j
    sed -i '/@Slf4j/d' "$file"
    # 获取类名
    classname=$(grep "public class" "$file" | awk '{print $3}')
    # 添加log字段
    sed -i "/public class $classname/a\\
    private static final Logger log = LoggerFactory.getLogger($classname.class);" "$file"
done < /tmp/slf4j-classes.txt
```

**手动修复SendResult.java**:
```java
// 移除@AllArgsConstructor
// 添加手动构造函数
public SendResult() {}
public SendResult(boolean success, String messageId, String errorMessage, String threadId) {
    this.success = success;
    this.messageId = messageId;
    this.errorMessage = errorMessage;
    this.threadId = threadId;
}
```

#### 1.4 验证修复 (5分钟)
```bash
mvn clean compile -DskipTests
# 期望: BUILD SUCCESS
```

---

### Phase 2: 核心卡片功能实施 (30分钟)

#### 2.1 创建Card包结构
```bash
mkdir -p feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card
mkdir -p feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card
```

#### 2.2 创建CardElement.java (8分钟)
**位置**: `domain/card/CardElement.java`
**内容**:
- 卡片元素基础类
- 文本元素
- 按钮元素
- 使用Lombok注解（现在可以用了！）

#### 2.3 创建CardBuilder.java (8分钟)
**位置**: `domain/card/CardBuilder.java`
**内容**:
- 流式API构建器
- JSON序列化
- 支持各种卡片元素

#### 2.4 创建OpenCodeCardTemplate.java (14分钟)
**位置**: `domain/card/OpenCodeCardTemplate.java`
**功能**:
- buildProjectListCard() - 项目列表
- buildSessionListCard() - 会话列表
- buildBindingCard() - 绑定成功
- buildHelpCard() - 使用指南

---

### Phase 3: Gateway扩展 (20分钟)

#### 3.1 扩展FeishuGateway接口 (5分钟)
```java
// domain/gateway/FeishuGateway.java
boolean isCardContent(String content);
SendResult sendCard(String receiveOpenId, String cardContent);
```

#### 3.2 实现FeishuGatewayImpl (10分钟)
```java
// infrastructure/gateway/FeishuGatewayImpl.java
@Override
public boolean isCardContent(String content) {
    // 检测卡片格式
}

@Override
public SendResult sendCard(String receiveOpenId, String cardContent) {
    // 发送interactive消息
}

// 修改sendMessage()添加智能检测
```

#### 3.3 编译验证 (5分钟)
```bash
mvn clean compile -pl feishu-bot-infrastructure -am -DskipTests
```

---

### Phase 4: OpenCode集成 (20分钟)

#### 4.1 扩展OpenCodeGateway (5分钟)
```java
// domain/gateway/OpenCodeGateway.java
List<String> listProjectsAsList();
List<Map<String, Object>> listSessionsAsList(String project);
```

#### 4.2 实现OpenCodeGatewayImpl (10分钟)
**位置**: `infrastructure/gateway/OpenCodeGatewayImpl.java`
- 实现结构化数据返回
- 不破坏现有文本方法

#### 4.3 创建OpenCodeCardService (5分钟)
**位置**: `domain/opencode/OpenCodeCardService.java`
- 提供卡片发送服务
- 回退到文本模式

---

### Phase 5: 测试和优化 (15分钟)

#### 5.1 完整编译
```bash
mvn clean package -DskipTests
# 确保整个项目编译成功
```

#### 5.2 编写单元测试 (可选，如果时间允许)
- CardBuilder测试
- OpenCodeCardTemplate测试
- FeishuGateway卡片检测测试

#### 5.3 更新文档
- 更新APP_USAGE_GUIDE.md
- 添加卡片使用示例

---

## 🔧 应急预案

### 如果Lombok修复失败
**备用方案**: 手动编写所有getter/setter/log
- 时间增加30分钟
- 代码更冗长但绝对可靠

### 如果编译仍然失败
**检查清单**:
1. Java版本是否正确 (需要17)
2. Maven版本是否太旧
3. 缓存是否清理 `mvn clean`
4. 依赖是否下载完整 `mvn dependency:resolve`

---

## ✅ 成功标准

### Must Have (必须完成)
- [ ] 项目可以成功编译 `mvn clean package -DskipTests`
- [ ] 卡片类可以正常创建和使用
- [ ] FeishuGateway可以发送卡片消息
- [ ] 至少1个OpenCode命令返回卡片

### Nice to Have (最好有)
- [ ] 所有管理命令支持卡片
- [ ] 卡片与文本模式可配置切换
- [ ] 单元测试覆盖
- [ ] 性能测试

---

## 📂 文件清单

### 新增文件
1. `domain/card/CardElement.java`
2. `domain/card/CardBuilder.java`
3. `domain/card/OpenCodeCardTemplate.java`
4. `domain/opencode/OpenCodeCardService.java`

### 修改文件
1. `domain/gateway/FeishuGateway.java` (添加方法)
2. `domain/gateway/OpenCodeGateway.java` (添加方法)
3. `infrastructure/gateway/FeishuGatewayImpl.java` (实现卡片功能)
4. `infrastructure/gateway/OpenCodeGatewayImpl.java` (实现结构化数据)
5. `feishu-bot-domain/pom.xml` (可能修改Lombok配置)

---

## ⏱️ 详细时间规划

| 阶段 | 任务 | 时间 | 依赖 |
|-----|------|------|------|
| Phase 0 | 环境诊断 | 15分钟 | - |
| Phase 1 | Lombok修复 | 45分钟 | Phase 0 |
| Phase 2 | 卡片类创建 | 30分钟 | Phase 1 |
| Phase 3 | Gateway扩展 | 20分钟 | Phase 2 |
| Phase 4 | OpenCode集成 | 20分钟 | Phase 3 |
| Phase 5 | 测试优化 | 15分钟 | Phase 4 |
| **缓冲** | 意外处理 | 15分钟 | - |
| **总计** | | **160分钟** | (含缓冲) |

---

## 🚀 开始条件

当你回复"开始"后，我将按以下顺序执行：

1. **立即执行**: Phase 0 环境诊断
2. **汇报**: 诊断结果和下一步决策
3. **等待确认**: 你确认后继续Phase 1
4. **每阶段汇报**: 每个Phase完成后汇报进展
5. **最终测试**: 完成后提供测试清单

---

## 📝 决策清单

在开始之前，请确认：

- [ ] 你有120-160分钟时间进行开发
- [ ] 你可以在过程中回复确认消息
- [ ] 如果中途需要停止，可以保存当前进度
- [ ] 你愿意测试基础功能是否正常

**请回复**: 
- "**开始**" - 立即开始实施
- "**修改**" - 提出修改意见
- "**暂停**" - 稍后再做

---

*计划创建完成，等待你的回复...*
