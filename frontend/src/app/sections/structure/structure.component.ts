import { Component } from '@angular/core';
import { LucideAngularModule, Monitor, Server, FolderTree, FileCode, Box, Layers, Settings, HardDrive } from 'lucide-angular';

@Component({
  selector: 'app-structure',
  imports: [LucideAngularModule],
  templateUrl: './structure.component.html'
})
export class StructureComponent {
  readonly monitorIcon = Monitor;
  readonly serverIcon = Server;
  readonly treeIcon = FolderTree;
  readonly fileIcon = FileCode;
  readonly boxIcon = Box;
  readonly layersIcon = Layers;
  readonly settingsIcon = Settings;
  readonly driveIcon = HardDrive;

  readonly frontendTree = `
frontend/src/app
├── core/
│   ├── api/       (contratos da API)
│   ├── auth/      (login, guard, interceptor)
│   ├── http/      (stream SSE)
│   └── services/
└── sections/
    ├── backup
    ├── history
    ├── logs
    ├── storage
    └── login`.trim();

  readonly backendTree = `
src/main/java
└── com.backup_manager
    ├── application/
    ├── domain/
    └── infrastructure/
        └── logging/`.trim();
}
