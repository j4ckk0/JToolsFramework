import { HttpErrorResponse } from '@angular/common/http';

/**
 * What to tell the user of a failed call to the server: the reason WebServer gave (`{"error": reason}`, a Refusal's
 * message or a 400's), that the server cannot be reached (status 0: stopped, or the network), else the HTTP status.
 */
export function apiErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return String(error);
  if (error.status === 0) return 'The server cannot be reached.';
  const body = error.error as { error?: unknown } | null;
  if (body && typeof body.error === 'string') return body.error;
  return `${error.status} ${error.statusText}`.trim();
}
