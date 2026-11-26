import { Injectable } from '@angular/core';

/** The paths of the server's presence endpoints (WebServer.ALIVE, WebServer.BYE on the server). */
const ALIVE = 'api/alive';
const BYE = 'api/bye';

/** The Web Lock the pages of one server queue for: its stream's address, as the pages may be served under a prefix. */
const LOCK_PREFIX = 'jtools.presence ';

/** An id of this page: crypto.randomUUID needs a secure context, which http://<lan-ip> is not. */
function newPageId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** This page's place in the queue for the lock: abort leaves the queue, release gives the lock up once held. */
interface Turn {
  readonly abort: AbortController;
  release: () => void;
}

/**
 * Tells the server this page is open, so that it stops once the last page has gone (PageWatch on the server): an event
 * stream, which the browser drops with the page and reconnects after a sleep, and a beacon on pagehide. One stream per
 * browser, held by the page holding a Web Lock, as a stream per tab would take the six connections a browser opens to a
 * server; without Web Locks (not a secure context), each page holds its own (doc/CONCEPTS.md, the page's presence).
 */
@Injectable({ providedIn: 'root' })
export class PagePresenceService {
  private readonly id = newPageId();
  private stream: EventSource | null = null;
  private turn: Turn | null = null;

  constructor() {
    if (typeof addEventListener === 'undefined') return;
    addEventListener('pagehide', () => this.stop());
    addEventListener('pageshow', (event) => {
      if ((event as PageTransitionEvent).persisted) this.start();
    });
  }

  /** Holds the stream, or queues for it while another page of this browser does; nothing when already done. */
  start(): void {
    // no EventSource in a test's jsdom
    if (this.stream || this.turn || typeof EventSource === 'undefined') return;
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    if (!locks) {
      this.open();
      return;
    }
    const turn: Turn = { abort: new AbortController(), release: () => undefined };
    this.turn = turn;
    locks
      .request(this.lockName(), { signal: turn.abort.signal }, () => {
        // granted just as the page stopped: give the lock straight to the next page
        if (this.turn !== turn) return undefined;
        this.open();
        return new Promise<void>((release) => (turn.release = release));
      })
      .catch(() => {
        /* stopped while queuing: the request was aborted */
      });
  }

  /** Closes the stream and tells the server at once (a beacon outlives the page), then hands the lock on. */
  stop(): void {
    const turn = this.turn;
    this.turn = null;
    if (this.stream) {
      this.stream.close();
      this.stream = null;
      navigator.sendBeacon?.(this.withId(BYE));
    }
    turn?.abort.abort();
    turn?.release();
  }

  private open(): void {
    this.stream = new EventSource(this.withId(ALIVE));
  }

  private lockName(): string {
    const base = typeof document !== 'undefined' ? document.baseURI : undefined;
    return LOCK_PREFIX + (base ? new URL(ALIVE, base).href : ALIVE);
  }

  private withId(path: string): string {
    return `${path}?id=${encodeURIComponent(this.id)}`;
  }
}
