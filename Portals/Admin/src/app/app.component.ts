import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-shell">
      <header class="app-header" role="banner">
        <h1 class="app-title">Admin Portal</h1>
        <nav aria-label="Main navigation" class="main-nav">
          <a routerLink="/eod" routerLinkActive="active" aria-label="EOD Dashboard">EOD Dashboard</a>
          <a routerLink="/trades" routerLinkActive="active" aria-label="Trade Investigation">Trades</a>
          <a routerLink="/risk" routerLinkActive="active" aria-label="Risk Aggregation">Risk</a>
          <a routerLink="/exceptions" routerLinkActive="active" aria-label="Exception Queue">Exceptions</a>
          <a routerLink="/approvals" routerLinkActive="active" aria-label="Agent Approvals">Approvals</a>
        </nav>
      </header>
      <main class="app-content" role="main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .app-shell { min-height: 100vh; display: flex; flex-direction: column; }
    .app-header {
      background: #1a237e; color: #fff; padding: 1rem 1.5rem;
      display: flex; align-items: center; gap: 2rem;
    }
    .app-title { margin: 0; font-size: 1.25rem; }
    .main-nav { display: flex; gap: 1rem; }
    .main-nav a {
      color: rgba(255,255,255,0.85); text-decoration: none; padding: 0.5rem 0.75rem;
      border-radius: 4px; font-weight: 500; font-size: 0.9rem;
    }
    .main-nav a:hover { background: rgba(255,255,255,0.1); }
    .main-nav a.active { background: rgba(255,255,255,0.2); color: #fff; }
    .app-content { flex: 1; }
  `]
})
export class AppComponent {}
