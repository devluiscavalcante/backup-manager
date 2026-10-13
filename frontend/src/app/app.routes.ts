import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar · Backup Manager',
    loadComponent: () => import('./sections/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'inicio',
        pathMatch: 'full'
      },
      {
        path: 'inicio',
        title: 'Painel · Backup Manager',
        loadComponent: () => import('./sections/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'backup',
        title: 'Novo backup · Backup Manager',
        loadComponent: () => import('./sections/backup/backup.component').then(m => m.BackupComponent)
      },
      {
        path: 'historico',
        title: 'Histórico · Backup Manager',
        loadComponent: () => import('./sections/history/history.component').then(m => m.HistoryComponent)
      },
      {
        path: 'storage',
        title: 'Armazenamento · Backup Manager',
        loadComponent: () => import('./sections/storage/storage.component').then(m => m.StorageComponent)
      },
      {
        path: 'logs',
        title: 'Alertas · Backup Manager',
        loadComponent: () => import('./sections/logs/logs.component').then(m => m.LogsComponent)
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'inicio'
  }
];
