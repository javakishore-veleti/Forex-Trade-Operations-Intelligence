import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-trade-search',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section aria-label="Trade search" class="trade-search">
      <h2>Trade Investigation</h2>
      <form (ngSubmit)="search()" class="search-form" role="search" aria-label="Search for a trade by ID">
        <label for="tradeId" class="search-label">Trade ID</label>
        <div class="search-input-group">
          <input
            id="tradeId"
            type="text"
            [(ngModel)]="tradeId"
            name="tradeId"
            placeholder="e.g. FX-000001"
            aria-describedby="tradeIdHint"
            class="search-input"
            required
          />
          <button type="submit" class="btn btn-search" [disabled]="!tradeId.trim()" aria-label="Search for trade">
            Search
          </button>
        </div>
        <small id="tradeIdHint" class="hint">Enter a trade identifier (FX- prefix)</small>
      </form>
    </section>
  `,
  styles: [`
    :host { display: block; padding: 1.5rem; }
    .search-form { max-width: 500px; margin-top: 1rem; }
    .search-label { display: block; font-weight: 600; margin-bottom: 0.5rem; }
    .search-input-group { display: flex; gap: 0.5rem; }
    .search-input {
      flex: 1; padding: 0.5rem 0.75rem;
      border: 1px solid #ccc; border-radius: 4px; font-size: 1rem;
    }
    .btn-search {
      padding: 0.5rem 1.5rem; background: #1976d2; color: #fff;
      border: none; border-radius: 4px; cursor: pointer; font-weight: 500;
    }
    .btn-search:disabled { background: #bbb; cursor: not-allowed; }
    .hint { color: #666; font-size: 0.8rem; }
  `]
})
export class TradeSearchComponent {
  tradeId = '';

  constructor(private readonly router: Router) {}

  search(): void {
    const id = this.tradeId.trim();
    if (id) {
      this.router.navigate(['/trades', id]);
    }
  }
}
