import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, timer } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { ApprovalClient } from '../../core/api/approval.client';
import { AppConfigService } from '../../core/config/app-config.service';
import { ApprovalRequest, ApprovalDecision } from '../../core/models';
import { AppError } from '../../core/http/error.interceptor';

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './approval-inbox.component.html',
  styleUrl: './approval-inbox.component.css'
})
export class ApprovalInboxComponent implements OnInit, OnDestroy {
  approvals: ApprovalRequest[] = [];
  loading = true;
  error: string | null = null;

  confirmingDecision: { approval: ApprovalRequest; decision: 'APPROVED' | 'REJECTED' } | null = null;
  operatorNote = '';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly approvalClient: ApprovalClient,
    readonly config: AppConfigService
  ) {}

  ngOnInit(): void {
    timer(0, this.config.approvalInboxIntervalMs)
      .pipe(
        switchMap(() => this.approvalClient.getPendingApprovals()),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (approvals) => {
          this.approvals = approvals;
          this.loading = false;
          this.error = null;
        },
        error: (err: AppError) => {
          this.error = err.message;
          this.loading = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  confirmApprove(approval: ApprovalRequest): void {
    this.confirmingDecision = { approval, decision: 'APPROVED' };
    this.operatorNote = '';
  }

  confirmReject(approval: ApprovalRequest): void {
    this.confirmingDecision = { approval, decision: 'REJECTED' };
    this.operatorNote = '';
  }

  cancelDecision(): void {
    this.confirmingDecision = null;
    this.operatorNote = '';
  }

  submitDecision(): void {
    if (!this.confirmingDecision) return;
    const { approval, decision } = this.confirmingDecision;

    if (!approval.approvalReference) {
      this.error = 'Cannot submit: approval reference is missing.';
      this.confirmingDecision = null;
      return;
    }

    const payload: ApprovalDecision = {
      approvalReference: approval.approvalReference,
      decision,
      operatorNote: this.operatorNote || undefined
    };

    this.approvalClient.submitDecision(payload).subscribe({
      next: () => {
        this.confirmingDecision = null;
        this.operatorNote = '';
        // Remove from local list optimistically
        this.approvals = this.approvals.filter(
          a => a.approvalReference !== approval.approvalReference
        );
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.confirmingDecision = null;
      }
    });
  }

  retry(): void {
    this.loading = true;
    this.error = null;
    this.approvalClient.getPendingApprovals().subscribe({
      next: (approvals) => {
        this.approvals = approvals;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }
}
