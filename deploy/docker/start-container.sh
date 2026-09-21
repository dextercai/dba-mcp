#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${DBA_MCP_ENV_FILE:-"$script_dir/.env.production"}
set -a
. "$env_file"
set +a

exec docker run --detach --name "${DBA_MCP_CONTAINER_NAME:-dba-mcp}" --restart unless-stopped --init \
  --env-file "$env_file" \
  --env SPRING_PROFILES_ACTIVE=http \
  --env DBA_HTTP_ADDRESS=0.0.0.0 \
  --env DBA_HTTP_PORT=8080 \
  --env DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db \
  --env DBA_ASSETS_READ_ONLY=true \
  --env SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/ \
  --publish "${DBA_MCP_BIND_ADDRESS:-127.0.0.1}:${DBA_MCP_HOST_PORT:-8080}:8080" \
  --mount "type=bind,src=$DBA_MCP_CONFIG_DIR,dst=/config,readonly" \
  --mount "type=bind,src=$DBA_MCP_ASSETS_FILE,dst=/data/assets/dba-mcp-assets.db,readonly" \
  --mount "type=bind,src=$DBA_MCP_KNOWN_HOSTS_FILE,dst=/data/known-hosts/known_hosts,readonly" \
  --mount "type=bind,src=$DBA_MCP_AUDIT_DIR,dst=/data/audit" \
  --tmpfs /tmp/dba-mcp:rw,noexec,nosuid,size=64m,uid=10001,gid=10001,mode=1770 \
  --read-only --security-opt no-new-privileges:true --cap-drop ALL \
  "$DBA_MCP_IMAGE"
