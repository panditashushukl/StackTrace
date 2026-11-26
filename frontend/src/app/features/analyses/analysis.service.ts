import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnalysisSummary,
  HistoryScope,
  PagedResponse,
  StackTraceRequest,
  StackTraceResponse,
} from './analysis.model';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/analyses`;

  analyze(request: StackTraceRequest): Observable<StackTraceResponse> {
    return this.http.post<StackTraceResponse>(this.baseUrl, request);
  }

  getHistory(params: {
    scope: HistoryScope;
    q?: string;
    language?: string;
    page?: number;
    size?: number;
  }): Observable<PagedResponse<AnalysisSummary>> {
    let httpParams = new HttpParams()
      .set('scope', params.scope)
      .set('page', String(params.page ?? 0))
      .set('size', String(params.size ?? 10));

    if (params.q && params.q.trim()) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.language && params.language !== 'all') {
      httpParams = httpParams.set('language', params.language);
    }

    return this.http.get<PagedResponse<AnalysisSummary>>(this.baseUrl, { params: httpParams });
  }

  getById(id: string): Observable<StackTraceResponse> {
    return this.http.get<StackTraceResponse>(`${this.baseUrl}/${id}`);
  }

  deleteById(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
