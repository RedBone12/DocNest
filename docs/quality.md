# Quality gates and measured baseline

Run `mvn.cmd -B -ntp clean verify` from `backend/distributed-document-search-engine-main`. All four applications run Checkstyle, SpotBugs, tests, JaCoCo reports and a coverage failure gate. Common event contracts and the backend E2E module run static checks; their code is represented in the aggregate report through application test execution. The aggregator contains no production code and has no separate coverage gate.

The applications retain their existing Spring Boot parents. Configuration is explicit in each module because the root POM aggregates them but is not their parent. Do not assume root plugin declarations are inherited. Gateway retains its stricter import/braces rules; the other modules initially enforce naming rules. Expand those shared Checkstyle rules after fixing their existing style debt.

| Module | Minimum line coverage | Minimum branch coverage |
| --- | ---: | ---: |
| api-gateway | 90% | 80% |
| document-service | 70% | 62% |
| indexing-service | 37% | 46% |
| search-service | 59% | 70% |

These are measured anti-regression floors, not claims that every service meets 90%. Increase them in the same PR as meaningful new tests. Do not exclude business classes to improve the percentage. DTO defensive-copy tests were added to check isolation of caller-owned collections.

Per-service reports are in `services/<service>/target/site/jacoco/index.html`. The full report is `services/coverage-report/target/site/jacoco-aggregate/index.html`. It includes common-events and all four services, including uncovered classes. Run `./quality/check-aggregate-coverage.ps1` after Maven verify to enforce the measured aggregate floors of 61% lines and 59% branches; CI executes this automatically and fails if groups or counters are missing. Run clean after changing Java/compiler configuration so old class identifiers do not distort coverage.

SpotBugs runs at Max effort and Medium threshold and fails the build on reported issues. `quality/spotbugs-exclude.xml` documents reviewed framework cases: constructor injection intentionally shares Spring collaborators; Jackson reads transport fields reflectively; specific validating constructors have no finalizer exposure. These exclusions are not blanket suppression of DTO collection exposure. Report remaining findings before adding exclusions.

## Frontend

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run test:coverage
npm.cmd run lint
npm.cmd run build
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Vitest 5 V8 coverage includes all src TypeScript/TSX except the browser bootstrap and type declarations. After the toolchain migration and new account/API/upload/preferences/highlight tests, the measured baseline is 60.33% lines, 58.58% statements, 59.80% branches and 52.05% functions. Gates are 60/58/59/52 respectively. Existing Node API tests execute separately and do not contribute to this V8 report. Vitest 5 uses a different coverage mapping from the previous Vitest 3 baseline; percentages are not directly comparable. Low layout/provider coverage remains known test debt. No business files were excluded to pass the migration. Run `npm run test:types` to type-check test files as well as application code.

Playwright uses the real container stack at http://localhost:8088 by default. Override E2E_BASE_URL for a disposable staging environment. It creates an isolated account, disables external AI, uploads a unique text file, waits for READY, searches its content, verifies downloaded bytes and deletes the file. Browser traces and screenshots are retained only on failure. The stack must be healthy before running the browser test.

## CI

The root workflow runs Maven verify, frontend tests/coverage, lint and build, and uploads coverage/static-analysis reports. The browser workflow builds and starts an isolated stack before Playwright. It does not upload full service logs. Check the exact commit's GitHub run before claiming remote CI success. Local `.publish` is a separate publication checkout and is not automatically synchronized by these edits.

After publication and a successful run, configure required checks and branch protection in the repository settings. YAML alone does not prevent merging a failed PR. No remote branch protection or cloud resources were changed by these local edits.
# Dependency security verification

On 2026-10-20, compatible dependency updates resolved the three production dependency audit findings. `npm audit --omit=dev` reports zero known vulnerabilities for the current lockfile. CI now runs `npm audit --omit=dev --audit-level=high`. This is an npm dependency audit, not a comprehensive application, Java or container security assessment.

The subsequent explicit Vitest 5.0.5 and Tailwind 4.3.3 migrations resolved the remaining development-toolchain findings. Full `npm audit` now reports zero known vulnerabilities for the current lockfile; CI fails on moderate or higher dependency findings. Tailwind's official migration adjusted utilities and CSS compatibility rules; Vitest's new coverage mapping was addressed with additional tests, not lowered thresholds. Node 22.12+ or supported Node 24 is required for this toolchain. No blanket audit suppression was applied. Do not expose test/dev servers publicly or run untrusted tests or build input. Audit success is time-dependent and does not establish that Java dependencies, container images or the application are vulnerability-free.
