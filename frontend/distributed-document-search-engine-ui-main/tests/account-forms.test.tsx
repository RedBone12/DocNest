import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { RegisterPage } from "../src/pages/RegisterPage";
import { ForgotPasswordPage } from "../src/pages/ForgotPasswordPage";
import { ResetPasswordPage } from "../src/pages/ResetPasswordPage";
import { LoginPage } from "../src/pages/LoginPage";
import { AccountSecurity } from "../src/components/common/AccountSecurity";
import * as api from "../src/api/auth.api";

const state = vi.hoisted(() => ({ token: "signed" as string | null, login: vi.fn() }));
vi.mock("../src/api/auth.api", () => ({ registerRequest: vi.fn(), authCapabilities: vi.fn(), forgotPasswordRequest: vi.fn(), resetPasswordRequest: vi.fn(), changePasswordRequest: vi.fn() }));
vi.mock("../src/components/common/AuthContext", () => ({ useAuth: () => state }));
beforeEach(() => { vi.resetAllMocks(); state.token = "signed"; window.history.replaceState(null, "", "/"); });
const change = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
const submit = (container: HTMLElement) => fireEvent.submit(container.querySelector("form")!);
function mount(element: React.ReactNode, notice = false) {
  return render(<MemoryRouter initialEntries={[notice ? { pathname: "/", state: { message: "Account ready" } } : "/"]}><Routes>
    <Route path="/" element={element} /><Route path="/login" element={<p>Login destination</p>} /><Route path="/documents" element={<p>Documents destination</p>} />
  </Routes></MemoryRouter>);
}

it("validates registration, surfaces failure and navigates on success", async () => {
  const view = mount(<RegisterPage />);
  change("Email", "demo@example.com"); change("Password", "short"); submit(view.container);
  expect(screen.getByRole("alert")).toHaveTextContent("12 characters");
  change("Password", "long-password-123"); change("Confirm password", "different-password"); submit(view.container);
  expect(screen.getByRole("alert")).toHaveTextContent("do not match");
  change("Confirm password", "long-password-123");
  vi.mocked(api.registerRequest).mockRejectedValueOnce(new Error("duplicate")); submit(view.container);
  await screen.findByText("duplicate");
  vi.mocked(api.registerRequest).mockResolvedValueOnce(); submit(view.container);
  await screen.findByText("Login destination");
});

it.each([false, true])("checks recovery availability %s", async available => {
  vi.mocked(api.authCapabilities).mockResolvedValue({ passwordResetEnabled: available });
  const view = mount(<ForgotPasswordPage />);
  if (!available) { await screen.findByText(/not configured on this server/); expect(screen.getByRole("button", { name: "Send reset link" })).toBeDisabled(); return; }
  await waitFor(() => expect(screen.getByRole("button", { name: "Send reset link" })).toBeEnabled());
  change("Email", "demo@example.com");
  vi.mocked(api.forgotPasswordRequest).mockRejectedValueOnce(new Error("mail unavailable")); submit(view.container); await screen.findByText("mail unavailable");
  vi.mocked(api.forgotPasswordRequest).mockResolvedValueOnce("Check your inbox"); submit(view.container); await screen.findByText("Check your inbox");
  expect(screen.getByRole("button", { name: /Please wait before/ })).toBeDisabled();
});

it("shows a recovery capability outage", async () => {
  vi.mocked(api.authCapabilities).mockRejectedValue(new Error("capability outage")); mount(<ForgotPasswordPage />); await screen.findByText("capability outage");
});

it("rejects a reset link without a token", () => { mount(<ResetPasswordPage />); expect(screen.getByRole("alert")).toHaveTextContent("missing its token"); });
it("removes the reset token from history and handles validation, failure and success", async () => {
  window.history.replaceState(null, "", "/#token=disposable-reset");
  const view = mount(<ResetPasswordPage />); expect(window.location.hash).toBe("");
  change("New password", "short"); submit(view.container); expect(screen.getByRole("alert")).toHaveTextContent("12 characters");
  change("New password", "long-password-123"); change("Confirm new password", "other-password-123"); submit(view.container); expect(screen.getByRole("alert")).toHaveTextContent("do not match");
  change("Confirm new password", "long-password-123"); vi.mocked(api.resetPasswordRequest).mockRejectedValueOnce(new Error("expired reset")); submit(view.container); await screen.findByText("expired reset");
  vi.mocked(api.resetPasswordRequest).mockResolvedValueOnce(); submit(view.container); await screen.findByText("Login destination");
  expect(api.resetPasswordRequest).toHaveBeenLastCalledWith("disposable-reset", "long-password-123");
});

it("shows login notices, toggles password visibility and handles sign-in errors", async () => {
  const view = mount(<LoginPage />, true); expect(screen.getByRole("status")).toHaveTextContent("Account ready");
  change("Email", "demo@example.com"); change("Password", "long-password-123");
  fireEvent.click(screen.getByRole("button", { name: "Show password" })); expect(screen.getByLabelText("Password", { exact: true })).toHaveAttribute("type", "text");
  fireEvent.click(screen.getByRole("button", { name: "Hide password" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "Remember me" }));
  state.login.mockRejectedValueOnce(new Error("sign-in failed")); submit(view.container); await screen.findByText("sign-in failed");
  state.login.mockResolvedValueOnce("signed"); submit(view.container); await screen.findByText("Documents destination");
  expect(state.login).toHaveBeenLastCalledWith("demo@example.com", "long-password-123", true);
});

it("handles password-change validation, errors and session revocation", async () => {
  const view = mount(<AccountSecurity />); state.token = null; submit(view.container); expect(api.changePasswordRequest).not.toHaveBeenCalled();
  state.token = "signed"; view.rerender(<MemoryRouter><AccountSecurity /></MemoryRouter>); change("Current password", "old-password-123");
  change("New password", "short"); submit(view.container); expect(screen.getByRole("alert")).toHaveTextContent("12 characters");
  change("New password", "long-password-123"); change("Confirm new password", "different-password"); submit(view.container); expect(screen.getByRole("alert")).toHaveTextContent("do not match");
  change("Confirm new password", "long-password-123"); vi.mocked(api.changePasswordRequest).mockRejectedValueOnce(new Error("wrong old password")); submit(view.container); await screen.findByText("wrong old password");
  vi.mocked(api.changePasswordRequest).mockResolvedValueOnce(); submit(view.container); await waitFor(() => expect(api.changePasswordRequest).toHaveBeenCalledTimes(2));
});
