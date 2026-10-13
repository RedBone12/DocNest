export const DOCUMENT_STATUSES = [
  "UPLOADED",
  "INDEXING",
  "READY",
  "FAILED"
] as const;

export type Status = typeof DOCUMENT_STATUSES[number];
