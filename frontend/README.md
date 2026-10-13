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

## Interface

A interface contém apenas a aplicação. A documentação do projeto (arquitetura, API, configuração) fica no [README principal](../README.md).

| Seção | Rota | Conteúdo |
|---|---|---|
| Painel | `/inicio` | Indicadores, atividade recente e resumo dos discos |
| Novo backup | `/backup` | Fila de pares origem → destino, progresso, pausa e cancelamento |
| Histórico | `/historico` | Execuções com filtro por status e busca |
| Armazenamento | `/storage` | Uso de cada disco (somente administrador) |
| Alertas | `/logs` | Avisos do último backup, com filtro e exportação (somente administrador) |

### Privacidade na tela

- Os caminhos aparecem reduzidos à unidade e ao último segmento (`C:\Users\ana\Documentos` vira `C:\…\Documentos`), via [`shared/format/path.ts`](src/app/shared/format/path.ts). Isso vale para o histórico, a fila de backup, as mensagens de erro e os alertas.
- O progresso mostra só o nome do arquivo que está sendo copiado.
- A exportação de alertas gera um arquivo com os caminhos já mascarados.
- O bundle não leva credenciais, variáveis de ambiente, URL do backend nem a estrutura do código.
- Depois do login, o `returnUrl` só aceita rotas internas.

## Estrutura

```
src/app
├── core/
│   ├── api/        contratos de resposta do backend (CollectionResponse, ApiErrorResponse...)
│   ├── auth/       AuthService, guard e interceptor
│   ├── http/       leitor de Server-Sent Events via fetch
│   └── services/   BackupService, StorageService, LogsService, AppInfoService
├── shared/
│   ├── format/     mascaramento de caminhos e pipes
│   └── ui/         cabeçalho de página, badge de status, estados vazio/erro/carregando
├── components/layout/sidebar
└── sections/       páginas (login, dashboard, backup, history, storage, logs)
```

Os estilos base (`.card`, `.btn-*`, `.input`, `.chip-*`) ficam em [`src/styles.scss`](src/styles.scss).

## Deploy

O frontend é publicado separadamente da API. Como os services usam caminhos relativos (`/api/...`), sirva `dist/backup-manager-frontend/browser` e a API **na mesma origem**: um proxy reverso (nginx, Caddy, IIS) entrega os arquivos estáticos e encaminha `/api` ao backend. Configure o fallback de rotas para `index.html`.

Configure também os cabeçalhos de segurança no proxy, por exemplo:

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
```
