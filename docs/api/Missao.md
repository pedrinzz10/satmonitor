# Missao — Missões espaciais

## Índice

1. [O que é uma missão](#o-que-é-uma-missão)
2. [Roles e permissões](#roles-e-permissões)
3. [Criar missão](#criar-missão)
4. [Listar e buscar missões](#listar-e-buscar-missões)
5. [Editar e excluir missão](#editar-e-excluir-missão)
6. [Solicitar entrada (fluxo de aprovação)](#solicitar-entrada-fluxo-de-aprovação)
7. [Aprovar ou rejeitar solicitações](#aprovar-ou-rejeitar-solicitações)
8. [Sair da missão](#sair-da-missão)
9. [Gerenciar membros](#gerenciar-membros)
10. [Links HATEOAS](#links-hateoas)
11. [Erros](#erros)

---

## O que é uma missão

Uma **missão** agrupa satélites e operadores em torno de um objetivo espacial. O criador vira **DONO** automaticamente. Para outros operadores entrarem, existem dois modelos:

```
Criador cria missão  →  vira DONO automaticamente
Outros operadores    →  POST /solicitar (com senha)
                          ↓
                     SolicitacaoEntrada criada (PENDENTE)
                          ↓
                     SUPERVISOR/DONO aprova ou rejeita
                          ↓
                     Aprovado → vira MEMBRO
```

### Campo `permitirCowork`

| `permitirCowork` | Comportamento |
|:---:|---|
| `false` (padrão) | Só operadores da **mesma agência** da missão podem solicitar entrada |
| `true` | Qualquer operador autenticado pode solicitar entrada |

---

## Roles e permissões

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão e membros | ✓ | ✓ | ✓ |
| Ver / registrar leituras | ✓ | ✓ | ✓ |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Aprovar / rejeitar solicitações | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir missão / satélite / sensor | ✓ | — | — |
| Remover e promover membros | ✓ | — | — |

`DONO=0`, `SUPERVISOR=1`, `MEMBRO=2`. Menor ordinal = mais permissão.

---

## Criar missão

```bash
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{
    "nome": "Missao Alpha",
    "descricao": "Missão de monitoramento orbital 2026",
    "dataLancamento": "2026-06-01",
    "status": "PLANEJADA",
    "senhaMissao": "acesso123",
    "agenciaId": 1,
    "objetivo": "Monitoramento de órbita baixa",
    "dataFimPrevista": "2027-12-31",
    "permitirCowork": false
  }'
```

**Resposta — 201 Created:**
```json
{
  "id": 1,
  "nome": "Missao Alpha",
  "descricao": "Missão de monitoramento orbital 2026",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "roleDoOperador": "DONO",
  "totalMembros": 1,
  "totalSatelites": 0,
  "agenciaId": 1,
  "nomeAgencia": "NASA",
  "objetivo": "Monitoramento de órbita baixa",
  "dataFimPrevista": "2027-12-31",
  "permitirCowork": false,
  "_links": {
    "self": { "href": "http://localhost:8080/missoes/1" },
    "membros": { "href": "http://localhost:8080/missoes/1/membros" },
    "sair": { "href": "http://localhost:8080/missoes/1/sair" },
    "atualizar": { "href": "http://localhost:8080/missoes/1" },
    "deletar": { "href": "http://localhost:8080/missoes/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `nome` | String | Sim | Nome da missão |
| `descricao` | String | Não | Descrição detalhada |
| `dataLancamento` | LocalDate (`yyyy-MM-dd`) | Sim | Data de lançamento |
| `status` | Enum | Sim | `PLANEJADA`, `ATIVA` ou `ENCERRADA` |
| `senhaMissao` | String | Sim (mín. 6 chars) | Senha para solicitação de entrada |
| `agenciaId` | Long | Não | ID da agência responsável |
| `objetivo` | String | Não | Objetivo da missão |
| `dataFimPrevista` | LocalDate (`yyyy-MM-dd`) | Não | Data prevista de encerramento |
| `permitirCowork` | Boolean | Não | `false` por padrão — só agência da missão; `true` = qualquer agência |

> A `senhaMissao` é salva com BCrypt e **nunca aparece em nenhuma resposta**.

---

## Listar e buscar missões

### Listar minhas missões

```bash
curl -s http://localhost:8080/missoes \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna apenas missões onde **você é membro**. Paginada.

### Buscar por id

```bash
curl -s http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna 403 se você não for membro.

### Busca pública por nome

```bash
# Permite descobrir missões antes de solicitar entrada
curl -s "http://localhost:8080/missoes/buscar?nome=Alpha"
```

Não exige token — útil para que operadores descubram missões antes de solicitar entrada.

---

## Editar e excluir missão

### Editar (apenas DONO)

```bash
curl -s -X PUT http://localhost:8080/missoes/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_DONO" \
  -d '{
    "nome": "Missao Alpha v2",
    "descricao": "Descrição atualizada",
    "dataLancamento": "2026-07-01",
    "status": "ATIVA",
    "agenciaId": 2,
    "objetivo": "Objetivo atualizado",
    "dataFimPrevista": "2028-06-01",
    "permitirCowork": true
  }'
```

> `PUT` não aceita `senhaMissao` — a senha não é alterável por este endpoint.

### Excluir (apenas DONO)

```bash
curl -s -X DELETE http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

Todos os vínculos de membros são removidos em cascata.

---

## Solicitar entrada (fluxo de aprovação)

### 1. Solicitar entrada

O operador envia a senha da missão para criar uma solicitação pendente:

```bash
curl -s -X POST http://localhost:8080/missoes/1/solicitar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"senha": "acesso123"}'
```

**Resposta — 200 OK:**
```json
{
  "id": 5,
  "operadorId": 10,
  "nomeOperador": "Bruno Lima",
  "agenciaId": 2,
  "nomeAgencia": "ESA",
  "status": "PENDENTE",
  "dataSolicitacao": "2026-06-06T14:00:00"
}
```

**Regras de validação:**

| Situação | HTTP | Erro |
|----------|:----:|------|
| Senha correta, operador de agência compatível | 200 | — |
| Senha incorreta | 401 | `"Senha da missão incorreta"` |
| Operador sem agência cadastrada | 400 | `"Operador não está vinculado a nenhuma agência"` |
| Missão sem cowork + agência diferente | 403 | `"Esta missão não permite operadores de outras agências (cowork desabilitado)"` |
| Já é membro ativo | 409 | `"Operador já é membro desta missão"` |
| Já tem solicitação PENDENTE | 409 | `"Já existe uma solicitação pendente para esta missão"` |
| Missão não existe | 404 | — |

### 2. Listar solicitações (SUPERVISOR ou DONO)

```bash
# Listar todas as solicitações pendentes
curl -s "http://localhost:8080/missoes/1/solicitacoes?status=PENDENTE" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"

# Listar aprovadas
curl -s "http://localhost:8080/missoes/1/solicitacoes?status=APROVADO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
```

Retorna 403 se o operador for MEMBRO (exige SUPERVISOR ou DONO).

---

## Aprovar ou rejeitar solicitações

Exige **SUPERVISOR ou DONO** na missão.

### Aprovar

```bash
curl -s -X PATCH http://localhost:8080/missoes/1/solicitacoes/5/aprovar \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 204 No Content
```

Ao aprovar: cria `OperadorMissao` com `role=MEMBRO` e muda o status para `APROVADO`.

### Rejeitar

```bash
curl -s -X PATCH http://localhost:8080/missoes/1/solicitacoes/5/rejeitar \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 204 No Content
```

**Regras:**

| Situação | HTTP | Erro |
|----------|:----:|------|
| Solicitação já foi respondida (APROVADO ou REJEITADO) | 400 | `"Solicitação já foi respondida"` |
| Solicitação não pertence a esta missão | 404 | — |
| Operador é MEMBRO | 403 | `"Role mínima exigida: SUPERVISOR"` |

---

## Sair da missão

```bash
curl -s -X POST http://localhost:8080/missoes/1/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{}'
# → 204 No Content
```

**Regra do DONO único:** se você for o único DONO, a API bloqueia com HTTP 400. Promova outro membro para DONO antes de sair.

---

## Gerenciar membros

### Listar membros

```bash
curl -s http://localhost:8080/missoes/1/membros \
  -H "Authorization: Bearer SEU_TOKEN"
```

Qualquer membro (DONO, SUPERVISOR ou MEMBRO) pode listar.

**Resposta — 200 OK:**
```json
[
  {
    "operadorId": 1,
    "nome": "Ana Souza",
    "login": "ana.souza@sat.dev",
    "role": "DONO",
    "dataEntrada": "2026-06-01T10:00:00",
    "_links": {
      "remover": { "href": "http://localhost:8080/missoes/1/membros/1" },
      "promover": { "href": "http://localhost:8080/missoes/1/membros/1" }
    }
  }
]
```

### Promover ou rebaixar membro (apenas DONO)

```bash
# Promover para SUPERVISOR
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"

# Rebaixar para MEMBRO
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=MEMBRO" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
```

> O DONO não pode alterar a própria role nem se remover via este endpoint — para sair, use `POST /missoes/{id}/sair`.

### Remover membro (apenas DONO)

```bash
curl -s -X DELETE http://localhost:8080/missoes/1/membros/2 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/missoes/{id}` |
| `membros` | GET | `/missoes/{id}/membros` |
| `sair` | POST | `/missoes/{id}/sair` |
| `atualizar` | PUT | `/missoes/{id}` |
| `deletar` | DELETE | `/missoes/{id}` |

---

## Erros

| Exceção | HTTP | Quando ocorre |
|---------|:----:|--------------|
| `EntityNotFoundException` | 404 | Missão não encontrada |
| `EntityNotFoundException` | 404 | Operador não é membro (em operações de escrita via `verificarRole`) |
| `AcessoNegadoException` | 403 | Sem role suficiente |
| `AcessoNegadoException` | 403 | Não é membro e tenta ver detalhes (`buscarPorId`) |
| `AcessoNegadoException` | 403 | DONO tentando se remover via DELETE /membros |
| `AcessoNegadoException` | 403 | DONO tentando alterar a própria role |
| `AcessoNegadoException` | 403 | Missão sem cowork, agência incompatível |
| `SenhaMissaoInvalidaException` | 401 | Senha incorreta ao solicitar entrada |
| `OperadorJaMembroException` | 409 | Já é membro ou já tem solicitação pendente |
| `DonoUnicoException` | 400 | Único DONO tentando sair sem transferir |
