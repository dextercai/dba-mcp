# DBA MCP 服务架构与实施规划

> 状态：阶段 1–2 实施中
>
> 更新日期：2026-09-22
>
> 适用范围：Oracle、OceanBase（Oracle 模式）、达梦数据库和受控 SSH 主机操作

## 1. 文档目的

本文档记录 DBA MCP 服务的技术选型、系统架构、安全边界、资产模型、MCP 工具设计和分阶段实施方案，作为后续开发、评审、测试与部署的共同基线。

项目采用 Java 作为主要技术栈。第一阶段优先实现安全、可审计的数据库只读能力和固定 SSH 动作，不开放任意数据库变更及任意 Shell 命令。

## 2. 建设目标

系统需要向 MCP 客户端提供以下能力：

- 管理并查询主机、数据库、OGG 和配置资源等资产。
- 连接 Oracle 数据库。
- 连接 OceanBase Oracle 模式租户。
- 连接达梦数据库。
- 查询数据库元数据和运行状态。
- 执行受控的只读 SQL 和预定义 DBA 检查项。
- 通过 SSH 执行服务端预定义的固定动作。
- 查询受控的 OGG 配置文件。
- 对所有外部操作执行鉴权、授权、限流、超时控制和审计。
- 同时支持本地 STDIO 调试和生产环境 Streamable HTTP 部署。

## 3. 第一阶段非目标

以下能力不进入第一阶段：

- 任意 Shell 命令执行。
- 任意文件路径读取或写入。
- 通用 DDL、DML、PL/SQL 或存储过程执行。
- 自动修改数据库参数、表结构或数据。
- 自动修改 OGG 参数文件。
- 在共享或生产环境中保存数据库密码、SSH 私钥等秘密正文。
- 将资产管理建设为完整 CMDB。
- 初期拆分为多个独立微服务。

## 4. 核心架构决策

### 4.1 Java 作为主要技术栈

采用 Java 的主要原因：

- Oracle、OceanBase 和达梦均提供成熟的 JDBC 驱动。
- 三类数据库可以通过统一的 JDBC API 接入。
- 避免 Python 方案中 OBCI、DPI 等本地动态库带来的部署复杂度。
- Spring Boot 在认证、配置、指标、审计和生产部署方面生态完整。
- JDBC 和 SSH 都属于阻塞式 I/O，使用 Spring WebMVC 容易控制并发和资源隔离。

### 4.2 模块化单体

第一阶段采用单仓库、单部署单元、模块化包结构：

- 降低跨服务调用与部署成本。
- 权限、安全策略和审计逻辑集中管理。
- 通过 Java 接口隔离 MCP、资产、数据库和 SSH 实现。
- 当团队或规模扩大时，可再拆分物理模块或独立连接器服务。

### 4.3 资产图模型

资产不组织为单层列表，也不将数据库、OGG 等全部嵌入主机 JSON。系统使用：

> 资产节点 + 显式关系 + 类型化详情 + 凭证引用

数据库和 OGG 是独立资产，通过 `RUNS_ON` 等关系连接到主机。该结构能够表达 RAC、多机部署、跨库 OGG 复制等场景。

### 4.4 安全默认值

- 数据库账号默认只读；唯一已批准的例外是显式启用且受高权限账户预检保护的 Oracle 用户解锁操作。
- 工具默认拒绝高风险操作。
- SSH 只允许固定动作 ID。
- 文件只允许读取已登记的配置资源。
- 所有返回结果都有行数、大小和时间限制。
- 共享和生产环境中的凭证只保存外部 Secret 引用；本地开发可选择在被忽略的 SQLite 文件中保存测试凭证。

## 5. 推荐技术栈

| 领域 | 选型 | 说明 |
| --- | --- | --- |
| 运行时 | Java 21 LTS | 长期支持版本 |
| 应用框架 | Spring Boot、Spring WebMVC | 适合 JDBC 和 SSH 阻塞式调用 |
| MCP | MCP Java SDK 2.x / Spring AI MCP Server | 优先使用 Streamable HTTP |
| 构建 | Maven Wrapper | 便于企业环境和私有驱动管理 |
| Oracle | Oracle JDBC Thin | 驱动版本按数据库/JDK兼容矩阵确定 |
| OceanBase | OceanBase Connector/J | 支持 Oracle 模式 |
| 达梦 | DmJdbcDriver18 或服务端匹配驱动 | 驱动版本尽量接近服务端版本 |
| 数据库连接池 | HikariCP | 有界的热点数据库目标连接池；冷目标按需直连 |
| SSH | SSHJ 0.40 或更高维护版本 | 必须严格校验主机密钥 |
| 安全 | Spring Security | JWT/OIDC 或 mTLS |
| 韧性 | Resilience4j | 限流、隔离、超时和熔断 |
| 指标 | Micrometer、Prometheus | 连接池、工具和执行指标 |
| 链路 | OpenTelemetry | MCP 到数据库/SSH 的调用链路 |
| 测试 | JUnit 5、Mockito、Testcontainers | 单元、协议及集成测试 |
| 部署 | Docker、Kubernetes 或普通主机 | 默认不保存明文凭证；资产管理接口可按受控例外写入数据库密码 |

依赖版本应由 BOM 和 Maven 锁定。具体版本在项目初始化时根据数据库版本矩阵和当前维护版本确定，不在架构文档中写死补丁版本。

## 6. 系统架构

