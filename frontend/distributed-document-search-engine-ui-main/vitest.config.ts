import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    env: { VITE_API_BASE_URL: "/api" },
    environment: "jsdom",
    include: ["tests/**/*.test.{ts,tsx}"],
    setupFiles: ["./tests/setup.ts"],
    restoreMocks: true,
    clearMocks: true,
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/main.tsx", "src/types/**"],
      reporter: ["text", "html", "json-summary", "lcov"],
      thresholds: { lines: 60, statements: 58, branches: 59, functions: 52 },
    },
  },
});
