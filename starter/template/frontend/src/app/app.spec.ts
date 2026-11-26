import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }),
  );

  it('shows what the server says the application is', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('api/about').flush({ name: '__APP_NAME__', version: '1.2.3' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('header')?.textContent).toContain('__APP_NAME__');
    expect(page.querySelector('footer')?.textContent).toContain('1.2.3');
  });

  it('says so when the server cannot be reached', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('api/about')
      .error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error')?.textContent).toContain('cannot be reached');
  });

  it('says the program has stopped once File ▸ Quit has stopped it', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window, 'close').mockImplementation(() => undefined);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    [...page.querySelectorAll('button')].find((b) => b.textContent?.includes('Quit'))?.click();
    TestBed.inject(HttpTestingController).expectOne('api/quit').flush({ ok: true });
    fixture.detectChanges();
    expect(page.textContent).toContain('__APP_NAME__ stopped');
    vi.restoreAllMocks();
  });
});