```text
MCP Client
    │
    │ STDIO（本地）/ Streamable HTTP（生产）
    ▼
MCP Transport
    │
    ├── Authentication
    ├── Authorization
    ├── Request Validation
    └── Request Context
    ▼
MCP Tool Registry
    ├── Asset Tools
    ├── Database Tools
    ├── Host Tools
    └── OGG/Configuration Tools
    ▼
Application Services
    ├── Asset Service
    ├── Database Execution Service
    ├── Host Action Service
    └── Audit Service
    ▼
Policy and Execution Layer
    ├── Target Permission Policy
    ├── SQL Safety Policy
    ├── Host Action Policy
    ├── Timeout and Output Limit
    └── Sensitive Data Masking
    ▼
Infrastructure
    ├── AssetRepository
    │     └── SQLite implementation
    ├── JDBC Connector Registry
    │     ├── Oracle Adapter
    │     ├── OceanBase Oracle Adapter
    │     └── Dameng Adapter
    ├── SSH Fixed Action Executor
    ├── Secret Provider
    └── Metrics / Tracing / Audit Sink
```

## 7. MCP 传输与部署模式

### 7.1 本地开发

使用 STDIO：

- 便于 MCP Inspector 和桌面客户端调试。
- 业务日志只能写入 `stderr` 或日志文件。
- 禁止向 `stdout` 写入非 MCP 消息。

### 7.2 生产部署

使用 Streamable HTTP：

- 默认端点为 `/mcp`。
- 通过 HTTPS 暴露。
- 端点前启用认证和授权。
- 校验 `Origin`，避免 DNS rebinding。
- 默认不直接监听公共网络地址。
- 通过 API Gateway 或反向代理实施限流和 TLS 策略。

### 7.3 服务模式

第一阶段使用同步 MCP 工具处理器。数据库和 SSH 调用为阻塞式操作，应通过有界连接资源、并发隔离和超时限制保护服务。长时间任务后续可扩展为异步任务模式。

## 8. 建议项目结构

初期使用单 Maven 模块，通过包边界隔离：

```text
dba_mcp/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Dockerfile
├── README.md
├── docs/
│   ├── architecture-plan.md
│   ├── tool-contracts.md
│   ├── security-model.md
│   └── deployment.md
├── src/main/java/.../
│   ├── bootstrap/
│   ├── mcp/
│   │   ├── tool/
│   │   ├── resource/
│   │   └── transport/
│   ├── application/
│   │   ├── asset/
│   │   ├── database/
│   │   ├── host/
│   │   └── audit/
│   ├── domain/
│   │   ├── asset/
│   │   ├── target/
│   │   ├── policy/
│   │   └── execution/
│   ├── infrastructure/
│   │   ├── asset/sqlite/
│   │   ├── jdbc/oracle/
│   │   ├── jdbc/oceanbase/
│   │   ├── jdbc/dameng/
│   │   ├── ssh/
│   │   ├── secret/
│   │   └── observability/
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   ├── application-stdio.yml
│   ├── application-http.yml
│   ├── db-checks/
│   └── host-actions/
└── src/test/
```

如果后续拆分 Maven 模块，建议拆为：

- `dba-mcp-app`
- `dba-mcp-core`
- `dba-mcp-assets`
- `dba-mcp-jdbc`
- `dba-mcp-ssh`
- `dba-mcp-integration-tests`

## 9. 资产体系

### 9.1 资产类型

第一阶段定义以下资产类型：

| 类型 | 说明 |
| --- | --- |
| `HOST` | 主机或虚拟机 |
| `DATABASE_CLUSTER` | 数据库集群，可选 |
| `DATABASE_INSTANCE` | 数据库实例 |
| `DATABASE_SERVICE` | 对外数据库连接服务 |
| `OGG_DEPLOYMENT` | OGG 安装或部署 |
| `OGG_PROCESS` | Extract、Pump、Replicat 等进程 |
| `CONFIG_RESOURCE` | 受控配置文件或目录 |

资产类型采用后端枚举管理。新增类型需要同时定义详情结构、关系约束和可执行能力。

### 9.2 资产关系

| 关系 | 含义 |
| --- | --- |
| `RUNS_ON` | 数据库实例或 OGG 部署运行在某主机 |
| `MEMBER_OF` | 实例属于数据库集群 |
| `EXPOSED_AS` | 实例或集群通过数据库服务暴露 |
| `PART_OF` | OGG 进程属于 OGG 部署 |
| `CONFIG_OF` | 配置资源属于某部署或进程 |
| `CAPTURES_FROM` | OGG Extract 从数据库捕获 |
| `REPLICATES_TO` | OGG Replicat 写入数据库 |
| `CONNECTS_TO` | 一般性连接关系 |
| `DEPENDS_ON` | 其他依赖关系 |

关系具有方向。系统需要校验允许的源类型、关系类型和目标类型组合，例如：

```text
DATABASE_INSTANCE --RUNS_ON--> HOST
OGG_DEPLOYMENT    --RUNS_ON--> HOST
OGG_PROCESS       --PART_OF--> OGG_DEPLOYMENT
CONFIG_RESOURCE   --CONFIG_OF--> OGG_PROCESS
OGG_PROCESS       --CAPTURES_FROM--> DATABASE_INSTANCE
```

### 9.3 示例拓扑

```text
HOST: host-xxx
├── DATABASE_INSTANCE: oracle-prod-01
│       └── DATABASE_SERVICE: oracle-prod-service
└── OGG_DEPLOYMENT: ogg-deploy-01
        ├── OGG_PROCESS: extract-ext01
        ├── OGG_PROCESS: replicat-rep01
        └── CONFIG_RESOURCE: dirprm/ext01.prm

extract-ext01 --CAPTURES_FROM--> oracle-prod-01
replicat-rep01 --REPLICATES_TO--> oracle-dr-01
```

数据库内保存扁平资产节点及关系，接口返回时按需组装成树或拓扑，避免嵌套数据重复和更新困难。

## 10. SQLite 资产存储设计

SQLite 是第一种资产存储实现，不应成为领域层硬依赖。应用层通过 `AssetRepository` 接口读取资产，以便后续接入 CMDB、HTTP API 或其他数据库。

