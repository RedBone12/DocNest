import { Download, Trash2, FileText, Sparkles, SearchX, AlertCircle } from "lucide-react";
import { useState } from "react";
import type { DocumentDto } from "../../types/documents";
import { StatusBadge } from "./StatusBadge";
import { useDocuments } from "../../hooks/useDocuments";
import {
  deleteDocument,
  downloadDocument,
  requestAiAnalysis,
} from "../../api/documents.api";
import { useAuth } from "../common/AuthContext";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { HighlightedText } from "./HighlightedText";
import type { SearchFilters } from "../../api/search.api";
import { usePreferences } from "../../hooks/usePreferences";
import { errorMessage } from "../../api/errors";

interface DocumentTableProps {
  refreshKey: number;
  searchQuery: string;
  filters: SearchFilters;
  onDeleted: () => void;
  onClearSearch?: () => void;
  onUpload?: () => void;
}

function formatFileSize(fileSize?: number): string {
  if (typeof fileSize !== "number" || !Number.isFinite(fileSize)) return "N/A";
  if (fileSize < 1024) return `${fileSize} B`;
  if (fileSize < 1024 * 1024) return `${(fileSize / 1024).toFixed(1)} KB`;
  return `${(fileSize / (1024 * 1024)).toFixed(2)} MB`;
}

