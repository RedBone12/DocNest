import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { DeleteConfirmationModal } from "../src/components/documents/DeleteConfirmationModal";
import { UploadFormModal } from "../src/components/documents/UploadFormModal";

vi.mock("../src/hooks/usePreferences", () => ({
  usePreferences: () => ({ preferences: { defaultAiAnalysis: false } }),
}));

describe("Delete confirmation", () => {
  it("does not render a closed dialog", () => {
    render(<DeleteConfirmationModal isOpen={false} onClose={vi.fn()} onConfirm={vi.fn()} documentName="Report" />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("requires confirmation and identifies the selected document", async () => {
    const confirm = vi.fn();
    render(<DeleteConfirmationModal isOpen onClose={vi.fn()} onConfirm={confirm} documentName="Annual Report" />);
    expect(screen.getByRole("dialog", { name: "Delete Document" })).toHaveTextContent("Annual Report");
    expect(confirm).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(confirm).toHaveBeenCalledTimes(1);
  });

  it("cancel closes without deleting", async () => {
    const close = vi.fn();
    const confirm = vi.fn();
    render(<DeleteConfirmationModal isOpen onClose={close} onConfirm={confirm} documentName="Report" />);
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(close).toHaveBeenCalledOnce();
    expect(confirm).not.toHaveBeenCalled();
  });

  it("Escape closes an idle dialog", async () => {
    const close = vi.fn();
    render(<DeleteConfirmationModal isOpen onClose={close} onConfirm={vi.fn()} documentName="Report" />);
    await userEvent.keyboard("{Escape}");
    expect(close).toHaveBeenCalledOnce();
  });

  it("disables confirmation and Escape while deleting", async () => {
    const close = vi.fn();
    const confirm = vi.fn();
    render(<DeleteConfirmationModal isOpen isDeleting onClose={close} onConfirm={confirm} documentName="Report" />);
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Deleting..." }));
    await userEvent.keyboard("{Escape}");
    expect(confirm).not.toHaveBeenCalled();
    expect(close).not.toHaveBeenCalled();
  });
});

describe("Upload dialog", () => {
  it("requires a file and keeps AI analysis opt-in", () => {
    render(<UploadFormModal isOpen onClose={vi.fn()} onUpload={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Upload" })).toBeDisabled();
    expect(screen.getByRole("checkbox")).not.toBeChecked();
  });

  it("submits selected files, description and explicit AI consent then closes", async () => {
    const user = userEvent.setup();
    const upload = vi.fn().mockResolvedValue(undefined);
    const close = vi.fn();
    render(<UploadFormModal isOpen onClose={close} onUpload={upload} />);
    const file = new File(["report"], "report.pdf", { type: "application/pdf" });
    await user.upload(screen.getByLabelText("Select files"), file);
    await user.type(screen.getByLabelText("Description (Optional)"), "Quarterly results");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Upload (1)" }));
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
    expect(upload).toHaveBeenCalledOnce();
    const [files, description, consent] = upload.mock.calls[0];
    expect(Array.from(files)).toEqual([file]);
    expect(description).toBe("Quarterly results");
    expect(consent).toBe(true);
  });

  it("shows server errors and retains files for retry", async () => {
    const user = userEvent.setup();
    const upload = vi.fn().mockRejectedValue(new Error("Storage unavailable"));
    const close = vi.fn();
    render(<UploadFormModal isOpen onClose={close} onUpload={upload} />);
    await user.upload(screen.getByLabelText("Select files"), new File(["report"], "report.pdf"));
    await user.click(screen.getByRole("button", { name: "Upload (1)" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Storage unavailable");
    expect(screen.getByText("report.pdf")).toBeInTheDocument();
    expect(close).not.toHaveBeenCalled();
    upload.mockResolvedValue(undefined);
    await user.click(screen.getByRole("button", { name: "Upload (1)" }));
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
    expect(upload).toHaveBeenCalledTimes(2);
  });

  it.each([45, 100])("displays progress %i without claiming completion", async (progress) => {
    const close = vi.fn();
    render(<UploadFormModal isOpen isUploading progress={progress} onClose={close} onUpload={vi.fn()} />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("value", String(progress));
    expect(screen.getByRole("status")).toHaveTextContent(progress === 100 ? "Waiting for the server" : "45%");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
    await userEvent.keyboard("{Escape}");
    expect(close).not.toHaveBeenCalled();
  });
});