### 10.1 基础资产表

```sql
CREATE TABLE asset (
    id              TEXT PRIMARY KEY,
    asset_type      TEXT NOT NULL,
    asset_code      TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    environment     TEXT,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    source_name     TEXT NOT NULL DEFAULT 'local',
    external_id     TEXT,
    labels_json     TEXT NOT NULL DEFAULT '{}',
    metadata_json   TEXT NOT NULL DEFAULT '{}',
    version         INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_asset_external_identity
    ON asset(source_name, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_asset_type_status
    ON asset(asset_type, status);
```

`metadata_json` 只保存不稳定的扩展属性。经常用于查询、校验、连接或授权的字段必须进入类型化详情表。

### 10.2 资产关系表

```sql
CREATE TABLE asset_relation (
    id                TEXT PRIMARY KEY,
    source_asset_id   TEXT NOT NULL,
    relation_type     TEXT NOT NULL,
    target_asset_id   TEXT NOT NULL,
    attributes_json   TEXT NOT NULL DEFAULT '{}',
    created_at        TEXT NOT NULL,

    FOREIGN KEY (source_asset_id) REFERENCES asset(id),
    FOREIGN KEY (target_asset_id) REFERENCES asset(id),

    UNIQUE (source_asset_id, relation_type, target_asset_id)
);

CREATE INDEX idx_relation_source
    ON asset_relation(source_asset_id, relation_type);

CREATE INDEX idx_relation_target
    ON asset_relation(target_asset_id, relation_type);
```

SQLite 连接必须启用外键检查：

```sql
PRAGMA foreign_keys = ON;
```

### 10.3 主机详情

```sql
CREATE TABLE host_detail (
    asset_id           TEXT PRIMARY KEY,
    hostname           TEXT NOT NULL,
    management_ip      TEXT NOT NULL,
    ssh_port           INTEGER NOT NULL DEFAULT 22,
    os_type            TEXT,
    os_version         TEXT,
    architecture       TEXT,
    ssh_credential_ref TEXT,
    bastion_asset_id   TEXT,

    FOREIGN KEY (asset_id) REFERENCES asset(id),
    FOREIGN KEY (bastion_asset_id) REFERENCES asset(id)
);
```

### 10.4 数据库详情

```sql
CREATE TABLE database_detail (
    asset_id              TEXT PRIMARY KEY,
    database_type         TEXT NOT NULL,
    database_version      TEXT,
    role                  TEXT,
    host                  TEXT,
    port                  INTEGER,
    service_name          TEXT,
    database_name         TEXT,
    tenant_name           TEXT,
    cluster_name          TEXT,
    connection_properties TEXT NOT NULL DEFAULT '{}',
    credential_ref        TEXT NOT NULL,
    read_only             INTEGER NOT NULL DEFAULT 1,
    user_unlock_enabled   INTEGER NOT NULL DEFAULT 0,

    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
```

`database_type` 第一阶段限定为：

- `ORACLE`
- `OCEANBASE_ORACLE`
- `DAMENG`

共享和生产环境中，连接属性默认只保存非敏感驱动属性，令牌、SSH 私钥和钱包密钥仍必须使用 `credential_ref`。经 Basic Auth 认证并获资产管理员授权的库存管理接口，可将数据库密码写入 `connection_properties.password`，以支持受控本地资产维护；该字段必须是只写字段，绝不能出现在 REST/MCP 响应、日志、审计参数或错误信息中。SQLite 文件因此属于敏感凭证存储，必须限制为服务账号可读写、禁止提交或打包进镜像，并优先采用加密、运行时主密钥和定期轮换。

`user_unlock_enabled` 默认为 `0`，只用于授权已登记 Oracle 目标上的专用 `oracle.unlockUser` 操作；它不能开启通用 DDL。该操作还需要服务端运行时开关 `DBA_ORACLE_USER_UNLOCK_ENABLED=true`。执行凭证必须只授予完成该动作所需的最小权限（`ALTER USER` 以及预检需要的 `DBA_USERS`、`DBA_ROLE_PRIVS`、`DBA_SYS_PRIVS`、`V$PWFILE_USERS` 只读访问），并应与常规只读查询凭证隔离；当前本地 SQLite 凭证实现部署时应采用独立的受控资产或后续 Secret Provider 来实现该隔离。

### 10.5 OGG 部署详情

```sql
CREATE TABLE ogg_deployment_detail (
    asset_id             TEXT PRIMARY KEY,
    ogg_version          TEXT,
    deployment_mode      TEXT NOT NULL,
    install_home         TEXT NOT NULL,
    deployment_home      TEXT,
    service_manager_port INTEGER,
    admin_server_port    INTEGER,
    credential_ref       TEXT,

    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
```

`deployment_mode` 可取：

- `CLASSIC`
- `MICROSERVICES`

### 10.6 OGG 进程详情

```sql
CREATE TABLE ogg_process_detail (
    asset_id        TEXT PRIMARY KEY,
    process_type    TEXT NOT NULL,
    process_name    TEXT NOT NULL,
    parameter_file  TEXT,
    report_file     TEXT,
    trail_name      TEXT,
    enabled         INTEGER NOT NULL DEFAULT 1,

    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
```

`process_type` 可取：

- `EXTRACT`
- `PUMP`
- `REPLICAT`
- `RECEIVER`
- `DISTRIBUTION`

### 10.7 配置资源详情

```sql
CREATE TABLE config_resource_detail (
    asset_id          TEXT PRIMARY KEY,
    resource_type     TEXT NOT NULL,
    logical_name      TEXT NOT NULL,
    absolute_path     TEXT NOT NULL,
    charset           TEXT NOT NULL DEFAULT 'UTF-8',
    readable          INTEGER NOT NULL DEFAULT 1,
    writable          INTEGER NOT NULL DEFAULT 0,
    sensitive         INTEGER NOT NULL DEFAULT 0,
    max_read_bytes    INTEGER NOT NULL DEFAULT 1048576,
    masking_policy    TEXT,

    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
```

