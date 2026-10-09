import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Monitor, Server, FolderTree, FileCode, Box, Layers, Settings, HardDrive } from 'lucide-angular';

@Component({
  selector: 'app-structure',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
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
src/app
├── core/services
└── sections/
    ├── backup
    ├── history
    └── structure`.trim();

  readonly backendTree = `
src/main/java
└── com.backup_manager
    ├── application/
    ├── domain/
    └── infrastructure/
        └── logging/`.trim();
}
