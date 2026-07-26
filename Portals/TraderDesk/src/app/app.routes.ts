import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'trades',
    pathMatch: 'full'
  },
  {
    path: 'trades',
    loadComponent: () =>
      import('./features/trade-status/trade-search.component').then(
        m => m.TradeSearchComponent
      ),
    title: 'Trade Search - TraderDesk'
  },
  {
    path: 'trades/:tradeId',
    loadComponent: () =>
      import('./features/trade-status/trade-status.component').then(
        m => m.TradeStatusComponent
      ),
    title: 'Trade Status - TraderDesk'
  },
  {
    path: 'risk/:tradeId',
    loadComponent: () =>
      import('./features/risk-explanation/risk-explanation.component').then(
        m => m.RiskExplanationComponent
      ),
    title: 'Risk Explanation - TraderDesk'
  },
  {
    path: 'positions',
    loadComponent: () =>
      import('./features/position-summary/position-summary.component').then(
        m => m.PositionSummaryComponent
      ),
    title: 'Position Summary - TraderDesk'
  },
  {
    path: 'books',
    loadComponent: () =>
      import('./features/trading-book/trading-book.component').then(
        m => m.TradingBookComponent
      ),
    title: 'Trading Book - TraderDesk'
  },
  {
    path: 'books/:bookId',
    loadComponent: () =>
      import('./features/trading-book/trading-book.component').then(
        m => m.TradingBookComponent
      ),
    title: 'Trading Book - TraderDesk'
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(
        m => m.NotFoundComponent
      ),
    title: 'Not Found - TraderDesk'
  }
];
