import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-shell">
      <header class="app-header" role="banner">
        <h1 class="app-title">FX Trade Blotter</h1>
        <nav aria-label="Main navigation" class="main-nav">
          <a routerLink="/positions" routerLinkActive="active" aria-label="Live Positions">Positions</a>
          <a routerLink="/exposure" routerLinkActive="active" aria-label="Exposure View">Exposure</a>
          <a routerLink="/settlement" routerLinkActive="active" aria-label="Settlement Status">Settlement</a>
          <a routerLink="/counterparty" routerLinkActive="active" aria-label="Counterparty Exposure">Counterparty</a>
        </nav>
        <span class="read-only-badge" aria-label="This portal is read-only">Read-Only</span>
      </header>
      <main class="app-content" role="main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .app-shell { min-height: 100vh; display: flex; flex-direction: column; }
    .app-header {
      background: #004d40; color: #fff; padding: 1rem 1.5rem;
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
    .read-only-badge {
      margin-left: auto; padding: 0.25rem 0.75rem;
      background: rgba(255,255,255,0.15); border-radius: 4px;
      font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
    }
    .app-content { flex: 1; }
  `]
})
export class AppComponent {}
