# Testes manuais — Guia para Postman

Guia para testar todos os endpoints da API SatMonitor no Postman. Os cenários cobrem fluxos de sucesso, regras de negócio e controle de acesso, na mesma ordem do script `TestApiControllers/test-api.sh`.

**Base URL:** `http://localhost:8080`

**Como usar o token no Postman:**  
Em cada requisição protegida, vá em **Authorization → Bearer Token** e cole o token obtido no login. Ou configure uma variável de ambiente `{{token_dono}}`, `{{token_membro}}`, etc.

---

## Seção 1 — Health check

### GET /actuator/health

```
GET http://localhost:8080/actuator/health
```

**Resposta esperada — 200 OK:**
```json
{
  "status": "UP"
}
```

---

## Seção 2 — Registro de operadores

### 2.1 Registrar dono — 201 Created

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "dono@sat.dev",
  "senha": "senha123",
  "nome": "Operador Dono"
}
```

**Resposta esperada — 201 Created** (corpo vazio)

---

### 2.2 Registrar membro — 201 Created

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "membro@sat.dev",
  "senha": "senha123",
  "nome": "Operador Membro"
}
```

**Resposta esperada — 201 Created**

---

### 2.3 Registrar supervisor — 201 Created

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "supervisor@sat.dev",
  "senha": "senha123",
  "nome": "Operador Supervisor"
}
```

**Resposta esperada — 201 Created**

---

### 2.4 Registrar forasteiro — 201 Created

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "forasteiro@sat.dev",
  "senha": "senha123",
  "nome": "Operador Forasteiro"
}
```

**Resposta esperada — 201 Created**

---

### 2.5 Login duplicado — 400 Bad Request

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "dono@sat.dev",
  "senha": "abc",
  "nome": "Dup"
}
```

**Resposta esperada — 400 Bad Request:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 400,
  "error": "Login já está em uso",
  "path": "/auth/registrar"
}
```

---

### 2.6 Campo obrigatório vazio — 400 Bad Request

```
POST http://localhost:8080/auth/registrar
Content-Type: application/json
```

**Body:**
```json
{
  "login": "",
  "senha": "abc",
  "nome": "Vazio"
}
```

