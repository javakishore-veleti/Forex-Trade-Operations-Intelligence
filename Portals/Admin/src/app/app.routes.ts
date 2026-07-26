import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'eod',
    pathMatch: 'full'
  },
  {
    path: 'eod',
    loadComponent: () =>
      import('./features/eod-dashboard/eod-dashboard.component').then(
        m => m.EodDashboardComponent
      ),
    title: 'EOD Dashboard - Admin Portal'
  },
  {
    path: 'trades',
    loadComponent: () =>
      import('./features/trade-investigation/trade-search.component').then(
        m => m.TradeSearchComponent
      ),
    title: 'Trade Search - Admin Portal'
  },
  {
    path: 'trades/:tradeId',
    loadComponent: () =>
      import('./features/trade-investigation/trade-investigation.component').then(
        m => m.TradeInvestigationComponent
      ),
    title: 'Trade Investigation - Admin Portal'
  },
  {
    path: 'risk',
    loadComponent: () =>
      import('./features/risk-aggregation/risk-aggregation.component').then(
        m => m.RiskAggregationComponent
      ),
    title: 'Risk Aggregation - Admin Portal'
  },
  {
    path: 'exceptions',
    loadComponent: () =>
      import('./features/exception-queue/exception-queue.component').then(
        m => m.ExceptionQueueComponent
      ),
    title: 'Exception Queue - Admin Portal'
  },
  {
    path: 'approvals',
    loadComponent: () =>
      import('./features/approval-inbox/approval-inbox.component').then(
        m => m.ApprovalInboxComponent
      ),
    title: 'Approvals - Admin Portal'
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        m => m.NotFoundComponent
      ),
    title: 'Not Found - Admin Portal'
  }
];
