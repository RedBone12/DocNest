import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { DocumentTable } from "../src/components/documents/DocumentTable";
import { useDocuments } from "../src/hooks/useDocuments";
import { deleteDocument } from "../src/api/documents.api";
import { AuthContext } from "../src/components/common/AuthContext";

vi.mock("../src/hooks/useDocuments", () => ({ useDocuments: vi.fn() }));
vi.mock("../src/hooks/usePreferences", () => ({
  usePreferences: () => ({ preferences: { showAiSummaries: true, density: "comfortable" } }),
}));
vi.mock("../src/api/documents.api", () => ({
  deleteDocument: vi.fn(), downloadDocument: vi.fn(), requestAiAnalysis: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(useDocuments).mockReturnValue({
    documents: [{ id: "doc-1", title: "Report", description: "Results", status: "READY", createdAt: "2026-01-01" }],
    total: 1, loading: false, error: null, retry: vi.fn(),
  });
  vi.mocked(deleteDocument).mockReset().mockResolvedValue(undefined);
});

function mount() {
  const refreshed = vi.fn();
  render(<AuthContext.Provider value={{ token: "token", login: vi.fn(), logout: vi.fn() }}>
    <DocumentTable refreshKey={0} searchQuery="" filters={{}} onDeleted={refreshed} />
  </AuthContext.Provider>);
  return refreshed;
}

it("canceling the confirmation never calls the delete API", async () => {
  const refreshed = mount();
  await userEvent.click(screen.getByRole("button", { name: "Delete Report" }));
  await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(deleteDocument).not.toHaveBeenCalled();
  expect(refreshed).not.toHaveBeenCalled();
});

it("deletes only after confirmation and requests a library refresh on success", async () => {
  const refreshed = mount();
  await userEvent.click(screen.getByRole("button", { name: "Delete Report" }));
  expect(deleteDocument).not.toHaveBeenCalled();
  await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));
  await waitFor(() => expect(refreshed).toHaveBeenCalledOnce());
  expect(deleteDocument).toHaveBeenCalledWith("doc-1", "token");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

it("keeps the dialog open and does not refresh when deletion fails", async () => {
  vi.mocked(deleteDocument).mockRejectedValue(new Error("Delete failed"));
  const alert = vi.spyOn(window, "alert").mockImplementation(() => {});
  const refreshed = mount();
  await userEvent.click(screen.getByRole("button", { name: "Delete Report" }));
  await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));
  await waitFor(() => expect(alert).toHaveBeenCalledWith("Delete failed"));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  expect(refreshed).not.toHaveBeenCalled();
});