**Resposta esperada — 400 Bad Request:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 400,
  "error": "login: não deve estar em branco",
  "path": "/auth/registrar"
}
```

---

## Seção 3 — Login e tokens JWT

### 3.1 Login do dono — salvar o token retornado

```
POST http://localhost:8080/auth/login
Content-Type: application/json
```

**Body:**
```json
{
  "login": "dono@sat.dev",
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

> Salve o valor de `token` como `token_dono` no Postman Environment.

---

### 3.2 Login do membro

```
POST http://localhost:8080/auth/login
Content-Type: application/json
```

**Body:**
```json
{
  "login": "membro@sat.dev",
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK** com `token`. Salvar como `token_membro`.

---

### 3.3 Login do supervisor

```
POST http://localhost:8080/auth/login
Content-Type: application/json
```

**Body:**
```json
{
  "login": "supervisor@sat.dev",
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK** com `token`. Salvar como `token_supervisor`.

---

### 3.4 Login do forasteiro

```
POST http://localhost:8080/auth/login
Content-Type: application/json
```

**Body:**
```json
{
  "login": "forasteiro@sat.dev",
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK** com `token`. Salvar como `token_forasteiro`.

---

## Seção 4 — Missões: CRUD básico

### 4.1 Criar missão (dono) — 201 Created

```
POST http://localhost:8080/missoes
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "Missao Alpha",
  "descricao": "Missao principal de testes",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "senha123"
}
```

**Resposta esperada — 201 Created:**
```json
{
  "id": 1,
  "nome": "Missao Alpha",
  "descricao": "Missao principal de testes",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "roleDoOperador": "DONO",
  "totalMembros": 1,
  "totalSatelites": 0,
  "_links": {
    "self": { "href": "http://localhost:8080/missoes/1" },
    "membros": { "href": "http://localhost:8080/missoes/1/membros" },
    "sair": { "href": "http://localhost:8080/missoes/1/sair" },
    "atualizar": { "href": "http://localhost:8080/missoes/1" },
    "deletar": { "href": "http://localhost:8080/missoes/1" }
  }
}
```

> Salvar o `id` retornado como `missao_id`.

---

### 4.2 Listar missões do dono — 200 OK

```
GET http://localhost:8080/missoes
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 200 OK** com `totalElements >= 1`.

---

### 4.3 Buscar missão por id (dono) — 200 OK

```
GET http://localhost:8080/missoes/{{missao_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 200 OK** com `nome: "Missao Alpha"`.

---

### 4.4 Forasteiro tenta ver missão — 403 Forbidden

```
GET http://localhost:8080/missoes/{{missao_id}}
Authorization: Bearer {{token_forasteiro}}
```

**Resposta esperada — 403 Forbidden:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 403,
  "error": "Você não tem acesso a esta missão",
  "path": "/missoes/1"
}
```

---

### 4.5 Sem token tenta ver missão — 403 Forbidden

```
GET http://localhost:8080/missoes/{{missao_id}}
```

**Resposta esperada — 403 Forbidden**

---

### 4.6 Atualizar missão (DONO) — 200 OK

```
PUT http://localhost:8080/missoes/{{missao_id}}
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "Missao Alpha v2",
  "descricao": "Atualizada",
  "dataLancamento": "2026-07-01",
  "status": "ATIVA"
}
```

**Resposta esperada — 200 OK:**
```json
{
  "id": 1,
  "nome": "Missao Alpha v2",
  "status": "ATIVA",
  "roleDoOperador": "DONO",
  ...
}
```

---

### 4.7 Forasteiro tenta atualizar missão — 404 Not Found

```
PUT http://localhost:8080/missoes/{{missao_id}}
Content-Type: application/json
Authorization: Bearer {{token_forasteiro}}
```

**Body:**
```json
{
  "nome": "Invasao",
  "descricao": "x",
  "dataLancamento": "2026-07-01",
  "status": "ATIVA"
}
```

**Resposta esperada — 404 Not Found** (não é membro = vínculo não existe)

---

## Seção 5 — Missões: entrar e sair

### 5.1 Senha errada — 401 Unauthorized

```
POST http://localhost:8080/missoes/{{missao_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "senha": "errada"
}
```

**Resposta esperada — 401 Unauthorized:**
```json
{
  "timestamp": "2026-06-01T14:00:00.000",
  "status": 401,
  "error": "Senha da missão incorreta",
  "path": "/missoes/1/entrar"
}
```

---

### 5.2 Membro entra com senha correta — 200 OK

```
POST http://localhost:8080/missoes/{{missao_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK:**
```json
{
  "id": 1,
  "nome": "Missao Alpha v2",
  "roleDoOperador": "MEMBRO",
  ...
}
```

---

### 5.3 Já é membro — 409 Conflict

```
POST http://localhost:8080/missoes/{{missao_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "senha": "senha123"
}
```

**Resposta esperada — 409 Conflict:**
```json
{
  "status": 409,
  "error": "Operador já é membro desta missão",
  ...
}
```

---

### 5.4 Supervisor entra — 200 OK

```
POST http://localhost:8080/missoes/{{missao_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK** com `roleDoOperador: "MEMBRO"` (todos entram como MEMBRO).

---

### 5.5 MEMBRO tenta atualizar missão — 403 Forbidden

```
PUT http://localhost:8080/missoes/{{missao_id}}
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "nome": "Invasao",
  "descricao": "x",
  "dataLancamento": "2026-07-01",
  "status": "ATIVA"
}
```

**Resposta esperada — 403 Forbidden**

---

### 5.6 MEMBRO tenta deletar missão — 403 Forbidden

