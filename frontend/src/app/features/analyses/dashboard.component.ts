import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AnalysisService } from './analysis.service';
import {
  AnalysisSummary,
  HistoryScope,
  PagedResponse,
  Severity,
  StackTraceResponse,
} from './analysis.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MarkdownComponent],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly authService = inject(AuthService);
  private readonly analysisService = inject(AnalysisService);

  private readonly destroy$ = new Subject<void>();
  private readonly searchInput$ = new Subject<string>();

  readonly languages = [
    'Auto-detect',
    'Java',
    'Python',
    'JavaScript',
    'TypeScript',
    'Node.js',
    'C#',
    'Go',
    'PHP',
  ];

  // Input state
  readonly rawStackTrace = signal('');
  readonly languageHint = signal('Auto-detect');
  readonly codeContext = signal('');
  readonly showContextInput = signal(false);

  // Output state
  readonly analyzing = signal(false);
  readonly loadingDetail = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedAnalysis = signal<StackTraceResponse | null>(null);
  readonly showRawTrace = signal(false);
  readonly copiedCode = signal(false);
  readonly copiedMarkdown = signal(false);

  // UI overlays
  readonly showInfo = signal(false);
  readonly showUserMenu = signal(false);
  readonly showMobileHistory = signal(false);

  // History state
  readonly historyScope = signal<HistoryScope>('all');
  readonly searchQuery = signal('');
  readonly historyLanguage = signal('all');
  readonly historyPage = signal(0);
  readonly historyLoading = signal(false);
  readonly historyData = signal<PagedResponse<AnalysisSummary>>({
    items: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  });

  readonly canSubmit = computed(
    () =>
      !this.analyzing() &&
      this.rawStackTrace().trim().length > 0 &&
      this.rawStackTrace().length <= 50000 &&
      this.codeContext().length <= 10000
  );

  ngOnInit(): void {
    if (!this.authService.isInitialized()) {
      this.authService.restoreSession().subscribe();
    }

    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => {
        this.historyPage.set(0);
        this.loadHistory();
      });

    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submitAnalysis(): void {
    if (!this.canSubmit()) {
      return;
    }

    this.analyzing.set(true);
    this.errorMessage.set(null);
    this.showRawTrace.set(false);

    const hint =
      this.languageHint() === 'Auto-detect' ? null : this.languageHint();
    const context = this.codeContext().trim() ? this.codeContext().trim() : null;

    this.analysisService
      .analyze({
        rawStackTrace: this.rawStackTrace().trim(),
        languageHint: hint,
        codeContext: context,
      })
      .subscribe({
        next: (response) => {
          this.analyzing.set(false);
          this.selectedAnalysis.set(response);
          this.historyPage.set(0);
          this.loadHistory();
        },
        error: (err) => {
          this.analyzing.set(false);
          this.errorMessage.set(
            err?.error?.message ||
              'Unable to analyze stack trace right now. Please try again.'
          );
        },
      });
  }

  onKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.submitAnalysis();
    }
  }

  clearInput(): void {
    this.rawStackTrace.set('');
    this.codeContext.set('');
    this.languageHint.set('Auto-detect');
    this.errorMessage.set(null);
  }

  onScopeChange(scope: HistoryScope): void {
    this.historyScope.set(scope);
    this.historyPage.set(0);
    this.loadHistory();
  }

  onLanguageFilterChange(language: string): void {
    this.historyLanguage.set(language);
    this.historyPage.set(0);
    this.loadHistory();
  }

  onSearchChange(value: string): void {
    this.searchQuery.set(value);
    this.searchInput$.next(value);
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.historyPage.set(0);
    this.loadHistory();
  }

  goToPage(page: number): void {
    if (page < 0) return;
    this.historyPage.set(page);
    this.loadHistory();
  }

  selectHistoryItem(id: string): void {
    this.loadingDetail.set(true);
    this.errorMessage.set(null);
    this.showRawTrace.set(false);
    this.showMobileHistory.set(false);

    this.analysisService.getById(id).subscribe({
      next: (detail) => {
        this.loadingDetail.set(false);
        this.selectedAnalysis.set(detail);
      },
      error: (err) => {
        this.loadingDetail.set(false);
        this.errorMessage.set(err?.error?.message || 'Failed to load analysis record.');
      },
    });
  }

  deleteHistoryItem(event: Event, id: string): void {
    event.stopPropagation();
    this.analysisService.deleteById(id).subscribe({
      next: () => {
        if (this.selectedAnalysis()?.id === id) {
          this.selectedAnalysis.set(null);
        }
        this.loadHistory();
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message || 'Unable to delete record.');
      },
    });
  }

  copyCodePatch(): void {
    const snippet = this.selectedAnalysis()?.analysisResult?.codeFix?.snippet;
    if (!snippet || !navigator?.clipboard) return;
    navigator.clipboard.writeText(snippet).then(() => {
      this.copiedCode.set(true);
      setTimeout(() => this.copiedCode.set(false), 1800);
    });
  }

  copyMarkdownReport(): void {
    const md = this.selectedAnalysis()?.markdownReport;
    if (!md || !navigator?.clipboard) return;
    navigator.clipboard.writeText(md).then(() => {
      this.copiedMarkdown.set(true);
      setTimeout(() => this.copiedMarkdown.set(false), 1800);
    });
  }

  exportMarkdownFile(): void {
    const item = this.selectedAnalysis();
    if (!item) return;
    const blob = new Blob([item.markdownReport], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const safeName = item.errorType.replace(/[^a-zA-Z0-9_-]/g, '-').toLowerCase() || 'diagnosis';
    link.href = url;
    link.download = `${safeName}.md`;
    link.click();
    URL.revokeObjectURL(url);
  }

  loadHistory(): void {
    this.historyLoading.set(true);
    this.analysisService
      .getHistory({
        scope: this.historyScope(),
        q: this.searchQuery(),
        language: this.historyLanguage(),
        page: this.historyPage(),
        size: 10,
      })
      .subscribe({
        next: (data) => {
          this.historyLoading.set(false);
          this.historyData.set(data);
        },
        error: () => {
          this.historyLoading.set(false);
        },
      });
  }

  severityClass(severity: Severity): string {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-red-500/15 text-red-300 border-red-500/30';
      case 'HIGH':
        return 'bg-orange-500/15 text-orange-300 border-orange-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/15 text-amber-300 border-amber-500/30';
      default:
        return 'bg-sky-500/15 text-sky-300 border-sky-500/30';
    }
  }

  severityDotClass(severity: Severity): string {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-red-400';
      case 'HIGH':
        return 'bg-orange-400';
      case 'MEDIUM':
        return 'bg-amber-400';
      default:
        return 'bg-sky-400';
    }
  }

  formatDate(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
