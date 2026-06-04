# Auth — Autenticação e segurança

## Índice

1. [Como funciona em 4 passos](#como-funciona-em-4-passos)
2. [Registrar operador](#registrar-operador)
3. [Fazer login](#fazer-login)
4. [Usar o token](#usar-o-token)
5. [Rotas públicas e protegidas](#rotas-públicas-e-protegidas)
6. [Erros comuns](#erros-comuns)
7. [Referência técnica](#referência-técnica)

---

## Como funciona em 4 passos

```
1. Registrar    →  POST /auth/registrar    →  conta criada
2. Login        →  POST /auth/login        →  token JWT (válido 8h)
3. Usar token   →  Authorization: Bearer <token>  nas rotas protegidas
4. Expirou?     →  novo POST /auth/login
```

O servidor nunca guarda sessão — cada requisição é autossuficiente com o JWT no header.

---

## Registrar operador

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{
    "login": "ana.souza@sat.dev",
    "senha": "minhasenha123",
    "nome": "Ana Souza"
  }'
```

**Resposta de sucesso — 201 Created** (corpo vazio):
```
HTTP/1.1 201 Created
```

**Resposta de erro — login já existe:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 400,
  "error": "Login já está em uso",
  "path": "/auth/registrar"
}
```

**Campos obrigatórios:**

| Campo | Tipo | Regra |
|-------|------|-------|
| `login` | String | Obrigatório, não pode ser vazio, deve ser único |
| `senha` | String | Obrigatório, não pode ser vazio |
| `nome` | String | Obrigatório, não pode ser vazio |

> A senha é salva com hash **BCrypt** — nunca em texto puro.

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
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzYXRtb25pdG9yIiwic3ViIjoiYW5hLnNvdXphQHNhdC5kZXYiLCJleHAiOjE3NDkwMDAwMDB9.abc123"
}
```

**Resposta de erro — credenciais inválidas:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 401,
  "error": "Credenciais inválidas",
  "path": "/auth/login"
}
```

> A mensagem é sempre `"Credenciais inválidas"` para login inexistente e para senha errada — isso impede descobrir se um login existe ou não.

---

## Usar o token

Salve o valor do campo `token` e envie em toda requisição protegida:

```bash
# Exemplo: criar uma missão
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{"nome":"Missao Alpha","descricao":"...","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"acesso123"}'
```

O token é válido por **8 horas**. Após expirar, refaça o login para obter um novo.

---

## Rotas públicas e protegidas

### Públicas (sem token)

| Método | Rota | Motivo |
|:------:|------|--------|
| POST | `/auth/login` | Entrada no sistema |
| POST | `/auth/registrar` | Cadastro de novos operadores |
| GET | `/satelites/**` | Dados públicos de satélites |
| GET | `/sensores/**` | Dados públicos de sensores |
| GET | `/leituras/**` | Dados públicos de leituras |
| POST | `/leituras` | ESP32 posta leituras sem gerenciar tokens |
| GET | `/actuator/health` | Health check do container |
| qualquer | `/swagger-ui/**`, `/v3/api-docs/**` | Documentação interativa |

### Protegidas (token obrigatório)

Qualquer rota não listada acima exige `Authorization: Bearer <token>` válido.

**Importante:** `GET /missoes/**` é protegida — diferente dos outros módulos, missões só são visíveis para quem é membro, então a API precisa saber quem está perguntando.

---

## Erros comuns

| Status | Situação | O que fazer |
|:------:|---------|-------------|
| 400 | Campo obrigatório vazio ou ausente | Verifique `login`, `senha` e `nome` |
| 400 | Login já cadastrado | Escolha outro login |
| 401 | Credenciais incorretas no login | Verifique login e senha |
| 401 | Token ausente em rota protegida | Adicione `Authorization: Bearer <token>` |
| 401 | Token expirado (após 8 horas) | Faça login novamente |
| 403 | Token válido, mas role insuficiente | Verifique sua role na missão |

---

## Referência técnica

### Token JWT

| Claim | Valor |
|-------|-------|
| `iss` (issuer) | `"satmonitor"` |
| `sub` (subject) | login do operador |
| `exp` (expiration) | 8 horas a partir da emissão |
| Algoritmo de assinatura | HMAC256 com secret da variável `api.security.token.secret` |

### Fluxo de validação por requisição

```
Requisição com header "Authorization: Bearer eyJ..."
  ↓
SecurityFilter extrai o token
  ↓ (sem token → prossegue sem autenticação)
TokenService verifica assinatura, issuer e expiração
  ↓ (token inválido → prossegue sem autenticação)
Carrega Operador do banco pelo login extraído do JWT
  ↓
Injeta autenticação no SecurityContextHolder
  ↓
Controller processa a requisição com o operador identificado
```

### Variáveis de ambiente

| Variável | Descrição | Obrigatória em prod? |
|----------|-----------|:--------------------:|
| `JWT_SECRET` (via `api.security.token.secret`) | Secret HMAC256 para assinar tokens | Sim |
| `ORACLE_URL` | URL JDBC do Oracle FIAP | Sim |
| `ORACLE_USER` | Usuário Oracle | Sim |
| `ORACLE_PASSWORD` | Senha Oracle | Sim |

Em desenvolvimento, o secret tem um fallback configurado em `application.properties`. Em produção (`application-prod.properties`), não há fallback — a aplicação não sobe se `JWT_SECRET` não estiver definido.

### Entidade Operador

| Campo | Tipo | Detalhe |
|-------|------|---------|
| `id` | Long | PK, sequence `SEQ_OPERADOR` |
| `login` | String | Único no banco, serve como username |
| `senha` | String | Sempre hash BCrypt — `@JsonIgnore` no JSON |
| `nome` | String | Nome de exibição |
| `role` | String | Sempre `"OPERADOR"` (valor padrão via `@Builder.Default`) |

### Decisão: por que não há sessão?

A API usa `SessionCreationPolicy.STATELESS` — nenhum `HttpSession` é criado. Cada requisição é autenticada pelo JWT no header, sem estado no servidor. Isso permite escalar horizontalmente sem sincronizar sessões entre instâncias.

### Decisão: por que CSRF está desabilitado?

CSRF protege contra ataques em que um browser envia requisições autenticadas por **cookie** sem o usuário saber. Como esta API usa JWT em header e não usa cookies, CSRF não se aplica.
