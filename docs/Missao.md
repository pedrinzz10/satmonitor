# Missao — Missões espaciais

## Índice

1. [O que é uma missão](#o-que-é-uma-missão)
2. [Roles e permissões](#roles-e-permissões)
3. [Criar missão](#criar-missão)
4. [Listar e buscar missões](#listar-e-buscar-missões)
5. [Editar e excluir missão](#editar-e-excluir-missão)
6. [Entrar e sair da missão](#entrar-e-sair-da-missão)
7. [Gerenciar membros](#gerenciar-membros)
8. [Links HATEOAS](#links-hateoas)
9. [Erros](#erros)

---

## O que é uma missão

Uma **missão** agrupa satélites e operadores em torno de um objetivo espacial. Para entrar numa missão, qualquer operador autenticado apresenta a **senha da missão**. O criador começa como **DONO**; quem entra por senha começa como **MEMBRO**.

```
Criador cria missão   →  vira DONO automaticamente
Outros operadores     →  entram com a senha da missão  →  começam como MEMBRO
DONO pode promover    →  MEMBRO → SUPERVISOR → DONO
```

---

## Roles e permissões

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão e membros | ✓ | ✓ | ✓ |
| Ver / registrar leituras | ✓ | ✓ | ✓ |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir missão / satélite / sensor | ✓ | — | — |
| Remover e promover membros | ✓ | — | — |

**Como funciona a comparação de roles:** `DONO=0`, `SUPERVISOR=1`, `MEMBRO=2`. Menor ordinal = mais permissão. `temPermissao(SUPERVISOR)` aceita DONO e SUPERVISOR, mas não MEMBRO.

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
    "dataFimPrevista": "2027-12-31"
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
| `senhaMissao` | String | Sim (mín. 6 chars) | Senha para outros operadores entrarem |
| `agenciaId` | Long | Não | ID da agência responsável — 404 se não existir |
| `objetivo` | String | Não | Objetivo da missão |
| `dataFimPrevista` | LocalDate (`yyyy-MM-dd`) | Não | Data prevista de encerramento |

> A `senhaMissao` é salva com BCrypt e **nunca aparece em nenhuma resposta**.  
> Campos opcionais (`agenciaId`, `objetivo`, `dataFimPrevista`) podem ser omitidos — vêm como `null` na resposta.

---

## Listar e buscar missões

### Listar todas as minhas missões

```bash
curl -s http://localhost:8080/missoes \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna apenas missões onde **você é membro** (operador logado). A listagem é paginada.

**Resposta — 200 OK:**
```json
{
  "content": [
    {
      "id": 1,
      "nome": "Missao Alpha",
      "status": "PLANEJADA",
      "roleDoOperador": "DONO",
      "totalMembros": 3,
      "totalSatelites": 2,
      "_links": { ... }
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Buscar missão por id

```bash
curl -s http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna 403 se você não for membro da missão.

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
    "dataFimPrevista": "2028-06-01"
  }'
```

> O `PUT` não aceita `senhaMissao` — a senha não é alterável por este endpoint.  
> Os campos opcionais podem ser enviados, removidos (enviando `null`) ou simplesmente omitidos.

### Excluir (apenas DONO)

```bash
curl -s -X DELETE http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

Ao deletar a missão, todos os vínculos de membros são removidos em cascata.

---

## Entrar e sair da missão

### Entrar com senha

Qualquer operador autenticado pode entrar apresentando a senha correta:

```bash
curl -s -X POST http://localhost:8080/missoes/1/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"senha": "acesso123"}'
```

**Resposta — 200 OK:**
```json
{
  "id": 1,
  "nome": "Missao Alpha",
  "roleDoOperador": "MEMBRO",
  ...
}
```

| Situação | HTTP | Erro |
|----------|:----:|------|
| Senha correta, operador novo | 200 | — |
| Senha errada | 401 | `"Senha da missão incorreta"` |
| Já é membro | 409 | `"Operador já é membro desta missão"` |
| Missão não existe | 404 | `"Missão não encontrada..."` |

### Sair da missão

```bash
curl -s -X POST http://localhost:8080/missoes/1/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{}'
# → 204 No Content
```

**Regra do DONO único:** se você for o único DONO, a API bloqueia a saída com HTTP 400. Promova outro membro para DONO antes de sair.

---

## Gerenciar membros

### Listar membros

```bash
curl -s http://localhost:8080/missoes/1/membros \
  -H "Authorization: Bearer SEU_TOKEN"
```

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
  },
  {
    "operadorId": 2,
    "nome": "Bruno Lima",
    "login": "bruno@sat.dev",
    "role": "MEMBRO",
    "dataEntrada": "2026-06-02T09:00:00",
    ...
  }
]
```

### Promover ou rebaixar membro (apenas DONO)

```bash
# Promover para SUPERVISOR
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"

# Promover para DONO
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=DONO" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"

# Rebaixar para MEMBRO
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=MEMBRO" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
```

**Resposta — 200 OK:**
```json
{
  "operadorId": 2,
  "nome": "Bruno Lima",
  "login": "bruno@sat.dev",
  "role": "SUPERVISOR",
  "dataEntrada": "2026-06-02T09:00:00"
}
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

Todos os responses de missão incluem `_links` com navegação pronta:

| Link | Método | Disponível para |
|------|:------:|----------------|
| `self` | GET | Todos |
| `membros` | GET | Todos |
| `sair` | POST | Todos |
| `atualizar` | PUT | Apenas DONO |
| `deletar` | DELETE | Apenas DONO |

Cada `MembroResponse` na listagem de membros inclui links `remover` e `promover` com a URL específica daquele membro.

---

## Erros

| Exceção | HTTP | Quando ocorre |
|---------|:----:|--------------|
| `EntityNotFoundException` | 404 | Missão não encontrada |
| `EntityNotFoundException` | 404 | Operador não é membro (em operações de escrita) |
| `AcessoNegadoException` | 403 | Sem role suficiente (ex: MEMBRO tentando editar) |
| `AcessoNegadoException` | 403 | Não é membro e tenta ver detalhes |
| `AcessoNegadoException` | 403 | DONO tentando se remover via DELETE /membros |
| `AcessoNegadoException` | 403 | DONO tentando alterar a própria role |
| `SenhaMissaoInvalidaException` | 401 | Senha incorreta ao entrar |
| `OperadorJaMembroException` | 409 | Já é membro e tenta entrar novamente |
| `DonoUnicoException` | 400 | Único DONO tentando sair sem transferir |
