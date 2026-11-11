import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** What the program is (GET api/about, the framework's own route). */
export interface About {
  name: string;
  version: string;
}

/** What the business says of its state (GET api/status). */
export interface Status {
  summary: string;
}

/**
 * The page's only door to the server: one method per route of the API (__APP_NAME__Api on the server). The page
 * decides nothing — it shows what these answer and sends what the user asks; a rule written here belongs in core.
 * The paths are relative to the page's base href, so the page works under any prefix the server is given.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  about(): Observable<About> {
    return this.http.get<About>('api/about');
  }

  status(): Observable<Status> {
    return this.http.get<Status>('api/status');
  }
}
