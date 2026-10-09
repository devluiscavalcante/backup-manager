# Backup Manager — Frontend

Interface web do Backup Manager, feita em **Angular 21** (componentes standalone e Signals) com **Tailwind CSS 3** e ícones **Lucide**.

## Requisitos

- Node.js 20+ (o CI usa Node 24) e npm
- Backend rodando em `http://localhost:8080` (veja o [README principal](../README.md#início-rápido))

## Desenvolvimento

```bash
npm ci
npm start        # ng serve em http://localhost:4200
```

O `ng serve` usa o [`proxy.conf.json`](proxy.conf.json) para encaminhar `/api/**` ao backend em `:8080`. Os services sempre chamam caminhos relativos (`/api/...`), então não há URL do backend fixa no código.

### Autenticação

O backend exige **HTTP Basic** em todos os endpoints `/api/**`, exceto `GET /api/health/application`.

- A tela `/login` valida as credenciais em `GET /api/backup/active` e guarda a sessão no `sessionStorage`, que é apagado ao fechar a aba.
- O `authInterceptor` envia `Authorization` e `X-Requested-With: XMLHttpRequest` nas chamadas à API. Com esse cabeçalho, o backend não manda o desafio `WWW-Authenticate`, e o navegador não abre o popup nativo de senha.
- Uma resposta 401 encerra a sessão e volta para o login.
- O usuário **operador** acessa backup e histórico. Storage e Logs exigem **administrador**, e a interface avisa quando o perfil não tem acesso.

### Progresso em tempo real

`GET /api/backup/progress` é um stream SSE com eventos nomeados (`progress`, `control`, `complete`, `error`). Como `EventSource` não envia cabeçalhos de autenticação, o stream é lido com `fetch` em [`core/http/event-stream.ts`](src/app/core/http/event-stream.ts).

## Scripts

| Comando | Descrição |
|---|---|
| `npm start` | Servidor de desenvolvimento com proxy |
| `npm test` | Testes unitários (Vitest), execução única |
| `npm run test:watch` | Testes em modo watch |
| `npm run build` | Build de produção em `dist/backup-manager-frontend/browser` |

## Estrutura

```
src/app
├── core/
│   ├── api/        contratos de resposta do backend (CollectionResponse, ApiErrorResponse...)
│   ├── auth/       AuthService, guard e interceptor
│   ├── http/       leitor de Server-Sent Events via fetch
│   └── services/   BackupService, StorageService, LogsService, AppInfoService
├── components/layout/sidebar
└── sections/       páginas (login, inicio, backup, historico, storage, logs, sobre...)
```

## Deploy

O frontend é publicado separadamente da API. Como os services usam caminhos relativos (`/api/...`), sirva `dist/backup-manager-frontend/browser` e a API **na mesma origem**: um proxy reverso (nginx, Caddy, IIS) entrega os arquivos estáticos e encaminha `/api` ao backend. Configure o fallback de rotas para `index.html`.
