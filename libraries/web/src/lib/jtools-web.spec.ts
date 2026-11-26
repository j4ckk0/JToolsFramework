import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { apiErrorMessage, PagePresenceService, provideJTools } from './index';

describe('PagePresenceService', () => {
  /** The pages a test made: stopped after it, so that a later test's pagehide finds none of them still streaming. */
  const pages: PagePresenceService[] = [];

  afterEach(() => {
    pages.splice(0).forEach((page) => page.stop());
    vi.unstubAllGlobals();
  });

  function page(): PagePresenceService {
    const made = new PagePresenceService();
    pages.push(made);
    return made;
  }

  /** The browser's Web Locks for one lock: granted in turn, released as the holder's promise settles. */
  function fakeLocks() {
    const names: string[] = [];
    const queue: { grant: () => void }[] = [];
    let held = false;
    const next = () => {
      if (!held) queue.shift()?.grant();
    };
    const locks = {
      request(
        name: string,
        options: { signal?: AbortSignal },
        callback: () => unknown,
      ): Promise<unknown> {
        names.push(name);
        return new Promise((resolve, reject) => {
          const waiting = {
            grant: () => {
              held = true;
              Promise.resolve(callback()).then((value) => {
                held = false;
                resolve(value);
                next();
              });
            },
          };
          options.signal?.addEventListener('abort', () => {
            const index = queue.indexOf(waiting);
            if (index < 0) return;
            queue.splice(index, 1);
            reject(new DOMException('The request was aborted.', 'AbortError'));
          });
          queue.push(waiting);
          queueMicrotask(next);
        });
      },
    };
    return { locks, names };
  }

  function fakeBrowser(withLocks = true) {
    const opened: string[] = [];
    const closed: string[] = [];
    const beacons: string[] = [];
    vi.stubGlobal(
      'EventSource',
      class {
        constructor(readonly url: string) {
          opened.push(url);
        }
        close() {
          closed.push(this.url);
        }
      },
    );
    const { locks, names } = fakeLocks();
    vi.stubGlobal('navigator', {
      sendBeacon: (url: string) => beacons.push(url) > 0,
      ...(withLocks ? { locks } : {}),
    });
    return { opened, closed, beacons, lockNames: names };
  }

  /** Lets the locks' grants and releases, which are asynchronous, run. */
  const settle = () => new Promise((resolve) => setTimeout(resolve));

  it('holds a stream while the page lives and says goodbye as it goes', async () => {
    const browser = fakeBrowser();
    const presence = page();
    presence.start();
    presence.start();
    await settle();
    expect(browser.opened.length).toBe(1);
    expect(browser.opened[0]).toMatch(/^api\/alive\?id=.+/);
    expect(browser.lockNames).toEqual([
      `jtools.presence ${new URL('api/alive', document.baseURI).href}`,
    ]);

    dispatchEvent(new Event('pagehide'));
    expect(browser.closed).toEqual(browser.opened);
    expect(browser.beacons).toEqual([browser.opened[0].replace('api/alive', 'api/bye')]);
  });

  it("holds one stream for all the browser's pages, handed to the next as its holder goes", async () => {
    const browser = fakeBrowser();
    const first = page();
    const second = page();
    first.start();
    second.start();
    await settle();
    expect(browser.opened.length).toBe(1);

    first.stop();
    await settle();
    expect(browser.closed).toEqual([browser.opened[0]]);
    expect(browser.beacons).toEqual([browser.opened[0].replace('api/alive', 'api/bye')]);
    expect(browser.opened.length).toBe(2);
    expect(browser.opened[1]).not.toBe(browser.opened[0]);
  });

  it('leaves the queue without a stream nor a beacon when it stops while waiting', async () => {
    const browser = fakeBrowser();
    const first = page();
    const second = page();
    first.start();
    second.start();
    await settle();
    second.stop();
    first.stop();
    await settle();
    expect(browser.opened.length).toBe(1);
    expect(browser.beacons.length).toBe(1);

    second.start();
    await settle();
    expect(browser.opened.length).toBe(2);
  });

  it('holds a stream of its own where the browser has no Web Locks', () => {
    const browser = fakeBrowser(false);
    page().start();
    page().start();
    expect(browser.opened.length).toBe(2);
  });

  it('is started by provideJTools as the application starts', async () => {
    const browser = fakeBrowser();
    TestBed.configureTestingModule({ providers: [provideJTools()] });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    pages.push(TestBed.inject(PagePresenceService));
    await settle();
    expect(browser.opened.length).toBe(1);
  });
});

describe('apiErrorMessage', () => {
  it("says the server's reason", () => {
    const refused = new HttpErrorResponse({ status: 409, statusText: 'Conflict', error: { error: 'the name is taken' } });
    expect(apiErrorMessage(refused)).toBe('the name is taken');
  });

  it('says when the server cannot be reached', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }))).toBe('The server cannot be reached.');
  });

  it('falls back on the status', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' }))).toBe(
      '500 Internal Server Error',
    );
  });
});
