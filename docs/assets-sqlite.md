# Assets SQLite 数据库说明

## 用途与边界

Assets SQLite 是 DBA MCP 的资产库存：保存主机、数据库、OGG、受控配置资源等资产节点，以及它们之间的有向关系和类型化详情。它不是审计库，也不应被领域层直接依赖；应用层始终通过 `AssetRepository` 读取资产，以便未来替换为 CMDB 或其他资产源。

数据库工具和主机工具仅接收稳定的资产 ID，不接收调用方提供的地址、文件路径或凭证。资产中的 `CONFIG_RESOURCE` 仅用于登记受控资源，不能据此开放任意路径访问。

## 文件位置与运行模式

默认 JDBC URL 为 `jdbc:sqlite:./data/dba-mcp-assets.db`，相对于应用的工作目录。可使用环境变量调整：

```bash
DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db
DBA_ASSETS_READ_ONLY=true
```

| 模式 | `DBA_ASSETS_READ_ONLY` | 应用行为 |
| --- | --- | --- |
| 本地开发 | `false`（默认） | 自动创建数据库父目录，并在启动时执行 V1 schema 初始化。 |
| 生产或外部快照 | `true` | 不创建目录、不执行 schema 初始化，也不应修改资产文件。部署前必须已建立 schema。 |

生产快照更新应先在独立文件中完成校验，再以原子文件替换方式切换；不要在服务运行期间原地重写文件。资产库应与高频审计数据分离，并仅授予服务账号最小必要的文件权限。

## 初始化与查看

可写的本地默认库会在应用启动时初始化。也可以使用项目中的迁移脚本手动建立一个空库：

```bash
sqlite3 ./data/dba-mcp-assets.db < src/main/resources/db/migration/V1__asset_schema.sql
```

查看已建立的表和索引：

```bash
sqlite3 ./data/dba-mcp-assets.db ".tables"
sqlite3 ./data/dba-mcp-assets.db ".schema asset"
```

SQLite 外键检查必须开启（`PRAGMA foreign_keys = ON`）。应用建立资产数据源连接时会设置该选项和 `busy_timeout`；通过其他工具维护该库时也应在对应连接上开启外键检查。

## 数据模型

### 通用资产节点：`asset`

每一条资产记录具有稳定的 `id`、人类可读且唯一的 `asset_code`、`asset_type`、展示名称、环境、状态与审计时间。

| 字段 | 说明 |
| --- | --- |
| `id` | 稳定资产 ID；推荐 UUID，不能使用 IP 或主机名代替。 |
| `asset_type` | `HOST`、`DATABASE_CLUSTER`、`DATABASE_INSTANCE`、`DATABASE_SERVICE`、`OGG_DEPLOYMENT`、`OGG_PROCESS` 或 `CONFIG_RESOURCE`。 |
| `asset_code` | 唯一业务编码，供人员识别和工具调用定位。 |
| `environment` / `status` | 环境标识与资产状态；状态包括 `ACTIVE`、`DISABLED`、`RETIRED`。 |
| `source_name` / `external_id` | 外部资产源及其标识；两者组合在 `external_id` 非空时唯一。 |
| `labels_json` | 可用于筛选的标签对象。 |
| `metadata_json` | 非关键、非稳定的扩展属性；连接、授权和校验所需字段不得放入这里。 |
| `version` | 乐观锁版本号。 |
| `created_at` / `updated_at` | UTC ISO-8601 时间戳。 |

### 资产关系：`asset_relation`

关系由 `source_asset_id --relation_type--> target_asset_id` 表示，同一三元组不能重复。关系类型包括：

- `RUNS_ON`：数据库实例或 OGG 部署运行于主机。
- `MEMBER_OF`、`EXPOSED_AS`、`PART_OF`、`CONFIG_OF`：归属或暴露关系。
- `CAPTURES_FROM`、`REPLICATES_TO`：OGG 与数据库的复制关系。
- `CONNECTS_TO`、`DEPENDS_ON`：其他明确的连接或依赖关系。

