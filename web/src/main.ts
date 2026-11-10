import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideJTools } from './lib';

/**
 * Not shipped: a page using the library, so that `ng build` compiles @jtools/web the way a project's page will
 * (ahead of time, strict templates) before it is released.
 */
@Component({ selector: 'app-root', template: '<p>@jtools/web</p>' })
class Harness {}

bootstrapApplication(Harness, { providers: [provideJTools()] }).catch((err) => console.error(err));
