import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MenuBar, ProgramAbout, ProgramApiService, apiErrorMessage } from '@jtools/web';

/** The frame of every page: the menu bar, the view, the footer, with what the server says the application is. */
@Component({
  imports: [RouterOutlet, MenuBar],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly about = signal<ProgramAbout | undefined>(undefined);
  /** Why the server did not answer, when it did not. */
  protected readonly error = signal<string | undefined>(undefined);
  /** Set once File ▸ Quit has stopped the server: the page then says so and nothing else. */
  protected readonly stopped = signal(false);

  constructor() {
    inject(ProgramApiService)
      .about()
      .subscribe({
        next: (about) => this.about.set(about),
        error: (error) => this.error.set(apiErrorMessage(error)),
      });
  }
}
