import {
  Component,
  HostListener,
  ViewEncapsulation,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { apiErrorMessage } from '../api-error';
import { PagePresenceService } from '../page-presence.service';
import {
  PORT_RANGE,
  ProgramAbout,
  ProgramApiService,
  ProgramSettings,
} from '../program-api.service';
import { ThemeService } from '../theme.service';
import { Menu, MenuBarState } from './menu';

/** The dialogs the menus open. */
type Dialog = 'about' | 'runtime' | 'port';

/**
 * The page's top bar: the menus every program has, over the endpoints WebApp answers — File ▸ Quit, Settings (the dark
 * theme, the server's automatic stop, its port), Logs, Help (the embedded Java runtime, About) —, the program's own
 * menus between File and Settings, its name at the right, and a line under it saying how the last action went.
 *
 *   <jtools-menu-bar name="Bookshelf" (stopped)="stopped.set(true)">
 *     <button fileItems type="button" (click)="open()">Open…</button>      ← File's own entries, above Quit
 *     <jtools-menu label="Books"> … </jtools-menu>                          ← the program's menus
 *     <button settingsItems type="button" (click)="prefs()">Shelves…</button> ← Settings' own entries, above its own
 *     <p about>What Bookshelf is for.</p>                                   ← About's own text
 *     <button helpItems type="button" (click)="guide()">Guide…</button>    ← Help's own entries
 *   </jtools-menu-bar>
 */
@Component({
  selector: 'jtools-menu-bar',
  imports: [Menu],
  providers: [MenuBarState],
  templateUrl: './menu-bar.html',
  styleUrl: './menu-bar.css',
  // its styles reach the entries a program gives (projected, so outside its view); every rule is scoped to jtools-menu-bar
  encapsulation: ViewEncapsulation.None,
})
export class MenuBar {
  /** The program's name: at the right of the bar, and in its menus' and dialogs' texts. */
  readonly name = input.required<string>();
  /** Emitted once the server has stopped on File ▸ Quit: the page has nothing left to show. */
  readonly stopped = output<void>();

  protected readonly theme = inject(ThemeService);
  protected readonly portRange = PORT_RANGE;
  protected readonly dialog = signal<Dialog | null>(null);
  protected readonly settings = signal<ProgramSettings | undefined>(undefined);
  protected readonly about = signal<ProgramAbout | undefined>(undefined);
  /** The line under the bar: the last action's outcome, an error unless info. */
  protected readonly message = signal<{ text: string; info: boolean } | null>(null);
  /** What the port dialog's field holds. */
  protected readonly portText = signal('');
  /** The major version of the server's Java runtime ('25' of '25.0.5'), when known. */
  protected readonly javaMajor = computed(() => this.about()?.javaVersion?.match(/^\d+/)?.[0]);

  private readonly state = inject(MenuBarState);
  private readonly api = inject(ProgramApiService);
  private readonly presence = inject(PagePresenceService);

  constructor() {
    this.api.settings().subscribe({
      next: (settings) => this.settings.set(settings),
      error: () => {
        /* server unreachable: the menu shows the defaults */
      },
    });
  }

  /** A click anywhere closes the menus, an entry's included. */
  @HostListener('document:click')
  protected closeMenus(): void {
    this.state.close();
  }

  @HostListener('document:keydown.escape')
  protected escape(): void {
    this.state.close();
    this.dialog.set(null);
  }

  protected closeDialog(): void {
    this.dialog.set(null);
  }

  /** A click on the dimmed backdrop, not on the dialog itself, closes it. */
  protected backdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closeDialog();
  }

  // ---- File

  /** File ▸ Quit: once confirmed, stops the server; the page then has nothing to show. */
  protected quit(): void {
    if (!confirm(`Stop the ${this.name()} server and close this page?`)) return;
    this.presence.stop();
    this.api.quit().subscribe({
      next: () => {
        this.stopped.emit();
        window.close();
      },
      error: (error) => {
        this.presence.start();
        this.say(`Cannot stop the server: ${apiErrorMessage(error)}`);
      },
    });
  }

  // ---- Settings

  protected autoStop(): boolean {
    return this.settings()?.autoStop !== false;
  }

  /** Settings ▸ Automatic stop: flipped on the server, which keeps it. */
  protected toggleAutoStop(): void {
    this.api.saveSettings({ autoStop: !this.autoStop() }).subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.say(
          settings.autoStop
            ? 'The server will stop by itself once the last page has been closed'
            : `The server keeps running when the last page is closed: stop it with File ▸ Quit ${this.name()}`,
          true,
        );
      },
      error: (error) => this.say(apiErrorMessage(error)),
    });
  }

  /** Settings ▸ Server port…: the port of the next start; the field left empty, the default. */
  protected showPortDialog(): void {
    const port = this.settings()?.port;
    this.portText.set(port ? String(port) : '');
    this.dialog.set('port');
  }

  protected savePort(): void {
    const text = this.portText().trim();
    let chosen: number = PORT_RANGE.UNSET;
    if (text) {
      chosen = Number(text);
      if (!Number.isInteger(chosen) || chosen < PORT_RANGE.MIN || chosen > PORT_RANGE.MAX) {
        this.say(
          `The port must be a whole number between ${PORT_RANGE.MIN} and ${PORT_RANGE.MAX}.`,
        );
        return;
      }
    }
    this.api.saveSettings({ port: chosen }).subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.dialog.set(null);
        this.say(
          settings.port
            ? `Port set to ${settings.port}. Restart ${this.name()} to use it.`
            : `Port reset to the default (${settings.defaultPort}). Restart ${this.name()} to use it.`,
          true,
        );
      },
      error: (error) => this.say(apiErrorMessage(error)),
    });
  }

  // ---- Logs

  /** Logs ▸ Open the log: in the system's text viewer, by the server — which refuses a page of another computer. */
  protected openLog(): void {
    this.api.openLog().subscribe({
      next: (answer) => this.say(`Opened in your text viewer: ${answer.file}`, true),
      error: (error) => this.say(apiErrorMessage(error)),
    });
  }

  // ---- Help

  protected showRuntime(): void {
    this.loadAbout();
    this.dialog.set('runtime');
  }

  protected showAbout(): void {
    this.loadAbout();
    this.dialog.set('about');
  }

  // ----

  protected clearMessage(): void {
    this.message.set(null);
  }

  /** The server's name, version, runtime and log file, asked afresh each time a menu or dialog shows them. */
  protected loadAbout(): void {
    this.api.about().subscribe({
      next: (about) => this.about.set(about),
      error: () => {
        /* server unreachable: the dialogs show ? */
      },
    });
  }

  private say(text: string, info = false): void {
    this.message.set({ text, info });
  }
}
