# SatMonitor — API REST de Monitoramento de Satélites

API REST Java para monitoramento de satélites em órbita. Desenvolvida como Global Solution 2026/1 da FIAP — 2TDS.

> **Solução proposta:** plataforma de telemetria espacial em que operadores criam missões, vinculam satélites com sensores físicos (térmico, pressão, radiação, magnetômetro) e recebem alertas automáticos quando as leituras ultrapassam os limites configurados. A API é conteinerizada com Docker + PostgreSQL e roda em nuvem (Azure VM), integrando-se com aplicação mobile, dispositivos IoT (ESP32) e uma API .NET paralela.

---

## O que faz

Uma estação terrestre cria **missões espaciais**, cada missão agrupa **satélites**, cada satélite carrega **sensores**, e cada sensor gera **leituras** contínuas. Quando uma leitura ultrapassa os limites configurados, a API classifica automaticamente como **NORMAL**, **ALERTA** ou **CRÍTICO** — sem nenhuma intervenção manual.

```
Agencia → Missao → Satelite → Sensor → LeituraSensor
                                               ↓
                                           StatusCalculator
                                               ↓
                                        NORMAL | ALERTA | CRITICO
                                               ↓ (se ALERTA ou CRITICO)
                                             Alerta (gerado automaticamente)
```

---

## Stack

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Security + JWT | auth0/java-jwt 4.4.0 |
| Spring HATEOAS | — |
| Spring Data JPA + Hibernate | — |
| PostgreSQL | Container Docker (produção) |
| H2 | Desenvolvimento local (in-memory) |
| Springdoc OpenAPI | 2.8.0 |
| Docker + Docker Compose | Deploy na VM Azure |
| GitHub Actions | CI/CD — testes + deploy automático |

---

## CI/CD — GitHub Actions

Todo push na branch `main` dispara automaticamente:

1. Roda os testes JUnit (`./gradlew test`)
2. Se os testes passarem: deploy via SSH na VM Azure (`git pull` + `docker compose up --build -d`)
3. Health check em `/actuator/health` — confirma que a API subiu
4. Se falhar em qualquer etapa: posta comentário no commit com link para os logs

Workflow: [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)

---

## Início rápido

### 1. Criar uma agência (público, sem token)

`POST /agencias`
```json
{
  "nome": "Agencia Espacial Brasileira",
  "siglaPais": "BR",
  "tipoAgencia": "Governamental"
}
```
`→ 201 Created`

### 2. Registrar um operador

`POST /auth/registrar`
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123",
  "nome": "Administrador",
  "agenciaId": 1
}
```
`→ 201 Created`

### 3. Fazer login e obter o token JWT

`POST /auth/login`
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123"
}
```
`→ 200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 4. Criar uma missão (com token)

`POST /missoes` · `Authorization: Bearer <token>`
```json
{
  "nome": "Missao Alpha",
  "descricao": "Missão principal de monitoramento",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "acesso123",
  "agenciaId": 1,
  "permitirCowork": true
}
```
`→ 201 Created com o id da missão`

### 5. Criar um satélite (com token)

`POST /satelites` · `Authorization: Bearer <token>`
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-06-01",
  "missaoId": 1,
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.0,
    "longitudeNodo": 210.5
  },
  "tipoOrbita": "LEO",
  "statusSatelite": "ATIVO"
}
```
`→ 201 Created`

### 6. Criar um sensor (com token)

`POST /sensores` · `Authorization: Bearer <token>`
```json
{
  "nome": "Sensor Térmico Principal",
  "unidade": "°C",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": 1,
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```
`→ 201 Created`

### 7. Registrar leitura (sem token — endpoint IoT)

`POST /leituras`
```json
{
  "valor": 95.3,
  "sensorId": 1,
  "latitude": -23.5505,
  "longitude": -46.6333
}
```
`→ 201 Created`
```json
{
  "id": 1,
  "valor": 95.3,
  "status": "CRITICO"
}
```

---

## Classificação automática de leituras

A regra central da API. Com base nos parâmetros do sensor:

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

**Exemplo — Sensor Térmico:** `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%`

```
|──CRITICO──|──ALERTA──|──────NORMAL──────|──ALERTA──|──CRITICO──|
0           8         72                  80
```

