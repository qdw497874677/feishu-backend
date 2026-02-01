# 话题映射持久化 - SQLite 版本

## 📊 概述

话题映射持久化功能支持将飞书话题与应用的映射关系保存到 SQLite 数据库，数据库文件可以加入 Git 版本控制，实现跨环境共享话题映射数据。

---

## 🎯 特性

- ✅ **轻量级数据库**：使用 SQLite，无需额外部署数据库服务
- ✅ **版本控制友好**：数据库文件可以提交到 Git，方便团队共享
- ✅ **自动初始化**：首次启动自动创建表结构和索引
- ✅ **双存储模式**：支持 SQLite（默认）和 JSON 文件两种存储方式

---

## 📁 数据库文件位置

**默认路径**：`data/feishu-topic-mappings.db`

**配置方式**：

```yaml
# application.yml
feishu:
  topic-mapping:
    sqlite:
      path: data/feishu-topic-mappings.db  # 可自定义路径
```

**相对路径示例**：
- `data/feishu-topic-mappings.db` → 项目根目录下的 `data/` 文件夹
- `./mappings.db` → 项目根目录
- `/tmp/feishu.db` → 绝对路径（不推荐，无法加入 Git）

---

## 🗄️ 数据库结构

### 表：topic_mapping

| 字段 | 类型 | 说明 |
|------|------|------|
| topic_id | TEXT (PK) | 飞书话题 ID |
| app_id | TEXT | 应用 ID（如 bash, time, help） |
| created_at | INTEGER | 创建时间（毫秒时间戳） |
| last_active_at | INTEGER | 最后活跃时间（毫秒时间戳） |

### 索引

- **PRIMARY KEY**：topic_id
- **INDEX**：app_id（用于按应用查询）

---

## 🔄 存储模式切换

### 模式 1：SQLite（默认，推荐）

```yaml
feishu:
  topic-mapping:
    storage-type: sqlite
    sqlite:
      path: data/feishu-topic-mappings.db
```

**优势**：
- ✅ 支持版本控制
- ✅ 结构化查询
- ✅ 事务支持
- ✅ 跨平台兼容

### 模式 2：JSON 文件（兼容模式）

```yaml
feishu:
  topic-mapping:
    storage-type: file
```

**存储位置**：`/tmp/feishu-topic-mappings.json`

**适用场景**：
- 快速测试
- 临时数据存储

---

## 📤 将数据库推送到 Git

### 方式 1：默认自动包含（推荐）

SQLite 数据库文件（`*.db`）默认不会被 `.gitignore` 排除，可以直接提交：

```bash
# 添加数据库文件到 Git
git add data/feishu-topic-mappings.db

# 提交
git commit -m "feat: add topic mappings database"

# 推送
git push
```

### 方式 2：排除数据库文件

如果不想将数据库文件加入 Git（例如包含敏感数据），修改 `.gitignore`：

```gitignore
# SQLite Database
*.db
data/*.db
```

---

## 🛠️ 数据库管理

### 查看数据库内容

**使用 SQLite 命令行工具**：

```bash
# 安装 SQLite
sudo apt-get install sqlite3  # Ubuntu/Debian
brew install sqlite3           # macOS

# 查询数据
sqlite3 data/feishu-topic-mappings.db "SELECT * FROM topic_mapping;"

# 查看表结构
sqlite3 data/feishu-topic-mappings.db ".schema topic_mapping"

# 统计话题数量
sqlite3 data/feishu-topic-mappings.db "SELECT COUNT(*) FROM topic_mapping;"

# 按应用分组统计
sqlite3 data/feishu-topic-mappings.db "SELECT app_id, COUNT(*) as count FROM topic_mapping GROUP BY app_id;"
```

**使用 GUI 工具**（推荐）：
- **DB Browser for SQLite**：https://sqlitebrowser.org/
- **DBeaver**：https://dbeaver.io/

### 数据库迁移

**导出数据**：

