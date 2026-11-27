import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideMarkdown } from 'ngx-markdown';
import { DashboardComponent } from './dashboard.component';
import { authInterceptor } from '../../core/auth/auth.interceptor';

describe('DashboardComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideMarkdown(),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load all history without login and attach guest session header on analyze', async () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const meReq = httpMock.expectOne((req) => req.url.endsWith('/api/v1/users/me'));
    meReq.flush({}, { status: 401, statusText: 'Unauthorized' });
    const refreshReq401 = httpMock.expectOne((req) => req.url.endsWith('/api/v1/auth/refresh'));
    refreshReq401.flush({}, { status: 401, statusText: 'Unauthorized' });

    const historyReq = httpMock.expectOne((req) =>
      req.url.includes('/api/v1/analyses') && req.params.get('scope') === 'all'
    );
    expect(historyReq.request.headers.has('X-Session-Id')).toBe(true);
    historyReq.flush({
      items: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          detectedLanguage: 'Java',
          errorType: 'NullPointerException',
          severity: 'HIGH',
          summary: 'Null reference in OrderService',
          fileName: 'OrderService.java',
          modelUsed: 'gemini-2.5-flash',
          createdAt: '2026-11-26T00:00:00Z',
          canDelete: true,
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    });

    const component = fixture.componentInstance;
    component.rawStackTrace.set('java.lang.NullPointerException at OrderService.java:42');
    component.submitAnalysis();

    const analyzeReq = httpMock.expectOne(
      (req) => req.method === 'POST' && req.url.endsWith('/api/v1/analyses')
    );
    expect(analyzeReq.request.headers.has('X-Session-Id')).toBe(true);
    analyzeReq.flush({
      id: '22222222-2222-2222-2222-222222222222',
      rawStackTrace: 'java.lang.NullPointerException at OrderService.java:42',
      detectedLanguage: 'Java',
      errorType: 'NullPointerException',
      severity: 'HIGH',
      analysisResult: {
        detectedLanguage: 'Java',
        errorType: 'NullPointerException',
        severity: 'HIGH',
        summary: 'Null reference',
        rootCause: 'Null returned',
        failureLocation: {
          fileName: 'OrderService.java',
          className: 'OrderService',
          methodName: 'run',
          lineNumber: 42,
        },
        stepByStepFix: ['Check for null'],
        codeFix: {
          language: 'java',
          explanation: 'Guard null',
          snippet: 'if (x != null) {}',
        },
        preventiveRecommendations: ['Use Optional'],
      },
      markdownReport: '## Summary\nNull reference',
      modelUsed: 'gemini-2.5-flash',
      createdAt: '2026-11-26T00:00:00Z',
      canDelete: true,
    });

    // Reload history triggered after analyze
    const refreshReq = httpMock.expectOne((req) =>
      req.method === 'GET' && req.url.endsWith('/api/v1/analyses')
    );
    refreshReq.flush({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.selectedAnalysis()?.errorType).toBe('NullPointerException');
    expect(fixture.nativeElement.textContent).toContain('Summary');
  });

  it('should switch history scope from dropdown to mine', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    httpMock.expectOne((req) => req.url.endsWith('/api/v1/users/me')).flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne((req) => req.url.endsWith('/api/v1/auth/refresh')).flush({}, { status: 401, statusText: 'Unauthorized' });

    const initReq = httpMock.expectOne((req) => req.params.get('scope') === 'all');
    initReq.flush({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    });

    fixture.componentInstance.onScopeChange('mine');
    const mineReq = httpMock.expectOne((req) => req.params.get('scope') === 'mine');
    mineReq.flush({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    });

    expect(fixture.componentInstance.historyScope()).toBe('mine');
  });

  it('should render all history without exposing user details and send keyword query', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    httpMock.expectOne((req) => req.url.endsWith('/api/v1/users/me')).flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne((req) => req.url.endsWith('/api/v1/auth/refresh')).flush({}, { status: 401, statusText: 'Unauthorized' });

    const initReq = httpMock.expectOne((req) => req.params.get('scope') === 'all');
    initReq.flush({
      items: [
        {
          id: '33333333-3333-3333-3333-333333333333',
          detectedLanguage: 'Python',
          errorType: 'KeyError',
          severity: 'MEDIUM',
          summary: 'Missing config key in parser.py',
          fileName: 'parser.py',
          modelUsed: 'gemini-2.5-flash',
          createdAt: '2026-11-26T00:00:00Z',
          canDelete: false,
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    });

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('KeyError');

    fixture.componentInstance.searchQuery.set('parser.py');
    fixture.componentInstance.loadHistory();
    const searchReq = httpMock.expectOne(
      (req) => req.params.get('scope') === 'all' && req.params.get('q') === 'parser.py'
    );
    searchReq.flush({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    });
  });
});
