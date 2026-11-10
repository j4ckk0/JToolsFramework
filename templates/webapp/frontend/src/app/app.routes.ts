import { Routes } from '@angular/router';
import { Home } from './home/home';

/** The page's places; their parameters reach the components as inputs (withComponentInputBinding). */
export const routes: Routes = [
  { path: '', component: Home },
  { path: '**', redirectTo: '' },
];
