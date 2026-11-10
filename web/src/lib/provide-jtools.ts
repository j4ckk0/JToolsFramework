import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { PagePresenceService } from './page-presence.service';

/**
 * What every org.jtools page needs to talk to its server (WebServer): HttpClient on fetch, and the page's presence
 * from its start, so that the server stops once its last page has gone. In the application's providers:
 * `providers: [provideJTools(), provideRouter(routes)]`.
 */
export function provideJTools(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideHttpClient(withFetch()),
    provideAppInitializer(() => inject(PagePresenceService).start()),
  ]);
}