第一阶段 `writable` 应始终为 `0`。如果未来支持配置修改，需要设计独立的审批、备份、差异预览、回滚和审计流程。

### 10.8 数据存储约束

- 资产 ID 使用稳定 UUID，不使用主机名或 IP 作为主键。
- `asset_code` 是面向人类和工具调用的稳定业务编码。
- 删除资产优先采用状态变更，不直接物理删除。
- 使用 `version` 实现乐观锁。
- 所有时间统一保存为 UTC ISO-8601 格式。
- SQLite 资产库与审计库分离。
- 如果 SQLite 是外部生成的资产快照，服务以只读方式打开。
- 如果通过文件替换刷新 SQLite，应先生成完整新文件，再执行原子替换。
- 当启用资产库存写入 API 时，数据库密码仅允许由 Basic Auth 的资产管理员写入 `connection_properties.password`；任何读取 API 均不得返回该字段。

## 11. 资产仓储接口

领域层只依赖接口：

```java
public interface AssetRepository {

    Optional<Asset> findById(AssetId id);

    Optional<Asset> findByCode(String code);

    AssetPage search(AssetQuery query);

    List<AssetRelation> findOutgoingRelations(
            AssetId sourceId,
            Set<RelationType> relationTypes);

    List<AssetRelation> findIncomingRelations(
            AssetId targetId,
            Set<RelationType> relationTypes);
}
```

第一阶段实现：

```text
AssetRepository
└── SqliteAssetRepository
```

后续可以扩展：

```text
AssetRepository
├── SqliteAssetRepository
├── CmdbAssetRepository
├── HttpAssetRepository
└── CompositeAssetRepository
```

`CompositeAssetRepository` 需要明确数据源优先级、冲突处理和稳定 ID 映射规则，第一阶段不实现。

## 12. 数据库连接架构

### 12.1 核心接口

```java
public interface DatabaseDialect {

    DatabaseType type();

    SqlValidationResult validateReadOnlySql(String sql);

    String applyRowLimit(String sql, int maxRows);

    String quoteIdentifier(String identifier);

    DatabaseError classify(SQLException exception);

    DatabaseMetadataProvider metadataProvider();
}
```

```java
public interface DatabaseExecutor {

    QueryResult query(QueryRequest request);

    ExplainResult explain(ExplainRequest request);

    ConnectionTestResult testConnection(AssetId databaseAssetId);

    CheckResult runNamedCheck(NamedCheckRequest request);
}
```

```java
public interface TargetDataSourceRegistry {

    DataSourceHandle get(AssetId databaseAssetId);

    void refresh(AssetId databaseAssetId);

    void evict(AssetId databaseAssetId);
}
```

### 12.2 连接池策略

- 数据库资产数量不等于常驻连接池数量。系统使用按资产键控、容量有界的热点池注册表。
- 连接池延迟创建；每个已缓存热点资产最多使用受服务端限制的少量连接，默认值为 `1`。
- 注册表按最近使用顺序淘汰空闲池；淘汰前必须关闭 `HikariDataSource`，不得只删除映射。
- 池数量达到上限时，仅淘汰没有在途请求的最久未使用池；若全部池都在使用，则拒绝新目标的池化请求，避免资产规模或突发流量突破资源预算。
- 连接测试及其他一次性、低频操作使用一次性 JDBC 连接，不创建或保留资产池。
- 冷池在短暂的注册表空闲期后关闭；默认关闭 keepalive，避免将低频资产转化为持续探活流量。
- 连接池参数仅由服务端配置，并须受服务端安全上限约束；客户端不得通过 MCP 参数修改。
- 不允许客户端通过 MCP 参数直接修改连接池配置。
- 凭证轮换后应支持安全刷新对应连接池。

需要限制：

- 最大连接数。
- 获取连接超时。
- SQL 执行超时。
- 最大返回行数。
- 最大列数。
- 最大单元格大小。
- 最大结果字节数。
- 最大并发工具调用数。

### 12.3 SQL 策略

第一阶段只提供预定义检查项和固定数据字典查询；不在 MCP 注册通用只读 SQL 查询工具。若未来重新开放通用 SQL 查询，必须同时满足：

- 数据库使用只读账号。
- 仅允许单条语句。
- 只允许允许清单中的语句类型。
- 禁止匿名块、存储过程和动态 SQL。
- 禁止仅通过字符串正则表达式判定安全性。
- 执行前应用数据库方言校验。
- 设置 JDBC Query Timeout 和最大行数。
- 达到结果上限时返回 `truncated=true`。

## 13. SSH 固定动作设计

### 13.1 基本规则

- MCP 客户端只能提交 `hostAssetId`、`actionId` 和类型化参数。
- 客户端不得传递命令正文。
- 不提供 `shell`、`command`、`extraArgs` 等自由输入字段。
- 尽量以 argv 方式调用固定脚本，不拼接 Shell 字符串。
- 参数只允许枚举、数字、IP、日期、受限标识符等类型。
- 每个动作独立配置超时、最大输出、权限和可用环境。

### 13.2 动作配置示例

```yaml
dba:
  host-actions:
    disk-usage:
      command:
        - /usr/local/dba-tools/disk-usage
      timeout: 10s
      max-output-bytes: 65536

    service-status:
      command:
        - /usr/local/dba-tools/service-status
        - "${service}"
      timeout: 15s
      max-output-bytes: 65536
      parameters:
        service:
          type: enum
          values:
            - oracle
            - oceanbase
            - dmserver
            - ogg
```

