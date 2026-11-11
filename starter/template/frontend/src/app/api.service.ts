import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** What the server says the application is (GET api/info). */
export interface Info {
  name: string;
  version: string;
}

/**
 * The page's only door to the server: one method per route of the API (__APP_NAME__Api on the server). The page
 * decides nothing — it shows what these answer and sends what the user asks; a rule written here belongs in core.
 * The paths are relative to the page's base href, so the page works under any prefix the server is given.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  info(): Observable<Info> {
    return this.http.get<Info>('api/info');
  }
}
