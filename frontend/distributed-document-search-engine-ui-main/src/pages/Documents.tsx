import { useEffect, useRef, useState } from "react";
import { DocumentTable } from "../components/documents/DocumentTable";
import { uploadDocument } from "../api/documents.api";
import { useAuth } from "../components/common/AuthContext";
import { UploadFormModal } from "../components/documents/UploadFormModal";
import { useOutletContext } from "react-router-dom";
import type { RefObject } from "react";
import { Filter, X } from "lucide-react";
import type { SearchFilters } from "../api/search.api";

type DocumentsOutletContext = {
  uploadModalOpen: boolean;
  openUpload: () => void;
  closeUpload: () => void;
  searchInputRef: RefObject<HTMLInputElement | null>;
  searchQuery: string;
  setSearchQuery: (value: string) => void;
};
export const Documents = () => {
  const uploadKeys = useRef(new WeakMap<File, string>());
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const { uploadModalOpen, openUpload, closeUpload, searchInputRef, searchQuery, setSearchQuery } =
    useOutletContext<DocumentsOutletContext>();
  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [filters, setFilters] = useState<SearchFilters>({});

  const [refreshKey, setRefreshKey] = useState<number>(0);

  const { token } = useAuth();

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedSearchQuery(searchQuery);
    }, 300);

    return () => window.clearTimeout(timeoutId);
  }, [searchQuery]);

  const handleUpload = async (files: FileList, description: string, enableAiAnalysis: boolean) => {
    if (!token) {
      throw new Error("You must be logged in to upload documents");
    }

    setUploading(true);
    setUploadProgress(0);

    try {
      const docs = Array.from(files);
      const normalizedDescription = description.trim() || "Uploaded file";
      const progress = docs.map(() => 0);
      const totalBytes = docs.reduce((sum, file) => sum + file.size, 0) || 1;

      const results = await Promise.allSettled(
        docs.map(async (file, index) => {
          let idempotencyKey = uploadKeys.current.get(file);
          if (!idempotencyKey) {
            idempotencyKey = crypto.randomUUID();
            uploadKeys.current.set(file, idempotencyKey);
          }

          return uploadDocument(file, {
            title: file.name,
            description: normalizedDescription,
            idempotencyKey,
            token,
            enableAiAnalysis,
            onProgress: (percent) => {
              progress[index] = percent;
              setUploadProgress(Math.round(docs.reduce((sum, item, i) => sum + item.size * progress[i], 0) / totalBytes));
            },
          });
        }),
      );

      const failed = results.filter((r) => r.status === "rejected");
      setRefreshKey((k) => k + 1);
      if (failed.length > 0) {
        const firstError = failed[0] as PromiseRejectedResult;
        throw new Error(
          `${failed.length} of ${docs.length} uploads failed. Successfully saved files are already in your library. ${(firstError.reason as Error)?.message ?? ""}`.trim(),
        );
      }

    } finally {
      setUploading(false);
    }
  };

  return (
    <>
      {uploadModalOpen && <UploadFormModal
        isOpen={uploadModalOpen}
        onClose={closeUpload}
        onUpload={handleUpload}
        isUploading={uploading}
        progress={uploadProgress}
      />}

      <div className="flex flex-wrap items-center mb-3 gap-3">
        <input
          ref={searchInputRef}
          placeholder="Search documents..."
          aria-label="Search documents"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="min-w-0 w-full sm:w-auto sm:flex-1 px-4 py-2 rounded-lg bg-gray-100"
        />

        <button
          type="button"
          aria-expanded={filtersOpen}
          className={`flex items-center gap-2 rounded-lg border px-4 py-2 ${
            filtersOpen ? "border-black bg-black text-white" : "bg-white text-gray-700"
          }`}
          onClick={() => setFiltersOpen((open) => !open)}
        >
          <Filter size={16} />
          Filters
        </button>

        <button
          className="bg-black text-white px-4 py-2 rounded-lg disabled:opacity-60"
          onClick={openUpload}
          disabled={uploading}
        >
          {uploading ? "Uploading..." : "Upload Files"}
        </button>
      </div>

      {filtersOpen && (
        <div className="mb-6 grid gap-4 rounded-xl border bg-white p-4 md:grid-cols-[2fr_1fr_1fr_auto] md:items-end">
          <label className="text-sm text-gray-700">
            <span className="mb-1 block font-medium">AI tags</span>
            <input
              type="text"
              value={filters.tags ?? ""}
              onChange={(event) =>
                setFilters((current) => ({ ...current, tags: event.target.value }))
              }
              placeholder="e.g. Java, cloud"
              className="w-full rounded-lg border px-3 py-2 focus:border-black focus:outline-none"
            />
          </label>

          <label className="text-sm text-gray-700">
            <span className="mb-1 block font-medium">From date</span>
            <input
              type="date"
              value={filters.fromDate ?? ""}
              max={filters.toDate || undefined}
              onChange={(event) =>
                setFilters((current) => ({ ...current, fromDate: event.target.value }))
              }
              className="w-full rounded-lg border px-3 py-2 focus:border-black focus:outline-none"
            />
          </label>

          <label className="text-sm text-gray-700">
            <span className="mb-1 block font-medium">To date</span>
            <input
              type="date"
              value={filters.toDate ?? ""}
              min={filters.fromDate || undefined}
              onChange={(event) =>
                setFilters((current) => ({ ...current, toDate: event.target.value }))
              }
              className="w-full rounded-lg border px-3 py-2 focus:border-black focus:outline-none"
            />
          </label>

          <button
            type="button"
            onClick={() => setFilters({})}
            disabled={!filters.tags && !filters.fromDate && !filters.toDate}
            className="flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 disabled:opacity-40"
          >
            <X size={16} />
            Clear
          </button>
        </div>
      )}

      <DocumentTable
        refreshKey={refreshKey}
        searchQuery={debouncedSearchQuery}
        filters={filters}
        onDeleted={() => setRefreshKey((k) => k + 1)}
        onClearSearch={() => { setSearchQuery(""); setFilters({}); }}
        onUpload={openUpload}
      />
    </>
  );
};