### 13.3 SSH 安全要求

- 严格校验 `known_hosts`。
- 禁止自动接受未知主机密钥。
- SSH 私钥从 Secret Provider 获取。
- 推荐使用独立低权限系统账号。
- 需要提权时，配合 `sudoers` 命令白名单。
- 禁止交互式 Shell。
- 限制标准输出和标准错误大小。
- 超时后关闭远程 Channel，并清理本地资源。
- 跳板机通过主机资产关系和受控配置启用。

## 14. OGG 配置资源安全

OGG 参数文件可能包含账号别名、钱包位置、密钥引用或其他敏感内容，必须执行以下约束：

- 文件路径只能从 `CONFIG_RESOURCE` 资产获得。
- MCP 请求不得传递任意绝对路径或相对路径。
- 读取前对路径执行规范化。
- 防止通过 `..` 和软链接逃逸允许目录。
- 文件必须位于所属 OGG 部署的允许根目录内。
- 第一阶段只读。
- 限制单文件最大读取字节数。
- 根据 `masking_policy` 对敏感字段脱敏。
- 审计日志只记录配置资源 ID 和摘要，不记录完整内容。

## 15. MCP 工具规划

### 15.1 资产工具

| 工具 | 说明 |
| --- | --- |
| `list_assets` | 按类型、环境、状态和标签查询资产 |
| `get_asset` | 查询资产详情 |
| `get_asset_topology` | 查询资产上下游关系并限制深度 |
| `list_host_components` | 查询主机上的数据库、OGG 等组件 |
| `list_related_assets` | 按关系类型查询相关资产 |
| `list_asset_actions` | 返回当前用户可对资产执行的动作 |

### 15.2 数据库工具

| 工具 | 说明 |
| --- | --- |
| `oracle.testDatabaseConnection` | 测试 Oracle 数据库连通性和身份 |
| `oracle.listSchemas` | 查询 Oracle Schema |
| `oracle.listTables` | 查询指定 Oracle Schema 内的表 |
| `oracle.listDatabaseUsers` | 查询 Oracle 数据库用户及锁定状态；仅返回非敏感账户属性 |
| `oracle.unlockUser` | 仅解锁普通 Oracle 用户；运行时和目标资产双重显式启用，并拒绝 DBA 类及其他高权限用户 |
| `oracle.listTablespaceUsage` | 查询 Oracle 永久和临时表空间的原始容量字段，不在服务端计算汇总或使用率 |
| `oracle.getSessionSummary` | 按数据库用户和会话状态查询 Oracle 用户会话汇总；不返回客户端标识、操作系统用户或 SQL 文本 |
| `oracle.listLongRunningTransactions` | 查询 Oracle 打开用户事务的受限会话与 Undo 元数据；不返回事务标识或 SQL 文本 |
| `oracle.listBlockingSessions` | 查询 Oracle 本实例会话等待/阻塞关系；不返回客户端标识或 SQL 文本，不补全 RAC 远程阻塞方 |
| `oracle.describeTable` | 查询 Oracle 表属性、字段、约束和索引定义 |
| `list_database_objects` | 查询表、视图、索引等对象 |
| `describe_database_object` | 查询字段、索引和约束 |
| `run_database_check` | 执行服务端预定义 DBA 检查项 |
| `explain_sql` | 按方言返回执行计划 |
| `get_database_status` | 查询基础运行状态 |

当前已注册的 Oracle 工具必须使用 `oracle.` 命名空间前缀；该前缀是 MCP 工具契约的一部分。新增 Oracle 工具必须遵循相同规则，且不保留无前缀别名。

### 15.3 主机与 OGG 工具

| 工具 | 说明 |
| --- | --- |
| `test_host_connection` | 测试 SSH 连通性 |
| `list_host_actions` | 返回允许的固定动作 |
| `run_host_action` | 执行固定动作 |
| `list_ogg_processes` | 查询已登记的 OGG 进程 |
| `read_config_resource` | 读取并脱敏受控配置资源 |

### 15.4 工具参数原则

数据库和主机工具只接收资产 ID，不接收原始地址与凭证：

```json
{
  "assetId": "oracle-prod-01",
  "checkId": "tablespace-usage"
}
```

禁止设计为：

```json
{
  "host": "10.1.2.3",
  "port": 1521,
  "username": "system",
  "password": "..."
}
```

### 15.5 资产库存管理 REST API

