# Project Instructions

## Required planning baseline

Before planning, designing, reviewing, or changing this repository, read
`docs/architecture-plan.md` completely and treat it as the project's current
architecture and implementation baseline.

Do not silently diverge from that plan. If a request conflicts with it or
requires a material architectural change:

1. Identify the conflicting decision and its impact.
2. Ask for confirmation when the change would materially alter security,
   scope, persistence, protocol, deployment, or connector architecture.
3. Update `docs/architecture-plan.md` when the user approves the change.
4. Keep implementation and documentation consistent.

The user's explicit request in the current task may revise the plan. Treat such
a revision as a design change that must be reflected in the planning document.

## Current technical baseline

- Use Java 21 as the primary runtime.
- Use Maven Wrapper for builds.
- Use Spring Boot with Spring WebMVC.
- Use the maintained MCP Java SDK 2.x / compatible Spring AI MCP server stack.
- Use STDIO for local development and Streamable HTTP for production.
- Start as a modular monolith; do not introduce microservices without a
  demonstrated need and explicit approval.
- Use JDBC adapters for Oracle, OceanBase Oracle mode, and Dameng.
- Use an independent HikariCP pool for each database asset.
- Use SSHJ for SSH operations.
- Keep infrastructure implementations behind domain/application interfaces.

## Mandatory security constraints

- Database access is read-only by default.
- Do not add a generic DDL, DML, PL/SQL, stored-procedure, or database mutation
  tool without explicit approval and a dedicated permission model.
- Never expose arbitrary SSH command execution.
- SSH tools accept only registered action IDs and strongly typed parameters.
- Never expose arbitrary filesystem paths.
- Configuration files may be accessed only through registered
  `CONFIG_RESOURCE` assets.
- Production inventory and repository configuration should store only secret
  references. For explicitly local development, credentials may be kept in an
  ignored SQLite file when this materially simplifies setup; do not commit that
  file or copy it to shared environments. Prefer encrypted storage, a runtime
  master key, restrictive file permissions, rotation, and redacted logs.
- Apply authentication, target-level authorization, timeouts, concurrency
  limits, row limits, response-size limits, masking, and auditing to external
  operations.
- Do not rely on regular expressions alone to prove that SQL is safe.
- Do not disable SSH host-key verification.
- Do not include secrets, complete sensitive results, or server stack traces in
  MCP responses or normal logs.

## Asset model constraints

- Model inventory as typed asset nodes plus explicit directed relations.
- Do not embed all databases, OGG deployments, processes, and files in one host
  JSON document.
- Use stable asset IDs in MCP tools instead of accepting raw addresses and
  credentials from callers.
- Keep commonly queried and validated fields in typed detail tables; use JSON
  only for noncritical extension attributes.
- Keep credentials separate from inventory records in shared and production
  deployments. A local, ignored development SQLite inventory may contain test
  credentials when the developer explicitly chooses that trade-off.
- Access inventory through `AssetRepository`; SQLite is one implementation,
  not a domain dependency.
- Keep the SQLite asset store separate from high-frequency audit storage.

## Implementation discipline

- Implement the smallest safe vertical slice that follows the staged plan.
- Prefer predefined database checks over unrestricted SQL.
- Keep vendor-specific SQL, metadata, error handling, and limit syntax inside
  database dialect adapters.
- Ensure one failed or slow target cannot exhaust global threads, database
  connections, or response memory.
- Add tests for authorization, injection attempts, timeout behavior, result
  truncation, path traversal, host-key mismatch, and secret redaction whenever
  the related capability is introduced.
- Preserve unrelated user changes in the working tree.

## Documentation maintenance

When a change affects architecture, tool contracts, asset schema, security,
deployment, or the implementation stages, update the relevant documentation in
the same task. The primary architecture record is:

- `docs/architecture-plan.md`

Planned supporting documents are listed in that file and should be added as
their corresponding implementation work begins.
