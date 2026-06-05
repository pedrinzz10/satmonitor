# Alerta — Alertas automáticos de leituras críticas

## Índice

1. [O que é um alerta](#o-que-é-um-alerta)
2. [Como é gerado](#como-é-gerado)
3. [Ciclo de vida](#ciclo-de-vida)
4. [Listar e buscar alertas](#listar-e-buscar-alertas)
5. [Atualizar status (reconhecer/resolver)](#atualizar-status-reconhecerresolver)
6. [Links HATEOAS](#links-hateoas)
7. [Integração com Oracle PL/SQL](#integração-com-oracle-plsql)
8. [Erros](#erros)

---

## O que é um alerta

Um **alerta** é criado automaticamente pela API sempre que uma leitura recebe status `ALERTA` ou `CRITICO`. Ele representa um evento anômalo que precisa ser reconhecido e resolvido pelo time de operações.

```
POST /leituras { valor: 150.0, sensorId: 1 }
    ↓
StatusCalculator → CRITICO
    ↓
LeituraSensor salva com status=CRITICO
    ↓
Alerta criado automaticamente (statusAlerta=ATIVO)
    ↓
TB_ALERTA → trigger Oracle do Henrique processa
    ↓
Mobile exibe alerta em vermelho
```

**Endpoints GET são públicos** — sem token. `PATCH` exige autenticação.

---

## Como é gerado

O `LeituraService` verifica o status calculado após cada `POST /leituras`. Se o status for `ALERTA` ou `CRITICO`, um `Alerta` é criado automaticamente na mesma transação:

- `tipoAlerta` = nome do status (`"ALERTA"` ou `"CRITICO"`)
- `descricao` = mensagem automática com nome do sensor, valor e limites
- `dataAlerta` = instante do registro (servidor)
- `statusAlerta` = `ATIVO`

Leituras `NORMAL` **não geram alerta**.

---

## Ciclo de vida

```
ATIVO  →  RECONHECIDO  →  RESOLVIDO
```

| Status | Significado |
|--------|-------------|
| `ATIVO` | Alerta recém-gerado, ainda não tratado |
| `RECONHECIDO` | Time de operações está ciente |
| `RESOLVIDO` | Problema corrigido |

A transição é feita via `PATCH /alertas/{id}?novoStatus=X`. Não há validação de ordem — qualquer transição é aceita.

---

## Listar e buscar alertas

### Listar todos (sem filtro)

```
GET http://localhost:8080/alertas
```

### Filtrar por status

```
GET http://localhost:8080/alertas?status=ATIVO
GET http://localhost:8080/alertas?status=RECONHECIDO
GET http://localhost:8080/alertas?status=RESOLVIDO
```

**Resposta — 200 OK:**
```json
{
  "content": [
    {
      "id": 1,
      "leituraId": 42,
      "valorLeitura": 150.0,
      "nomeSensor": "Termometro",
      "nomeSatelite": "SAT-01",
      "tipoAlerta": "CRITICO",
      "descricao": "Sensor 'Termometro': valor 150.0 fora dos limites [-10.0, 90.0]",
      "dataAlerta": "2026-06-04T21:15:03.412",
      "statusAlerta": "ATIVO",
      "_links": {
        "self":             { "href": "http://localhost:8080/alertas/1" },
        "atualizar-status": { "href": "http://localhost:8080/alertas/1" }
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Buscar por id

```
GET http://localhost:8080/alertas/1
```

### Listar alertas de um satélite

```
GET http://localhost:8080/alertas/satelite/1
```

Retorna 404 se o satélite não existir.

---

## Atualizar status (reconhecer/resolver)

Exige autenticação (qualquer token válido).

### Reconhecer alerta

```
PATCH http://localhost:8080/alertas/1?novoStatus=RECONHECIDO
Authorization: Bearer {{token}}
```

### Resolver alerta

```
PATCH http://localhost:8080/alertas/1?novoStatus=RESOLVIDO
Authorization: Bearer {{token}}
```

**Resposta — 200 OK:**
```json
{
  "id": 1,
  "tipoAlerta": "CRITICO",
  "statusAlerta": "RECONHECIDO",
  "dataAlerta": "2026-06-04T21:15:03.412",
  ...
}
```

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/alertas/{id}` |
| `atualizar-status` | PATCH | `/alertas/{id}` |

---

## Integração com Oracle PL/SQL

O `TB_ALERTA` é o ponto de integração com o schema do Henrique (PL/SQL). A API Java insere registros em `TB_ALERTA` e o Oracle responde via trigger:

| Responsabilidade | Quem |
|-----------------|------|
| Criar linha em `TB_ALERTA` | API Java (automático via `LeituraService`) |
| Processar o alerta (trigger, auditoria) | Oracle PL/SQL (Henrique) |
| Exibir alerta no app | Mobile (Fabrício) — consulta `GET /alertas` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 401 | Token ausente no PATCH |
| 404 | Alerta não encontrado pelo id |
| 404 | Satélite não encontrado em `GET /alertas/satelite/{id}` |
