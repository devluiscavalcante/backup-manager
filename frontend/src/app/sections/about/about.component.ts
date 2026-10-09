import { Component, computed, inject, signal } from '@angular/core';
import { LucideAngularModule, LucideIconData, Info, Github, ExternalLink, Code2, ShieldCheck, Cpu, Layers, Server, Terminal, Download, Play } from 'lucide-angular';
import { AppInfoService } from '../../core/services/app-info.service';

interface Step {
  label: string;
  desc: string;
  command: string;
  icon: LucideIconData;
}

interface DocSection {
  title: string;
  icon: LucideIconData;
  content: string[];
}

interface BackendCard {
  label: string;
  desc: string;
  tech: string;
}

@Component({
  selector: 'app-about',
  imports: [LucideAngularModule],
  templateUrl: './about.component.html'
})
export class AboutComponent {
  private appInfo = inject(AppInfoService);

  readonly infoIcon = Info;
  readonly githubIcon = Github;
  readonly linkIcon = ExternalLink;
  readonly codeIcon = Code2;
  readonly serverIcon = Server;
  readonly terminalIcon = Terminal;
  readonly downloadIcon = Download;
  readonly playIcon = Play;

  quickStartSteps = signal<Step[]>([
    {
      label: 'Prerequisites',
      desc: 'Ensure you have Java 21, Node.js 20+ and Docker (for PostgreSQL) ready.',
      command: 'java --version && node -v && docker compose up -d postgres',
      icon: Download
    },
    {
      label: 'Backend Setup',
      desc: 'Set DB_PASSWORD and APP_SECURITY_PASSWORD in .env, then run the Spring Boot engine.',
      command: './mvnw spring-boot:run',
      icon: Terminal
    },
    {
      label: 'Frontend Launch',
      desc: 'Install dependencies and launch the Angular dashboard (proxied to :8080).',
      command: 'cd frontend && npm install && npm start',
      icon: Play
    }
  ]);

  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).catch(() => undefined);
  }

  projectDocs = signal<DocSection[]>([
    {
      title: 'Project Purpose',
      icon: ShieldCheck,
      content: [
        'Automated file synchronization between local directories.',
        'Reliable data protection for Windows environments.',
        'Minimalist management through a modern web interface.'
      ]
    },
    {
      title: 'System Architecture',
      icon: Layers,
      content: [
        'Backend: Java 21 with Spring Boot 4 and PostgreSQL.',
        'Frontend: Angular 21 with Signals and Tailwind CSS.',
        'Integration: REST/JSON APIs secured with HTTP Basic, plus SSE for live progress.'
      ]
    },
    {
      title: 'Reliability',
      icon: Cpu,
      content: [
        'Real-time logging system for backup tracking.',
        'Transactional operations to ensure data consistency.',
        'Optimized I/O processing for file management.'
      ]
    }
  ]);

  backendArchitecture = signal<BackendCard[]>([
    {
      label: 'Service Layer (BO)',
      desc: 'The heart of BKM. It orchestrates complex backup tasks, manages path validations, and ensures thread safety during I/O operations.',
      tech: 'Spring Services'
    },
    {
      label: 'Persistence Layer',
      desc: 'Utilizes Spring Data JPA to manage ScheduledBackupEntity and history, persisting configurations and audit logs into PostgreSQL (Flyway migrations).',
      tech: 'Spring Data JPA / PostgreSQL'
    },
    {
      label: 'REST Controller',
      desc: 'Highly organized API layer that decouples frontend requests from business logic, providing standardized DTO responses.',
      tech: 'Spring Web'
    }
  ]);

  advancedFeatures = signal<BackendCard[]>([
    {
      label: 'Reactive Monitoring (SSE)',
      desc: 'Implemented via SseEmitter, the progress system streams live backup status from the backend to the UI without polling.',
      tech: 'SseEmitter / Progress'
    },
    {
      label: 'Dynamic Task Scheduling',
      desc: 'Allows for cron-based automation and one-time tasks, with full lifecycle control (pause, resume, and cancel) for active jobs.',
      tech: 'Dynamic Task Scheduler'
    },
    {
      label: 'Security & Integrity',
      desc: 'Strict path validation (validateSafePath) and disk space checks ensure backups run in secure environments with enough capacity.',
      tech: 'I/O Security Service'
    }
  ]);

  observabilityFeatures = signal<BackendCard[]>([
    {
      label: 'Diagnostic Logging',
      desc: 'Exposes detailed execution logs through the LogController, allowing users to review warnings.log and audit backup session alerts.',
      tech: 'Java NIO / Logging'
    },
    {
      label: 'System Storage Stats',
      desc: 'The SystemController provides real-time information about Windows storage partitions, mapping disk health and available space.',
      tech: 'SystemStorageService'
    },
    {
      label: 'Context Awareness',
      desc: 'Integrates BackupContext to track the last destination of files, providing immediate access to relevant logs and session data.',
      tech: 'Infrastructure Context'
    }
  ]);

  projectMeta = computed(() => ({
    version: this.appInfo.version(),
    author: 'Luis Cavalcante',
    githubUser: 'devluiscavalcante',
    repoBackendUrl: 'https://github.com/devluiscavalcante/backup-manager',
    repoFrontendUrl: 'https://github.com/devluiscavalcante/backup-manager/tree/master/frontend'
  }));

  getYear(): number {
    return new Date().getFullYear();
  }
}
