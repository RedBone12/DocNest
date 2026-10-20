# Local backup and restore drill

Run from the repository root with Docker Desktop (Linux containers), PowerShell and the official AWS CLI v2 installed:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/recovery-drill.ps1
```

Despite the client name, every S3 request uses an explicit `http://127.0.0.1:<random-port>` endpoint and temporary synthetic credentials. No AWS cloud resource or account is used. The script preserves and restores process AWS credential environment variables. The example does not use real document/account data.

The drill creates uniquely named and labeled PostgreSQL 15 and MinIO containers, seeds one metadata row with a storage key and SHA-256 and uploads one text object. It freezes synthetic writes, creates a PostgreSQL custom-format dump and copies the stopped MinIO data directory. It restores into a second independent database/storage pair, checks the metadata and downloads the restored object to verify its SHA-256. The source and restored stores are different containers; checking only that a backup file exists is not sufficient.

Only containers created by this run and bearing its exact owner label are removed. Existing `docnest-app`/`infra` containers and volumes are untouched. Synthetic backups and result.json remain under `.local-recovery/<run-id>/`, ignored by Git. An explicit OutputRoot can be provided for local evidence. Do not commit real backups; they can include account records, password hashes, credentials and private document contents.

Local verification on 2026-10-20 passed with one row and one object. One measured restore phase took about 3 seconds; this tiny synthetic run is not a production recovery-time guarantee. CI also runs the drill with disposable test data and does not upload its raw backups.

## Limits and real-data recovery procedure

This is a storage-level drill, not a complete application disaster-recovery certification. It does not restore Kafka, Redis, Elasticsearch or user authentication sessions, and does not demonstrate recovery of a full live tenant. MinIO cold-copy recovery is tested with the same pinned local image; upgrades and cross-version recovery need separate testing.

For a real application backup, announce a maintenance window, stop document uploads/deletions and indexing writers, ensure asynchronous jobs are drained or record outstanding offsets, and freeze writes. Take the PostgreSQL dump and stopped MinIO snapshot as a consistent pair; record versions, image digests, timestamp and checksums. Keep backups encrypted with restricted access, separate from the running data, and define retention. Copying changing database/object files without quiescing writers is not this procedure.

Restore only into a newly created isolated deployment, never overwrite the original volumes. Restore the metadata and corresponding objects, verify owner/storage-key consistency, and decide explicitly whether to restore search/index state or rebuild from retained source documents. Validate registration/login, owner isolation, existing-document download, reindex/search and deletion before switching traffic. The current repository still needs a controlled full-tenant reindex/recovery workflow before claiming full disaster recovery. Preserve the old deployment for rollback until verification succeeds.
