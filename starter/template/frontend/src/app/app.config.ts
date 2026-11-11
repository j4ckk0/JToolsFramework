import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideJTools } from '@jtools/web';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // HttpClient, and the page's presence: the server stops once its last page has gone
    provideJTools(),
    provideRouter(routes, withComponentInputBinding()),
  ],
};
