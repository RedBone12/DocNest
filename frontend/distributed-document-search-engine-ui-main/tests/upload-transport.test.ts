import { afterEach, expect, it, vi } from "vitest";
import { uploadDocument } from "../src/api/documents.api";

class UploadRequest {
  static latest: UploadRequest;
  upload: { onprogress?: (event: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
  status = 201;
  responseText = '{"documentId":"sample","status":"UPLOADED"}';
  withCredentials = true;
  timeout = 0;
  headers: Record<string, string> = {};
  form?: FormData;
  onload?: () => void;
  onerror?: () => void;
  ontimeout?: () => void;
  onabort?: () => void;
  constructor() { UploadRequest.latest = this; }
  open = vi.fn();
  setRequestHeader(key: string, value: string) { this.headers[key] = value; }
  send(form: FormData) { this.form = form; }
}
afterEach(() => vi.unstubAllGlobals());

it("uploads metadata with explicit consent, bearer header and progress", async () => {
  vi.stubGlobal("XMLHttpRequest", UploadRequest);
  const progress = vi.fn();
  const pending = uploadDocument(new File(["sample"], "sample.txt"), { title: "Title", description: "Description", idempotencyKey: "key", token: "signed", enableAiAnalysis: true, onProgress: progress });
  const request = UploadRequest.latest;
  expect(request.withCredentials).toBe(false);
  expect(request.headers).toEqual({ "Idempotency-Key": "key", Authorization: "Bearer signed" });
  expect(request.form?.get("enableAiAnalysis")).toBe("true");
  expect(request.form?.get("title")).toBe("Title");
  request.upload.onprogress?.({ lengthComputable: false, loaded: 1, total: 2 }); expect(progress).not.toHaveBeenCalled();
  request.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 2 }); expect(progress).toHaveBeenCalledWith(50);
  request.onload?.(); await expect(pending).resolves.toMatchObject({ documentId: "sample" });
});

it.each(["invalid", "server", "network", "timeout", "abort", "success"])("handles upload outcome %s", async outcome => {
  vi.stubGlobal("XMLHttpRequest", UploadRequest);
  const pending = uploadDocument(new File(["sample"], "sample.txt"));
  const request = UploadRequest.latest;
  expect(request.form?.get("enableAiAnalysis")).toBe("false");
  request.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 2 });
  if (outcome === "invalid") { request.responseText = "bad json"; request.onload?.(); }
  if (outcome === "server") { request.status = 500; request.onload?.(); }
  if (outcome === "network") request.onerror?.();
  if (outcome === "timeout") request.ontimeout?.();
  if (outcome === "abort") request.onabort?.();
  if (outcome === "success") { request.status = 200; request.onload?.(); await expect(pending).resolves.toMatchObject({ status: "UPLOADED" }); }
  else await expect(pending).rejects.toThrow();
});
