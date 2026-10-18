import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { ProtectedRoute } from "../src/components/common/ProtectedRoute";
import { AuthContext } from "../src/components/common/AuthContext";

it.each([null, "valid-token"])("protects routes for token %s", (token) => {
  render(
    <AuthContext.Provider value={{ token, login: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={["/documents"]}>
        <Routes>
          <Route path="/login" element={<p>Sign in</p>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/documents" element={<p>Private library</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  expect(screen.getByText(token ? "Private library" : "Sign in")).toBeInTheDocument();
  expect(screen.queryByText(token ? "Sign in" : "Private library")).not.toBeInTheDocument();
});