```bash
# 导出为 SQL
sqlite3 data/feishu-topic-mappings.db .dump > backup.sql

# 导出为 CSV
sqlite3 data/feishu-topic-mappings.db <<EOF
.mode csv
.headers on
.output topic_mapping.csv
SELECT * FROM topic_mapping;
.quit
EOF
```

**导入数据**：

```bash
# 从 SQL 导入
sqlite3 data/feishu-topic-mappings.db < backup.sql

# 从 CSV 导入
sqlite3 data/feishu-topic-mappings.db <<EOF
.mode csv
.import topic_mapping.csv topic_mapping
.quit
EOF
```

---

## 🔧 开发指南

### 添加新的查询方法

编辑 `TopicMappingSqliteGateway.java`：

```java
public List<TopicMapping> findByAppId(String appId) {
    String sql = "SELECT topic_id, app_id, created_at, last_active_at FROM topic_mapping WHERE app_id = ?";

    return jdbcTemplate.query(sql, (rs, rowNum) -> new TopicMapping(
            rs.getString("topic_id"),
            rs.getString("app_id")
    ), appId);
}

public List<TopicMapping> findActiveTopics(long since) {
    String sql = "SELECT topic_id, app_id, created_at, last_active_at FROM topic_mapping WHERE last_active_at > ?";

    return jdbcTemplate.query(sql, (rs, rowNum) -> new TopicMapping(
            rs.getString("topic_id"),
            rs.getString("app_id")
    ), since);
}
```

### 数据库版本升级

**方式 1：创建新表**

```java
private void createV2Table() {
    String sql = """
        CREATE TABLE IF NOT EXISTS topic_mapping_v2 (
            topic_id TEXT PRIMARY KEY NOT NULL,
            app_id TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_active_at INTEGER NOT NULL,
            metadata TEXT  -- 新增字段
        )
    """;

    jdbcTemplate.execute(sql);
}
```

**方式 2：ALTER TABLE**

```java
private void addMetadataColumn() {
    String sql = "ALTER TABLE topic_mapping ADD COLUMN metadata TEXT";

    try {
        jdbcTemplate.execute(sql);
        log.info("成功添加 metadata 字段");
    } catch (Exception e) {
        log.debug("metadata 字段可能已存在: {}", e.getMessage());
    }
}
```

---

## 🚀 生产环境部署

### 推荐配置

```yaml
# application-prod.yml
feishu:
  topic-mapping:
    storage-type: sqlite
    sqlite:
      path: /var/lib/feishu-bot/mappings.db  # 生产环境路径
```

### 数据持久化策略

| 环境 | 数据库路径 | 是否加入 Git | 说明 |
|------|-----------|-------------|------|
| **开发环境** | `data/mappings.db` | ✅ 是 | 团队共享测试数据 |
| **测试环境** | `data/test-mappings.db` | ❌ 否 | 独立测试数据 |
| **生产环境** | `/var/lib/feishu-bot/mappings.db` | ❌ 否 | 定期备份 |

---

## 🐛 故障排查

### 问题 1：数据库文件被锁定

**错误信息**：
```
database is locked
```

**解决方案**：
```bash
# 检查是否有其他进程占用
lsof data/feishu-topic-mappings.db

# 停止应用后重新启动
```

### 问题 2：表不存在

**错误信息**：
```
no such table: topic_mapping
```

**解决方案**：
- 检查 `TopicMappingSqliteGateway` 的 `@PostConstruct` 方法是否执行
- 查看启动日志中的错误信息

### 问题 3：权限不足

**错误信息**：
```
unable to open database file
```

**解决方案**：
```bash
# 创建数据库目录
mkdir -p data

# 设置权限
chmod 755 data
```

---

## 📚 参考资料

- [SQLite 官方文档](https://www.sqlite.org/docs.html)
- [Spring JDBC 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html)
- [COLA 架构 - Gateway 模式](https://github.com/alibaba/COLA)

---

**最后更新**: 2026-02-01
