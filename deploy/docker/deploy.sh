#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
env_file=${DBA_MCP_ENV_FILE:-"$script_dir/.env.production"}
if [ ! -r "$env_file" ]; then
  echo "Deployment environment file is missing or unreadable: $env_file" >&2
  echo "Copy deploy/docker/.env.production.example to a protected location and set DBA_MCP_ENV_FILE." >&2
  exit 1
fi
set -a
. "$env_file"
set +a
required_values='DBA_MCP_IMAGE DBA_MCP_API_TOKEN DBA_MCP_ALLOWED_ORIGINS DBA_ASSET_ADMIN_USERNAME DBA_ASSET_ADMIN_PASSWORD_HASH DBA_MCP_HOST_BASE_DIR'
for name in $required_values; do
  value=$(printenv "$name" || true)
  if [ -z "$value" ] || printf '%s' "$value" | grep -q 'REPLACE_WITH'; then
    echo "Required deployment value is not set: $name" >&2
    exit 1
  fi
done
if ! printf '%s' "$DBA_MCP_IMAGE" | grep -Eq '^.+@sha256:[[:xdigit:]]{64}$'; then
  echo "DBA_MCP_IMAGE must be pinned to a SHA-256 digest" >&2
  exit 1
fi
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
export DBA_MCP_CONTAINER_UID DBA_MCP_CONTAINER_GID
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
exec docker compose -f "$project_dir/docker-compose.production.yml" up -d --pull always --remove-orphans