```
DELETE http://localhost:8080/missoes/{{missao_id}}
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 403 Forbidden**

---

### 5.7 Criar missão solo para testar regra do DONO único

```
POST http://localhost:8080/missoes
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "Missao Solo",
  "descricao": "Sem outros donos",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "senha456"
}
```

**Resposta esperada — 201 Created**. Salvar `id` como `missao_solo_id`.

---

### 5.8 DONO único tenta sair — 400 Bad Request

```
POST http://localhost:8080/missoes/{{missao_solo_id}}/sair
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{}
```

**Resposta esperada — 400 Bad Request:**
```json
{
  "status": 400,
  "error": "Transfira a propriedade antes de sair da missão",
  ...
}
```

---

### 5.9 Supervisor entra na missão solo — 200 OK

```
POST http://localhost:8080/missoes/{{missao_solo_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "senha": "senha456"
}
```

**Resposta esperada — 200 OK**

---

### 5.10 Supervisor sai da missão solo — 204 No Content

```
POST http://localhost:8080/missoes/{{missao_solo_id}}/sair
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{}
```

**Resposta esperada — 204 No Content**

---

## Seção 6 — Missões: gerenciamento de membros

### 6.1 Forasteiro tenta listar membros — 403 Forbidden

```
GET http://localhost:8080/missoes/{{missao_id}}/membros
Authorization: Bearer {{token_forasteiro}}
```

**Resposta esperada — 403 Forbidden**

---

### 6.2 Membro lista membros — 200 OK com 3 membros

```
GET http://localhost:8080/missoes/{{missao_id}}/membros
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 200 OK:**
```json
[
  {
    "operadorId": 1,
    "nome": "Operador Dono",
    "login": "dono@sat.dev",
    "role": "DONO",
    "dataEntrada": "2026-06-01T10:00:00"
  },
  {
    "operadorId": 2,
    "nome": "Operador Membro",
    "login": "membro@sat.dev",
    "role": "MEMBRO",
    "dataEntrada": "2026-06-01T10:05:00"
  },
  {
    "operadorId": 3,
    "nome": "Operador Supervisor",
    "login": "supervisor@sat.dev",
    "role": "MEMBRO",
    "dataEntrada": "2026-06-01T10:10:00"
  }
]
```

> Anotar os `operadorId` de cada um para usar nos próximos testes. Salvar como `id_dono`, `id_membro`, `id_supervisor`.

---

### 6.3 DONO tenta se remover via DELETE /membros — 403 Forbidden

```
DELETE http://localhost:8080/missoes/{{missao_id}}/membros/{{id_dono}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 403 Forbidden** ("Use o endpoint /sair")

---

### 6.4 MEMBRO tenta promover — 403 Forbidden

```
PATCH http://localhost:8080/missoes/{{missao_id}}/membros/{{id_supervisor}}?novoRole=SUPERVISOR
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 403 Forbidden**

---

### 6.5 DONO promove supervisor para SUPERVISOR — 200 OK

```
PATCH http://localhost:8080/missoes/{{missao_id}}/membros/{{id_supervisor}}?novoRole=SUPERVISOR
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 200 OK:**
```json
{
  "operadorId": 3,
  "nome": "Operador Supervisor",
  "login": "supervisor@sat.dev",
  "role": "SUPERVISOR",
  "dataEntrada": "2026-06-01T10:10:00"
}
```

---

### 6.6 DONO rebaixa supervisor para MEMBRO — 200 OK

```
PATCH http://localhost:8080/missoes/{{missao_id}}/membros/{{id_supervisor}}?novoRole=MEMBRO
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 200 OK** com `role: "MEMBRO"`.

---

### 6.7 Re-promover supervisor para SUPERVISOR (necessário para as próximas seções)

```
PATCH http://localhost:8080/missoes/{{missao_id}}/membros/{{id_supervisor}}?novoRole=SUPERVISOR
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 200 OK** com `role: "SUPERVISOR"`.

---

### 6.8 DONO tenta alterar a própria role — 403 Forbidden

```
PATCH http://localhost:8080/missoes/{{missao_id}}/membros/{{id_dono}}?novoRole=MEMBRO
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 403 Forbidden**

---

### 6.9 MEMBRO tenta remover supervisor — 403 Forbidden

```
DELETE http://localhost:8080/missoes/{{missao_id}}/membros/{{id_supervisor}}
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 403 Forbidden**

---

### 6.10 DONO remove membro — 204 No Content

```
DELETE http://localhost:8080/missoes/{{missao_id}}/membros/{{id_membro}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 204 No Content**

---

### 6.11 Membro removido tenta sair — 404 Not Found

```
POST http://localhost:8080/missoes/{{missao_id}}/sair
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{}
```

**Resposta esperada — 404 Not Found** (vínculo não existe mais)

---

## Seção 7 — Satélites: CRUD e controle de acesso

> Antes de continuar: o membro foi removido na seção anterior. Ele deve re-entrar na missão para os testes de permissão de satélite.