export const DocumentTable = ({
  refreshKey,
  searchQuery,
  filters,
  onDeleted,
  onClearSearch,
  onUpload,
}: DocumentTableProps) => {
  const queryKey = JSON.stringify([searchQuery, filters]);
  const [pagination, setPagination] = useState({ key: queryKey, page: 0 });
  const page = pagination.key === queryKey ? pagination.page : 0;
  const size = 10;
  const setPage = (next: number) => setPagination({ key: queryKey, page: next });
  const {
    documents: docs,
    loading,
    error,
    total,
    retry,
  } = useDocuments(refreshKey, searchQuery, filters, page, size);
  const { token } = useAuth();
  const { preferences } = usePreferences();
  const cellSpacing = preferences.density === "compact" ? "px-4 py-2" : "px-6 py-4";
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pendingDeleteDoc, setPendingDeleteDoc] = useState<DocumentDto | null>(
    null,
  );

  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [analyzingId, setAnalyzingId] = useState<string | null>(null);

  const handleAiAnalysis = async (doc: DocumentDto) => {
    if (!token || analyzingId !== null) return;
    if (!window.confirm(
      `Analyzing "${doc.title}" may send its title and up to 12,000 characters of extracted text to OpenAI. Continue?`,
    )) return;

    try {
      setAnalyzingId(doc.id);
      await requestAiAnalysis(doc.id, token);
      onDeleted();
    } catch (error) {
      alert(
        errorMessage(error),
      );
    } finally {
      setAnalyzingId(null);
    }
  };

  const handleDownload = async (doc: DocumentDto) => {
    if (downloadingId !== null) return;

    if (!token) {
      alert("Please log in before downloading.");
      return;
    }

    setDownloadingId(doc.id);

    try {
      const blob = await downloadDocument(doc.id, token);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");

      link.href = url;
      link.download = doc.title || `document-${doc.id}`;

      document.body.appendChild(link);

      try {
        link.click();
      } finally {
        link.remove();

        window.setTimeout(() => {
          URL.revokeObjectURL(url);
        }, 1000);
      }
    } catch (error) {
      alert(
        errorMessage(error),
      );
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDeleteClick = (doc: DocumentDto) => {
    setPendingDeleteDoc(doc);
  };

  const closeDeleteModal = () => {
    if (deletingId) return;
    setPendingDeleteDoc(null);
  };

  const handleDeleteConfirm = async () => {
    const doc = pendingDeleteDoc;
    if (!doc) return;

    if (!doc.id) {
      alert("Document id is missing");
      return;
    }

    if (!token) {
      alert("You must be logged in to delete documents");
      return;
    }

    try {
      setDeletingId(doc.id);
      await deleteDocument(doc.id, token);
      setPage(0);
      setPendingDeleteDoc(null);
      onDeleted();
    } catch (err) {
      alert(errorMessage(err));
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) return <p role="status" className="rounded-xl border bg-white p-8 text-center">Loading documents...</p>;
  if (error) return <div role="alert" className="rounded-xl border bg-white p-8">
    <AlertCircle className="mb-3 text-red-600" />
    <h2 className="font-semibold">Unable to load documents</h2>
    <p className="mt-2 text-sm text-gray-600">{error}</p>
    <button onClick={retry} className="mt-4 rounded-lg bg-black px-4 py-2 text-white">Try again</button>
  </div>;

  if (docs.length === 0) {
    const searching = Boolean(searchQuery.trim() || filters.tags || filters.fromDate || filters.toDate);
    return <div className="rounded-xl border bg-white p-10 text-center" role="status">
      <SearchX className="mx-auto mb-4 text-gray-400" size={32} />
      <h2 className="text-lg font-semibold">{page > 0 ? "No documents on this page" : searching ? "No results found" : "Your library is empty"}</h2>
      <p className="mt-2 text-sm text-gray-500">{searching ? "Try a different search or remove your filters. Only successfully indexed documents appear in search." : "Upload your first document to get started."}</p>
      <button className="mt-5 rounded-lg bg-black px-4 py-2 text-white" onClick={page > 0 ? () => setPage(0) : searching ? onClearSearch : onUpload}>
        {page > 0 ? "Back to first page" : searching ? "Clear search and filters" : "Upload files"}
      </button>
    </div>;
  }

  return (
    <>
      <DeleteConfirmationModal
        isOpen={Boolean(pendingDeleteDoc)}
        onClose={closeDeleteModal}
        onConfirm={handleDeleteConfirm}
        documentName={pendingDeleteDoc?.title ?? ""}
        isDeleting={Boolean(deletingId)}
      />

      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="document-table w-full text-sm">
          <thead className="bg-gray-50 text-left text-gray-600">
            <tr>
              <th className="px-6 py-3">Name</th>
              <th className="px-6 py-3">Description</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">File Size</th>
              <th className="px-6 py-3">Uploaded By</th>
              <th className="px-6 py-3">Upload Date</th>
              <th className="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>

          <tbody>
            {docs.map((doc: DocumentDto) => (
              <tr key={doc.id} className="border-t hover:bg-gray-50 transition">
                <td className={`${cellSpacing} align-top`}>
                  <div className="flex items-center gap-2">
                    <FileText size={16} className="shrink-0 text-gray-500" />
                    <span className="font-medium wrap-break-word min-w-0">{doc.title}</span>
                  </div>

                  {searchQuery.trim() &&
                    (doc.contentHighlights?.length ?? 0) > 0 && (
                      <div className="mt-3 max-w-xl space-y-2">
                        <p className="text-xs font-medium text-gray-500">
                          Matched content
                        </p>

                        {doc.contentHighlights?.map((fragment, index) => (
                          <p
                            key={index}
                            className="wrap-break-word text-sm leading-6 text-gray-600"
                          >
                            <HighlightedText text={fragment} />
                          </p>
                        ))}
                      </div>
                    )}

                  {preferences.showAiSummaries && doc.aiStatus === "COMPLETED" && doc.aiSummary && (
                    <div className="mt-3 max-w-xl rounded-lg bg-violet-50 p-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-violet-700">
                        AI Summary
                      </p>
                      <p className="mt-1 text-sm leading-6 text-gray-700">
                        {doc.aiSummary}
                      </p>

                      {(doc.aiTags?.length ?? 0) > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {doc.aiTags?.map((tag) => (
                            <span
                              key={tag}
                              className="rounded-full bg-violet-100 px-2 py-0.5 text-xs text-violet-700"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {doc.status === "FAILED" && <div role="status" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
                    <p className="font-semibold">Indexing failed</p>
                    <p>{doc.failureMessage || "This document could not be indexed. It will not appear in search. Please contact support or upload a corrected file."}</p>
                    {doc.failureCode === "RETRY_EXHAUSTED" && <p className="mt-1">Automatic processing stopped after {doc.failureCount ?? 3} failed attempts. The message was sent to the dead-letter queue for review.</p>}
                  </div>}
                  {doc.status !== "FAILED" && doc.aiStatus === "PENDING" && (
                    <p className="mt-2 text-xs text-gray-500">
                      AI analysis pending...
                    </p>
                  )}

                  {doc.aiStatus === "FAILED" && (
                    <p className="mt-2 text-xs text-red-600">
                      {doc.aiMessage ??
                        "AI analysis failed. Check the API configuration and retry."}
                    </p>
                  )}

                  {doc.aiStatus === "DISABLED" && (
                    <p className="mt-2 text-xs text-amber-700">
                      {doc.aiMessage ?? "AI analysis is not configured"}
                    </p>
                  )}
                </td>
                <td data-label="Description" className={`${cellSpacing} text-gray-600`}>{doc.description}</td>

                <td data-label="Status" className={cellSpacing}>
                  <StatusBadge status={doc.status} />
                </td>

                <td data-label="File size" className={`${cellSpacing} text-gray-600`}>
                  {formatFileSize(doc.fileSize)}
                </td>

                <td data-label="Uploaded by" className={cellSpacing}>YOU</td>

                <td data-label="Upload date" className={`${cellSpacing} text-gray-600`}>
                  {doc.createdAt
                    ? new Date(doc.createdAt).toLocaleDateString()
                    : "N/A"}
                </td>

                <td data-label="Actions" className={`${cellSpacing} text-right space-x-3`}>
                  {doc.status === "READY" && doc.aiStatus !== "COMPLETED" && (
                    <button
                      className="text-violet-600 hover:text-violet-800 disabled:opacity-50"
                      onClick={() => handleAiAnalysis(doc)}
                      disabled={
                        analyzingId !== null || doc.aiStatus === "PENDING"
                      }
                      title={
                        doc.aiStatus === "FAILED"
                          ? "Retry AI Analysis"
                          : "Generate AI Summary"
                      }
                      aria-label={
                        doc.aiStatus === "FAILED"
                          ? `Retry AI analysis for ${doc.title}`
                          : `Generate AI summary for ${doc.title}`
                      }
                    >
                      <Sparkles
                        size={16}
                        className={
                          doc.aiStatus === "PENDING" ? "animate-pulse" : ""
                        }
                      />
                    </button>
                  )}
                  <button
                    className="text-gray-600 hover:text-black disabled:opacity-50 disabled:cursor-not-allowed"
                    onClick={() => handleDownload(doc)}
                    disabled={downloadingId !== null}
                    title={
                      downloadingId === doc.id ? "Downloading..." : "Download"
                    }
                    aria-label={`Download ${doc.title}`}
                  >
                    <Download
                      size={16}
                      className={
                        downloadingId === doc.id ? "animate-pulse" : ""
                      }
                    />
                  </button>
                  <button
                    className="text-red-500 hover:text-red-700 disabled:opacity-50"
                    onClick={() => handleDeleteClick(doc)}
                    disabled={deletingId === doc.id}
                    title={deletingId === doc.id ? "Deleting..." : "Delete"}
                    aria-label={`Delete ${doc.title}`}
                  >
                    <Trash2 size={16} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav aria-label="Document pagination" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm">
        <p role="status">Showing {page * size + 1}–{Math.min((page + 1) * size, total)} of {total} documents</p>
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0} className="rounded-lg border bg-white px-3 py-2 disabled:opacity-40">Previous</button>
          <span>Page {page + 1} of {Math.max(1, Math.ceil(total / size))}</span>
          <button type="button" onClick={() => setPage(page + 1)} disabled={(page + 1) * size >= total} className="rounded-lg border bg-white px-3 py-2 disabled:opacity-40">Next</button>
        </div>
      </nav>
    </>
  );
};
