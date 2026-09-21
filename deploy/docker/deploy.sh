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
  eval "value=\${$name:-}"
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
if [ ! -d "$DBA_MCP_HOST_BASE_DIR" ]; then
  echo "Host base directory must exist: $DBA_MCP_HOST_BASE_DIR" >&2
  exit 1
fi
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
umask 077
exec docker compose --env-file "$env_file" -f "$project_dir/docker-compose.production.yml" up -d --pull always --remove-orphans
