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
    TestBed.inject(HttpTestingController).expectOne('api/info').flush({ name: '__APP_NAME__', version: '1.2.3' });
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('header')?.textContent).toContain('__APP_NAME__');
    expect(page.querySelector('footer')?.textContent).toContain('1.2.3');
  });

  it('says so when the server cannot be reached', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('api/info')
      .error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error')?.textContent).toContain('cannot be reached');
  });
});
