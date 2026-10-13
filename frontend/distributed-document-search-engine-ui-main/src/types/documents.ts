export type DocumentStatus =
  | "UPLOADED"
  | "INDEXING"
  | "READY"
  | "FAILED";

export interface DocumentDto {
  id: string;
  title: string;
  description: string;
  status: DocumentStatus;
  createdAt: string; // ISO timestamp from backend
  fileSize?: number;
  size?: number | string;
  contentHighlights?: string[];
  aiSummary?: string;
  aiTags?: string[];
  aiStatus?: "PENDING" | "COMPLETED" | "FAILED" | "DISABLED";
  aiMessage?: string;
  failureCode?: string;
  failureMessage?: string;
  failureCount?: number;
  failedAt?: string;
}
