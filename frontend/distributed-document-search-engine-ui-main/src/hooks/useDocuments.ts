import { useEffect, useRef, useState } from "react";
import { getDocuments } from "../api/documents.api";
import { searchDocuments } from "../api/search.api";
import type { SearchFilters } from "../api/search.api";
import type { DocumentDto } from "../types/documents";
import { useAuth } from "../components/common/AuthContext";
import { errorMessage } from "../api/errors";

export function useDocuments(refreshKey: number, searchQuery = "", filters: SearchFilters = {}, page = 0, size = 10) {
  const [documents, setDocuments] = useState<DocumentDto[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const documentsRef = useRef<DocumentDto[]>([]);
  const { token } = useAuth();
  const tags = filters.tags?.trim() ?? "";
  const fromDate = filters.fromDate ?? "";
  const toDate = filters.toDate ?? "";

  useEffect(() => {
    const controller = new AbortController();
    let busy = false;
    let timer: ReturnType<typeof setTimeout>;
    const load = async (initial = false) => {
      if (busy || controller.signal.aborted) return;
      busy = true;
      if (initial) setLoading(true);
      try {
        const result = !token ? { documents: [], total: 0 }
          : searchQuery.trim() || tags || fromDate || toDate
            ? await searchDocuments(searchQuery.trim(), token, { tags, fromDate, toDate }, page, size, controller.signal)
            : await getDocuments(token, page, size, controller.signal).then((data) => ({ documents: data.content, total: data.totalElements }));
        if (!controller.signal.aborted) {
          documentsRef.current = result.documents;
          setDocuments(result.documents);
          setTotal(result.total);
          setError(null);
        }
      } catch (cause) {
        if (!controller.signal.aborted) setError(errorMessage(cause));
      } finally {
        busy = false;
        if (!controller.signal.aborted) {
          setLoading(false);
          timer = setTimeout(() => {
            if (documentsRef.current.some((doc) =>
              doc.status === "UPLOADED" || doc.status === "INDEXING" ||
              (doc.status !== "FAILED" && doc.aiStatus === "PENDING"))) void load();
          }, 3000);
        }
      }
    };
    void load(true);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [token, refreshKey, retryKey, searchQuery, tags, fromDate, toDate, page, size]);

  return { documents, total, loading, error, retry: () => setRetryKey((value) => value + 1) };
}
