import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should send withCredentials and transparently refresh and retry on 401', () => {
    let result: any;
    http.get(`${environment.apiUrl}/users/me`).subscribe((res) => {
      result = res;
    });

    const firstReq = httpMock.expectOne(`${environment.apiUrl}/users/me`);
    expect(firstReq.request.withCredentials).toBe(true);
    firstReq.flush({ message: 'Access token expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(refreshReq.request.method).toBe('POST');
    expect(refreshReq.request.withCredentials).toBe(true);
    refreshReq.flush({
      user: {
        id: 'u-1',
        email: 'dev@example.com',
        name: 'Dev',
        username: 'dev',
        role: 'ROLE_USER',
      },
    });

    const retriedReq = httpMock.expectOne(`${environment.apiUrl}/users/me`);
    expect(retriedReq.request.withCredentials).toBe(true);
    retriedReq.flush({
      id: 'u-1',
      email: 'dev@example.com',
      name: 'Dev',
      username: 'dev',
      role: 'ROLE_USER',
    });

    expect(result?.username).toBe('dev');
    expect(authService.isAuthenticated()).toBe(true);
  });

  it('should share a single refresh call for concurrent 401 responses', () => {
    let r1: any;
    let r2: any;
    http.get(`${environment.apiUrl}/analyses?scope=mine`).subscribe((res) => (r1 = res));
    http.get(`${environment.apiUrl}/users/me`).subscribe((res) => (r2 = res));

    const req1 = httpMock.expectOne(`${environment.apiUrl}/analyses?scope=mine`);
    const req2 = httpMock.expectOne(`${environment.apiUrl}/users/me`);

    req1.flush({}, { status: 401, statusText: 'Unauthorized' });
    req2.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refreshReqs = httpMock.match(`${environment.apiUrl}/auth/refresh`);
    expect(refreshReqs.length).toBe(1);
    refreshReqs[0].flush({
      user: {
        id: 'u-1',
        email: 'dev@example.com',
        name: 'Dev',
        username: 'dev',
        role: 'ROLE_USER',
      },
    });

    httpMock.expectOne(`${environment.apiUrl}/analyses?scope=mine`).flush({ items: [] });
    httpMock.expectOne(`${environment.apiUrl}/users/me`).flush({
      id: 'u-1',
      email: 'dev@example.com',
      name: 'Dev',
      username: 'dev',
      role: 'ROLE_USER',
    });

    expect(r1).toEqual({ items: [] });
    expect(r2?.email).toBe('dev@example.com');
  });
});
