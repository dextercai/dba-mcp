#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${DBA_MCP_ENV_FILE:-"$script_dir/.env.production"}
if [ ! -r "$env_file" ]; then
  echo "Deployment environment file is missing or unreadable: $env_file" >&2
  echo "Copy deploy/docker/.env.production.example to a protected location and set DBA_MCP_ENV_FILE." >&2
  exit 1
fi
set -a
. "$env_file"
set +a

: "${DBA_MCP_HOST_BASE_DIR:?DBA_MCP_HOST_BASE_DIR must be set}"
case "$DBA_MCP_HOST_BASE_DIR" in
  /*) ;;
  *) echo "DBA_MCP_HOST_BASE_DIR must be an absolute path" >&2; exit 1 ;;
esac
host_uid=$(id -u)
host_gid=$(id -g)
if [ -z "${DBA_MCP_CONTAINER_UID:-}" ]; then
  DBA_MCP_CONTAINER_UID=$host_uid
  [ "$host_uid" -eq 0 ] && DBA_MCP_CONTAINER_UID=10001
fi
if [ -z "${DBA_MCP_CONTAINER_GID:-}" ]; then
  DBA_MCP_CONTAINER_GID=$host_gid
  [ "$host_uid" -eq 0 ] && DBA_MCP_CONTAINER_GID=10001
fi
case "$DBA_MCP_CONTAINER_UID:$DBA_MCP_CONTAINER_GID" in
  *[!0-9:]*|:*|*:|0:*) echo "DBA_MCP_CONTAINER_UID and DBA_MCP_CONTAINER_GID must be non-root numeric IDs" >&2; exit 1 ;;
esac
config_dir=$DBA_MCP_HOST_BASE_DIR/config
assets_dir=$DBA_MCP_HOST_BASE_DIR/assets
assets_file=$assets_dir/dba-mcp-assets.db
known_hosts_file=$DBA_MCP_HOST_BASE_DIR/known-hosts/known_hosts
audit_dir=$DBA_MCP_HOST_BASE_DIR/audit
umask 077
mkdir -p "$config_dir" "$assets_dir" "$(dirname "$known_hosts_file")" "$audit_dir"
[ -e "$known_hosts_file" ] || : > "$known_hosts_file"
if [ "$host_uid" -eq 0 ]; then
  chown -R "$DBA_MCP_CONTAINER_UID:$DBA_MCP_CONTAINER_GID" "$config_dir" "$assets_dir" "$(dirname "$known_hosts_file")" "$audit_dir"
fi
if [ -e "$assets_file" ] && { [ ! -f "$assets_file" ] || [ ! -r "$assets_file" ] || [ ! -w "$assets_file" ]; }; then
  echo "Existing assets database must be a readable and writable regular file: $assets_file" >&2
  exit 1
fi
if [ ! -r "$config_dir" ] || [ ! -r "$known_hosts_file" ] || [ ! -w "$assets_dir" ] || [ ! -w "$audit_dir" ]; then
  echo "Deployment account lacks required access under: $DBA_MCP_HOST_BASE_DIR" >&2
  exit 1
fi

exec docker run --detach --name "${DBA_MCP_CONTAINER_NAME:-dba-mcp}" --restart unless-stopped --init \
  --user "$DBA_MCP_CONTAINER_UID:$DBA_MCP_CONTAINER_GID" \
  --env-file "$env_file" \
  --env SPRING_PROFILES_ACTIVE=http \
  --env DBA_HTTP_ADDRESS=0.0.0.0 \
  --env DBA_HTTP_PORT=8080 \
  --env DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db \
  --env DBA_ASSETS_READ_ONLY=false \
  --env SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/ \
  --env 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp/dba-mcp -Dorg.sqlite.lib.path=/opt/sqlite -Dorg.sqlite.lib.name=libsqlitejdbc.so' \
  --publish "${DBA_MCP_BIND_ADDRESS:-127.0.0.1}:${DBA_MCP_HOST_PORT:-8080}:8080" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/config,dst=/config,readonly" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/assets,dst=/data/assets" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/known-hosts/known_hosts,dst=/data/known-hosts/known_hosts,readonly" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/audit,dst=/data/audit" \
  --tmpfs "/tmp/dba-mcp:rw,noexec,nosuid,size=64m,uid=$DBA_MCP_CONTAINER_UID,gid=$DBA_MCP_CONTAINER_GID,mode=1770" \
  --read-only --security-opt no-new-privileges:true --cap-drop ALL \
  "$DBA_MCP_IMAGE"