### 7.1 Membro re-entra na missão — 200 OK

```
POST http://localhost:8080/missoes/{{missao_id}}/entrar
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "senha": "senha123"
}
```

**Resposta esperada — 200 OK**

---

### 7.2 Sem token tenta criar satélite — 403 Forbidden

```
POST http://localhost:8080/satelites
Content-Type: application/json
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "missaoId": 1,
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.5,
    "longitudeNodo": 12.3
  }
}
```

**Resposta esperada — 403 Forbidden**

---

### 7.3 MEMBRO tenta criar satélite — 403 Forbidden

```
POST http://localhost:8080/satelites
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.5,
    "longitudeNodo": 12.3
  }
}
```

**Resposta esperada — 403 Forbidden**

---

### 7.4 SUPERVISOR cria satélite — 201 Created

```
POST http://localhost:8080/satelites
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.5,
    "longitudeNodo": 12.3
  }
}
```

**Resposta esperada — 201 Created:**
```json
{
  "id": 1,
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "altitudeKm": 550.0,
  "inclinacao": 53.5,
  "longitudeNodo": 12.3,
  "missaoId": 1,
  "nomeMissao": "Missao Alpha v2",
  "totalSensores": 0,
  "_links": { ... }
}
```

> Salvar `id` como `sat_id`.

---

### 7.5 Nome duplicado na missão — 400 Bad Request

```
POST http://localhost:8080/satelites
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.5
  }
}
```

**Resposta esperada — 400 Bad Request**

---

### 7.6 missaoId inexistente — 404 Not Found

```
POST http://localhost:8080/satelites
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "SAT-X",
  "dataLancamento": "2026-01-01",
  "missaoId": 99999,
  "coordenadas": {
    "altitudeKm": 100.0,
    "inclinacao": 0.0
  }
}
```

**Resposta esperada — 404 Not Found**

---

### 7.7 GET público — lista todos os satélites

```
GET http://localhost:8080/satelites
```

**Resposta esperada — 200 OK** (paginado)

---

### 7.8 GET público — buscar satélite por id com HATEOAS

```
GET http://localhost:8080/satelites/{{sat_id}}
```

**Resposta esperada — 200 OK** com `_links` presente.

---

### 7.9 Satélite inexistente — 404

```
GET http://localhost:8080/satelites/99999
```

**Resposta esperada — 404 Not Found**

---

### 7.10 GET público — satélites da missão

```
GET http://localhost:8080/satelites/missao/{{missao_id}}
```

**Resposta esperada — 200 OK** com `totalElements >= 1`.

---

### 7.11 missaoId inexistente nos satélites da missão — 404

```
GET http://localhost:8080/satelites/missao/99999
```

**Resposta esperada — 404 Not Found**

---

### 7.12 Estatísticas sem leituras — totalLeituras=0

```
GET http://localhost:8080/satelites/{{sat_id}}/estatisticas
```

**Resposta esperada — 200 OK:**
```json
{
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "mediaValor": 0,
  "minValor": 0,
  "maxValor": 0,
  "totalLeituras": 0,
  "totalAlertas": 0,
  "totalCriticos": 0,
  "ultimaLeitura": null
}
```

---

### 7.13 SUPERVISOR atualiza satélite — 200 OK

```
PUT http://localhost:8080/satelites/{{sat_id}}
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-02-01",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 600.0,
    "inclinacao": 55.0
  }
}
```

**Resposta esperada — 200 OK** com `altitudeKm: 600.0`.

---

### 7.14 DONO atualiza satélite — 200 OK

```
PUT http://localhost:8080/satelites/{{sat_id}}
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-03-01",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 650.0,
    "inclinacao": 57.0
  }
}
```

**Resposta esperada — 200 OK** com `altitudeKm: 650.0`.

---

### 7.15 SUPERVISOR tenta deletar satélite — 403 Forbidden

