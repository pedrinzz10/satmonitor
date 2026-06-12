# Auth — Autenticação e segurança

## Índice

1. [Como funciona em 4 passos](#como-funciona-em-4-passos)
2. [Registrar operador](#registrar-operador)
3. [Fazer login](#fazer-login)
4. [Usar o token](#usar-o-token)
5. [Proteção anti-brute-force (rate limiter)](#proteção-anti-brute-force-rate-limiter)
6. [Rotas públicas e protegidas](#rotas-públicas-e-protegidas)
7. [Erros comuns](#erros-comuns)
8. [Referência técnica](#referência-técnica)

---

## Como funciona em 4 passos

```
1. Registrar    →  POST /auth/registrar    →  conta criada (vinculada a uma agência)
2. Login        →  POST /auth/login        →  token JWT (válido 8h)
3. Usar token   →  Authorization: Bearer <token>  nas rotas protegidas
4. Expirou?     →  novo POST /auth/login
```

O servidor nunca guarda sessão — cada requisição é autossuficiente com o JWT no header. Isso é chamado de arquitetura **stateless** (`SessionCreationPolicy.STATELESS`).

---

## Registrar operador

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{
    "login": "ana.souza@sat.dev",
    "senha": "minhasenha123",
    "nome": "Ana Souza",
    "agenciaId": 1
  }'
```

**Resposta de sucesso — 201 Created** (corpo vazio):
```
HTTP/1.1 201 Created
```

**Campos obrigatórios:**

| Campo | Tipo | Regra |
|-------|------|-------|
| `login` | String | Obrigatório, não pode ser vazio, deve ser único no sistema |
| `senha` | String | Obrigatório, não pode ser vazio. Armazenada com BCrypt |
| `nome` | String | Obrigatório, não pode ser vazio |
| `agenciaId` | Long | Obrigatório. A agência deve existir no banco |

> `agenciaId` é obrigatório porque a verificação de cowork nas missões compara a agência do operador com a da missão. Um operador sem agência não pode solicitar entrada em nenhuma missão.

**Regras de negócio do `OperadorService.registrar`:**
1. Verifica se `login` já está cadastrado → lança `IllegalArgumentException` (400) se sim
2. Busca a agência pelo `agenciaId` → lança `EntityNotFoundException` (404) se não existe
3. Cria `Operador` com `senha` encodada via `BCryptPasswordEncoder`
4. `role` padrão = `"OPERADOR"` (string fixa — não há hierarquia global de operadores, apenas de missão)

**Resposta de erro — login já existe:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 400,
  "error": "Login já está em uso",
  "path": "/auth/registrar"
}
```

---

## Fazer login

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "login": "ana.souza@sat.dev",
    "senha": "minhasenha123"
  }'
```

**Resposta de sucesso — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Fluxo interno do `AuthController.login`:**
1. Chama `rateLimiter.verificarLimite(login)` — lança 429 se excedeu 5 falhas em 60s
2. Delega para o `AuthenticationManager` do Spring Security autenticar login/senha
3. Se sucesso: chama `rateLimiter.registrarSucesso(login)` (reseta contador) e gera JWT
4. Se falha: chama `rateLimiter.registrarFalha(login)` (incrementa contador) e relança exceção → 401

> A mensagem de erro é sempre `"Credenciais inválidas"` tanto para login inexistente quanto para senha errada. Isso impede descobrir se um login existe ou não (proteção contra **user enumeration**).

---

## Usar o token

Salve o valor do campo `token` e envie em toda requisição protegida:

```bash
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{"nome":"Missao Alpha","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"acesso123"}'
```

O token é válido por **8 horas**. Após expirar, refaça o login para obter um novo.

**O `SecurityFilter` processa o token em toda requisição:**
1. Extrai o token do header `Authorization: Bearer <token>` (7 caracteres removidos com `substring(7)`)
2. Chama `TokenService.validarToken(token)` que verifica assinatura HMAC256, issuer `"satmonitor"` e expiração
3. Se válido: busca o `Operador` no banco pelo `login` (subject do JWT) e seta no `SecurityContextHolder`
4. Se inválido ou ausente: a requisição continua sem autenticação — o Spring Security decidirá depois se rejeita

---

## Proteção anti-brute-force (rate limiter)

O `LoginRateLimiter` impede ataques de força bruta no login sem nenhuma dependência externa (sem Redis):

```
Implementação: ConcurrentHashMap<login → Deque<timestamps>>
Janela deslizante: 60 segundos
Máximo de falhas: 5
```

**Como funciona a janela deslizante:**

```
Tentativa às 14:00:00  → falha → timestamps: [00]
Tentativa às 14:00:10  → falha → timestamps: [00, 10]
Tentativa às 14:00:20  → falha → timestamps: [00, 10, 20]
Tentativa às 14:00:30  → falha → timestamps: [00, 10, 20, 30]
Tentativa às 14:00:40  → falha → timestamps: [00, 10, 20, 30, 40]
Tentativa às 14:00:50  → BLOQUEADO (5 falhas nos últimos 60s) → HTTP 429

Tentativa às 14:01:05  → expira timestamp 00 → timestamps: [10, 20, 30, 40]
                       → apenas 4 falhas na janela → permitido tentar novamente
```

Login bem-sucedido **reseta imediatamente** o contador do usuário (`falhas.remove(login)`).

---

## Rotas públicas e protegidas

### Públicas (sem token)

| Método | Rota | Motivo |
|:------:|------|--------|
| POST | `/auth/login` | Entrada no sistema |
| POST | `/auth/registrar` | Cadastro de novos operadores |
| GET | `/agencias/**` | Dados de agências — necessário antes de registrar operador |
| POST | `/agencias` | Criação de agências |
| POST | `/leituras` | Dispositivos IoT (ESP32) postam leituras sem gerenciar tokens |
| GET | `/actuator/health` | Health check do container (CI/CD e monitoramento) |
| qualquer | `/swagger-ui/**`, `/v3/api-docs/**` | Documentação interativa |
| OPTIONS | `/**` | Preflight CORS — browsers enviam antes de requisições cross-origin |

### Protegidas (token obrigatório)

Qualquer rota não listada acima exige `Authorization: Bearer <token>` válido.

**Importantes: as rotas abaixo exigem token mesmo sendo apenas GET:**

| Rota | Motivo |
|------|--------|
| `GET /missoes/**` | Missões só são visíveis para membros — precisa saber quem está perguntando |
| `GET /satelites/**` | Dados orbitais vinculados a missões protegidas |
| `GET /sensores/**` | Sensores vinculados a satélites de missões protegidas |
| `GET /leituras/**` | Leituras de sensores de missões protegidas |
| `GET /alertas/**` | Alertas filtrados pelas missões do operador logado |

---

## Erros comuns

| Status | Situação | O que fazer |
|:------:|---------|-------------|
| 400 | Campo obrigatório vazio ou ausente | Verifique `login`, `senha`, `nome` e `agenciaId` |
| 400 | Login já cadastrado | Escolha outro login |
| 401 | Credenciais incorretas no login | Verifique login e senha |
| 401 | Token ausente em rota protegida | Adicione `Authorization: Bearer <token>` |
| 401 | Token expirado (após 8 horas) | Faça login novamente |
| 403 | Token válido, mas role insuficiente na missão | Verifique sua role na missão |
| 404 | `agenciaId` não existe no registro | Crie a agência antes de registrar o operador |
| 429 | Muitas tentativas de login falhas | Aguarde 1 minuto e tente novamente |

---

## Referência técnica

### Token JWT

| Claim | Valor |
|-------|-------|
| `iss` (issuer) | `"satmonitor"` |
| `sub` (subject) | login do operador (ex: `"ana.souza@sat.dev"`) |
| `exp` (expiration) | 8 horas a partir da emissão (`Instant.now().plus(8, ChronoUnit.HOURS)`) |
| Algoritmo de assinatura | HMAC256 com secret definido em `${api.security.token.secret}` |

O payload do JWT **não contém** senha, role ou id — apenas o login. A cada requisição, o `SecurityFilter` recarrega o `Operador` do banco pelo login extraído do token.

### Fluxo de validação por requisição

```
Requisição com header "Authorization: Bearer eyJ..."
  ↓
SecurityFilter (OncePerRequestFilter) extrai o token do header
  ↓ (sem token → continua sem autenticação → Spring Security decide depois)
TokenService.validarToken(token):
  - verifica algoritmo HMAC256 + secret
  - verifica issuer = "satmonitor"
  - verifica expiração
  ↓ (token inválido ou expirado → continua sem autenticação)
OperadorRepository.findByLogin(login) → carrega Operador do banco
  ↓
SecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken)
  ↓
Controller processa a requisição com o Operador injetado via @AuthenticationPrincipal
```

### Variáveis de ambiente

| Variável | Descrição | Obrigatória em prod? |
|----------|-----------|:--------------------:|
| `JWT_SECRET` | Secret HMAC256 para assinar tokens | Sim |
| `POSTGRES_URL` | URL JDBC do PostgreSQL | Sim (inferida do compose) |
| `POSTGRES_USER` | Usuário do banco | Sim |
| `POSTGRES_PASSWORD` | Senha do banco | Sim |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas separadas por vírgula | Recomendado |

Em desenvolvimento, o secret tem um fallback em `application.properties`. Em produção, use sempre a variável de ambiente.

### Por que CSRF está desabilitado?

CSRF protege contra ataques em que um browser envia requisições autenticadas por **cookie** sem o usuário saber. Como esta API usa JWT em header e não usa cookies, CSRF não se aplica — desabilitá-lo é a configuração correta para APIs stateless.

### Por que `SessionCreationPolicy.STATELESS`?

`STATELESS` impede que o Spring Security crie `HttpSession` no servidor. Isso permite escalar horizontalmente sem sincronizar sessões entre instâncias — cada requisição se autentica sozinha pelo JWT no header.
