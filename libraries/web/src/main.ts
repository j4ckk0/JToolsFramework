import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { Menu, MenuBar, provideJTools } from './lib';

/**
 * Not shipped: a page using the library, so that `ng build` compiles @jtools/web the way a project's page will
 * (ahead of time, strict templates) before it is released — the menu bar with entries and a menu of a program's own.
 */
@Component({
  selector: 'app-root',
  imports: [MenuBar, Menu],
  template: `
    <jtools-menu-bar name="Harness" (stopped)="stopped = true">
      <button fileItems type="button">Open…</button>
      <jtools-menu label="Things" hint="A program's own menu">
        <button type="button" role="menuitemcheckbox" aria-checked="true">A switch</button>
      </jtools-menu>
      <p about>What the harness is for.</p>
      <button helpItems type="button">Guide…</button>
    </jtools-menu-bar>
    <p>{{ stopped ? 'stopped' : '@jtools/web' }}</p>
  `,
})
class Harness {
  stopped = false;
}

bootstrapApplication(Harness, { providers: [provideJTools()] }).catch((err) => console.error(err));
