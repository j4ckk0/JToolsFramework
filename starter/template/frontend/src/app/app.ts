import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { apiErrorMessage } from '@jtools/web';
import { About, ApiService } from './api.service';

/** The frame of every page: the header and the footer, with what the server says the application is. */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly about = signal<About | undefined>(undefined);
  /** Why the server did not answer, when it did not. */
  protected readonly error = signal<string | undefined>(undefined);

  constructor() {
    inject(ApiService)
      .about()
      .subscribe({
        next: (about) => this.about.set(about),
        error: (error) => this.error.set(apiErrorMessage(error)),
      });
  }
}
