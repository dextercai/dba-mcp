# MCP 工具契约（阶段 1–2）

当前暴露只读资产工具及 Oracle 固定连接/数据字典诊断工具。所有目标以稳定 `assetId` 定位；工具绝不接收地址、用户名、口令、私钥、文件路径、命令正文或任意 SQL。

HTTP Profile 额外提供受 Basic Auth 保护的资产库存 REST API：`/api/v1/assets` 和 `/api/v1/asset-relations`。该 API 不属于 MCP 工具契约，接口定义由 `/v3/api-docs` 和 `/swagger-ui/index.html` 发布；它仅管理登记资产和关系，不提供任意 SQLite SQL 或文件访问。

| 工具 | 输入 | 输出 | 限制 |
| --- | --- | --- | --- |
| `listAssets` | 类型、环境、状态、分页 | `AssetPage` | 单页最多 100 项；默认只返回 ACTIVE |
| `getAsset` | `assetId` | `Asset` | 不返回类型化详情中的凭证引用 |
| `getAssetTopology` | `assetId`、深度 | 资产与有向关系 | 深度 0–5、至多 200 个节点 |
| `listRelatedAssets` | `assetId`、关系类型、方向 | 关系列表 | 关系类型为服务端枚举 |
| `oracle.testDatabaseConnection` | Oracle `assetId` | 数据库产品、版本、认证身份 | 资产必须标记只读 |
| `oracle.listDatabaseUsers` | Oracle `assetId` | 用户名、账户状态、`locked`、创建/锁定/过期时间、Profile、认证类型 | 固定 `DBA_USERS` 查询；不接收 SQL 或筛选条件；30 秒、500 行、4 MiB 上限 |
| `oracle.unlockUser` | Oracle `assetId`、`username` | 用户名、解锁前后账户状态 | 受控变更工具：运行时开关和资产 `user_unlock_enabled` 均须显式启用；用户名仅支持未加引号 Oracle 标识符；先检查 `DBA_ROLE_PRIVS`、`DBA_SYS_PRIVS` 与 `V$PWFILE_USERS`，命中 DBA 类角色、广泛 `ANY` 系统权限、`ALTER USER` 等管理权限或 password-file 管理权限即拒绝；30 秒超时 |
| `oracle.listSchemas` | Oracle `assetId` | Schema 名称及非敏感账户属性 | 固定 `DBA_USERS` 查询；30 秒、500 行、4 MiB 上限 |
| `oracle.listTables` | Oracle `assetId`、`owner` | Schema 内的表及基础属性 | `owner` 仅支持未加引号的 Oracle 标识符；固定 `DBA_TABLES` 查询；30 秒、500 行、4 MiB 上限 |
| `oracle.listTablespaceUsage` | Oracle `assetId` | 表空间及数据文件的原始容量字段；临时表空间的原始已用/空闲字段 | 固定数据字典查询；不计算汇总、空闲量或使用率；不接收 SQL 或筛选条件；30 秒、500 行、4 MiB 上限 |
| `oracle.getSessionSummary` | Oracle `assetId` | 按数据库用户和会话状态聚合的连接数、最早登录时间与最长空闲/活动时长 | 固定 `V$SESSION` 查询；不返回客户端标识、操作系统用户或 SQL 文本；30 秒、500 行、4 MiB 上限 |
| `oracle.listLongRunningTransactions` | Oracle `assetId` | 持续至少 60 秒的打开事务对应的会话、开始时间、持续秒数和 Undo 使用量 | 固定 `V$TRANSACTION`/`V$SESSION` 查询；不返回事务标识或 SQL 文本；30 秒、500 行、4 MiB 上限 |
| `oracle.listBlockingSessions` | Oracle `assetId` | 本实例等待会话及可见阻塞会话的受限元数据 | 固定 `V$SESSION` 自连接；不返回客户端标识或 SQL 文本；RAC 的远程阻塞方不会补全；30 秒、500 行、4 MiB 上限 |
| `oracle.describeTable` | Oracle `assetId`、`owner`、`tableName` | 表属性、字段、约束字段与索引字段 | 名称仅支持未加引号的 Oracle 标识符；固定数据字典查询；不接收 SQL |
| `oracle.listConstraints` | Oracle `assetId`、`owner` | 指定 Schema 跨表的约束、约束列及外键引用目标 | `owner` 仅支持未加引号的 Oracle 标识符；固定 `DBA_CONSTRAINTS`、`DBA_CONS_COLUMNS` 查询；单表详情使用 `oracle.describeTable`；30 秒、500 行、4 MiB 上限 |
| `oracle.listIndexes` | Oracle `assetId`、`owner` | 指定 Schema 跨表的索引、索引列、方向和状态 | `owner` 仅支持未加引号的 Oracle 标识符；固定 `DBA_INDEXES`、`DBA_IND_COLUMNS` 查询；单表详情使用 `oracle.describeTable`；30 秒、500 行、4 MiB 上限 |
| `oracle.listAlertLogEvents` | Oracle `assetId`、可选 `offset`、可选 `pageSize` | 当前容器最近的 ADR 告警事件元数据；可选原始消息文本 | 固定 `V$DIAG_ALERT_EXT` 查询；`offset` 默认 0，`pageSize` 默认 100、范围 1–500；30 秒、4 MiB 上限。`MESSAGE_TEXT` 默认不返回，只有 `dba.database.alert-log.allow-sensitive-message-text=true` 时才原样返回 |

