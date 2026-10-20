import {
  AlertCircle,
  CheckCircle2,
  Clock,
  FileText,
  LogOut,
  X,
} from "lucide-react";
import type { DocumentDto } from "../../types/documents";
import { useRef } from "react";
import { useDialogFocus } from "../../hooks/useDialogFocus";

type Props = {
  open: boolean;
  onClose: () => void;
  userEmail: string;
  documents: DocumentDto[];
  onLogout: () => void;
};

function getInitials(email: string) {
  return (email || "U").slice(0, 2).toUpperCase();
}

function getSizeInBytes(
  value: number | string | undefined,
  fileSize: number | undefined
): number {
  if (typeof fileSize === "number") {
    return Number.isFinite(fileSize) ? fileSize : 0;
  }

  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value !== "string") return 0;

  const parsed = parseFloat(value);
  if (!Number.isFinite(parsed)) return 0;

  // Backward compatibility for old string values interpreted as MB.
  return parsed * 1024 * 1024;
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${Math.round(bytes)} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export const UserPanel = ({
  open,
  onClose,
  userEmail,
  documents,
  onLogout,
}: Props) => {
  const dialogRef = useRef<HTMLElement>(null);
  useDialogFocus(open, dialogRef, onClose);
  const total = documents.length;
  const completed = documents.filter((d) => d.status === "READY").length;
  const pending = documents.filter((d) => d.status === "UPLOADED").length;
  const indexing = documents.filter((d) => d.status === "INDEXING").length;
  const failed = documents.filter((d) => d.status === "FAILED").length;
  const totalSizeBytes = documents.reduce(
    (acc, doc) => acc + getSizeInBytes(doc.size, doc.fileSize),
    0
  );
  const totalCapacityBytes = 100 * 1024 * 1024;
  const usedPercent = Math.min((totalSizeBytes / totalCapacityBytes) * 100, 100);

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-black/20" onClick={onClose} />}

      <aside
        ref={dialogRef}
        inert={!open}
        role="dialog"
        aria-modal={open || undefined}
        aria-label="User profile"
        tabIndex={-1}
        className={`fixed right-0 top-0 z-50 h-full w-full max-w-md border-l bg-white shadow-xl transition-transform duration-300 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between border-b px-5 py-4">
          <h2 className="text-lg font-semibold">User Profile</h2>
          <button
            onClick={onClose}
            className="rounded-sm p-1 text-gray-600 hover:bg-gray-100"
            aria-label="Close user panel"
          >
            <X size={18} />
          </button>
        </div>

        <div className="h-[calc(100%-64px)] space-y-5 overflow-y-auto p-5">
          <section className="rounded-xl border bg-white p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-black text-sm font-semibold text-white">
                {getInitials(userEmail)}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{userEmail}</p>
                <p className="text-xs text-gray-500">Member</p>
              </div>
            </div>
            <div className="my-4 border-t" />
            <button
              onClick={onLogout}
              className="inline-flex w-full items-center justify-center gap-2 rounded-md border px-3 py-2 text-sm hover:bg-gray-50"
            >
              <LogOut size={16} />
              Logout
            </button>
          </section>

          <section className="rounded-xl border bg-white p-4">
            <h3 className="mb-3 text-base font-semibold">Recent Documents</h3>
            <div className="space-y-3">
              <div className="flex items-center justify-between text-sm">
                <span className="inline-flex items-center gap-2">
                  <FileText size={16} className="text-gray-500" />
                  Total Documents
                </span>
                <span className="font-medium">{total}</span>
              </div>
              <div className="border-t" />
              <div className="flex items-center justify-between text-sm">
                <span className="inline-flex items-center gap-2">
                  <CheckCircle2 size={16} className="text-green-600" />
                  Completed
                </span>
                <span className="font-medium">{completed}</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="inline-flex items-center gap-2">
                  <Clock size={16} className="text-blue-600" />
                  Indexing
                </span>
                <span className="font-medium">{indexing}</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="inline-flex items-center gap-2">
                  <Clock size={16} className="text-yellow-600" />
                  Pending
                </span>
                <span className="font-medium">{pending}</span>
              </div>
              {failed > 0 && (
                <div className="flex items-center justify-between text-sm">
                  <span className="inline-flex items-center gap-2">
                    <AlertCircle size={16} className="text-red-600" />
                    Failed
                  </span>
                  <span className="font-medium">{failed}</span>
                </div>
              )}
              <div className="border-t" />
              <div className="flex items-center justify-between text-sm">
                <span className="text-gray-500">Total Size</span>
                <span className="font-medium">{formatBytes(totalSizeBytes)}</span>
              </div>
            </div>
          </section>

          <section className="rounded-xl border bg-white p-4">
            <h3 className="mb-3 text-base font-semibold">Storage</h3>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Used</span>
                <span>{formatBytes(totalSizeBytes)}</span>
              </div>
              <div className="h-2 w-full rounded-full bg-slate-200">
                <div
                  className="h-2 rounded-full bg-black transition-all"
                  style={{ width: `${usedPercent}%` }}
                />
              </div>
              <div className="flex justify-between text-xs text-gray-500">
                <span>{formatBytes(totalSizeBytes)} used</span>
                <span>Display reference: 100 MB, not a quota</span>
              </div>
            </div>
          </section>
        </div>
      </aside>
    </>
  );
};
