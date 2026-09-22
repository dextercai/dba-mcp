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

连接串和驱动属性保存在资产 SQLite 的 `database_detail.connection_properties`。规划中的、经 HTTPS Basic Auth 保护的资产库存管理 REST API 可写入 `connection_properties.password`；它是只写字段，不会由任何 MCP 或 REST 读取接口返回，也不得记录到日志、审计参数或错误响应。令牌、SSH 私钥和钱包密钥仍不保存。SSH 固定动作和配置资源读取尚未注册。

本地开发资产可选用 `connection_properties.username` 和 `connection_properties.password` 供 Oracle Thin 驱动认证；该字段不会由资产 MCP 工具返回，也不得写入日志。

Oracle 工具统一使用 `oracle.` 前缀；此前未加前缀的工具名不再注册。`oracle.listDatabaseUsers` 不返回密码散列、密码版本、外部认证细节或最近登录时间。`locked` 由 `ACCOUNT_STATUS` 是否包含 `LOCKED` 推导，因此同时覆盖人工锁定和定时锁定等 Oracle 状态。

`oracle.listTablespaceUsage` 不进行任何容量计算或汇总。每行由 `source_view` 标识来源：`DBA_TABLESPACE_USAGE_METRICS` 的 `TABLESPACE_SIZE`、`USED_SPACE`、`USED_PERCENT` 和 `BLOCK_SIZE` 原样返回；`DBA_DATA_FILES`/`DBA_TEMP_FILES` 的 `BYTES`、`MAXBYTES`、`AUTOEXTENSIBLE` 原样返回；`V$TEMP_SPACE_HEADER` 的 `BYTES_USED`、`BYTES_FREE`、`CON_ID` 原样返回。调用账号需要具备这些数据字典视图的只读权限。

`oracle.unlockUser` 是当前唯一的数据库变更 MCP 工具，不是通用 DDL 入口。默认关闭；除 `DBA_ORACLE_USER_UNLOCK_ENABLED=true` 外，目标资产的 `database_detail.user_unlock_enabled` 还必须为 `1`。工具只对当前已锁定用户执行 `ALTER USER <已验证标识符> ACCOUNT UNLOCK`，并在执行前以固定、绑定参数查询检查角色继承链、直接系统权限和 password-file 管理权限。预检所需目录视图不可读、预检超时或失败时一律拒绝解锁。

`oracle.describeTable` 查询 `DBA_TABLES`、`DBA_TAB_COLS`、`DBA_COL_COMMENTS`、`DBA_CONSTRAINTS`、`DBA_CONS_COLUMNS`、`DBA_INDEXES` 与 `DBA_IND_COLUMNS`。为避免返回潜在敏感默认表达式，不返回 `DATA_DEFAULT`。

三个会话诊断工具需要调用账号拥有 `V$SESSION` 的只读访问权；`oracle.listLongRunningTransactions` 还需要 `V$TRANSACTION`。`oracle.listBlockingSessions` 仅关联当前实例的 `V$SESSION`，因此在 RAC 环境中会保留等待方的 `blocking_instance`，但不会读取 `GV$SESSION` 来补全远程阻塞方。