| Leitura | Status |
|---------|--------|
| 95°C | CRITICO (> limiteMax) |
| 75°C | ALERTA (entre 72 e 80) |
| 40°C | NORMAL (entre 8 e 72) |
| 5°C | ALERTA (entre 0 e 8) |
| -5°C | CRITICO (< limiteMin) |

---

## Autenticação

JWT com assinatura HMAC256, válido por **8 horas**. Enviar em toda requisição protegida:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Rotas públicas (sem token):**
- `POST /auth/login` e `POST /auth/registrar`
- `POST /agencias` — qualquer um pode criar uma agência
- Todos os `GET /satelites/**`, `GET /sensores/**`, `GET /leituras/**`
- `GET /agencias/**`, `GET /alertas/**`
- `GET /missoes/buscar` — diretório de missões abertas
- `POST /leituras` — ESP32 (IoT) envia leituras sem token
- `GET /actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`

**Atenção:** `GET /missoes/**` exige token, pois retorna apenas missões onde o operador logado é membro.

---

## Roles de missão

Cada operador tem uma role específica em cada missão:

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão / membros / leituras | ✓ | ✓ | ✓ |
| Registrar leitura | ✓ | ✓ | ✓ |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir satélite, sensor, missão | ✓ | — | — |
| Gerenciar membros | ✓ | — | — |

O criador da missão começa como **DONO**. Novos membros solicitam entrada via `POST /missoes/{id}/solicitar` (com a senha da missão) e ficam **PENDENTE** até um SUPERVISOR ou DONO aprovar.

---

## Todos os endpoints

### Agências
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| POST | `/agencias` | — | Cria agência espacial |
| GET | `/agencias` | — | Lista paginada |
| GET | `/agencias/{id}` | — | Busca por id |
| PUT | `/agencias/{id}` | ✓ | Atualiza |
| DELETE | `/agencias/{id}` | ✓ | Remove |

### Auth
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| POST | `/auth/registrar` | — | Cria novo operador |
| POST | `/auth/login` | — | Retorna token JWT |

### Missões
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/missoes` | ✓ | — |
| GET | `/missoes` | ✓ | MEMBRO |
| GET | `/missoes/{id}` | ✓ | MEMBRO |
| PUT | `/missoes/{id}` | ✓ | DONO |
| DELETE | `/missoes/{id}` | ✓ | DONO |
| GET | `/missoes/buscar?nome=X` | — | — |
| POST | `/missoes/{id}/solicitar` | ✓ | — |
| GET | `/missoes/{id}/solicitacoes` | ✓ | SUPERVISOR |
| PATCH | `/missoes/{id}/solicitacoes/{solId}/aprovar` | ✓ | SUPERVISOR |
| PATCH | `/missoes/{id}/solicitacoes/{solId}/rejeitar` | ✓ | SUPERVISOR |
| POST | `/missoes/{id}/sair` | ✓ | MEMBRO |
| GET | `/missoes/{id}/membros` | ✓ | MEMBRO |
| DELETE | `/missoes/{id}/membros/{opId}` | ✓ | DONO |
| PATCH | `/missoes/{id}/membros/{opId}?novoRole=X` | ✓ | DONO |

### Satélites
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/satelites` | ✓ | SUPERVISOR |
| GET | `/satelites` | — | — |
| GET | `/satelites/{id}` | — | — |
| GET | `/satelites/missao/{missaoId}` | — | — |
| GET | `/satelites/{id}/estatisticas` | — | — |
| PUT | `/satelites/{id}` | ✓ | SUPERVISOR |
| DELETE | `/satelites/{id}` | ✓ | DONO |

### Sensores
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/sensores` | ✓ | SUPERVISOR |
| GET | `/sensores` | — | — |
| GET | `/sensores/{id}` | — | — |
| GET | `/sensores/satelite/{sateliteId}` | — | — |
| PUT | `/sensores/{id}` | ✓ | SUPERVISOR |
| DELETE | `/sensores/{id}` | ✓ | DONO |

### Leituras
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/leituras` | — | — |
| GET | `/leituras` | — | — |
| GET | `/leituras/{id}` | — | — |
| GET | `/leituras/sensor/{sensorId}` | — | — |
| GET | `/leituras/satelite/{sateliteId}` | — | — |
| DELETE | `/leituras/{id}` | ✓ | SUPERVISOR |

