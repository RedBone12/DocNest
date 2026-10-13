import type { DocumentDto, DocumentStatus } from "../types/documents";
import { apiUrl } from "../config";
import { serviceError } from "./errors";

export type UploadResponse = {
	documentId: string;
	status: string;
	message?: string;
};

export type UploadOptions = {
	title?: string;
	description?: string;
	idempotencyKey?: string;
	token?: string;
  enableAiAnalysis?: boolean;
  onProgress?: (percent: number) => void;
};

export async function uploadDocument(
	file: File,
	opts: UploadOptions = {}
): Promise<UploadResponse> {
	const { title, description, idempotencyKey, token, enableAiAnalysis = false} = opts;

	const form = new FormData();
	form.append("file", file);
  form.append("enableAiAnalysis", String(enableAiAnalysis));
	if (title) form.append("title", title);
	if (description) form.append("description", description);

	const headers: Record<string, string> = {};
	if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
	if (token) headers["Authorization"] = `Bearer ${token}`;

  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", apiUrl("/documents/upload"));
    request.withCredentials = true;
    request.timeout = 120000;
    Object.entries(headers).forEach(([key, value]) => request.setRequestHeader(key, value));
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) opts.onProgress?.(Math.round(event.loaded / event.total * 100));
    };
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        try { resolve(JSON.parse(request.responseText) as UploadResponse); }
        catch { reject(new Error("The upload response was invalid. Refresh your documents before retrying.")); }
      } else reject(new Error(serviceError(request.status)));
    };
    request.onerror = () => reject(new Error(serviceError(0)));
    request.ontimeout = () => reject(new Error("Upload timed out. Refresh your documents before retrying."));
    request.onabort = () => reject(new Error("Upload cancelled."));
    request.send(form);
  });
}
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

type BackendDocumentDto = Omit<DocumentDto, "id"> & {
  id?: string;
  documentId?: string;
  fileSize?: number;
};

function normalizeDocument(document: BackendDocumentDto): DocumentDto {
  return {
    ...document,
    id: document.id ?? document.documentId ?? "",
    fileSize: typeof document.fileSize === "number" ? document.fileSize : undefined,
    size:
      document.size ??
      (typeof document.fileSize === "number" ? document.fileSize : undefined),
  };
}

export async function getDocuments(
  token: string,
  page = 0,
  size = 10,
  signal?: AbortSignal,
): Promise<PageResponse<DocumentDto>> {
  const response = await fetch(apiUrl(`/documents?page=${page}&size=${size}`), {
    signal,
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }

  const payload = (await response.json()) as PageResponse<BackendDocumentDto>;
  return {
    ...payload,
    content: payload.content.map(normalizeDocument),
  };
}

export type DocumentStatusResponse = {
  documentId: string;
  status: DocumentStatus;
};

export async function getDocumentStatus(
  documentId: string,
  token: string
): Promise<DocumentStatusResponse> {
  const response = await fetch(
    apiUrl(`/documents/${encodeURIComponent(documentId)}/status`),
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      credentials: "include",
    }
  );

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }

  return response.json();
}

export async function deleteDocument(
  documentId: string,
  token: string
): Promise<void> {
  const response = await fetch(apiUrl(`/documents/${encodeURIComponent(documentId)}`), {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
  });

  if (response.status === 204) {
    return;
  }

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }
}

export async function downloadDocument(
  documentId: string,
  token: string
): Promise<Blob> {
  const response = await fetch(
    apiUrl(`/documents/${encodeURIComponent(documentId)}/download`),
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }

  return response.blob();
}

export async function requestAiAnalysis(
  documentId: string,
  token: string,
): Promise<void> {
  const response = await fetch(
    apiUrl(`/documents/${encodeURIComponent(documentId)}/ai-analysis`),
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  );

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }
}
