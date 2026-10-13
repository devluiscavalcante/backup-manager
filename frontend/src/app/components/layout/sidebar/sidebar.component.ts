import { Component, HostListener, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { AppInfoService } from '../../../core/services/app-info.service';
import {
  LucideAngularModule,
  LucideIconData,
  LayoutDashboard,
  Play,
  History,
  HardDrive,
  ScrollText,
  ShieldCheck,
  LogOut,
  Menu,
  X
} from 'lucide-angular';

interface NavItem {
  path: string;
  label: string;
  icon: LucideIconData;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'app-sidebar',
  imports: [LucideAngularModule, NgTemplateOutlet, RouterLink, RouterLinkActive],
  template: `
    <!-- Barra superior (mobile) -->
    <header class="fixed inset-x-0 top-0 z-40 flex h-14 items-center justify-between border-b border-zinc-200/80 bg-white/90 px-4 backdrop-blur lg:hidden">
      <ng-container *ngTemplateOutlet="brand"></ng-container>
      <button type="button" class="btn-icon" (click)="toggle()"
              [attr.aria-expanded]="isOpen()" aria-controls="app-sidebar"
              [attr.aria-label]="isOpen() ? 'Fechar menu' : 'Abrir menu'">
        <lucide-icon [name]="isOpen() ? closeIcon : menuIcon" class="h-5 w-5"></lucide-icon>
      </button>
    </header>

    @if (isOpen()) {
      <div class="fixed inset-0 z-40 bg-zinc-900/20 backdrop-blur-sm lg:hidden" (click)="close()" aria-hidden="true"></div>
    }

    <aside id="app-sidebar"
           class="fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-zinc-200/80 bg-white transition-transform duration-200 lg:translate-x-0"
           [class.-translate-x-full]="!isOpen()"
           aria-label="Navegação principal">
      <div class="flex h-14 items-center px-5 lg:h-16">
        <ng-container *ngTemplateOutlet="brand"></ng-container>
      </div>

      <nav class="custom-scrollbar flex-1 space-y-6 overflow-y-auto px-3 py-4">
        @for (group of groups; track group.label) {
          <div>
            <p class="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-zinc-400">{{ group.label }}</p>
            <ul class="space-y-0.5">
              @for (item of group.items; track item.path) {
                <li>
                  <a [routerLink]="item.path" routerLinkActive="!bg-zinc-100 !text-zinc-900"
                     ariaCurrentWhenActive="page" (click)="close()"
                     class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 transition-colors hover:bg-zinc-50 hover:text-zinc-900">
                    <lucide-icon [name]="item.icon" class="h-4 w-4"></lucide-icon>
                    {{ item.label }}
                  </a>
                </li>
              }
            </ul>
          </div>
        }
      </nav>

      <div class="border-t border-zinc-100 p-3">
        <div class="flex items-center gap-3 rounded-lg px-3 py-2">
          <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-900 text-xs font-semibold uppercase text-white">
            {{ initial() }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-zinc-900">{{ auth.username() }}</p>
            @if (appInfo.version()) {
              <p class="text-xs text-zinc-400">v{{ appInfo.version() }}</p>
            }
          </div>
          <button type="button" class="btn-icon" (click)="logout()" aria-label="Sair" title="Sair">
            <lucide-icon [name]="logoutIcon" class="h-4 w-4"></lucide-icon>
          </button>
        </div>
      </div>
    </aside>

    <ng-template #brand>
      <a routerLink="/inicio" class="flex items-center gap-2.5" (click)="close()">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-zinc-900 text-white">
          <lucide-icon [name]="brandIcon" class="h-4 w-4"></lucide-icon>
        </span>
        <span class="text-sm font-semibold tracking-tight text-zinc-900">Backup Manager</span>
      </a>
    </ng-template>
  `
})
export class SidebarComponent {
  protected readonly auth = inject(AuthService);
  protected readonly appInfo = inject(AppInfoService);

  readonly menuIcon = Menu;
  readonly closeIcon = X;
  readonly brandIcon = ShieldCheck;
  readonly logoutIcon = LogOut;

  readonly groups: NavGroup[] = [
    {
      label: 'Operação',
      items: [
        { path: '/inicio', label: 'Painel', icon: LayoutDashboard },
        { path: '/backup', label: 'Novo backup', icon: Play },
        { path: '/historico', label: 'Histórico', icon: History }
      ]
    },
    {
      label: 'Sistema',
      items: [
        { path: '/storage', label: 'Armazenamento', icon: HardDrive },
        { path: '/logs', label: 'Alertas', icon: ScrollText }
      ]
    }
  ];

  readonly isOpen = signal(false);

  initial(): string {
    return (this.auth.username() ?? '?').charAt(0);
  }

  toggle() { this.isOpen.update(v => !v); }
  close() { this.isOpen.set(false); }

  @HostListener('document:keydown.escape')
  onEscape() { this.close(); }

  logout() {
    this.close();
    this.auth.logout();
  }
}
