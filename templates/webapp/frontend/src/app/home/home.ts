import { Component } from '@angular/core';

/** The first view of the page; the application's views join it in app.routes.ts. */
@Component({
  selector: 'app-home',
  template: `
    <h2>Welcome</h2>
    <p>__APP_NAME__ runs on the server; this page is its view.</p>
  `,
})
export class Home {}
