import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Menu } from './menu';
import { MenuBar } from './menu-bar';

const SETTINGS = { autoStop: true, port: 0, defaultPort: 8090, currentPort: 8090 };

/** A program's page: its name, an entry of its own in File, a menu of its own, About's text. */
@Component({
  imports: [MenuBar, Menu],
  template: `
    <jtools-menu-bar name="Bookshelf" (stopped)="stopped = true">
      <button fileItems type="button">Open…</button>
      <jtools-menu label="Books"><button type="button">Add a book</button></jtools-menu>
      <p about>Keeps the books one owns.</p>
    </jtools-menu-bar>
  `,
})
class Page {
  stopped = false;
}

describe('MenuBar', () => {
  let fixture: ComponentFixture<Page>;
  let http: HttpTestingController;
  let page: HTMLElement;

  function button(text: string): HTMLButtonElement {
    const found = [...page.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
    if (!found) throw new Error(`no button "${text}"`);
    return found;
  }

  /** Clicks the button whose text is given, then lets the page render. */
  async function click(text: string): Promise<void> {
    button(text).click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** The texts of the open dropdowns. */
  function openMenus(): string[] {
    return [...page.querySelectorAll<HTMLElement>('.jtools-dropdown:not([hidden])')].map(
      (m) => m.textContent ?? '',
    );
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Page],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(Page);
    http = TestBed.inject(HttpTestingController);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    http.expectOne('api/settings').flush(SETTINGS);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it("puts the program's menus between File and Settings, and its entries in File", async () => {
    const menus = [...page.querySelectorAll('.jtools-menubtn')].map((b) => b.textContent?.trim());
    expect(menus).toEqual(['File ▾', 'Books ▾', 'Settings ▾', 'Logs ▾', 'Help ▾']);
    await click('File ▾');
    expect(openMenus()).toHaveLength(1);
    expect(openMenus()[0]).toContain('Open…');
    expect(openMenus()[0]).toContain('Quit Bookshelf');
  });

  it('opens one menu at a time, and closes it on a click elsewhere', async () => {
    await click('File ▾');
    await click('Books ▾');
    expect(openMenus()).toEqual([expect.stringContaining('Add a book')]);
    document.body.click();
    fixture.detectChanges();
    expect(openMenus()).toEqual([]);
  });

  it('flips the automatic stop on the server', async () => {
    await click('Settings ▾');
    const autoStop = button('Stop the server when the last page is closed');
    expect(autoStop.getAttribute('aria-checked')).toBe('true');
    autoStop.click();
    const request = http.expectOne('api/settings');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ autoStop: false });
    request.flush({ ...SETTINGS, autoStop: false });
    fixture.detectChanges();
    expect(page.querySelector('.jtools-status')?.textContent).toContain('File ▸ Quit Bookshelf');
  });

  it('refuses a port out of range, and saves one in range', async () => {
    await click('Settings ▾');
    await click('Server port…');
    const input = page.querySelector<HTMLInputElement>('#jtoolsPortInput')!;
    input.value = '70000';
    input.dispatchEvent(new Event('input'));
    await click('Save');
    expect(page.querySelector('.jtools-status')?.textContent).toContain('between 1 and 65535');
    input.value = '9000';
    input.dispatchEvent(new Event('input'));
    await click('Save');
    const request = http.expectOne('api/settings');
    expect(request.request.body).toEqual({ port: 9000 });
    request.flush({ ...SETTINGS, port: 9000 });
    fixture.detectChanges();
    expect(page.querySelector('#jtoolsPortInput')).toBeNull();
    expect(page.querySelector('.jtools-status')?.textContent).toContain('Restart Bookshelf');
  });

  it("shows the version, the runtime and the program's own text in About", async () => {
    await click('Help ▾');
    await click('About Bookshelf…');
    http
      .expectOne('api/about')
      .flush({ name: 'Bookshelf', version: '1.2.3', javaVersion: '25.0.5', logFile: '' });
    fixture.detectChanges();
    const about = page.querySelector<HTMLElement>('.jtools-modal:not([hidden])')!;
    expect(about.textContent).toContain('Version 1.2.3');
    expect(about.textContent).toContain('Java 25.0.5');
    expect(about.textContent).toContain('Keeps the books one owns.');
  });

  it("names the runtime's links after the server's Java", async () => {
    await click('Help ▾');
    await click('Embedded Java runtime…');
    http
      .expectOne('api/about')
      .flush({ name: 'Bookshelf', version: '1.2.3', javaVersion: '25.0.5' });
    fixture.detectChanges();
    const links = [...page.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(links).toEqual([
      'https://adoptium.net/temurin/releases/?version=25',
      'https://github.com/openjdk/jdk25u',
    ]);
  });

  it('stops the server on Quit, once confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window, 'close').mockImplementation(() => undefined);
    await click('File ▾');
    await click('Quit Bookshelf');
    const request = http.expectOne('api/quit');
    expect(request.request.method).toBe('POST');
    request.flush({ ok: true });
    expect(fixture.componentInstance.stopped).toBe(true);
  });

  it('leaves the server running when Quit is not confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    await click('File ▾');
    await click('Quit Bookshelf');
    http.expectNone('api/quit');
  });
});
