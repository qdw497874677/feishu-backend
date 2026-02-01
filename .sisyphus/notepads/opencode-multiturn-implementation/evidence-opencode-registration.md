# OpenCode Application Registration Evidence

**Captured**: 2026-02-01 09:10
**Source**: /tmp/feishu-manual-start.log

## OpenCode Gateway Initialization

```
2026-02-01 09:10:31.202 [main] INFO  c.q.f.i.gateway.OpenCodeGatewayImpl - OpenCode Gateway 初始化完成，可执行文件: opencode
```

## Application Registration

```
2026-02-01 09:10:31.349 [main] INFO  c.qdw.feishu.domain.app.AppRegistry - 检测到的应用类数量: 5
2026-02-01 09:10:31.350 [main] INFO  c.qdw.feishu.domain.app.AppRegistry -   - 帮助信息 (help)
2026-02-01 09:10:31.350 [main] INFO  c.qdw.feishu.domain.app.AppRegistry -   - 时间查询 (time)
2026-02-01 09:10:31.350 [main] INFO  c.qdw.feishu.domain.app.AppRegistry -   - 命令执行 (bash)
2026-02-01 09:10:31.350 [main] INFO  c.qdw.feishu.domain.app.AppRegistry -   - 历史查询 (history)
2026-02-01 09:10:31.350 [main] INFO  c.qdw.feishu.domain.app.AppRegistry -   - OpenCode 助手 (opencode)
2026-02-01 09:10:31.351 [main] INFO  c.qdw.feishu.domain.app.AppRegistry - 应用注册完成，共注册 5 个应用: [help, opencode, bash, history, time]
```

## Verification

✅ **OpenCode Gateway initialized successfully**
✅ **Executable found: opencode**
✅ **Application registered: opencode**
✅ **Total applications: 5**
✅ **AppRegistry initialization complete**

## Related Configuration

From `application.yml`:
```yaml
opencode:
  executable-path: /usr/bin/opencode
  default-timeout: 30
  max-output-length: 2000
  async-enabled: true
```

## Status

**Evidence Type**: Startup Log Verification
**Verification Method**: Automated log parsing
**Result**: ✅ PASS