### Alertas
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| GET | `/alertas` | — | Lista todos (filtro `?status=ATIVO\|RECONHECIDO\|RESOLVIDO`) |
| GET | `/alertas/{id}` | — | Busca por id |
| GET | `/alertas/satelite/{sateliteId}` | — | Alertas de um satélite |
| PATCH | `/alertas/{id}?novoStatus=X` | ✓ | Reconhece ou resolve — exige SUPERVISOR ou DONO na missão |

---

## Tipos de sensor

| Tipo | Campo extra | Valores |
|------|------------|---------|
| `TERMICO` | `unidadeEscala` | `CELSIUS`, `FAHRENHEIT`, `KELVIN` |
| `PRESSAO` | `tipoPressao` | `ABSOLUTA`, `RELATIVA` |
| `RADIACAO` | `tipoRadiacao` | `IONIZANTE`, `NAO_IONIZANTE` |
| `MAGNETOMETRO` | `eixosMedicao` | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ`, `XYZ` |

---

## Formato padrão de erro

Todos os erros seguem o mesmo formato:

```json
{
  "timestamp": "2026-06-01T14:32:07.123",
  "status": 404,
  "error": "Sensor não encontrado com id: 99",
  "path": "/sensores/99"
}
```

| Status | Situação |
|:------:|---------|
| 400 | Campo inválido, regra de negócio violada |
| 401 | Token ausente/expirado ou senha da missão errada |
| 403 | Sem permissão (role insuficiente ou não é membro) |
| 404 | Recurso não encontrado |
| 409 | Conflito (ex: operador já é membro, nome de agência duplicado) |
| 500 | Erro interno (detalhes apenas nos logs) |

---

## Modelo de dados simplificado

```
TB_AGENCIA             ← agência espacial
TB_OPERADOR
TB_MISSAO              ← senha BCrypt | FK opcional p/ TB_AGENCIA
TB_OPERADOR_MISSAO     ← junction table com role (DONO/SUPERVISOR/MEMBRO)
TB_SATELITE            ← coordenadas @Embeddable | tipoOrbita | statusSatelite
TB_SENSOR              ← base (herança JOINED)
  TB_SENSOR_TERMICO
  TB_SENSOR_PRESSAO
  TB_SENSOR_RADIACAO
  TB_MAGNETOMETRO
TB_LEITURA_SENSOR      ← status calculado pelo servidor | latitude/longitude
TB_ALERTA              ← gerado automaticamente em ALERTA/CRITICO
TB_SOLICITACAO_ENTRADA ← controla fluxo de aprovação de cowork
```

---

## Ordem de leitura da documentação

| # | Arquivo | Por que ler |
|:-:|---------|-------------|
| 1 | [`docs/api/Auth.md`](docs/api/Auth.md) | Ponto de entrada — sem entender autenticação JWT nada funciona |
| 2 | [`docs/api/Agencia.md`](docs/api/Agencia.md) | Entidade mais simples; apresenta o padrão de CRUD e HATEOAS |
| 3 | [`docs/api/Missao.md`](docs/api/Missao.md) | Núcleo da API — roles, fluxo de aprovação, `permitirCowork` |
| 4 | [`docs/internals/MissaoService.md`](docs/internals/MissaoService.md) | Regras internas de missão: ordem de verificações, invariantes |
| 5 | [`docs/api/Satelite.md`](docs/api/Satelite.md) | Satélites, coordenadas orbitais e `@Embeddable` |
| 6 | [`docs/api/Sensor.md`](docs/api/Sensor.md) | 4 tipos de sensor e herança JOINED |
| 7 | [`docs/api/Leitura.md`](docs/api/Leitura.md) | Contrato com o ESP32 (IoT) e o `StatusCalculator` |
| 8 | [`docs/api/Alerta.md`](docs/api/Alerta.md) | Alertas automáticos e ciclo de vida |
| 9 | [`docs/internals/Exception.md`](docs/internals/Exception.md) | Mapa completo de erros |
| 10 | [`docs/tests/UnitTests.md`](docs/tests/UnitTests.md) | Testes unitários JUnit e relatório JaCoCo |
| 11 | [`docs/infra/Deploy.md`](docs/infra/Deploy.md) | Infraestrutura Azure, Docker Compose e GitHub Actions |

---

## Documentação detalhada

### API — Endpoints
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/api/Auth.md`](docs/api/Auth.md) | JWT, registro, login, filtro de segurança |
| [`docs/api/Agencia.md`](docs/api/Agencia.md) | Agências espaciais — CRUD e vínculo com missões |
| [`docs/api/Missao.md`](docs/api/Missao.md) | Roles, endpoints de missão, fluxos de entrada/saída |
| [`docs/api/Satelite.md`](docs/api/Satelite.md) | Satélites, coordenadas orbitais, tipo de órbita, estatísticas |
| [`docs/api/Sensor.md`](docs/api/Sensor.md) | 4 tipos de sensor, herança JOINED, limites |
| [`docs/api/Leitura.md`](docs/api/Leitura.md) | StatusCalculator, contrato IoT, geração automática de alertas |
| [`docs/api/Alerta.md`](docs/api/Alerta.md) | Alertas automáticos, ciclo de vida (ATIVO → RECONHECIDO → RESOLVIDO) |

