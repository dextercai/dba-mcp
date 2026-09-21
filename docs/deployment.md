# 部署与运行

## 本地运行

HTTP Streamable MCP：`SPRING_PROFILES_ACTIVE=http DBA_MCP_API_TOKEN=<运行时秘密> ./mvnw spring-boot:run`。默认仅监听 `127.0.0.1:8080`，端点为 `/mcp`。

STDIO：`SPRING_PROFILES_ACTIVE=stdio ./mvnw spring-boot:run`。STDIO 模式严禁向标准输出写入普通日志。

资产库由 `DBA_ASSETS_JDBC_URL` 指定。默认生产部署启用受认证的资产管理 API，因此使用 `DBA_ASSETS_READ_ONLY=false`，并把整个资产目录以可写方式挂载，使 SQLite 可以创建 journal/WAL 辅助文件。若采用外部生成且不可由服务编辑的快照，才使用 `DBA_ASSETS_READ_ONLY=true` 和只读挂载。

## Docker 镜像

在仓库根目录执行以下命令构建镜像：

```sh
docker build --tag dba-mcp:local .
```

镜像使用 Java 21 多阶段构建，运行时仅包含 Spring Boot 可执行 JAR，并以 UID/GID `10001` 的非 root 用户运行。`.dockerignore` 排除了本地 `data/`、Git 元数据、构建产物和生产环境文件，因此资产 SQLite 文件和凭证不会进入镜像。

生产镜像由 GitHub Actions 在 `v*` 标签触发时发布到 `docker.cnb.cool/dextercai/docker/dba_mcp`，同时生成 `latest` 和 UTC 时间戳标签。生产部署必须固定到该镜像的 SHA-256 digest，而非可变标签。

## Docker Compose 生产部署

部署主机需要 Docker Engine 和 Compose plugin；将 TLS、公开入口、认证前置和速率限制交由反向代理或 API Gateway。Compose 默认只将服务发布至 `127.0.0.1:8080`。

1. 将 `deploy/docker/.env.production.example` 复制到仓库外的受限位置，例如 `/etc/dba-mcp/production.env`，写入实际的镜像 digest、运行时秘密和 `DBA_MCP_HOST_BASE_DIR` 绝对路径。文件权限应为 `0600`。
2. 在 `DBA_MCP_HOST_BASE_DIR` 下创建固定目录结构：`config/`、`assets/`、`known-hosts/known_hosts` 和 `audit/`。`assets/` 内的 `dba-mcp-assets.db` 可预先创建，也可由应用初始化；该目录必须对容器 UID `10001` 可读写。部署服务账号必须可读配置目录和 `known_hosts`；`audit/` 必须已存在且对容器 UID `10001` 可写。
3. 执行：

   ```sh
   DBA_MCP_ENV_FILE=/etc/dba-mcp/production.env ./deploy/docker/deploy.sh
   ```

`DBA_MCP_HOST_BASE_DIR` 是所有宿主机 bind mount 的唯一根目录；其中 `config/` 是非秘密 Spring 配置目录（例如 `application-http.yml`），以只读方式挂载到 `/config`，环境变量优先于其中的配置。`assets/` 以可写方式挂载到 `/data/assets`，仅供已认证的资产管理 API 修改 inventory；其余挂载保持只读或仅限审计写入。脚本在启动前验证必要的秘密、base directory、配置目录、资产目录、`known_hosts` 和审计目录，随后执行 `docker compose up -d --pull always`。它不会打印环境变量或秘密。应用容器启用了只读根文件系统、全部 Linux capability 移除、`no-new-privileges`、有限 tmpfs、PID/CPU/内存限制；只有 base directory 中固定的资产目录和审计目录可写。

## 单容器启动脚本

不使用 Compose 时，使用同一份环境文件启动：

```sh
DBA_MCP_ENV_FILE=/etc/dba-mcp/production.env ./deploy/docker/start-container.sh
```

该脚本直接读取环境文件并从 `DBA_MCP_HOST_BASE_DIR` 的固定目录结构挂载文件。`/config` 和 `known_hosts` 为只读；资产 SQLite 所在目录与审计目录是持久化可写挂载。

部署后通过受控反向代理向 `/mcp` 提供 HTTPS。不要直接把容器端口暴露到公共网络。资产库存 API 使用 Basic Auth，必须仅通过 HTTPS 访问；`DBA_ASSET_ADMIN_PASSWORD_HASH` 必须是 BCrypt 哈希。
