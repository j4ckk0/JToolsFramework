import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { apiErrorMessage, PagePresenceService, provideJTools } from './index';

describe('PagePresenceService', () => {
  afterEach(() => vi.unstubAllGlobals());

  function fakeBrowser() {
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
    vi.stubGlobal('navigator', { sendBeacon: (url: string) => beacons.push(url) > 0 });
    return { opened, closed, beacons };
  }

  it('holds a stream while the page lives and says goodbye as it goes', () => {
    const browser = fakeBrowser();
    const presence = TestBed.inject(PagePresenceService);
    presence.start();
    presence.start();
    expect(browser.opened.length).toBe(1);
    expect(browser.opened[0]).toMatch(/^api\/alive\?id=.+/);

    dispatchEvent(new Event('pagehide'));
    expect(browser.closed).toEqual(browser.opened);
    expect(browser.beacons).toEqual([browser.opened[0].replace('api/alive', 'api/bye')]);
  });

  it('is started by provideJTools as the application starts', async () => {
    const browser = fakeBrowser();
    TestBed.configureTestingModule({ providers: [provideJTools()] });
    await TestBed.inject(ApplicationInitStatus).donePromise;
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
