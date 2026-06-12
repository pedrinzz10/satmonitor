# Alerta — Alertas automáticos de leituras críticas

## Índice

1. [O que é um alerta](#o-que-é-um-alerta)
2. [Como é gerado](#como-é-gerado)
3. [Ciclo de vida](#ciclo-de-vida)
4. [Listar e buscar alertas](#listar-e-buscar-alertas)
5. [Atualizar status (reconhecer/resolver)](#atualizar-status-reconhecerresolver)
6. [Links HATEOAS](#links-hateoas)
7. [Observações de persistência](#observações-de-persistência)
8. [Erros](#erros)

---

## O que é um alerta

Um **alerta** é criado automaticamente pela API sempre que uma leitura recebe status `ALERTA` ou `CRITICO`. Ele representa um evento anômalo que precisa ser reconhecido e resolvido pelo time de operações.

```
POST /leituras { valor: 150.0, sensorId: 1 }  (público — sem token)
    ↓
LeituraService.criar() — 1 transação:
    ├─ StatusCalculator → CRITICO
    ├─ LeituraSensor salva com status=CRITICO
    └─ Alerta criado automaticamente (statusAlerta=ATIVO)
    ↓
TB_ALERTA persiste (statusAlerta=ATIVO)
    ↓
Mobile / Operador vê o alerta via GET /alertas ou GET /missoes/{id}/alertas
```

> **Todos os endpoints de alerta exigem token JWT** — GETs incluídos. O `GET /alertas` retorna apenas os alertas das missões às quais o operador logado pertence.

---

## Como é gerado

O `LeituraService` verifica o status calculado após cada `POST /leituras`. Se for `ALERTA` ou `CRITICO`, um `Alerta` é criado automaticamente na mesma transação:

```java
alertaRepository.save(Alerta.builder()
    .leitura(leitura)
    .tipoAlerta(status.name())  // "ALERTA" ou "CRITICO"
    .descricao("Sensor 'X': valor Y fora dos limites [min, max]")
    .build());
// statusAlerta = ATIVO  (@Builder.Default na entity Alerta)
// dataAlerta   = now()  (@Builder.Default na entity Alerta)
```

Leituras `NORMAL` **não geram alerta**.

---

## Ciclo de vida

```
ATIVO  →  RECONHECIDO  →  RESOLVIDO
```

| Status | Significado |
|--------|-------------|
| `ATIVO` | Alerta recém-gerado, ainda não tratado pelo time |
| `RECONHECIDO` | Time de operações está ciente e investigando |
| `RESOLVIDO` | Problema corrigido ou descartado |

A transição é feita via `PATCH`. Não há validação de ordem — qualquer transição é aceita (`RESOLVIDO → ATIVO`, `ATIVO → RESOLVIDO`, etc.).

---

## Listar e buscar alertas

Todos os GETs **exigem token JWT**.

### Listar alertas das minhas missões

```bash
# Todos os alertas das missões do operador logado
curl -s http://localhost:8080/alertas \
  -H "Authorization: Bearer SEU_TOKEN"

# Filtrar por status
curl -s "http://localhost:8080/alertas?status=ATIVO" \
  -H "Authorization: Bearer SEU_TOKEN"
```

> O `GET /alertas` **filtra pelos alertas das missões que o operador pertence** — não retorna alertas de todas as missões do sistema. A query no `AlertaRepository` faz JOIN com `TB_OPERADOR_MISSAO` para garantir isso.

### Buscar por id

```bash
curl -s http://localhost:8080/alertas/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

Verifica que o operador é membro (`MEMBRO` ou acima) da missão do alerta. Retorna 403 se não for membro.

### Alertas de uma missão específica

Acessado via `MissaoController` (não `AlertaController`):

```bash
# Todos os alertas da missão 1
curl -s http://localhost:8080/missoes/1/alertas \
  -H "Authorization: Bearer SEU_TOKEN"

# Filtrar por status
curl -s "http://localhost:8080/missoes/1/alertas?status=ATIVO" \
  -H "Authorization: Bearer SEU_TOKEN"
```

Exige ser membro da missão (qualquer role).

### Alertas de um satélite

```bash
curl -s http://localhost:8080/alertas/satelite/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna 404 se o satélite não existir. Não verifica membership — lista todos os alertas do satélite para qualquer operador autenticado.

**Resposta — 200 OK:**
```json
{
  "content": [
    {
      "id": 1,
      "leituraId": 42,
      "valorLeitura": 150.0,
      "sensorId": 1,
      "nomeSensor": "Termometro Principal",
      "sateliteId": 1,
      "nomeSatelite": "SAT-01",
      "missaoId": 1,
      "nomeMissao": "Missao Alpha",
      "tipoAlerta": "CRITICO",
      "descricao": "Sensor 'Termometro Principal': valor 150.0 fora dos limites [-10.0, 90.0]",
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

---

## Atualizar status (reconhecer/resolver)

Exige autenticação **e role SUPERVISOR ou DONO** na missão do alerta. A verificação percorre `Alerta → Leitura → Sensor → Satelite → Missao` para obter o `missaoId`.

### Via AlertaController

```bash
# Reconhecer alerta
curl -s -X PATCH "http://localhost:8080/alertas/1?novoStatus=RECONHECIDO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"

# Resolver alerta
curl -s -X PATCH "http://localhost:8080/alertas/1?novoStatus=RESOLVIDO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
```

### Via MissaoController (vinculado à missão)

```bash
curl -s -X PATCH "http://localhost:8080/missoes/1/alertas/7?novoStatus=RECONHECIDO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
```

**Ambos os endpoints chamam o mesmo `AlertaService.atualizarStatus`.**

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

> **MEMBRO não pode atualizar alertas** — exige SUPERVISOR ou DONO. Retorna 403 se a role for insuficiente.

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/alertas/{id}` |
| `atualizar-status` | PATCH | `/alertas/{id}` |

---

## Observações de persistência

O `TB_ALERTA` é gerenciado inteiramente pelo Hibernate. A API Java insere registros automaticamente via `LeituraService`:

| Responsabilidade | Quem |
|-----------------|------|
| Criar linha em `TB_ALERTA` | API Java (automático via `LeituraService`) |
| Deletar alerta ao deletar leitura | API Java — `alertaRepository.deleteByLeituraId(id)` antes do `leituraRepository.delete(leitura)` |
| Atualizar status | API Java — endpoint `PATCH /alertas/{id}` |
| Exibir alerta no app | Mobile — consulta `GET /alertas` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 401 | Token ausente em qualquer endpoint (incluindo GETs) |
| 403 | MEMBRO da missão tentando atualizar status (exige SUPERVISOR) |
| 403 | Operador não é membro da missão do alerta |
| 404 | Alerta não encontrado pelo id |
| 404 | Satélite não encontrado em `GET /alertas/satelite/{id}` |
