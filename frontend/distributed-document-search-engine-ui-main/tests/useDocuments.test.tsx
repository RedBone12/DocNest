import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AuthContext } from "../src/components/common/AuthContext";
import { useDocuments } from "../src/hooks/useDocuments";
import { getDocuments } from "../src/api/documents.api";
import { searchDocuments } from "../src/api/search.api";
import type { DocumentDto } from "../src/types/documents";

vi.mock("../src/api/documents.api", () => ({ getDocuments: vi.fn() }));
vi.mock("../src/api/search.api", () => ({ searchDocuments: vi.fn() }));

const document = { id: "doc-1", title: "Report", status: "READY" } as DocumentDto;
const library = { content: [document], totalElements: 21, totalPages: 3, number: 0, size: 10, first: true, last: false };

function wrapper({ children }: { children: ReactNode }) {
  return <AuthContext.Provider value={{ token: "token", login: vi.fn(), logout: vi.fn() }}>{children}</AuthContext.Provider>;
}

beforeEach(() => {
  vi.mocked(getDocuments).mockReset().mockResolvedValue(library);
  vi.mocked(searchDocuments).mockReset().mockResolvedValue({ documents: [document], total: 1 });
});

afterEach(() => vi.useRealTimers());

it("loads the library with pagination and preserves the total", async () => {
  const { result } = renderHook(() => useDocuments(0, "", {}, 2, 10), { wrapper });
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.documents).toEqual([document]);
  expect(result.current.total).toBe(21);
  expect(getDocuments).toHaveBeenCalledWith("token", 2, 10, expect.any(AbortSignal));
  expect(searchDocuments).not.toHaveBeenCalled();
});

it("routes trimmed queries and filters through search", async () => {
  const { result } = renderHook(() => useDocuments(0, " report ", { tags: " finance " }, 1, 5), { wrapper });
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(searchDocuments).toHaveBeenCalledWith("report", "token",
    { tags: "finance", fromDate: "", toDate: "" }, 1, 5, expect.any(AbortSignal));
  expect(getDocuments).not.toHaveBeenCalled();
});

it("searches with filters even when the text query is empty", async () => {
  const { result } = renderHook(() => useDocuments(0, "", { fromDate: "2026-01-01" }), { wrapper });
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(searchDocuments).toHaveBeenCalledOnce();
  expect(getDocuments).not.toHaveBeenCalled();
});

it("aborts an old search and prevents its late response from replacing new results", async () => {
  let finishOld!: (value: { documents: DocumentDto[]; total: number }) => void;
  vi.mocked(searchDocuments).mockImplementationOnce(() => new Promise((resolve) => { finishOld = resolve; }));
  const { result, rerender } = renderHook(({ query }) => useDocuments(0, query), {
    wrapper, initialProps: { query: "old" },
  });
  const oldSignal = vi.mocked(searchDocuments).mock.calls[0][5];
  rerender({ query: "new" });
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(oldSignal?.aborted).toBe(true);
  await act(async () => finishOld({ documents: [{ ...document, id: "stale" }], total: 99 }));
  expect(result.current.documents).toEqual([document]);
  expect(result.current.total).toBe(1);
});

it("surfaces an error and supports a successful retry", async () => {
  vi.mocked(getDocuments).mockRejectedValueOnce(new Error("Service unavailable"));
  const { result } = renderHook(() => useDocuments(0), { wrapper });
  await waitFor(() => expect(result.current.error).toBe("Service unavailable"));
  act(() => result.current.retry());
  await waitFor(() => expect(result.current.error).toBeNull());
  expect(result.current.documents).toEqual([document]);
  expect(getDocuments).toHaveBeenCalledTimes(2);
});

it("polls pending processing and stops once the document is ready", async () => {
  vi.useFakeTimers();
  vi.mocked(getDocuments).mockResolvedValueOnce({ ...library, content: [{ ...document, status: "INDEXING" }] });
  const { result } = renderHook(() => useDocuments(0), { wrapper });
  await act(async () => { await Promise.resolve(); });
  expect(result.current.documents[0].status).toBe("INDEXING");
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(result.current.documents[0].status).toBe("READY");
  await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
  expect(getDocuments).toHaveBeenCalledTimes(2);
});

it("aborts requests and clears polling on unmount", async () => {
  vi.useFakeTimers();
  vi.mocked(getDocuments).mockResolvedValue({ ...library, content: [{ ...document, status: "UPLOADED" }] });
  const { unmount } = renderHook(() => useDocuments(0), { wrapper });
  await act(async () => { await Promise.resolve(); });
  const signal = vi.mocked(getDocuments).mock.calls[0][3];
  unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
  expect(getDocuments).toHaveBeenCalledOnce();
});

it("does not load private documents without a session", async () => {
  function signedOut({ children }: { children: ReactNode }) {
    return <AuthContext.Provider value={{ token: null, login: vi.fn(), logout: vi.fn() }}>{children}</AuthContext.Provider>;
  }
  const { result } = renderHook(() => useDocuments(0), { wrapper: signedOut });
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.documents).toEqual([]);
  expect(getDocuments).not.toHaveBeenCalled();
  expect(searchDocuments).not.toHaveBeenCalled();
});
