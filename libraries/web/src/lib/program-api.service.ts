import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** What the program is: GET api/about, which WebApp answers. */
export interface ProgramAbout {
  name: string;
  version: string;
  /** The Java runtime the server runs on. */
  javaVersion?: string;
  /** The log file of this run; empty when it keeps none. */
  logFile?: string;
}

/**
 * The server's settings, kept from one run to the next by WebApp (GET and POST api/settings): its automatic stop, the
 * port chosen for its next start (0: none, the default applies), the default and the port it listens on now. A
 * program's own fields (SettingsFields on the server) come beside these.
 */
export interface ProgramSettings {
  autoStop: boolean;
  port: number;
  defaultPort: number;
  currentPort: number;
}

/** The ports api/settings takes: UNSET clears the choice. */
export const PORT_RANGE = { MIN: 1, MAX: 65535, UNSET: 0 } as const;

/**
 * The endpoints WebApp answers for every program, beside the program's own API. The paths are relative to the page's
 * base href, so the page works under any prefix the server is given.
 */
@Injectable({ providedIn: 'root' })
export class ProgramApiService {
  private readonly http = inject(HttpClient);

  about(): Observable<ProgramAbout> {
    return this.http.get<ProgramAbout>('api/about');
  }

  settings(): Observable<ProgramSettings> {
    return this.http.get<ProgramSettings>('api/settings');
  }

  /** Changes what is given and answers the settings as they now are. */
  saveSettings(
    changes: Partial<Pick<ProgramSettings, 'autoStop' | 'port'>>,
  ): Observable<ProgramSettings> {
    return this.http.post<ProgramSettings>('api/settings', changes);
  }

  /** Stops the server: it answers, then exits. */
  quit(): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>('api/quit', {});
  }

  /** Opens the log file in the system's text viewer — the server does it for a page of its own computer only. */
  openLog(): Observable<{ ok: boolean; file: string }> {
    return this.http.post<{ ok: boolean; file: string }>('api/log/open', {});
  }
}