连接串和驱动属性保存在资产 SQLite 的 `database_detail.connection_properties`。规划中的、经 HTTPS Basic Auth 保护的资产库存管理 REST API 可写入 `connection_properties.password`；它是只写字段，不会由任何 MCP 或 REST 读取接口返回，也不得记录到日志、审计参数或错误响应。令牌、SSH 私钥和钱包密钥仍不保存。SSH 固定动作和配置资源读取尚未注册。

本地开发资产可选用 `connection_properties.username` 和 `connection_properties.password` 供 Oracle Thin 驱动认证；该字段不会由资产 MCP 工具返回，也不得写入日志。

Oracle 工具统一使用 `oracle.` 前缀；此前未加前缀的工具名不再注册。`oracle.listDatabaseUsers` 不返回密码散列、密码版本、外部认证细节或最近登录时间。`locked` 由 `ACCOUNT_STATUS` 是否包含 `LOCKED` 推导，因此同时覆盖人工锁定和定时锁定等 Oracle 状态。

`oracle.listTablespaceUsage` 不进行任何容量计算或汇总。每行由 `source_view` 标识来源：`DBA_TABLESPACE_USAGE_METRICS` 的 `TABLESPACE_SIZE`、`USED_SPACE`、`USED_PERCENT` 和 `BLOCK_SIZE` 原样返回；`DBA_DATA_FILES`/`DBA_TEMP_FILES` 的 `BYTES`、`MAXBYTES`、`AUTOEXTENSIBLE` 原样返回；`V$TEMP_SPACE_HEADER` 的 `BYTES_USED`、`BYTES_FREE`、`CON_ID` 原样返回。调用账号需要具备这些数据字典视图的只读权限。

`oracle.unlockUser` 是当前唯一的数据库变更 MCP 工具，不是通用 DDL 入口。默认关闭；除 `DBA_ORACLE_USER_UNLOCK_ENABLED=true` 外，目标资产的 `database_detail.user_unlock_enabled` 还必须为 `1`。工具只对当前已锁定用户执行 `ALTER USER <已验证标识符> ACCOUNT UNLOCK`，并在执行前以固定、绑定参数查询检查角色继承链、直接系统权限和 password-file 管理权限。预检所需目录视图不可读、预检超时或失败时一律拒绝解锁。

`oracle.describeTable` 查询 `DBA_TABLES`、`DBA_TAB_COLS`、`DBA_COL_COMMENTS`、`DBA_CONSTRAINTS`、`DBA_CONS_COLUMNS`、`DBA_INDEXES` 与 `DBA_IND_COLUMNS`。为避免返回潜在敏感默认表达式，不返回 `DATA_DEFAULT`。

`oracle.listConstraints` 与 `oracle.listIndexes` 是 Schema 级库存接口，不接受 `tableName`；这使其与返回单一表完整定义的 `oracle.describeTable` 保持不同职责。它们每个约束或索引列各返回一行，因此复合对象可能占用多行并受统一 500 行上限截断。

