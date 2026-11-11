import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { ApiService } from '../api.service';

/** The first view of the page; the application's views join it in app.routes.ts. */
@Component({
  selector: 'app-home',
  template: `
    <h2>Welcome</h2>
    <p>{{ summary() ?? '__APP_NAME__ runs on the server; this page is its view.' }}</p>
  `,
})
export class Home {
  readonly summary = toSignal(inject(ApiService).status().pipe(map((status) => status.summary)));
}
