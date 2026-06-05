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

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123","nome":"Administrador"}'
# → 201 Created (corpo vazio)
```

### 3. Fazer login e obter o token JWT

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123"}'
# → {"token":"eyJhbGciOiJIUzI1NiJ9..."}
```

### 4. Criar uma missão (com token)

```bash
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_AQUI" \
  -d '{"nome":"Missao Alpha","descricao":"Missão principal","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"acesso123"}'
# → 201 Created com o id da missão
```

### 5. Registrar leitura (sem token — endpoint IoT)

```bash
curl -s -X POST http://localhost:8080/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":95.3,"sensorId":1}'
# → 201 Created com status calculado automaticamente (ex: "CRITICO")
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

O criador da missão começa como **DONO**. Novos membros entram com a **senha da missão** via `POST /missoes/{id}/entrar` e começam como **MEMBRO**.

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
| POST | `/missoes/{id}/entrar` | ✓ | — |
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
| PATCH | `/alertas/{id}?novoStatus=X` | ✓ | Reconhece ou resolve o alerta |

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

| Arquivo | Conteúdo |
|---------|---------|
| [`docs/Auth.md`](docs/Auth.md) | JWT, registro, login, filtro de segurança |
| [`docs/Agencia.md`](docs/Agencia.md) | Agências espaciais — CRUD e vínculo com missões |
| [`docs/Missao.md`](docs/Missao.md) | Roles, endpoints de missão, fluxos de entrada/saída |
| [`docs/Satelite.md`](docs/Satelite.md) | Satélites, coordenadas orbitais, tipo de órbita, estatísticas |
| [`docs/Sensor.md`](docs/Sensor.md) | 4 tipos de sensor, herança JOINED, limites |
| [`docs/Leitura.md`](docs/Leitura.md) | StatusCalculator, contrato IoT, geração automática de alertas |
| [`docs/Alerta.md`](docs/Alerta.md) | Alertas automáticos, ciclo de vida, integração Oracle |
| [`docs/Exception.md`](docs/Exception.md) | Mapa de erros, como adicionar nova exceção |
| [`docs/MissaoService.md`](docs/MissaoService.md) | Fluxos internos do service de missões |
| [`docs/Testes.md`](docs/Testes.md) | Coleção Postman importável com testes automáticos |

---

## Deploy

```bash
# Build da imagem
docker build -t satmonitor .

# Rodar com Oracle (produção)
docker run -p 8080:8080 \
  -e JWT_SECRET=seu_secret_aqui \
  -e ORACLE_URL=jdbc:oracle:thin:@... \
  -e ORACLE_USER=usuario \
  -e ORACLE_PASSWORD=senha \
  -e SPRING_PROFILES_ACTIVE=prod \
  satmonitor
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
