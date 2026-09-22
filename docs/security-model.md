# 安全模型（已实施基线）

- HTTP Profile 仅在 `/mcp` 接受配置的 Bearer token；未配置 token 时端点故障关闭（503）。token 只能由 `DBA_MCP_API_TOKEN` 注入，不能写入配置文件。
- 浏览器请求带有 `Origin` 时，必须命中 `DBA_MCP_ALLOWED_ORIGINS` 白名单。无 Origin 的非浏览器 MCP 客户端可继续使用认证连接。
- Oracle 工具只允许登记为只读的资产；SQL 以词法分析限制为单条 `SELECT`/`WITH`，并设置超时、行数、单元格及总响应大小上限。
- 常规只读 Oracle 调用账号仅授予 `CREATE SESSION`、`SELECT_CATALOG_ROLE` 以及本项目所需的 `SYS.V_$TEMP_SPACE_HEADER`、`SYS.V_$SESSION`、`SYS.V_$TRANSACTION`、`SYS.V_$DIAG_ALERT_EXT` 直读权限；不得以 `SELECT ANY DICTIONARY` 或 SYSDBA 扩大权限。完整工具到视图的映射见 `tool-contracts.md`。
- `oracle.listAlertLogEvents` 是经批准的敏感信息传递例外。默认只返回 `V$DIAG_ALERT_EXT` 的事件元数据；仅当服务器设置 `DBA_ORACLE_ALERT_LOG_ALLOW_SENSITIVE_MESSAGE_TEXT=true` 时才原样转发 `MESSAGE_TEXT`。该值不可由 MCP 请求控制，且启用后调用方可能收到凭证、业务数据、文件路径或网络拓扑；只能在受控环境、认证传输和访问控制已落实时启用。
- `oracle.unlockUser` 是受控例外而非通用 DDL：默认关闭，只有运行时 `DBA_ORACLE_USER_UNLOCK_ENABLED=true` 和目标资产 `user_unlock_enabled=1` 同时成立才可调用。它仅接受未加引号 Oracle 用户标识符；固定预检角色继承、系统权限和 password-file 管理权限，任何 DBA 类角色、广泛 `ANY` 权限、`ALTER USER` 等管理权限、password-file 管理权限或预检错误都会拒绝操作。
- Oracle 连接串和驱动属性可保存在资产 SQLite。资产管理 REST API 可在通过 HTTPS 的 Basic Auth 保护下写入数据库密码（`connection_properties.password`）；该字段为严格只写，所有 REST/MCP 响应、日志、审计参数和异常消息均不得包含它。令牌、SSH 私钥和钱包密钥仍不得存入 SQLite。
- 含数据库密码的 SQLite 文件必须排除版本控制和镜像，采用最小文件权限、加密（如可用）、运行时主密钥、定期轮换及日志脱敏。
- 当前不提供 SSH 或文件写入入口；规划中的资产库存管理 REST API 是唯一 SQLite 写入入口。
- 资产工具的 `assetId` 是不透明的库存 ID，不能替代主机地址、文件路径或凭证。

## 资产库存管理 API

- 仅 HTTP Profile 注册 `/api/v1/**`；所有该路径的请求必须经 HTTP Basic Auth 且具有 `ROLE_ASSET_ADMIN`。
- `DBA_ASSET_ADMIN_USERNAME` 提供用户名；`DBA_ASSET_ADMIN_PASSWORD_HASH` 提供 BCrypt 密码散列。任一项缺失时，管理 API 不会认证任何用户。
- Basic Auth 必须只经 HTTPS 使用。OpenAPI JSON（`/v3/api-docs`）和 Swagger UI（`/swagger-ui/index.html`）可匿名查看，但不包含密码或凭证引用。
- 资产更新和状态变更要求现有 `version`；不匹配时拒绝更新，避免并发覆盖。

生产接入前，Bearer token 桥接须替换或接入组织的 OIDC/JWT、mTLS 或可信 API Gateway 身份；随后才能进入规划的目标级 RBAC 阶段。
