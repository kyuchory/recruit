import { defineConfig, devices } from "@playwright/test";

/**
 * Minimal happy-path E2E. Requires the backend (`:8080`) and its PostgreSQL +
 * Redis to be running; the frontend dev server is started by Playwright.
 *   docker compose up -d && (cd backend && ./gradlew bootRun)
 *   cd frontend && npm run test:e2e
 */
const FRONTEND_URL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"]],
  use: {
    baseURL: FRONTEND_URL,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: FRONTEND_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
