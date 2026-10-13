import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { LoginPage } from "./pages/LoginPage";
import { AppLayout } from "./components/layout/AppLayout";
import { ProtectedRoute } from "./components/common/ProtectedRoute";
import { Documents } from "./pages/Documents";
import { useAuth } from "./components/common/AuthContext";
import { SettingsPage } from "./pages/SettingsPage";

function App() {
  const { token } = useAuth();
  return (
    <BrowserRouter>
      <Routes>
        {/* Default entry */}
        <Route
          path="/"
          element={<Navigate to={token ? "/documents" : "/login"} replace />}
        />

        {/* Public */}
        <Route path="/login" element={<LoginPage />} />

        {/* Protected */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/documents" element={<Documents />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
