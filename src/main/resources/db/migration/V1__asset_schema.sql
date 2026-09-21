CREATE TABLE IF NOT EXISTS asset (
    id TEXT PRIMARY KEY, asset_type TEXT NOT NULL, asset_code TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL, environment TEXT, status TEXT NOT NULL DEFAULT 'ACTIVE',
    source_name TEXT NOT NULL DEFAULT 'local', external_id TEXT, labels_json TEXT NOT NULL DEFAULT '{}',
    metadata_json TEXT NOT NULL DEFAULT '{}', version INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_asset_external_identity ON asset(source_name, external_id) WHERE external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_asset_type_status ON asset(asset_type, status);
CREATE TABLE IF NOT EXISTS asset_relation (
    id TEXT PRIMARY KEY, source_asset_id TEXT NOT NULL, relation_type TEXT NOT NULL, target_asset_id TEXT NOT NULL,
    attributes_json TEXT NOT NULL DEFAULT '{}', created_at TEXT NOT NULL,
    FOREIGN KEY (source_asset_id) REFERENCES asset(id), FOREIGN KEY (target_asset_id) REFERENCES asset(id),
    UNIQUE (source_asset_id, relation_type, target_asset_id)
);
CREATE INDEX IF NOT EXISTS idx_relation_source ON asset_relation(source_asset_id, relation_type);
CREATE INDEX IF NOT EXISTS idx_relation_target ON asset_relation(target_asset_id, relation_type);
CREATE TABLE IF NOT EXISTS host_detail (
    asset_id TEXT PRIMARY KEY, hostname TEXT NOT NULL, management_ip TEXT NOT NULL, ssh_port INTEGER NOT NULL DEFAULT 22,
    os_type TEXT, os_version TEXT, architecture TEXT, ssh_credential_ref TEXT, bastion_asset_id TEXT,
    FOREIGN KEY (asset_id) REFERENCES asset(id), FOREIGN KEY (bastion_asset_id) REFERENCES asset(id)
);
CREATE TABLE IF NOT EXISTS database_detail (
    asset_id TEXT PRIMARY KEY, database_type TEXT NOT NULL, database_version TEXT, role TEXT, host TEXT, port INTEGER,
    service_name TEXT, database_name TEXT, tenant_name TEXT, cluster_name TEXT,
    connection_properties TEXT NOT NULL DEFAULT '{}', credential_ref TEXT NOT NULL, read_only INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
CREATE TABLE IF NOT EXISTS ogg_deployment_detail (
    asset_id TEXT PRIMARY KEY, ogg_version TEXT, deployment_mode TEXT NOT NULL, install_home TEXT NOT NULL,
    deployment_home TEXT, service_manager_port INTEGER, admin_server_port INTEGER, credential_ref TEXT,
    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
CREATE TABLE IF NOT EXISTS ogg_process_detail (
    asset_id TEXT PRIMARY KEY, process_type TEXT NOT NULL, process_name TEXT NOT NULL, parameter_file TEXT,
    report_file TEXT, trail_name TEXT, enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (asset_id) REFERENCES asset(id)
);
CREATE TABLE IF NOT EXISTS config_resource_detail (
    asset_id TEXT PRIMARY KEY, resource_type TEXT NOT NULL, logical_name TEXT NOT NULL, absolute_path TEXT NOT NULL,
    charset TEXT NOT NULL DEFAULT 'UTF-8', readable INTEGER NOT NULL DEFAULT 1, writable INTEGER NOT NULL DEFAULT 0,
    sensitive INTEGER NOT NULL DEFAULT 0, max_read_bytes INTEGER NOT NULL DEFAULT 1048576, masking_policy TEXT,
    FOREIGN KEY (asset_id) REFERENCES asset(id), CHECK (writable = 0)
);
