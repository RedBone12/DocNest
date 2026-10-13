# DocNest Frontend

React 19, TypeScript, Vite, Tailwind CSS, React Router and Lucide icons.
Based on [21Priyanshu/distributed-document-search-engine-ui](https://github.com/21Priyanshu/distributed-document-search-engine-ui).

## Run locally

Requires Node.js 22 and a running DocNest backend. In this repository:

```powershell
npm.cmd ci
npm.cmd run dev
```

Create `.env.development` using `.env.example`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Open Vite's Local URL (normally port 5173). VITE variables are public, build-time values: never put database passwords, JWT secrets or OpenAI keys here.
Production deployments need the correct API URL at build time, gateway CORS configuration and SPA route fallback to index.html.

## User experience

- Paginated library and full-text search with totals, previous/next, and filters.
- Empty library and no-results states with upload or clear-search actions.
- Real upload-transfer progress; after 100%, the UI waits for the server response.
- Mixed upload batches report failures and refresh successfully saved documents.
- Service/network errors have a readable message and retry action.
- Indexing failures display safe reasons and retry-exhaustion/DLQ information returned by the backend.
- Pending polling is stopped for terminal indexing failures.
- Responsive document cards on narrow screens.
- Keyboard focus indicators, labeled icon buttons, Escape to close dialogs/drawers, trapped dialog focus and restored focus on close.
- Settings: default AI choice, summary visibility, list density and five color palettes. Save changes persists preferences per account in the current browser; Restore defaults resets them. These preferences do not sync between devices.

Uploaded documents are UPLOADED, INDEXING, READY or FAILED. AI has separate PENDING, COMPLETED, FAILED and DISABLED states.
AI text may be sent externally only when selected. Without a server API key, document search works and AI is DISABLED.
Search does not show failed/unindexed uploads; the library does.

## Checks

```powershell
npm.cmd run build
npm.cmd run lint
npm.cmd test
```

GitHub Actions in `.github/workflows/frontend-ci.yml` runs npm ci, TypeScript/Vite build and ESLint.
The independently versioned backend repository runs Maven tests and Docker Compose validation in its own workflow.
