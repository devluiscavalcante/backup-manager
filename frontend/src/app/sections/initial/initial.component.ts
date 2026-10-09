import { Component, inject } from '@angular/core';
import { RouterModule, Router } from '@angular/router';
import { LucideAngularModule, Zap, Shield, Package, LucideIconData } from 'lucide-angular';
import { AboutComponent } from '../about/about.component';
import { SpecsComponent } from '../specs/specs.component';
import { StructureComponent } from '../structure/structure.component';
import { StorageComponent } from '../storage/storage.component';
import { AppInfoService } from '../../core/services/app-info.service';

interface Feature {
  icon: LucideIconData;
  label: string;
  desc: string;
  css: string;
}

@Component({
  selector: 'app-initial',
  imports: [
    LucideAngularModule,
    RouterModule,
    AboutComponent,
    SpecsComponent,
    StructureComponent,
    StorageComponent
  ],
  templateUrl: './initial.component.html'
})
export class InitialComponent {
  private router = inject(Router);
  protected readonly appInfo = inject(AppInfoService);

  readonly features: Feature[] = [
    { icon: Zap, label: 'Fast', desc: 'Incremental backups', css: 'text-amber-500' },
    { icon: Shield, label: 'Secure', desc: 'Integrity check', css: 'text-blue-500' },
    { icon: Package, label: 'Flexible', desc: 'Multi-destination', css: 'text-emerald-500' }
  ];

  readonly titlePrefix = "Your infrastructure";
  readonly titleHighlight = "protected and automated.";

  navigateToSection(options: { fragment: string }) {
    this.router.navigate(['/inicio'], { fragment: options.fragment });
  }

  getGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }
}
