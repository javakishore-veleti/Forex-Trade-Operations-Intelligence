import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'positions',
    pathMatch: 'full'
  },
  {
    path: 'positions',
    loadComponent: () =>
      import('./features/live-position/live-position.component').then(
        m => m.LivePositionComponent
      ),
    title: 'Live Positions - FX Trade Blotter'
  },
  {
    path: 'exposure',
    loadComponent: () =>
      import('./features/exposure-view/exposure-view.component').then(
        m => m.ExposureViewComponent
      ),
    title: 'Exposure - FX Trade Blotter'
  },
  {
    path: 'settlement',
    loadComponent: () =>
      import('./features/settlement-status/settlement-status.component').then(
        m => m.SettlementStatusComponent
      ),
    title: 'Settlement Status - FX Trade Blotter'
  },
  {
    path: 'counterparty',
    loadComponent: () =>
      import('./features/counterparty-exposure/counterparty-exposure.component').then(
        m => m.CounterpartyExposureComponent
      ),
    title: 'Counterparty Exposure - FX Trade Blotter'
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        m => m.NotFoundComponent
      ),
    title: 'Not Found - FX Trade Blotter'
  }
];
