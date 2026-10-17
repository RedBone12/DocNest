import { Outlet, useNavigate, useLocation } from "react-router-dom";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { UserPanel } from "./UserPanel";
import { useAuth } from "../common/AuthContext";
import { useEffect, useRef, useState } from "react";
import { useDocuments } from "../../hooks/useDocuments";
import { usePreferences } from "../../hooks/usePreferences";
import { sessionEmail } from "../../auth/session";

function getUserFromToken(token: string | null): string {
  const storedUser = sessionEmail();

  if (storedUser) return storedUser;
  if (!token) return "User";
  try {
    const payloadPart = token.split(".")[1];
    if (!payloadPart) return "User";
    const normalized = payloadPart.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(
      normalized.length + ((4 - (normalized.length % 4)) % 4),
      "=",
    );
    const decoded = atob(padded);
    const payload = JSON.parse(decoded) as { sub?: string };
    return payload.sub || "User";
  } catch {
    return "User";
  }
}

export const AppLayout = () => {
  const { preferences } = usePreferences();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  const [userPanelOpen, setUserPanelOpen] = useState(false);
  const [panelRefreshKey, setPanelRefreshKey] = useState(0);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const focusSearchRequested = useRef(false);
  useEffect(() => {
    if (pathname === "/documents" && focusSearchRequested.current) {
      searchInputRef.current?.focus();
      focusSearchRequested.current = false;
    }
  }, [pathname]);
  const { token, logout } = useAuth();
  const { documents } = useDocuments(panelRefreshKey, "");

  useEffect(() => {
    if (!userPanelOpen) return;

    const intervalId = window.setInterval(() => {
      setPanelRefreshKey((k) => k + 1);
    }, 10000);

    return () => window.clearInterval(intervalId);
  }, [userPanelOpen]);

  const handleLogout = () => {
    void logout().catch((error: unknown) => window.alert(error instanceof Error ? error.message : "Unable to revoke your session."));
    setSidebarOpen(false);
    setUserPanelOpen(false);
    navigate("/login", { replace: true });
  };

  const handleOpenUpload = () => {
    setSidebarOpen(false);
    navigate("/documents");
    setUploadModalOpen(true);
  };

  const handleFocusSearch = () => {
    setSidebarOpen(false);
    if (pathname === "/documents") window.setTimeout(() => searchInputRef.current?.focus(), 0);
    else {
      focusSearchRequested.current = true;
      navigate("/documents");
    }
  };

  const handleShowDocuments = () => {
    setSidebarOpen(false);
    setSearchQuery("");
    navigate("/documents");
  };

  const handleShowSettings = () => {
    setSidebarOpen(false);
    navigate("/settings");
  };

  return (
    <div data-theme={preferences.theme} className="h-dvh flex bg-gray-50">
      <Sidebar
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onUpload={handleOpenUpload}
        onSearch={handleFocusSearch}
        onDocuments={handleShowDocuments}
        onSettings={handleShowSettings}
      />
      <UserPanel
        open={userPanelOpen}
        onClose={() => setUserPanelOpen(false)}
        userEmail={getUserFromToken(token)}
        documents={documents}
        onLogout={handleLogout}
      />

      <div className="flex min-w-0 flex-col flex-1">
        <Header
          onMenuClick={() => setSidebarOpen((prev) => !prev)}
          onUserClick={() =>
            setUserPanelOpen((prev) => {
              const next = !prev;
              if (next) setPanelRefreshKey((k) => k + 1);
              return next;
            })
          }
        />

        <main className="min-h-0 flex-1 p-3 sm:p-6 overflow-auto">
          <Outlet
            context={{
              uploadModalOpen,
              openUpload: handleOpenUpload,
              closeUpload: () => setUploadModalOpen(false),
              searchInputRef,
              searchQuery,
              setSearchQuery,
            }}
          />
        </main>
      </div>
    </div>
  );
};