### Internos — Comportamento e regras
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/internals/Exception.md`](docs/internals/Exception.md) | Mapa de erros, como adicionar nova exceção |
| [`docs/internals/MissaoService.md`](docs/internals/MissaoService.md) | Fluxos internos do service de missões |

### Testes
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/tests/UnitTests.md`](docs/tests/UnitTests.md) | Testes unitários JUnit/Mockito e relatório JaCoCo |

### Infra e Deploy
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/infra/Deploy.md`](docs/infra/Deploy.md) | Infraestrutura Azure, Docker Compose, GitHub Actions |
| [`COMANDOS_DEPLOY.md`](COMANDOS_DEPLOY.md) | Comandos prontos para copiar durante o deploy |

---

## Como executar — How-to

### Arquitetura em nuvem

```
┌──────────────────────────────────────────────────────────────┐
│                    Azure VM (Ubuntu 22.04)                   │
│                    Standard_D2s_v3 — eastus2                 │
│                                                              │
│  ┌────────────────────────┐  satmonitor-net  ┌────────────┐  │
│  │  satmonitor-app        │◄────────────────►│satmonitor  │  │
│  │  (Spring Boot 3 / JVM) │  jdbc:postgresql │-db         │  │
│  │  Porta 8080            │                  │(PostgreSQL │  │
│  │  Usuário: satuser      │                  │ 16-alpine) │  │
│  │  WORKDIR: /app         │                  │ Vol. named │  │
│  └──────────┬─────────────┘                  └─────┬──────┘  │
└─────────────┼────────────────────────────────────-─┼─────────┘
              │ :8080 (NSG aberta)                   │ :5432
              ▼                                      ▼
       Mobile / IoT / Postman               pgAdmin / psql
       (qualquer cliente HTTP)              (acesso externo ao DB)
```

---

### Pré-requisitos

- **VM Ubuntu 22.04+** (Azure, AWS ou outra nuvem)
- **Docker** instalado na VM
- Portas **8080** (API) e **5432** (banco) abertas no firewall/NSG

#### Instalar Docker na VM (Ubuntu)

```bash
sudo apt update && sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER
# Sair e reconectar via SSH para o grupo docker entrar em vigor
```

---

### Passo 1 — Clonar o repositório

```bash
git clone https://github.com/pedrinzz10/satmonitor.git
cd satmonitor
```

### Passo 2 — Criar o arquivo `.env`

```bash
cp .env.example .env
```

O `.env.example` já contém os valores configurados. O arquivo `.env` está no `.gitignore` e nunca é commitado.

### Passo 3 — Subir os dois containers em background

```bash
docker compose --profile docker up --build -d
```

- `--profile docker` — sobe `satmonitor-app-RM562312` e `satmonitor-db-RM562312`
- `--build` — reconstrói a imagem da API (obrigatório na primeira execução)
- `-d` — modo background

### Passo 4 — Verificar containers e logs

```bash
docker compose --profile docker ps
docker compose --profile docker logs -f
curl http://<IP-DA-VM>:8080/actuator/health
# → {"status":"UP"}
```

### Desenvolvimento local (H2 em memória)

```bash
./gradlew bootRun
# API disponível em http://localhost:8080
# Swagger UI em http://localhost:8080/swagger-ui.html
```

---

## Integrações

| Disciplina | Integração |
|------------|----------|
| **Mobile** (Fabrício) | Consome todos os endpoints; base URL = IP da VM Azure |
| **IoT** (Miguel) | ESP32 faz `POST /leituras` com `{valor, sensorId}` — sem token |
| **DevOps** | Dockeriza a API + PostgreSQL; containers na VM Azure com CI/CD via GitHub Actions |
| **.NET** | API espelho para apresentação |