```
DELETE http://localhost:8080/satelites/{{sat_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 403 Forbidden**

---

## Seção 8 — Sensores: criação dos 4 tipos

### 8.1 Sensor Térmico — 201 Created

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Termometro",
  "unidade": "graus_C",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 201 Created:**
```json
{
  "id": 1,
  "nome": "Termometro",
  "tipo": "TERMICO",
  "unidade": "graus_C",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "detalhe": "CELSIUS",
  "_links": { ... }
}
```

> Salvar `id` como `sensor_termico_id`.

---

### 8.2 Sensor de Pressão — 201 Created

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Barometro",
  "unidade": "hPa",
  "limiteMin": 950.0,
  "limiteMax": 1050.0,
  "margemAlerta": 5.0,
  "sateliteId": {{sat_id}},
  "tipo": "PRESSAO",
  "tipoPressao": "ABSOLUTA"
}
```

**Resposta esperada — 201 Created** com `detalhe: "ABSOLUTA"`. Salvar `id` como `sensor_pressao_id`.

---

### 8.3 Sensor de Radiação — 201 Created

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Geiger",
  "unidade": "Gy",
  "limiteMin": 0.0,
  "limiteMax": 5.0,
  "margemAlerta": 20.0,
  "sateliteId": {{sat_id}},
  "tipo": "RADIACAO",
  "tipoRadiacao": "IONIZANTE"
}
```

**Resposta esperada — 201 Created** com `detalhe: "IONIZANTE"`. Salvar `id` como `sensor_radiacao_id`.

---

### 8.4 Magnetômetro — 201 Created

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Magnetometro",
  "unidade": "nT",
  "limiteMin": -50000.0,
  "limiteMax": 50000.0,
  "margemAlerta": 15.0,
  "sateliteId": {{sat_id}},
  "tipo": "MAGNETOMETRO",
  "eixosMedicao": "XYZ"
}
```

**Resposta esperada — 201 Created** com `detalhe: "XYZ"`. Salvar `id` como `sensor_mag_id`.

---

## Seção 9 — Sensores: validações de regras de negócio

### 9.1 limiteMin >= limiteMax — 400 Bad Request

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Invalido",
  "unidade": "X",
  "limiteMin": 100.0,
  "limiteMax": 50.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 400 Bad Request**

---

### 9.2 Tipo de sensor inválido — 400 Bad Request

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Sonico",
  "unidade": "Hz",
  "limiteMin": 0.0,
  "limiteMax": 100.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "SONICO"
}
```

**Resposta esperada — 400 Bad Request:**
```json
{
  "status": 400,
  "error": "Corpo da requisição inválido ou mal formatado",
  ...
}
```

---

### 9.3 TERMICO sem unidadeEscala — 400 Bad Request

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "SemEscala",
  "unidade": "graus_C",
  "limiteMin": 0.0,
  "limiteMax": 100.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO"
}
```

**Resposta esperada — 400 Bad Request**

---

