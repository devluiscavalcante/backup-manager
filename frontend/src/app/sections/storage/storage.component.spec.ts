import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StorageComponent } from './storage.component';

describe('StorageComponent', () => {
  let fixture: ComponentFixture<StorageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StorageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(StorageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('should unwrap the CollectionResponse envelope', () => {
    http.expectOne('/api/system/storage').flush({
      success: true,
      count: 1,
      items: [{ driveLetter: 'C:\\', totalSpaceGB: 100, freeSpaceGB: 40, usedSpaceGB: 60, usagePercent: 60, isCritical: false }]
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.drives().length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('60%');
  });

  it('should explain when the account lacks permission', () => {
    http.expectOne('/api/system/storage').flush(
      { success: false, status: 403, error: 'Acesso negado para este recurso.' },
      { status: 403, statusText: 'Forbidden' }
    );

    expect(fixture.componentInstance.error()).toContain('administrator');
  });
});
