import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => controller.verify());

  function signIn() {
    auth.login('admin', 'secret').subscribe();
    controller.expectOne('/api/backup/active').flush({ success: true, items: [] });
  }

  it('should send Basic credentials and X-Requested-With after login', () => {
    signIn();

    http.get('/api/backup/history').subscribe();
    const req = controller.expectOne('/api/backup/history');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('admin:secret')}`);
    expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    req.flush({});
  });

  it('should not touch requests outside the API', () => {
    signIn();

    http.get('/assets/file.json').subscribe();
    const req = controller.expectOne('/assets/file.json');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should end the session on 401', () => {
    signIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get('/api/backup/history').subscribe({ error: () => undefined });
    controller.expectOne('/api/backup/history').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should keep the session closed when login fails', () => {
    auth.login('admin', 'wrong').subscribe({ error: () => undefined });
    controller.expectOne('/api/backup/active').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
  });
});