例如，数据库实例通过 `RUNS_ON` 指向主机；OGG Extract 通过 `CAPTURES_FROM` 指向来源数据库。关系属性存入 `attributes_json`，仅用于非关键扩展信息。

### 类型化详情表

| 表 | 对应资产类型 | 主要内容 |
| --- | --- | --- |
| `host_detail` | `HOST` | 主机名、管理 IP、SSH 端口、操作系统、跳板机资产和 SSH 凭证引用。 |
| `database_detail` | 数据库实例/服务 | 数据库类型、网络连接字段、租户/集群信息、驱动属性、只读标记和凭证引用。 |
| `ogg_deployment_detail` | `OGG_DEPLOYMENT` | OGG 版本、部署模式、安装/部署目录及服务端口。 |
| `ogg_process_detail` | `OGG_PROCESS` | 进程类型、名称、参数/报告文件和启用状态。 |
| `config_resource_detail` | `CONFIG_RESOURCE` | 已登记资源的逻辑名称、绝对路径、读取上限与脱敏策略。 |

`database_detail.database_type` 限定为 `ORACLE`、`OCEANBASE_ORACLE` 或 `DAMENG`。`config_resource_detail.writable` 受 schema 约束，始终为 `0`；第一阶段不支持通过 MCP 修改配置资源。

## 常用只读查询

以下示例仅用于检查资产库存，不包含凭证正文或敏感配置内容：

```sql
-- 按类型和环境查看启用资产
SELECT id, asset_code, display_name, environment, status
FROM asset
WHERE asset_type = 'DATABASE_INSTANCE'
  AND environment = 'prod'
  AND status = 'ACTIVE'
ORDER BY asset_code;

-- 查询某资产的出向关系
SELECT r.relation_type, target.id, target.asset_code, target.asset_type
FROM asset_relation r
JOIN asset AS target ON target.id = r.target_asset_id
WHERE r.source_asset_id = :asset_id
ORDER BY r.relation_type, target.asset_code;

-- 查看数据库资产的非秘密连接信息
SELECT a.id, a.asset_code, d.database_type, d.host, d.port,
       d.service_name, d.database_name, d.tenant_name, d.read_only,
       d.credential_ref
FROM asset AS a
JOIN database_detail AS d ON d.asset_id = a.id
WHERE a.id = :asset_id;
```

## 安全与维护要求

- 可经受 HTTPS 保护的 Basic Auth 资产管理 API 将数据库密码写入 `database_detail.connection_properties.password`。该字段仅可写入，绝不能由 REST、MCP、日志、审计参数或错误信息返回；查询示例也不得选择或展开 `connection_properties`。
- 令牌、SSH 私钥和钱包密钥仍只允许保存外部 `credential_ref`，不得写入 SQLite、源码、日志或任何响应。
- 含有数据库密码的 SQLite 文件必须被版本控制排除，采用最小文件权限、加密（如可用）、运行时主密钥、轮换和日志脱敏措施；不得复制到镜像或非必要环境。
- 优先将资产标记为 `RETIRED` 或 `DISABLED`，不要直接物理删除，以保留关系和审计可追溯性。
- 修改前应备份并验证新文件；外部生成的快照必须以只读模式提供给应用。
- 本文描述的 schema 当前对应 `src/main/resources/db/migration/V1__asset_schema.sql`。修改 schema、资产类型、关系约束或安全边界时，必须同步更新本文件和 `docs/architecture-plan.md`。

## 管理 API

HTTP Profile 通过 Basic Auth 提供资产和关系的 CRUD：`/api/v1/assets` 与 `/api/v1/asset-relations`。删除资产会将其标记为 `RETIRED`，不会物理删除；更新和状态变更必须传递当前 `version`，以使用乐观锁。

数据库详情使用 `detail` 对象中的 SQLite 列名，例如 `database_type`、`host`、`port` 和 `connection_properties`。`connection_properties.password` 可写但不会回显。完整的机器可读契约访问 `/v3/api-docs`，交互文档访问 `/swagger-ui/index.html`。
