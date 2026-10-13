# DocNest

Local workspace for a document management and search application.

- Backend repository: `backend/distributed-document-search-engine-main`
- Frontend repository: `frontend/distributed-document-search-engine-ui-main`

The local backend and frontend checkouts retain their independent upstream Git history. The published [RedBone12/DocNest](https://github.com/RedBone12/DocNest) repository contains both source directories as a single project. Its root workflow, `.github/workflows/ci.yml`, runs backend tests, frontend build/lint/tests and Compose validation. Local upstream remotes are preserved.

## Quick start (PowerShell)

Install Java 17+, Maven, Docker Desktop and Node.js 22. Start Docker Desktop.
Copy backend `.env.example` to `.env` and replace the credential/secret placeholders.
Then, from this workspace directory:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-infra.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1
```

Infrastructure comes first. Check that MinIO bucket initialization finished before uploading.
The backend launcher skips occupied ports; stop old service processes before relaunching changed Java code.

In another terminal:

```powershell
Set-Location .\frontend\distributed-document-search-engine-ui-main
npm.cmd ci
npm.cmd run dev
```

Use the Vite Local address. Frontend `.env.development` needs `VITE_API_BASE_URL=http://localhost:8080`.

See the [backend README](backend/distributed-document-search-engine-main/README.md) for ports, environment variables, architecture, business flows, failure behavior and test commands.
See the [frontend README](frontend/distributed-document-search-engine-ui-main/README.md) for UI behavior and browser preferences.

AI is optional: without OPENAI_API_KEY, documents still index and search normally, while AI status becomes DISABLED. A selected upload can send text externally when AI is configured.

The Compose project name remains `infra` to reuse existing data volumes. Do not use `docker compose down -v` as a restart command.
