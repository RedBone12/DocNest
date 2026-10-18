# DocNest Frontend

The website header, sign-in page, browser tab title and favicon use the DocNest identity. The npm package name is `docnest`; the existing checkout directory name is kept so startup scripts and CI paths continue to work.

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

API authentication uses explicit Authorization: Bearer headers, not cookie sessions. Upload XHR uses withCredentials=false; search, document status and delete requests use credentials=omit, matching the gateway's allowCredentials=false policy. Do not enable credentialed-cookie requests independently of the server policy: browsers will block them and the UI may show a generic network/service-unavailable error. Bearer headers must remain in place when changing cookie settings.

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
New analysis requests instruct the model to write summaries and tags in English, regardless of source language. Existing stored summaries are not automatically translated. Restart indexing-service after backend prompt changes. The prompt is not a deterministic language validator.
Search does not show failed/unindexed uploads; the library does.

## Accounts and sessions

Sign in with a registered email and password, not PostgreSQL or MinIO credentials. There is no default app account. **Create an account** opens `/register` with email/password/confirmation validation and show/hide password controls. New passwords require at least 12 characters and at most 72 UTF-8 bytes.

Without **Remember me**, sign-in uses sessionStorage and a 24-hour token. With it checked, sign-in uses localStorage and a token valid for up to 30 days. Passwords are never saved. Session storage can survive browser session restoration; it is not an absolute browser-close guarantee. Expired/malformed tokens are removed, saved sessions are checked on mount, every 30 seconds and on tab visibility, and 401 responses clear the session. Network outages do not automatically log out the user. The updated gateway applies CORS inside the security chain so authentication errors remain readable.

**Forgot password?** opens `/forgot-password`. It checks server capability and explicitly disables recovery when SMTP is not configured. Requests use a privacy-preserving response and a resend cooldown; server limits also apply. Email links open `/reset-password#token=...`; the token is removed from the displayed address after reading it, sent only in a POST body, and not saved in browser storage. Links expire after 15 minutes and can be used once. Reloading the reset page after the fragment is removed requires reopening the email link.

**Settings → Account Security** changes the password after verifying the current password and confirmation. Password changes/resets revoke all existing sessions; sign-out revokes the current one. If server revocation fails during sign-out, local state is cleared and a warning explains that the server token may remain valid until expiry. Both document-service and gateway must be restarted after the account update; pre-update tokens require a new login. SMTP configuration is documented in the root/backend README. Email verification and MFA are not implemented.

Preferences and login state are stored in the current browser; clearing site data removes them, but does not delete server documents.

## Checks

```powershell
npm.cmd run build
npm.cmd run lint
npm.cmd test
```

`npm.cmd test` runs the Node API/auth checks followed by the Vitest component and hook tests in jsdom.
Use `npm.cmd run test:components` to run only component/hook tests, or `npm.cmd run test:watch` during development.
The tests cover upload submission and failures, AI consent, progress display, deletion confirmation and refresh,
protected routing, search cancellation, retry, processing polling and unmount cleanup.
API calls are mocked in component/hook tests; these checks do not replace browser-to-backend end-to-end tests.

The published monorepo uses the root `.github/workflows/ci.yml` for frontend checks, backend Maven tests and Compose validation. The independently versioned frontend/backend checkouts also contain their own workflows. Local changes do not reach GitHub until explicitly published.
