import { Injectable } from '@angular/core';

/** The paths of the server's presence endpoints (WebServer.ALIVE, WebServer.BYE on the server). */
const ALIVE = 'api/alive';
const BYE = 'api/bye';

/** An id of this page: crypto.randomUUID needs a secure context, which http://<lan-ip> is not. */
function newPageId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Tells the server this page is open, so that it stops once the last page has gone (PageWatch on the server). An event
 * stream is used because the browser drops it when the page closes or crashes, background tabs do not throttle it, and
 * EventSource reconnects it under the same id after a sleep; the beacon on pagehide spares the server the wait.
 */
@Injectable({ providedIn: 'root' })
export class PagePresenceService {
  private readonly id = newPageId();
  private stream: EventSource | null = null;

  constructor() {
    if (typeof addEventListener === 'undefined') return;
    addEventListener('pagehide', () => this.stop());
    addEventListener('pageshow', (event) => {
      if ((event as PageTransitionEvent).persisted) this.start();
    });
  }

  start(): void {
    // no EventSource in a test's jsdom
    if (this.stream || typeof EventSource === 'undefined') return;
    this.stream = new EventSource(this.withId(ALIVE));
  }

  /** Closes the stream and tells the server at once (a beacon outlives the page). */
  stop(): void {
    if (!this.stream) return;
    this.stream.close();
    this.stream = null;
    navigator.sendBeacon?.(this.withId(BYE));
  }

  private withId(path: string): string {
    return `${path}?id=${encodeURIComponent(this.id)}`;
  }
}
