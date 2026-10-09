import { Component } from '@angular/core';
import { LucideAngularModule, Database, Github } from 'lucide-angular';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <header class="border-b border-gray-200 bg-white sticky top-0 z-50">
      <div class="flex items-center justify-between px-6 py-3">
        <div class="flex items-center space-x-2">
          <lucide-icon [name]="database" class="w-5 h-5 text-gray-900"></lucide-icon>
          <span class="text-sm font-semibold text-gray-900">Backup Manager (BKM)</span>
        </div>

        <a href="https://github.com/devluiscavalcante/backup-manager"
           target="_blank" class="flex items-center space-x-2 text-gray-600 hover:text-gray-900 transition-colors">
          <div class="w-8 h-8 bg-gray-900 rounded-full flex items-center justify-center">
            <lucide-icon [name]="github" class="w-4 h-4 text-white"></lucide-icon>
          </div>
          <span class="text-sm">BKM on Github</span>
        </a>
      </div>
    </header>
  `
})
export class HeaderComponent {
  readonly database = Database;
  readonly github = Github;
}
