#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
env_file=${DBA_MCP_ENV_FILE:-"$script_dir/.env.production"}
set -a
. "$env_file"
set +a

: "${DBA_MCP_HOST_BASE_DIR:?DBA_MCP_HOST_BASE_DIR must be set}"
case "$DBA_MCP_HOST_BASE_DIR" in
  /*) ;;
  *) echo "DBA_MCP_HOST_BASE_DIR must be an absolute path" >&2; exit 1 ;;
esac
config_dir=$DBA_MCP_HOST_BASE_DIR/config
assets_dir=$DBA_MCP_HOST_BASE_DIR/assets
assets_file=$assets_dir/dba-mcp-assets.db
known_hosts_file=$DBA_MCP_HOST_BASE_DIR/known-hosts/known_hosts
audit_dir=$DBA_MCP_HOST_BASE_DIR/audit
if [ ! -d "$config_dir" ] || [ ! -r "$config_dir" ]; then
  echo "Configuration directory must exist and be readable: $config_dir" >&2
  exit 1
fi
if [ ! -d "$assets_dir" ] || [ ! -r "$assets_dir" ] || [ ! -w "$assets_dir" ]; then
  echo "Assets directory must exist and be readable and writable: $assets_dir" >&2
  exit 1
fi
if [ -e "$assets_file" ] && { [ ! -f "$assets_file" ] || [ ! -r "$assets_file" ] || [ ! -w "$assets_file" ]; }; then
  echo "Existing assets database must be a readable and writable regular file: $assets_file" >&2
  exit 1
fi
for path in "$known_hosts_file"; do
  if [ ! -f "$path" ] || [ ! -r "$path" ]; then
    echo "Required readable file is missing: $path" >&2
    exit 1
  fi
done
if [ ! -d "$audit_dir" ] || [ ! -w "$audit_dir" ]; then
  echo "Audit directory must exist and be writable: $audit_dir" >&2
  exit 1
fi

exec docker run --detach --name "${DBA_MCP_CONTAINER_NAME:-dba-mcp}" --restart unless-stopped --init \
  --env-file "$env_file" \
  --env SPRING_PROFILES_ACTIVE=http \
  --env DBA_HTTP_ADDRESS=0.0.0.0 \
  --env DBA_HTTP_PORT=8080 \
  --env DBA_ASSETS_JDBC_URL=jdbc:sqlite:/data/assets/dba-mcp-assets.db \
  --env DBA_ASSETS_READ_ONLY=false \
  --env SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/config/ \
  --publish "${DBA_MCP_BIND_ADDRESS:-127.0.0.1}:${DBA_MCP_HOST_PORT:-8080}:8080" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/config,dst=/config,readonly" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/assets,dst=/data/assets" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/known-hosts/known_hosts,dst=/data/known-hosts/known_hosts,readonly" \
  --mount "type=bind,src=$DBA_MCP_HOST_BASE_DIR/audit,dst=/data/audit" \
  --tmpfs /tmp/dba-mcp:rw,noexec,nosuid,size=64m,uid=10001,gid=10001,mode=1770 \
  --read-only --security-opt no-new-privileges:true --cap-drop ALL \
  "$DBA_MCP_IMAGE"
