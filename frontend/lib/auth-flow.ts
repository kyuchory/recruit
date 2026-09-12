export const PENDING_URL_KEY = "recruitInbox.pendingUrl";

export function safeReturnTo(candidate: string | null | undefined): string {
  if (!candidate || !candidate.startsWith("/") || candidate.startsWith("//") || candidate.includes("\\")) {
    return "/app";
  }
  return candidate;
}
