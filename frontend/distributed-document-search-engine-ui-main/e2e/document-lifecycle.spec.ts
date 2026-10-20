import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";

test("register, sign in, index, search, download and delete a document", async ({ page }) => {
  const id = crypto.randomUUID();
  const email = `e2e-${id}@example.com`;
  const password = `DocNest-test-${id}`;
  const filename = `acceptance-${id}.txt`;
  const content = `DocNest acceptance document ${id} searchablequartz`;
  await page.goto("/register");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByLabel("Confirm password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page).toHaveURL(/\/documents$/);
  await page.getByRole("button", { name: "Upload Files", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Upload Documents", exact: true });
  await dialog.getByLabel("Select files").setInputFiles({
    name: filename, mimeType: "text/plain", buffer: Buffer.from(content),
  });
  await dialog.getByRole("checkbox").uncheck();
  await dialog.getByRole("button", { name: "Upload (1)", exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const row = page.getByRole("row").filter({ hasText: filename });
  await expect(row).toContainText("READY");
  await page.getByRole("textbox", { name: "Search documents", exact: true }).fill("searchablequartz");
  await expect(row).toBeVisible();
  await expect(row).toContainText("searchablequartz");
  const downloaded = page.waitForEvent("download");
  await page.getByRole("button", { name: `Download ${filename}`, exact: true }).click();
  const download = await downloaded;
  expect(download.suggestedFilename()).toBe(filename);
  const path = await download.path();
  expect(path).not.toBeNull();
  expect(await readFile(path!, "utf8")).toBe(content);
  await page.getByRole("button", { name: `Delete ${filename}`, exact: true }).click();
  await page.getByRole("dialog", { name: "Delete Document", exact: true })
    .getByRole("button", { name: "Delete", exact: true }).click();
  await expect(row).toHaveCount(0);
});
