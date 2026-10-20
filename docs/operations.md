# DocNest operations

## Deployment scope

`deploy/compose.yaml` runs all four Java applications, the React frontend and private infrastructure. Its project and volumes are separate from the existing `infra` development project. All five application images have been built locally and the stack has reached healthy state. This is a single-host deployment, not a highly available production platform.

Only the frontend is bound to the host, on 127.0.0.1:8088 by default. Nginx proxies `/api/` to the private gateway. Database, Kafka, Redis, Elasticsearch, MinIO and management ports are not published. The frontend public API value is `/api`, not a credential.

## Start and verify

From the DocNest root, copy `deploy/.env.example` to `deploy/.env` if it does not already exist. Replace every secret placeholder with an independent random value. Keep JWT_SECRET identical across services. Set FRONTEND_URL to the exact browser origin. Keep BIND_ADDRESS=127.0.0.1 when using an HTTPS reverse proxy on the host.

```powershell
docker build -t docnest/minio-ci:2025-09-07 -f backend/distributed-document-search-engine-main/infra/minio-ci.Dockerfile backend/distributed-document-search-engine-main/infra
docker compose --env-file deploy/.env -f deploy/compose.yaml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build --wait --wait-timeout 600
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
```

Open http://localhost:8088. Register a disposable account, upload a text file, wait for READY, search for a unique phrase, download and compare contents, delete and verify disappearance. Run `npm.cmd run test:e2e` in the frontend directory to automate this acceptance flow. The browser test creates a new disposable user per run; do not run it against a production tenant without an agreed test-data retention policy. AI is disabled in this deployment unless explicitly configured separately.

Local verification on 2026-10-20: all five application images built, all application health checks passed, and the Chromium lifecycle acceptance test passed in approximately 8 seconds. This exercised real services, Kafka, object storage and Elasticsearch, not mocked browser APIs. The DLQ replay command is supplied for controlled recovery but has not been executed against a real failed record in this verification.

## Health and metrics

The container profile exposes management on private port 9090. `/actuator/health/liveness` checks process availability and `/actuator/health/readiness` checks Spring readiness. Readiness does not establish that every asynchronous dependency or full business flow is working; use the acceptance test as well. Health details are hidden. `/actuator/metrics` is enabled internally and remains subject to each service's security configuration. Never publish management port 9090. JVM/process/HTTP metrics are available through Actuator; Kafka lag, DLQ size and business throughput need additional monitoring configuration.

```powershell
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T api-gateway curl -fsS http://localhost:9090/actuator/health/readiness
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --since 10m indexing-service
```

Container logs use JSON with timestamp, level, logger, message, stack trace and service name. Keep external log collection access restricted: error messages can contain document metadata. Logs go to stdout for container collection. Do not print keys, bearer tokens or document contents during troubleshooting.

## Failure investigation

* 401: verify login, token expiry and server-side session revocation; authenticate again before changing infrastructure.
* 503: inspect gateway-to-document-service connectivity and `/auth/session`, then service health. Do not translate an outage into an expired-password diagnosis.
* UPLOADED/INDEXING stuck: check indexing-service logs, Kafka consumer assignment, storage access, Elasticsearch and the document status callback URL.
* READY but absent from search: verify the same document owner, index entry, search filters and Redis invalidation. AI COMPLETED is independent of READY.
* FAILED: inspect failureCode/failureCount and the corresponding DLQ record. Repair the cause before replaying. Empty/scanned files may need replacement or OCR.
* Recovery email: SMTP is not configured by this Compose file. Supply server-side SMTP settings and verify actual delivery before offering recovery to public users.

## Controlled DLQ replay

Keep the original DLQ event and record its topic, partition, offset, documentId, reason, operator and time. Check that the document still exists and its MinIO object belongs to the same owner. Do not replay deleted documents. Fix the root cause first.

```powershell
./deploy/replay-dlq.ps1 -Partition 0 -Offset 12
./deploy/replay-dlq.ps1 -Partition 0 -Offset 12 -Execute -Confirm
```

The first command is a dry run. The second requeues only that selected event to `document_uploaded`, resets retryCount, preserves owner and AI consent, and retains the original DLQ record. CLI confirmation means the producer command completed; verify consumer processing, READY, search and cache invalidation before marking recovery complete. A crash between publish and confirmation may duplicate the event, so inspect state before rerunning. Do not bulk replay the entire topic or reset production consumer offsets.

## Cloud rollout and rollback

Before creating resources, choose the account, region, host size, monthly budget and domain. This stack needs enough memory for Elasticsearch, Kafka, four JVMs and builds; measure locally rather than promising a free-tier deployment. Use an HTTPS ingress/load balancer, restrictive firewall, persistent disks, secret storage and a backup/restore drill. MinIO and Elasticsearch are unauthenticated on the private application network and must never be publicly exposed.

Record the deployed Git commit and image digests. Back up PostgreSQL and MinIO data together, and verify restoration in isolation. Elasticsearch can be rebuilt only if retained source objects and metadata support reindexing. The current Hibernate `ddl-auto=update` is a development limitation: review schema changes or add versioned migrations before public production deployment.

Deploy into a staging environment first, run health and browser acceptance, then switch traffic. For rollback, use previously recorded image digests and verify schema compatibility before starting the previous version. `docker compose down` stops services while retaining volumes; do not use `down -v` as a restart or rollback command. No cloud resources have been created by this work.