HTTP Profile 提供 `/api/v1/**` 的受控资产库存管理面。它只操作已配置的 SQLite 资产库，不接受 SQLite 文件路径、表名或任意 SQL。所有接口均要求 `ROLE_ASSET_ADMIN` 的 HTTP Basic Auth；未配置 `DBA_ASSET_ADMIN_USERNAME` 或 `DBA_ASSET_ADMIN_PASSWORD_HASH` 时，请求应失败。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/assets` | 按类型、环境、状态分页查询资产。 |
| `POST` | `/api/v1/assets` | 创建资产及可选的类型化详情。 |
| `GET` | `/api/v1/assets/{assetId}` | 查询资产及已脱敏详情。 |
| `PATCH` | `/api/v1/assets/{assetId}?version={version}` | 以乐观锁更新资产和详情；类型不可变。 |
| `DELETE` | `/api/v1/assets/{assetId}?version={version}` | 逻辑删除，即标记为 `RETIRED`。 |
| `POST` | `/api/v1/assets/{assetId}/restore?version={version}` | 恢复为 `ACTIVE`。 |
| `GET` / `POST` | `/api/v1/assets/{assetId}/relations` | 查询或创建该资产的出向关系。 |
| `PATCH` / `DELETE` | `/api/v1/asset-relations/{relationId}` | 更新关系属性或删除关系。 |

数据库详情的 `connection_properties.password` 仅能经 `POST` 或 `PATCH` 写入；`GET`、所有 MCP 工具、日志、审计参数、错误和 OpenAPI 示例均不得返回该字段。OpenAPI JSON 位于 `/v3/api-docs`，Swagger UI 位于 `/swagger-ui/index.html`。

## 16. 统一返回和错误模型

数据库查询结果建议统一为：

```json
{
  "executionId": "0199...",
  "targetAssetId": "oracle-prod-01",
  "columns": [],
  "rows": [],
  "rowCount": 100,
  "truncated": true,
  "elapsedMs": 328,
  "warnings": []
}
```

统一错误分类至少包括：

- `ASSET_NOT_FOUND`
- `ASSET_DISABLED`
- `PERMISSION_DENIED`
- `SECRET_UNAVAILABLE`
- `CONNECTION_FAILED`
- `QUERY_REJECTED`
- `QUERY_TIMEOUT`
- `RESULT_LIMIT_EXCEEDED`
- `HOST_KEY_MISMATCH`
- `ACTION_NOT_ALLOWED`
- `REMOTE_EXECUTION_FAILED`
- `CONFIG_RESOURCE_DENIED`
- `INTERNAL_ERROR`

MCP 返回中不包含数据库密码、SSH 私钥、完整连接串或服务端堆栈。

## 17. 配置结构

`application.yml` 应只保存服务配置和外部引用，不保存实际密码。为降低本地开发门槛，测试凭证可选择保存在不提交的 SQLite 文件中：

```yaml
spring:
  application:
    name: dba-mcp

dba:
  assets:
    provider: sqlite
    sqlite:
      jdbc-url: jdbc:sqlite:/data/dba-mcp-assets.db
      read-only: false # 使用内置资产管理 API 的部署；外部只读快照设为 true
      busy-timeout: 5s
    cache:
      enabled: true
      maximum-size: 10000
      ttl: 60s

  secrets:
    provider: vault

  database:
    default-query-timeout: 30s
    default-max-rows: 500
    absolute-max-rows: 5000
    max-result-bytes: 4194304
    max-cell-bytes: 262144
    hikari:
      connection-timeout: 30s
      validation-timeout: 5s
      keepalive-time: 0s
      max-lifetime: 30m
      idle-timeout: 10m
    target-pools:
      maximum-cached-pools: 128
      maximum-pool-size: 1
      cache-idle-timeout: 1m
    user-unlock:
      enabled: false # 生产中仅通过 DBA_ORACLE_USER_UNLOCK_ENABLED=true 显式开启

  ssh:
    connect-timeout: 10s
    command-timeout: 30s
    strict-host-key-checking: true
    max-output-bytes: 1048576
```

生产环境通过环境变量或部署系统覆盖路径、端点和非敏感开关，秘密应通过引用在运行时解析。开发 SQLite 凭证库不得提交、打包进镜像或复制到共享环境；推荐加密并由运行时主密钥解锁。

## 18. 身份认证与授权

### 18.1 认证

生产环境选择以下一种或组合：

- OIDC/JWT Bearer Token。
- 服务间 mTLS。
- 内部 API Gateway 完成认证并传递可信身份。

禁止仅依赖网络可达性作为认证机制。

资产库存管理 REST API 是上述生产认证方案之外的受控例外：它使用 HTTP Basic Auth，且只能经 HTTPS 暴露。Basic Auth 用户名与 BCrypt 密码散列必须来自环境变量或 Secret，不能写入应用配置或 SQLite。该接口仅授予资产管理角色；其可写入数据库密码，但所有读取响应、日志、审计参数和错误信息必须省略密码字段。

### 18.2 授权模型

授权至少包含三个维度：

```text
主体 + 工具 + 目标资产
```

例如：

```text
用户 A 可以调用 describe_database_object，
但只能访问 test 环境的 Oracle 资产。
```

高风险操作即使未来启用，也应单独授权，不继承普通查询权限。

已批准的 `oracle.unlockUser` 是受控例外：它不能继承普通查询权限，且要求运行时开关和资产级 `user_unlock_enabled` 双重授权。工具只接受常规未加引号 Oracle 标识符；解锁前固定查询 `DBA_ROLE_PRIVS`（含角色继承）、`DBA_SYS_PRIVS` 和 `V$PWFILE_USERS`。若发现 DBA、导入导出/目录管理角色、广泛 `ANY` 系统权限、`ALTER USER` 等账户管理权限或任一 password-file 管理权限，或预检无法完成，则拒绝操作。仅当账户处于锁定状态且预检通过时才执行固定 `ALTER USER <validated-identifier> ACCOUNT UNLOCK`。

## 19. 审计设计

每次工具调用记录：

- 请求 ID 和执行 ID。
- 调用主体。
- MCP 客户端信息。
- 工具名称和版本。
- 目标资产 ID。
- 参数摘要或哈希。
- 开始、结束时间和耗时。
- 返回行数或输出字节数。
- 是否截断。
- 最终状态和错误分类。
- 策略判定结果。

禁止记录：

- 密码、令牌、私钥。
- 未脱敏配置文件全文。
- 默认记录完整 SQL 绑定值。
- 大体积查询结果。

## 20. 可观测性

### 20.1 指标

建议至少暴露：

- MCP 工具调用次数、耗时、成功率。
- 每个工具的并发数量和拒绝数量。
- 数据库连接池活跃、空闲、等待和超时数。
- SQL 超时、取消和截断次数。
- SSH 连接、认证、主机密钥失败次数。
- 资产加载、缓存命中和刷新失败次数。
- Secret 获取失败次数。

所有指标标签必须控制基数。数据库资产可使用受控资产编码，不使用完整 SQL、文件路径或用户输入作为标签。

### 20.2 日志

- 使用结构化 JSON 日志。
- STDIO 模式不得向标准输出打印普通日志。
- 日志使用请求 ID、执行 ID 关联。
- 数据库异常转换为统一错误后再输出。
- 生产默认不输出完整 SQL 和查询结果。

### 20.3 链路

链路范围包括：

```text
MCP Request
  → Authorization
  → Asset Lookup
  → Secret Lookup
  → Connection Acquisition
  → Database/SSH Execution
  → Result Conversion
