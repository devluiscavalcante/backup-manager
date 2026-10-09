import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'inicio',
    pathMatch: 'full'
  },
  {
    path: 'inicio',
    loadComponent: () => import('./sections/initial/initial.component').then(m => m.InitialComponent)
  },
  {
    path: 'backup',
    loadComponent: () => import('./sections/backup/backup.component').then(m => m.BackupComponent)
  },
  {
    path: 'historico',
    loadComponent: () => import('./sections/history/history.component').then(m => m.HistoryComponent)
  },
  {
    path: 'storage',
    loadComponent: () => import('./sections/storage/storage.component').then(m => m.StorageComponent)
  },
  {
    path: 'sobre',
    loadComponent: () => import('./sections/about/about.component').then(m => m.AboutComponent)
  },
  {
    path: 'especificacoes',
    loadComponent: () => import('./sections/specs/specs.component').then(m => m.SpecsComponent)
  },
  {
    path: 'estrutura',
    loadComponent: () => import('./sections/structure/structure.component').then(m => m.StructureComponent)
  },
  {
    path: 'logs',
    loadComponent: () => import('./sections/logs/logs.component').then(m => m.LogsComponent)
  }
];
