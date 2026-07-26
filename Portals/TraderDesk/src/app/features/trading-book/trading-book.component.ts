import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TradingBookClient } from '../../core/api/clients';
import { TradingBookEntry, PagedResult } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-trading-book',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './trading-book.component.html',
  styleUrl: './trading-book.component.css'
})
export class TradingBookComponent implements OnInit {
  bookId = '';
  trades: TradingBookEntry[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize = 25;

  sortBy = 'tradeDate';
  sortDir = 'desc';
  statusFilter = '';
  riskLevelFilter = '';

  loading = false;
  error: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly bookClient: TradingBookClient
  ) {}

  ngOnInit(): void {
    const bookParam = this.route.snapshot.paramMap.get('bookId');
    if (bookParam) {
      this.bookId = bookParam;
      this.loadTrades();
    }
  }

  loadTrades(): void {
    if (!this.bookId.trim()) return;
    this.loading = true;
    this.error = null;

    this.bookClient.getTrades(
      this.bookId,
      this.currentPage,
      this.pageSize,
      this.sortBy,
      this.sortDir,
      this.statusFilter || undefined,
      this.riskLevelFilter || undefined
    ).subscribe({
      next: (result: PagedResult<TradingBookEntry>) => {
        this.trades = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  searchBook(): void {
    this.currentPage = 0;
    this.loadTrades();
  }

  sort(column: string): void {
    if (this.sortBy === column) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortBy = column;
      this.sortDir = 'desc';
    }
    this.loadTrades();
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.loadTrades();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadTrades();
    }
  }

  retry(): void {
    this.loadTrades();
  }
}