`oracle.listAlertLogEvents` 查询当前连接容器的 `V$DIAG_ALERT_EXT`，按产生时间和记录号倒序返回。它使用从 0 开始的 `offset` 分页：`offset` 默认为 0，`pageSize` 默认为 100 且必须为 1–500。结果中的 `truncated=true` 表示该页之后仍至少存在一条事件（或已触发既有响应大小限制）；调用方应将下一请求的 `offset` 增加实际返回的 `rowCount`。该视图中的 `MESSAGE_TEXT` 可能包含凭证、业务数据、文件路径或网络拓扑。项目已批准此安全边界例外：仅当服务端配置 `dba.database.alert-log.allow-sensitive-message-text=true`（环境变量 `DBA_ORACLE_ALERT_LOG_ALLOW_SENSITIVE_MESSAGE_TEXT=true`）时，工具才返回未经脱敏的文本；默认关闭时只返回时间、记录号、消息类型、消息级别和问题键。该开关不由 MCP 参数控制，应只在访问控制和传输安全均已落实的受控环境启用。

三个会话诊断工具需要调用账号拥有 `V$SESSION` 的只读访问权；`oracle.listLongRunningTransactions` 还需要 `V$TRANSACTION`。`oracle.listBlockingSessions` 仅关联当前实例的 `V$SESSION`，因此在 RAC 环境中会保留等待方的 `blocking_instance`，但不会读取 `GV$SESSION` 来补全远程阻塞方。

## Oracle 调用账号权限

常规只读 Oracle 资产的连接账号至少需要 `CREATE SESSION`。若已授予 `SELECT_CATALOG_ROLE`，它覆盖本项目固定查询使用的静态 `DBA_*` 数据字典视图，包括 `DBA_USERS`、`DBA_TABLES`、`DBA_TAB_COLS`、`DBA_COL_COMMENTS`、`DBA_CONSTRAINTS`、`DBA_CONS_COLUMNS`、`DBA_INDEXES`、`DBA_IND_COLUMNS`、`DBA_TABLESPACE_USAGE_METRICS`、`DBA_DATA_FILES` 和 `DBA_TEMP_FILES`。不应以 `SELECT ANY DICTIONARY` 替代最小权限配置。

动态性能与诊断视图应按下列清单直接授予底层 `SYS.V_$*` 对象的 `SELECT` 权限；不要向应用账号授予 SYSDBA：

| 直授对象 | 支持的工具 |
| --- | --- |
| `SYS.V_$TEMP_SPACE_HEADER` | `oracle.listTablespaceUsage` |
| `SYS.V_$SESSION` | `oracle.getSessionSummary`、`oracle.listBlockingSessions`、`oracle.listLongRunningTransactions` |
| `SYS.V_$TRANSACTION` | `oracle.listLongRunningTransactions` |
| `SYS.V_$DIAG_ALERT_EXT` | `oracle.listAlertLogEvents` |

示例（由数据库管理员以受控变更执行）：

```sql
GRANT CREATE SESSION TO dba_mcp;
GRANT SELECT ON SYS.V_$TEMP_SPACE_HEADER TO dba_mcp;
GRANT SELECT ON SYS.V_$SESSION TO dba_mcp;
GRANT SELECT ON SYS.V_$TRANSACTION TO dba_mcp;
GRANT SELECT ON SYS.V_$DIAG_ALERT_EXT TO dba_mcp;
```

`V$DIAG_ALERT_EXT` 返回当前连接容器的数据；多租户部署应在 MCP 实际连接的 CDB root 或 PDB 中对相应用户授权并验证。不同 Oracle 版本可能以不同内部对象实现告警视图；若直接授权后仍出现 `ORA-00942` 或权限错误，DBA 必须检查该版本 `V$DIAG_ALERT_EXT` 同义词的实际目标后进行最小范围的直接授权。`oracle.unlockUser` 不使用此常规只读账号：它必须使用独立的受控资产/凭证，且仅在双重开关启用后最小化授予 `ALTER USER`、`DBA_USERS`、`DBA_ROLE_PRIVS`、`DBA_SYS_PRIVS` 和 `V$PWFILE_USERS` 的必要访问权限。
