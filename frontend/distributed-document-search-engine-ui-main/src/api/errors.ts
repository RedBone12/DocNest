export function serviceError(status: number): string {
  if (status === 401) {
    if (typeof window !== "undefined") window.dispatchEvent(new Event("docnest:session-expired"));
    return "Your session has expired. Please sign in again.";
  }
  if (status === 429) return "Too many attempts. Please try again later.";
  if (status === 403) return "You do not have permission to perform this action.";
  if (status === 413) return "This file is too large. The upload limit is 50 MB.";
  if (status >= 500 || status === 0) return "The service is currently unavailable. Please try again shortly.";
  return `The request could not be completed (HTTP ${status}). Please try again.`;
}
export function errorMessage(error: unknown): string {
  return error instanceof TypeError ? serviceError(0)
    : error instanceof Error ? error.message : serviceError(0);
}
