# SatMonitor — API REST de Monitoramento de Satélites

API Java para monitoramento de satélites em órbita. Desenvolvida como Global Solution 2026/1 da FIAP — 2TDS.

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
                                             Alerta  →  trigger Oracle (PL/SQL)
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
| Oracle Database | Produção (FIAP) |
| H2 | Desenvolvimento local |
| Springdoc OpenAPI | 2.5.0 |
| Docker | Deploy na VM Azure |

---

## Início rápido

### 1. Subir a aplicação

```bash
./gradlew bootRun
# API disponível em http://localhost:8080
# Swagger UI em http://localhost:8080/swagger-ui.html
```

### 2. Registrar um operador

`POST /auth/registrar`
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123",
  "nome": "Administrador"
}
```
`→ 201 Created (corpo vazio)`

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

### 4. Criar uma agência (com token)

`POST /agencias` · `Authorization: Bearer <token>`
```json
{
  "nome": "Agência Espacial Brasileira",
  "siglaPais": "BR",
  "tipoAgencia": "Governamental"
}
```
`→ 201 Created`
```json
{
  "id": 1,
  "nome": "Agência Espacial Brasileira",
  "siglaPais": "BR",
  "tipoAgencia": "Governamental"
}
```

### 5. Criar uma missão (com token)

`POST /missoes` · `Authorization: Bearer <token>`
```json
{
  "nome": "Missao Alpha",
  "descricao": "Missão principal de monitoramento",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "acesso123",
  "agenciaId": 1,
  "objetivo": "Monitorar temperatura orbital",
  "dataFimPrevista": "2027-06-01"
}
```
`→ 201 Created com o id da missão`

> `agenciaId`, `objetivo` e `dataFimPrevista` são opcionais.

### 6. Criar um satélite (com token)

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

> `tipoOrbita`, `statusSatelite` e `longitudeNodo` são opcionais.

### 7. Criar um sensor (com token)

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

### 8. Registrar leitura (sem token — endpoint IoT)

`POST /leituras`
```json
{
  "valor": 95.3,
  "sensorId": 1,
  "latitude": -23.5505,
  "longitude": -46.6333,
  "qualidade": "BOA"
}
```
`→ 201 Created`
```json
{
  "id": 1,
  "valor": 95.3,
  "status": "CRITICO",
  "qualidade": "BOA"
}
```

> `latitude`, `longitude` e `qualidade` são opcionais. `qualidade` aceita `BOA`, `DEGRADADA` ou `INVALIDA` (padrão: `BOA`).

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
- Todos os `GET /satelites/**`, `GET /sensores/**`, `GET /leituras/**`
- `GET /agencias/**`, `GET /alertas/**`
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

O criador da missão começa como **DONO**. Novos membros solicitam entrada via `POST /missoes/{id}/solicitar` (com a senha da missão) e ficam **PENDENTE** até um SUPERVISOR ou DONO aprovar. Quando `permitirCowork=false` (padrão), apenas operadores da mesma agência podem solicitar.

---

## Todos os endpoints

### Agências
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| POST | `/agencias` | ✓ | Cria agência espacial |
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
| 409 | Conflito (ex: operador já é membro da missão) |
| 500 | Erro interno (detalhes apenas nos logs) |

---

## Modelo de dados simplificado

```
TB_AGENCIA             ← agência espacial (opcional em Missao)
TB_OPERADOR
TB_MISSAO              ← senha BCrypt | FK opcional p/ TB_AGENCIA
TB_OPERADOR_MISSAO     ← junction table com role (DONO/SUPERVISOR/MEMBRO)
TB_SATELITE            ← coordenadas @Embeddable | tipoOrbita | statusSatelite
TB_SENSOR              ← base (herança JOINED)
  TB_SENSOR_TERMICO
  TB_SENSOR_PRESSAO
  TB_SENSOR_RADIACAO
  TB_MAGNETOMETRO
TB_LEITURA_SENSOR      ← status calculado pelo servidor | latitude/longitude | qualidade
TB_ALERTA              ← gerado automaticamente em ALERTA/CRITICO → trigger Oracle
```

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
| [`docs/api/Alerta.md`](docs/api/Alerta.md) | Alertas automáticos, ciclo de vida, integração Oracle |

### Internos — Comportamento e regras
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/internals/Exception.md`](docs/internals/Exception.md) | Mapa de erros, como adicionar nova exceção |
| [`docs/internals/MissaoService.md`](docs/internals/MissaoService.md) | Fluxos internos do service de missões |

### Testes
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/tests/IntegrationTests.md`](docs/tests/IntegrationTests.md) | Coleção Postman e script PowerShell (241 testes de integração) |
| [`docs/tests/UnitTests.md`](docs/tests/UnitTests.md) | Testes unitários JUnit/Mockito e relatório JaCoCo |

### Infraestrutura
| Arquivo | Conteúdo |
|---------|---------|
| [`docs/infra/Deploy.md`](docs/infra/Deploy.md) | Passo a passo: Docker + Azure VM + Oracle remoto |

---

## Deploy

### Desenvolvimento local (H2 em memória)

```bash
docker compose --profile dev up --build
```

### Produção (Oracle FIAP)

Crie um arquivo `.env` na raiz do projeto:

```
JWT_SECRET=seu_secret_aqui
ORACLE_URL=jdbc:oracle:thin:@<host>:<porta>/<service>
ORACLE_USER=usuario
ORACLE_PASSWORD=senha
```

```bash
docker compose --profile prod up --build
```

### Build manual da imagem

```bash
docker build -t satmonitor .
docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod satmonitor
```

Health check: `GET /actuator/health`

---

## Integrações

| Disciplina | Integração |
|------------|----------|
| **Mobile** (Fabrício) | Consome todos os endpoints; base URL = IP da VM Azure |
| **Oracle / PL/SQL** (Henrique) | Schema separado; triggers e procedures no banco |
| **IoT** (Miguel) | ESP32 faz `POST /leituras` com `{valor, sensorId}` — sem token |
| **DevOps** | Dockeriza a API; container na VM Azure |
| **.NET** | API espelho para apresentação; schema Oracle separado |
