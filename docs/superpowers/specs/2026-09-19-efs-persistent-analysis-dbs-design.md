# Design: Persist analysis Anki DBs across ECS task restarts via EFS

Date: 2026-09-19
Status: Approved

## Problem

Uploaded Anki collection DBs are written by `AnalysisService.uploadDB` to
`SmortProperties.getAnkiDbDirectory()`, i.e.
`Path.of(System.getProperty("user.home"), baseDir, analysisDbDirectoryName)`.
On Fargate the task runs as root, so this resolves to
`/root/<BASE_DATA_DIR>/<ANALYSIS_DB_DIRECTORY_NAME>/`.

Fargate task storage is ephemeral: the ECS task definition
(`terraform/modules/ecs/main.tf`) mounts no volumes, so every restart, redeploy,
or task replacement gets a fresh filesystem. Analysis meta rows in DynamoDB
still reference the old DB paths, but the DB files they point at are gone.

## Goal

Make the SQLite DB files survive task restarts so existing analyses remain
usable after any new task starts. Chosen approach: attach an EFS filesystem to
the Fargate task and mount it at the app's data directory.

## Approach

Approach 1 (EFS) selected over:

- **S3 + sync-on-access**: durable and cheap, but requires app changes to copy
  DBs to/from object storage around EMF lifecycle. Rejected: more moving parts.
- **Relocate data to DynamoDB**: largest refactor, kills the per-analysis
  SQLite design. Rejected: out of scope.

EFS works with Fargate (EBS does not), keeps DBs as real files, and requires no
I/O logic changes. Latency of a few ms is acceptable: a DB is written once at
upload and read on demand. Cost is negligible at DB sizes <= 50MB.

## Architecture

EFS resources are defined directly inside the ECS module (chosen to avoid a
module dependency cycle: the EFS SG needs the task SG id, and the task needs
the EFS file system/access point ids — with both in one module the reference
is internal and acyclic):

`terraform/modules/ecs/main.tf` additions:

- `aws_efs_file_system` — general purpose, bursting throughput, default KMS
  encryption enabled.
- `aws_efs_access_point` — single access point pointing at the filesystem root
  (`posix_user` UID/GID 0); the container runs as root, so files are already
  owned by uid 0. No `creation_info` (AWS rejects it with `path = "/"`).
- `aws_efs_mount_target` — one per AZ in the existing private subnets
  (`eu-central-1a`, `eu-central-1b`).
- `aws_security_group` — ingress TCP 2049 from the ECS task security group
  (`aws_security_group.task`, in the same module) only. Task egress is already
  all-open.

ECS task definition changes (same file):

- Add `volume` block:
  - `efs_volume_configuration` with `file_system_id`, `transit_encryption =
    "ENABLED"` (TLS on port 2049), and `authorization_config` with the
    `access_point_id` + `iam = "DISABLED"`.
- Add container `mountPoints`:
  - `sourceVolume = "efs-data"`, `containerPath` = the `BASE_DATA_DIR` value.

Knit together in `terraform/main.tf` and module inputs so the mount point and
the `BASE_DATA_DIR` env value always reference the same string:
`aws_ssm_parameter.base_data_dir.value` is passed into the ECS module as the
container data path. No drift possible.

## App changes

`SmortProperties.getAnkiDbDirectory()` (`SmortProperties.java:20-22`): drop the
`user.home` prefix so the data directory resolves as
`Path.of(baseDir, analysisDbDirectoryName)`.

Operational requirement: the SSM value of `BASE_DATA_DIR` must be set to the
mount path (e.g. `/data`) so the app writes under the mounted filesystem.

## Data flow

- Upload: `Files.write(dbPath, bytes, ...)` writes to
  `<mount>/<ANALYSIS_DB_DIRECTORY_NAME>/<analysisId>` — same code path, now on
  EFS.
- Restart/redeploy: the new task mounts the same filesystem, so DynamoDB paths
  resolve on the new task without any recovery logic.
- Cleanup: `CleanupCron.deleteAnalysesMarkedForDeletion` deletes DB files via
  `Files.deleteIfExists` — works unchanged on EFS.

## Error handling

EFS attach failures surface as task start/health-check failures; there is no
in-app handling needed. `transitEncryption = ENABLED` requires mount targets
supporting TLS on 2049 (default).

## Testing

No unit tests: the change is Terraform infrastructure plus a one-line path
change. Build/compile skipped per AGENTS.md. Verification is
`terraform plan`/`apply` by the human, followed by: upload a collection, force a
task replacement, confirm the analysis remains readable.

## Caveats

- Existing DBs currently in ephemeral storage cannot be migrated; they vanish
  with the old task. Acceptable — they were ephemeral by design.
- SQLite-over-EFS locking is only safe with a single running task
  (`desired_count = 1`). If the service ever scales to multiple simultaneous
  tasks, this design would need revisiting.