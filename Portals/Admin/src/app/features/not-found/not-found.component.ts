import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="not-found" aria-label="Page not found">
      <h2>Page Not Found</h2>
      <p>The requested page does not exist.</p>
      <a routerLink="/eod" aria-label="Return to EOD Dashboard">Return to Dashboard</a>
    </section>
  `,
  styles: [`
    .not-found { text-align: center; padding: 3rem; }
    a { color: #1976d2; text-decoration: underline; }
  `]
})
export class NotFoundComponent {}