### 9.4 Nome duplicado no mesmo satélite — 400 Bad Request

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Termometro",
  "unidade": "K",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "KELVIN"
}
```

**Resposta esperada — 400 Bad Request** (nome "Termometro" já existe neste satélite)

---

### 9.5 sateliteId inexistente — 404 Not Found

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Orfao",
  "unidade": "X",
  "limiteMin": 0.0,
  "limiteMax": 10.0,
  "margemAlerta": 5.0,
  "sateliteId": 99999,
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 404 Not Found**

---

### 9.6 MEMBRO tenta criar sensor — 403 Forbidden

```
POST http://localhost:8080/sensores
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{
  "nome": "SensorMembro",
  "unidade": "X",
  "limiteMin": 0.0,
  "limiteMax": 100.0,
  "margemAlerta": 10.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 403 Forbidden**

---

### 9.7 GET público — listar todos os sensores

```
GET http://localhost:8080/sensores
```

**Resposta esperada — 200 OK**

---

### 9.8 GET público — sensor por id com HATEOAS

```
GET http://localhost:8080/sensores/{{sensor_termico_id}}
```

**Resposta esperada — 200 OK** com `_links` presente.

---

### 9.9 GET público — sensores do satélite

```
GET http://localhost:8080/sensores/satelite/{{sat_id}}
```

**Resposta esperada — 200 OK** com `totalElements: 4`.

---

### 9.10 sateliteId inexistente nos sensores — 404

```
GET http://localhost:8080/sensores/satelite/99999
```

**Resposta esperada — 404 Not Found**

---

### 9.11 SUPERVISOR atualiza sensor — 200 OK

```
PUT http://localhost:8080/sensores/{{sensor_termico_id}}
Content-Type: application/json
Authorization: Bearer {{token_supervisor}}
```

**Body:**
```json
{
  "nome": "Termometro",
  "unidade": "K",
  "limiteMin": -10.0,
  "limiteMax": 90.0,
  "margemAlerta": 5.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 200 OK:**
```json
{
  "limiteMin": -10.0,
  "detalhe": "CELSIUS",
  ...
}
```

> Nota: `detalhe` permanece `"CELSIUS"` — o tipo é imutável mesmo que você envie outro valor.

---

### 9.12 DONO atualiza sensor — 200 OK

```
PUT http://localhost:8080/sensores/{{sensor_termico_id}}
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "Termometro",
  "unidade": "graus_C",
  "limiteMin": -10.0,
  "limiteMax": 90.0,
  "margemAlerta": 5.0,
  "sateliteId": {{sat_id}},
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

**Resposta esperada — 200 OK** com `unidade: "graus_C"`.

---

## Seção 10 — Leituras: StatusCalculator

Referência do sensor térmico após o PUT acima:
- `limiteMin = -10`, `limiteMax = 90`, `margemAlerta = 5%`
- `zonaAlertaMin = -10 + (100 × 0.05) = -5.0`
- `zonaAlertaMax = 90 - (100 × 0.05) = 85.0`

```
|──CRITICO──|─ALERTA─|──────────NORMAL──────────|─ALERTA─|──CRITICO──|
-10         -5       85                           90
```

---

### 10.1 NORMAL — 201 Created

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 40.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created:**
```json
{
  "id": 1,
  "valor": 40.0,
  "dataHoraLeitura": "2026-06-01T14:32:07.412",
  "status": "NORMAL",
  "sensorId": 1,
  "nomeSensor": "Termometro",
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "_links": { ... }
}
```

> Salvar `id` como `leitura_normal_id`.

---

### 10.2 ALERTA superior (87.0 — entre zonaAlertaMax=85 e limiteMax=90)

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 87.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "ALERTA"`. Salvar `id` como `leitura_alerta_id`.

---

### 10.3 CRITICO acima do limiteMax (150.0)

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 150.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "CRITICO"`. Salvar `id` como `leitura_critico_id`.

---

### 10.4 CRITICO abaixo do limiteMin (-50.0)

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": -50.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "CRITICO"`.

---

### 10.5 ALERTA inferior (-8.0 — entre limiteMin=-10 e zonaAlertaMin=-5)

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": -8.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "ALERTA"`.

---

### 10.6 Fronteira: valor == limiteMin → ALERTA

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": -10.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "ALERTA"`.

> Por quê? `-10.0 == limiteMin`, então não é `< limiteMin` (CRITICO). Cai na condição `< zonaAlertaMin` (-10 < -5) → ALERTA.

---

### 10.7 Fronteira: valor == zonaAlertaMax → NORMAL

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 85.0,
  "sensorId": {{sensor_termico_id}}
}
```

**Resposta esperada — 201 Created** com `status: "NORMAL"`.

> Por quê? `85.0 == zonaAlertaMax`, então não é `> zonaAlertaMax` (ALERTA). Nenhuma condição bate → NORMAL.

---

### 10.8 sensorId inexistente — 404 Not Found

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 40.0,
  "sensorId": 99999
}
```

**Resposta esperada — 404 Not Found**

---

### 10.9 Campo sensorId ausente — 400 Bad Request

```
POST http://localhost:8080/leituras
Content-Type: application/json
```

**Body:**
```json
{
  "valor": 40.0
}
```

**Resposta esperada — 400 Bad Request:**
```json
{
  "status": 400,
  "error": "sensorId: não deve ser nulo",
  ...
}
```

---

## Seção 11 — Leituras: listagem e filtros

### 11.1 Listar todas as leituras (público)

```
GET http://localhost:8080/leituras
```

**Resposta esperada — 200 OK** com `totalElements >= 7`.

---

### 11.2 Buscar leitura por id

```
GET http://localhost:8080/leituras/{{leitura_normal_id}}
```

**Resposta esperada — 200 OK** com `_links.sensor` e `_links.satelite` presentes.

---

### 11.3 Leitura inexistente — 404

