import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Server, Code, Cpu, HardDrive, Settings2 } from 'lucide-angular';

@Component({
  selector: 'app-specs',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './specs.component.html'
})
export class SpecsComponent {
  readonly serverIcon = Server;
  readonly codeIcon = Code;
  readonly cpuIcon = Cpu;
  readonly driveIcon = HardDrive;
  readonly settingsIcon = Settings2;

  readonly endpoints = [
    'POST /api/backup/start',
    'GET /api/backup/history',
    'GET /api/backup/progress',
    'POST /api/backup/taskId/pause',
    'POST /api/backup/taskId/resume',
    'DELETE /api/backup/taskId/cancel',
    'POST /api/backup/taskId/status'
  ];

  getMethodClass(endpoint: string): string {
    const method = endpoint.split(' ')[0];
    switch (method) {
      case 'GET': return 'text-emerald-400 font-bold';
      case 'POST': return 'text-blue-400 font-bold';
      case 'DELETE': return 'text-rose-400 font-bold';
      default: return 'text-gray-300 font-bold';
    }
  }
}
