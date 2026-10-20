# Local portfolio demo

## Scope and prerequisites

This mode adds no cloud hosting charges. It uses your computer, disk, electricity and network. It is not a public hosted site. Install Docker Desktop with Linux containers, Node.js/npm and the frontend dependencies (`npm.cmd ci` in the frontend folder). Install the browser once with `npx.cmd playwright install chromium` in that folder. Docker Desktop must have enough available memory for four JVMs, Kafka and Elasticsearch; startup failures must be diagnosed rather than assuming every laptop supports this stack.

Run `deploy/demo.ps1` from PowerShell as documented in the root README. First startup may download/build images and take several minutes. `Start -Build` rebuilds application images after code changes; ordinary Start reuses available images. `Status` displays container health. `Verify` requires a running stack and local frontend dependencies; it tests registration, login, upload, indexing, content search, byte-for-byte download and deletion against real services. It does not test SMTP recovery or paid AI analysis.

The script deliberately uses `deploy/.env.example` for consistency with the existing isolated local demonstration database. Do not replace its database password and expect an existing volume's credentials to change automatically. These are public example credentials, not production secrets. The frontend binds only to 127.0.0.1:8088; do not publish the port or use a tunnel with this configuration. Do not run the separate development launchers on top of this stack.

## Evidence and recording

`Verify -RecordVideo` saves an automatic Chromium recording under the frontend's `test-results` directory and an HTML report under `playwright-report`. This is unedited acceptance evidence, not a narrated presentation. Generated reports can contain test credentials and document text: use disposable data only, review recordings and do not commit the raw artifacts. A later Playwright run may replace previous artifacts; copy a wanted video elsewhere first.

For a narrated 2-3 minute recording, use Windows screen recording and follow this sequence:

1. 0:00-0:20: Introduce DocNest as a local document-management/search demonstration, with upstream attribution and your verified additions.
2. 0:20-0:45: Register a disposable account and log in. Do not record personal passwords or inboxes.
3. 0:45-1:20: Upload a sample text file with AI disabled; show the document becoming READY.
4. 1:20-1:50: Search for a phrase inside the file, then download and show matching contents.
5. 1:50-2:10: Delete the sample and show its disappearance.
6. 2:10-2:40: Briefly show the architecture and a successful test report. Distinguish local test results from remote CI and cloud deployment.

No video has been publicly uploaded automatically. Review and approve the recording before publishing it. README claims should say locally containerized and tested, not AWS deployed. A video URL can be added after an actual recording is reviewed and uploaded.

## Safe shutdown and troubleshooting

`Stop` stops only this Compose project and retains all data. Never use `down -v` as a restart step. Test accounts remain in the demo database. Existing development volumes belong to the separate `infra` project.

If Docker cannot connect, start Docker Desktop and wait for the Linux engine. If PowerShell denies script execution, inspect the script and use a process-scoped invocation such as `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\demo.ps1 -Action Status`; this does not change the machine policy. If Verify reports missing dependencies or browser, run the prerequisite commands. If a container is unhealthy, inspect `docker compose --env-file deploy/.env.example -f deploy/compose.yaml logs --tail 80 <service>` locally; do not share logs without checking for private metadata. See operations.md for indexing and dependency failures.