```
GET http://localhost:8080/leituras/99999
```

**Resposta esperada — 404 Not Found**

---

### 11.4 Todas as leituras do sensor

```
GET http://localhost:8080/leituras/sensor/{{sensor_termico_id}}
```

**Resposta esperada — 200 OK** com `totalElements >= 7`.

---

### 11.5 Filtro por CRITICO

```
GET http://localhost:8080/leituras/sensor/{{sensor_termico_id}}?status=CRITICO
```

**Resposta esperada — 200 OK** com `totalElements >= 2`.

---

### 11.6 Filtro por ALERTA

```
GET http://localhost:8080/leituras/sensor/{{sensor_termico_id}}?status=ALERTA
```

**Resposta esperada — 200 OK** com `totalElements >= 2`.

---

### 11.7 Filtro por NORMAL

```
GET http://localhost:8080/leituras/sensor/{{sensor_termico_id}}?status=NORMAL
```

**Resposta esperada — 200 OK** com `totalElements >= 1`.

---

### 11.8 sensorId inexistente nas leituras — 404

```
GET http://localhost:8080/leituras/sensor/99999
```

**Resposta esperada — 404 Not Found**

---

### 11.9 Sensor sem leituras — totalElements=0

```
GET http://localhost:8080/leituras/sensor/{{sensor_pressao_id}}
```

**Resposta esperada — 200 OK** com `totalElements: 0`.

---

### 11.10 Leituras por satélite

```
GET http://localhost:8080/leituras/satelite/{{sat_id}}
```

**Resposta esperada — 200 OK** com `totalElements >= 7`.

---

### 11.11 Filtro por status no satélite

```
GET http://localhost:8080/leituras/satelite/{{sat_id}}?status=CRITICO
```

**Resposta esperada — 200 OK**

---

### 11.12 sateliteId inexistente nas leituras — 404

```
GET http://localhost:8080/leituras/satelite/99999
```

**Resposta esperada — 404 Not Found**

---

## Seção 12 — Estatísticas após leituras

```
GET http://localhost:8080/satelites/{{sat_id}}/estatisticas
```

**Resposta esperada — 200 OK:**
```json
{
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "mediaValor": 42.0,
  "minValor": -50.0,
  "maxValor": 150.0,
  "totalLeituras": 7,
  "totalAlertas": 3,
  "totalCriticos": 2,
  "ultimaLeitura": "2026-06-01T14:32:07.412"
}
```

Verificar: `totalLeituras >= 7`, `totalCriticos >= 2`, `totalAlertas >= 2`, `ultimaLeitura` não nula, `mediaValor` calculado.

---

## Seção 13 — Leituras: exclusão com controle de acesso

### 13.1 Sem token — 403 Forbidden

```
DELETE http://localhost:8080/leituras/{{leitura_normal_id}}
```

**Resposta esperada — 403 Forbidden**

---

### 13.2 Forasteiro (não membro) — 403 Forbidden

```
DELETE http://localhost:8080/leituras/{{leitura_normal_id}}
Authorization: Bearer {{token_forasteiro}}
```

**Resposta esperada — 403 Forbidden**

---

### 13.3 MEMBRO da missão — 403 Forbidden

```
DELETE http://localhost:8080/leituras/{{leitura_normal_id}}
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 403 Forbidden**

---

### 13.4 SUPERVISOR deleta leitura — 204 No Content

```
DELETE http://localhost:8080/leituras/{{leitura_alerta_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 204 No Content**

---

### 13.5 Leitura já deletada — 404 Not Found

```
DELETE http://localhost:8080/leituras/{{leitura_alerta_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 404 Not Found**

---

### 13.6 DONO deleta leitura — 204 No Content

```
DELETE http://localhost:8080/leituras/{{leitura_critico_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 204 No Content**

---

## Seção 14 — Sensores: exclusão (apenas DONO)

### 14.1 SUPERVISOR tenta deletar sensor — 403 Forbidden

```
DELETE http://localhost:8080/sensores/{{sensor_mag_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 403 Forbidden**

---

### 14.2 DONO deleta sensor — 204 No Content

```
DELETE http://localhost:8080/sensores/{{sensor_radiacao_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 204 No Content**

---

### 14.3 Sensor deletado retorna 404