```

## 21. 测试策略

### 21.1 单元测试

- 资产关系约束。
- SQL 安全策略。
- 行数和结果大小限制。
- SSH 参数校验和命令构造。
- 配置路径规范化和越界防护。
- 权限策略。
- 敏感字段脱敏。

### 21.2 MCP 协议测试

- 工具发现。
- 输入 JSON Schema。
- 结构化输出。
- 错误转换。
- STDIO 输出纯净性。
- Streamable HTTP 认证和 Origin 校验。
- 请求体和响应体大小限制。

### 21.3 数据库集成测试

每类数据库至少验证：

- 连接成功和认证失败。
- 元数据查询。
- 中文和 Unicode。
- NUMBER/DECIMAL。
- DATE/TIMESTAMP。
- CLOB/BLOB 截断。
- NULL。
- 查询超时和取消。
- 网络中断和连接恢复。
- 连接池耗尽与背压。
- 服务端版本兼容。

达梦及商业数据库镜像无法进入公共 CI 时，通过可选集成测试 Profile 连接专用测试环境。

### 21.4 SSH 集成测试

- 正确和错误主机密钥。
- 密钥认证失败。
- 固定动作执行。
- 参数注入攻击。
- 超时和输出上限。
- 跳板机。
- 远程脚本非零退出码。

## 22. 部署与运行

### 22.1 Docker 镜像

- 使用最小化 JRE 运行镜像。
- 驱动依赖通过 Maven 或受控内部仓库获取。
- 不在镜像中写入生产资产库和凭证。
- 容器以非 root 用户运行。
- 文件系统默认只读，仅为必要目录挂载读写卷。

### 22.2 数据卷

建议分离：

```text
/data/assets/       SQLite 资产文件
/data/known-hosts/  SSH known_hosts
/data/audit/        可选本地审计缓冲
/tmp/               有限临时目录
```

生产部署通过 `DBA_MCP_HOST_BASE_DIR` 指定上述宿主机挂载项的唯一根目录；配置、资产库存、`known_hosts` 和审计目录必须位于该根目录的固定子路径，避免在部署配置中分别指定任意宿主机路径。使用内置资产管理 API 时，`assets/` 目录是必要的可写挂载，以支持 SQLite 数据库及其 journal/WAL 文件；配置与 `known_hosts` 继续只读。容器必须始终以非 root 用户运行；部署脚本默认采用启动脚本的非 root 宿主机 UID/GID，root 启动时回落为 `10001:10001` 并调整这些受控挂载目录的归属。

默认 Java 临时目录必须使用 `noexec`。构建镜像时从 SQLite JDBC JAR 提取与目标 Linux 架构匹配的原生库，并通过 `-Dorg.sqlite.lib.path` 从镜像内只读路径显式加载；不得将通用临时目录改为可执行。

资产 SQLite 文件应只授予服务账号最小必要权限。

### 22.3 健康检查

存活检查只验证进程和 MCP 服务状态，不访问所有外部资产。

就绪检查可验证：

- 资产仓储可读。
- Secret Provider 基础可用。
- 必要配置合法。

不得在每次健康检查中连接全部生产数据库或主机。

## 23. 分阶段实施计划

### 阶段 0：需求与安全基线

- 确定 MCP 客户端和部署方式。
- 收集数据库版本矩阵。
- 确定 SSH 固定动作清单。
- 确定身份提供方和 Secret Provider。
- 确定只读账号权限。

### 阶段 1：项目骨架与 MCP 链路

- 已初始化 Maven Wrapper、Spring Boot 4 和测试基线。
- 已接入 Spring AI MCP Server 2.x。
- 已配置 STDIO 与 Streamable HTTP Profile；HTTP `/mcp` 默认仅监听 loopback，且未配置运行时 Bearer token 时故障关闭，并校验浏览器 Origin。
- 统一错误模型、请求上下文和 MCP 协议兼容性测试将在资产工具的结构化错误转换实现时补齐。
- 已注册无外部目标依赖的只读资产工具，作为客户端兼容性验证面。

### 阶段 2：资产模块

- 已实现资产、关系、查询分页等领域模型及自关联防护。
- 已使用 Flyway `flyway_schema_history` 管理 SQLite schema。既有未追踪的线上 V1 库首次升级时基线标记为 V1，再应用后续迁移；可写本地开发库自动迁移，生产只读快照不执行写入。
- 已实现 `SqliteAssetRepository`。
- 已实现资产列表、详情、关联和有界拓扑 MCP 工具。
- 已实现 HTTP Basic Auth 保护的资产库存 REST CRUD、关系 CRUD、乐观锁逻辑删除/恢复和 OpenAPI/Swagger UI 文档；数据库密码为只写字段。
- 资产缓存和原子只读快照刷新待后续实现。

### 阶段 3：数据库连接层

- 不单独实现 Secret Provider；连接串及非秘密 JDBC 驱动属性保存在 SQLite `database_detail`。资产库存管理 REST API 可在 `connection_properties.password` 中写入数据库密码，但该字段只写且绝不返回或记录；令牌、SSH 私钥和钱包密钥仍不得存入资产库或源码。SQLite 文件必须采用最小权限、加密（如可用）、轮换和日志脱敏。
- 已实现 Oracle 有界热点 HikariCP 池注册表：默认最多缓存 128 个目标池、每池 1 个连接、空闲 1 分钟淘汰并关闭；一次性连接测试不进入池。资产数量不会线性转化为常驻池或空闲数据库会话。
- 已接入 Oracle JDBC Thin（ojdbc11）。
- 接入 OceanBase Connector/J。
- 接入达梦 JDBC。
- 实现连接测试、Schema、对象和字段查询。

### 阶段 4：查询与 DBA 检查

- 已实现 Oracle 单语句只读 SQL 词法策略，作为服务端固定查询执行路径的防御性校验；未将通用只读 SQL 查询注册为 MCP 工具。
- 已将所有已注册 Oracle MCP 工具迁移至 `oracle.` 命名空间。
- 已实现 Oracle Schema 与 Schema 内表的固定数据字典查询。
- 已实现 Oracle 查询超时、行数、单元格和响应大小限制。
- 已实现 Oracle 固定 `DBA_USERS` 用户目录查询，提供显式锁定状态且不返回密码相关或最近登录信息。
- 已实现 `oracle.unlockUser` 这一唯一受控数据库写操作：默认关闭，须经运行时和资产级双重启用；只针对锁定账户，并以固定字典查询拒绝 DBA 类角色、广泛系统权限和 password-file 管理权限。数据库详情 schema 已增加 `user_unlock_enabled`，既有 SQLite 库启动时迁移该字段。
- 已实现 Oracle 固定表空间容量查询，原样返回数据字典中的永久/临时表空间、数据文件及临时空间头部字段，不在服务端计算汇总、空闲量或使用率。
- 已实现 Oracle 固定表定义查询，使用绑定参数查询表属性、字段、约束和索引，且不返回默认表达式。
- 已实现 Oracle 用户会话汇总、长事务和本实例阻塞会话的固定诊断查询；不返回 SQL 文本、客户端标识、操作系统用户或事务标识。阻塞会话工具不使用 `GV$SESSION`，因此不补全 RAC 远程阻塞方。
- 建立版本化 DBA 检查项目录。
- 实现脱敏和统一数据库错误分类。

### 阶段 5：SSH 与 OGG

- 接入 SSHJ。
- 实现 known_hosts 和密钥认证。
- 实现固定动作注册表。
- 实现 OGG 资产和进程查询。
- 实现受控配置文件读取和脱敏。

### 阶段 6：生产化

- 接入认证和目标级 RBAC。
- 完成审计、指标和链路追踪。
- 已提供 Java 21 多阶段 Docker 镜像、非 root 运行时、生产 Compose 模板及单容器启动脚本；Spring 配置目录、资产快照与 SSH `known_hosts` 仅能以外部只读卷挂载，运行时秘密仅从部署环境注入。
- 已提供 GitHub Actions：所有 PR、main 推送均执行 Maven 校验、Compose 配置校验和镜像构建；`v*` 标签在校验通过后使用 CNB Docker 凭证发布 `docker.cnb.cool/dextercai/docker/dba_mcp` 镜像的 `latest` 和 UTC 时间戳标签。生产部署必须固定镜像 digest。
- 完成并发、压力和故障注入测试。
- 完成依赖漏洞和配置安全检查。

## 24. 第一阶段验收条件

- MCP 客户端能够发现并调用工具。
- STDIO 和 Streamable HTTP 均可运行。
- HTTP 模式启用认证和 Origin 校验。
- 能从 SQLite 查询资产、关系和主机组件。
- 能连接三类数据库并执行基础元数据查询。
- 所有数据库固定查询都受只读、超时、行数和大小限制；MCP 不提供通用 SQL 查询入口。
- SSH 不存在任意命令入口。
- 配置文件不存在任意路径读取入口。
- 凭证正文不出现在共享配置、响应和日志中；本地开发 SQLite 凭证文件必须被版本控制排除。
- 所有外部操作均生成可检索审计记录。
- 单个目标故障不会耗尽整个服务的连接和执行资源。

## 25. 待确认事项

实施前仍需确认：

- MCP 客户端种类及其协议版本。
- 生产是否只使用 Streamable HTTP。
- Oracle、OceanBase 和达梦的具体版本。
- Oracle 是否包含 RAC、Data Guard 或 CDB/PDB。
- OceanBase 是否通过 ODP 连接。
- 达梦是否包含主备集群。
- OGG 使用 Classic 还是 Microservices Architecture。
- 是否经过 SSH 堡垒机。
- 资产 SQLite 由本服务维护还是由外部系统生成。
- 使用 Vault、Kubernetes Secret 还是其他凭证服务。
- 需要开放的 DBA 检查项和 SSH 动作清单。
- 数据库查询结果的脱敏规则和数据分级要求。

## 26. 后续文档

进入开发阶段后，应继续补充：

- `tool-contracts.md`：每个 MCP 工具的输入、输出、错误和权限。
- `security-model.md`：威胁模型、角色、授权矩阵和审计要求。
- `deployment.md`：环境变量、Secret、Docker 和 Kubernetes 部署说明。
- `database-compatibility.md`：数据库、JDK 和 JDBC 驱动兼容矩阵。
- `host-actions.md`：固定 SSH 动作、参数和 sudoers 配置。
- `db-check-catalog.md`：预定义 DBA 检查项及各数据库实现。
- `assets-sqlite.md`：本地 SQLite 资产库存的配置、schema、运行模式与维护约束。

## 27. 参考资料

- [MCP Java SDK Server](https://java.sdk.modelcontextprotocol.io/latest/server/)
- [MCP Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [OceanBase Connector/J](https://en.oceanbase.com/docs/oceanbase-connector-j-en)
- [达梦 JDBC 文档](https://eco.dameng.com/document/dm/zh-cn/start/JAVA_NEW.html)
- [Oracle JDBC 文档](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/)
- [SSHJ](https://github.com/hierynomus/sshj)
