import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { SessionPageComponent } from './session/session-page.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'Planning Poker' },
  { path: 's/:id', component: SessionPageComponent, title: 'Planning Poker – Sitzung' },
  { path: '**', redirectTo: '' },
];