```
GET http://localhost:8080/sensores/{{sensor_radiacao_id}}
```

**Resposta esperada — 404 Not Found**

---

### 14.4 Satélite agora tem 3 sensores

```
GET http://localhost:8080/sensores/satelite/{{sat_id}}
```

**Resposta esperada — 200 OK** com `totalElements: 3`.

---

## Seção 15 — Satélites: exclusão em cascata

### 15.1 Criar satélite descartável

```
POST http://localhost:8080/satelites
Content-Type: application/json
Authorization: Bearer {{token_dono}}
```

**Body:**
```json
{
  "nome": "SAT-DESCARTAVEL",
  "dataLancamento": "2026-01-01",
  "missaoId": {{missao_id}},
  "coordenadas": {
    "altitudeKm": 200.0,
    "inclinacao": 0.0
  }
}
```

**Resposta esperada — 201 Created**. Salvar `id` como `sat_descartavel_id`.

---

### 15.2 SUPERVISOR tenta deletar — 403 Forbidden

```
DELETE http://localhost:8080/satelites/{{sat_descartavel_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 403 Forbidden**

---

### 15.3 DONO deleta satélite — 204 No Content

```
DELETE http://localhost:8080/satelites/{{sat_descartavel_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 204 No Content**

---

### 15.4 Satélite deletado retorna 404

```
GET http://localhost:8080/satelites/{{sat_descartavel_id}}
```

**Resposta esperada — 404 Not Found**

---

## Seção 16 — Missões: exclusão e saída voluntária

### 16.1 SUPERVISOR tenta deletar missão — 403 Forbidden

```
DELETE http://localhost:8080/missoes/{{missao_id}}
Authorization: Bearer {{token_supervisor}}
```

**Resposta esperada — 403 Forbidden**

---

### 16.2 MEMBRO tenta deletar missão — 403 Forbidden

```
DELETE http://localhost:8080/missoes/{{missao_id}}
Authorization: Bearer {{token_membro}}
```

**Resposta esperada — 403 Forbidden**

---

### 16.3 MEMBRO sai voluntariamente — 204 No Content

```
POST http://localhost:8080/missoes/{{missao_id}}/sair
Content-Type: application/json
Authorization: Bearer {{token_membro}}
```

**Body:**
```json
{}
```

**Resposta esperada — 204 No Content**

---

### 16.4 DONO deleta missão Solo — 204 No Content

```
DELETE http://localhost:8080/missoes/{{missao_solo_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 204 No Content**

---

### 16.5 Missão deletada retorna 404

```
GET http://localhost:8080/missoes/{{missao_solo_id}}
Authorization: Bearer {{token_dono}}
```

**Resposta esperada — 404 Not Found**

---

## Resumo dos cenários

### StatusCalculator — sensor térmico (limiteMin=-10, limiteMax=90, margemAlerta=5%)

| Valor | Status esperado | Motivo |
|------:|:---------------:|--------|
| 40.0 | NORMAL | Dentro de [-5, 85] |
| 87.0 | ALERTA | Entre zonaAlertaMax=85 e limiteMax=90 |
| 150.0 | CRITICO | > limiteMax=90 |
| -50.0 | CRITICO | < limiteMin=-10 |
| -8.0 | ALERTA | Entre limiteMin=-10 e zonaAlertaMin=-5 |
| -10.0 | ALERTA | == limiteMin → não é `<` → cai em `< zonaAlertaMin` |
| 85.0 | NORMAL | == zonaAlertaMax → não é `>` → nenhuma condição bate |

### Controle de acesso por role

| Operação | Sem token | MEMBRO | SUPERVISOR | DONO |
|----------|:---------:|:------:|:----------:|:----:|
| Criar satélite | 403 | 403 | 201 | 201 |
| Editar satélite | 403 | 403 | 200 | 200 |
| Deletar satélite | 403 | 403 | 403 | 204 |
| Criar sensor | 403 | 403 | 201 | 201 |
| Editar sensor | 403 | 403 | 200 | 200 |
| Deletar sensor | 403 | 403 | 403 | 204 |
| Deletar leitura | 403 | 403 | 204 | 204 |
| Editar missão | 403 | 403 | 403 | 200 |
| Deletar missão | 403 | 403 | 403 | 204 |
