import { Component, Injectable, computed, inject, input, output, signal } from '@angular/core';

/** Which menu of a menu bar is open: one at most. Each MenuBar holds its own. */
@Injectable()
export class MenuBarState {
  readonly open = signal<Menu | null>(null);

  close(): void {
    this.open.set(null);
  }
}

/**
 * One menu of a MenuBar: its button, and a dropdown of the entries given as its content — buttons, an <hr> between
 * groups, role="menuitemcheckbox" with aria-checked for a switch (MenuBar's styles draw its tick). A click on an entry,
 * or anywhere, closes it.
 */
@Component({
  selector: 'jtools-menu',
  template: `
    <button
      class="jtools-menubtn"
      type="button"
      aria-haspopup="menu"
      [attr.aria-expanded]="isOpen()"
      [attr.title]="hint() || null"
      (click)="toggle($event)"
    >
      {{ label() }} ▾
    </button>
    <div class="jtools-dropdown" role="menu" [hidden]="!isOpen()"><ng-content /></div>
  `,
})
export class Menu {
  readonly label = input.required<string>();
  /** What the menu is for, as its button's tooltip. */
  readonly hint = input<string>();
  /** Emitted as the menu opens: what it shows may be asked of the server then. */
  readonly opened = output<void>();

  private readonly state = inject(MenuBarState);
  protected readonly isOpen = computed(() => this.state.open() === this);

  /** Opens this menu and closes the others; a second click closes it. */
  protected toggle(event: Event): void {
    event.stopPropagation();
    if (this.isOpen()) {
      this.state.close();
      return;
    }
    this.state.open.set(this);
    this.opened.emit();
  }
}
