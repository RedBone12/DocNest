import type { DocumentDto, DocumentStatus } from "../types/documents";
import { apiUrl } from "../config";
import { serviceError } from "./errors";

type SearchResultItem = {
  id?: string;
  documentId?: string;
  title: string;
  description: string;
  fileSize?: number;
  createdAt: string;
  status?: DocumentStatus;
  contentHighlights?: string[];
  aiSummary?: string;
  aiTags?: string[];
  aiStatus?: DocumentDto["aiStatus"];
  aiMessage?: string;
};

type SearchResponse = {
  total: number;
  results: SearchResultItem[];
};

export type SearchFilters = {
  tags?: string;
  fromDate?: string;
  toDate?: string;
};

function normalizeSearchResult(item: SearchResultItem): DocumentDto {
  return {
    id: item.id ?? item.documentId ?? "",
    title: item.title,
    description: item.description,
    fileSize: typeof item.fileSize === "number" ? item.fileSize : undefined,
    size: typeof item.fileSize === "number" ? item.fileSize : undefined,
    createdAt: item.createdAt,
    status: item.status ?? "UPLOADED",
    contentHighlights: item.contentHighlights ?? [],
    aiSummary: item.aiSummary,
    aiTags: item.aiTags ?? [],
    aiStatus: item.aiStatus,
    aiMessage: item.aiMessage,
  };
}

export async function searchDocuments(
  query: string,
  token: string,
  filters: SearchFilters = {},
  page = 0,
  size = 10,
  signal?: AbortSignal,
): Promise<{ documents: DocumentDto[]; total: number }> {
  const url = new URL(apiUrl("/search"));
  url.searchParams.set("q", query);
  url.searchParams.set("page", String(page));
  url.searchParams.set("size", String(size));
  if (filters.tags?.trim()) url.searchParams.set("tags", filters.tags.trim());
  if (filters.fromDate) url.searchParams.set("fromDate", filters.fromDate);
  if (filters.toDate) url.searchParams.set("toDate", filters.toDate);

  const response = await fetch(url.toString(), {
    signal,
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    credentials: "omit",
  });

  if (!response.ok) {
    throw new Error(serviceError(response.status));
  }

  const payload = (await response.json()) as SearchResponse;
  return { total: payload.total, documents: payload.results
    .map(normalizeSearchResult)
    .filter((doc) => Boolean(doc.id)) };
}
