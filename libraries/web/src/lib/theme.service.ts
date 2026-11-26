import { Injectable, signal } from '@angular/core';

/** The page's palettes, as data-theme on <html> carries them. */
export type Theme = 'light' | 'dark';

/** Where this browser keeps the choice: the storage is the page's origin, so each program has its own. */
const STORAGE_KEY = 'jtools.theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

/**
 * The page's light or dark palette: the user's choice (the menu bar's Settings ▸ Dark theme), kept per browser, else
 * the browser's own preference — stamped as data-theme="light" or "dark" on <html>, for the page's styles to switch on.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly chosen = signal<Theme | null>(readChoice());
  private readonly prefersDark = signal(matchesDark());

  constructor() {
    if (typeof matchMedia === 'function') {
      matchMedia(DARK_QUERY).addEventListener?.('change', (event) => {
        this.prefersDark.set(event.matches);
        this.stamp();
      });
    }
    this.stamp();
  }

  /** True when the page shows the dark palette. */
  dark(): boolean {
    const chosen = this.chosen();
    return chosen ? chosen === 'dark' : this.prefersDark();
  }

  /** Dark on or off, at once and kept: the browser's preference no longer applies. */
  toggle(): void {
    const theme: Theme = this.dark() ? 'light' : 'dark';
    this.chosen.set(theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* storage unavailable: the choice lasts as long as the page */
    }
    this.stamp();
  }

  private stamp(): void {
    if (typeof document === 'undefined') return;
    document.documentElement.dataset['theme'] = this.dark() ? 'dark' : 'light';
  }
}

function readChoice(): Theme | null {
  try {
    const kept = localStorage.getItem(STORAGE_KEY);
    return kept === 'light' || kept === 'dark' ? kept : null;
  } catch {
    return null;
  }
}

function matchesDark(): boolean {
  return typeof matchMedia === 'function' && matchMedia(DARK_QUERY).matches;
}
