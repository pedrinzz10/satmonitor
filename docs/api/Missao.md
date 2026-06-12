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
10. [Alertas da missão](#alertas-da-missão)
11. [Links HATEOAS](#links-hateoas)
12. [Erros](#erros)

---

## O que é uma missão

Uma **missão** agrupa satélites e operadores em torno de um objetivo espacial. O criador vira **DONO** automaticamente. Para outros operadores entrarem, o fluxo é:

```
Criador cria missão  →  vira DONO automaticamente
                         (2 saves em 1 transação: Missao + OperadorMissao{role=DONO})

Outro operador descobre a missão
  ↓  GET /missoes/buscar?nome=X  (público, sem token)
  ↓  POST /missoes/{id}/solicitar  { "senha": "acesso123" }
  ↓  Validações em ordem (ver seção Solicitar entrada)
  ↓  SolicitacaoEntrada criada com status=PENDENTE

SUPERVISOR ou DONO da missão aprova ou rejeita
  ↓  PATCH /missoes/{id}/solicitacoes/{solId}/aprovar
  ↓  Aprovado → OperadorMissao criado com role=MEMBRO
```

### Campo `permitirCowork`

| `permitirCowork` | Comportamento |
|:---:|---|
| `false` (padrão) | Só operadores da **mesma agência** da missão podem solicitar entrada |
| `true` | Qualquer operador autenticado pode solicitar entrada, independente da agência |

> O operador precisa ter uma agência vinculada mesmo quando `permitirCowork=true`. Um operador sem agência não pode solicitar entrada em nenhuma missão.

---

## Roles e permissões

A hierarquia é implementada via `RoleMissao.temPermissao(roleMinimo)` com base no ordinal do enum:

```java
public enum RoleMissao {
    DONO,        // ordinal 0 — mais permissivo
    SUPERVISOR,  // ordinal 1
    MEMBRO;      // ordinal 2 — menos permissivo

    public boolean temPermissao(RoleMissao minimo) {
        return this.ordinal() <= minimo.ordinal();
    }
}
```

**Tabela de permissões:**

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão, membros, satélites, leituras | ✓ | ✓ | ✓ |
| Registrar leitura (público — sem token) | ✓ | ✓ | ✓ |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Aprovar / rejeitar solicitações de entrada | ✓ | ✓ | — |
| Ver solicitações pendentes | ✓ | ✓ | — |
| Atualizar status de alerta | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir missão / satélite / sensor | ✓ | — | — |
| Remover e promover membros | ✓ | — | — |

`DONO.temPermissao(SUPERVISOR)` = `0 <= 1` = `true` → DONO pode fazer tudo que SUPERVISOR faz.  
`MEMBRO.temPermissao(SUPERVISOR)` = `2 <= 1` = `false` → MEMBRO não pode.

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

**O que acontece internamente (`MissaoService.criar`):**
1. Resolve agência pelo `agenciaId` (opcional — se null, missão fica sem agência)
2. Encripta `senhaMissao` com `BCryptPasswordEncoder` (nunca armazenada em texto puro)
3. Salva a `Missao` no banco (gera id pela sequence `SEQ_MISSAO`)
4. Salva `OperadorMissao` vinculando o operador logado como `DONO` com `dataEntrada=now()`
5. Retorna `MissaoResponse` com `roleDoOperador="DONO"`, `totalMembros=1`, `totalSatelites=0`

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
    "self":      { "href": "http://localhost:8080/missoes/1" },
    "membros":   { "href": "http://localhost:8080/missoes/1/membros" },
    "sair":      { "href": "http://localhost:8080/missoes/1/sair" },
    "atualizar": { "href": "http://localhost:8080/missoes/1" },
    "deletar":   { "href": "http://localhost:8080/missoes/1" }
  }
}
```

> Os links `atualizar` e `deletar` só aparecem para operadores com role `DONO`. O controller adiciona os links baseando-se no `roleDoOperador` do response.

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `nome` | String | Sim | Nome da missão |
| `descricao` | String | Não | Descrição detalhada |
| `dataLancamento` | LocalDate (`yyyy-MM-dd`) | Sim | Data de lançamento |
| `status` | Enum | Sim | `PLANEJADA`, `ATIVA` ou `ENCERRADA` |
| `senhaMissao` | String | Sim | Senha para solicitação de entrada. Armazenada com BCrypt |
| `agenciaId` | Long | Não | ID da agência responsável. Se omitido, missão fica sem agência |
| `objetivo` | String | Não | Objetivo da missão (máx. 500 chars) |
| `dataFimPrevista` | LocalDate (`yyyy-MM-dd`) | Não | Data prevista de encerramento |
| `permitirCowork` | Boolean | Não | `false` por padrão — só agência da missão; `true` = qualquer agência |

> A `senhaMissao` nunca aparece em nenhuma resposta.

---

## Listar e buscar missões

### Listar minhas missões (requer token)

```bash
curl -s http://localhost:8080/missoes \
  -H "Authorization: Bearer SEU_TOKEN"
```

Retorna apenas missões onde **você é membro** (`findByMembrosOperadorId`). Para cada missão, uma query adicional busca sua role — N+1 por design aceitável para a escala do projeto.

**Response inclui `roleDoOperador`** do operador logado em cada missão, e os HATEOAS links são adicionados conforme o role.

### Buscar por id (requer token)

```bash
curl -s http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

**Regra:** verifica primeiro se a missão existe (404), depois se o operador é membro (403). A ordem importa — inverter vazaria a informação de que a missão não existe.

Retorna 403 se você não for membro.

### Busca pública por nome (sem token)

```bash
curl -s "http://localhost:8080/missoes/buscar?nome=Alpha"
```

Público — permite descobrir missões antes de solicitar entrada. Não exige token.

---

## Editar e excluir missão

### Editar — apenas DONO

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

> `PUT` **não aceita** `senhaMissao` — a senha não é alterável por este endpoint (`MissaoUpdateRequest` não tem esse campo).

### Excluir — apenas DONO

```bash
curl -s -X DELETE http://localhost:8080/missoes/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

O `cascade = CascadeType.ALL` na entidade `Missao` garante a remoção automática de:
- Todos os `OperadorMissao` (vínculos de membros)
- Todas as `SolicitacaoEntrada`

Satélites, sensores e leituras são removidos por cascade nas respectivas entidades (`Satelite` → `Sensor` → `LeituraSensor` → `Alerta`).

---

## Solicitar entrada (fluxo de aprovação)

### Validações em ordem (a ordem é intencional)

```
1. Missão existe?
   └─ Não → 404

2. Operador tem agência vinculada?
   └─ Não → 400 "Operador não possui agência vinculada"

3. permitirCowork == false E agências diferentes?
   └─ Sim → 403 "Sua agência não tem permissão para entrar nesta missão"

4. Senha correta? (BCrypt.matches)
   └─ Não → 401 "Senha da missão incorreta"

5. Já é membro?
   └─ Sim → 409 "Operador já é membro desta missão"

6. Já tem solicitação PENDENTE?
   └─ Sim → 409 "Já existe uma solicitação pendente para esta missão"

7. Cria SolicitacaoEntrada { status=PENDENTE }
```

**Por que agência é verificada ANTES da senha?**  
Se a senha fosse verificada primeiro, um operador de agência incompatível poderia tentar diversas senhas para descobri-la. Verificando agência antes, ele nem chega a testar senhas.

### Solicitar entrada

```bash
curl -s -X POST http://localhost:8080/missoes/1/solicitar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{"senha": "acesso123"}'
```

**Resposta — 201 Created:**
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

### Listar solicitações — SUPERVISOR ou DONO

```bash
# Pendentes (padrão)
curl -s "http://localhost:8080/missoes/1/solicitacoes?status=PENDENTE" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"

# Aprovadas
curl -s "http://localhost:8080/missoes/1/solicitacoes?status=APROVADO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"

# Rejeitadas
curl -s "http://localhost:8080/missoes/1/solicitacoes?status=REJEITADO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
```

Parâmetro `?status=` obrigatório, case-sensitive. Retorna 403 se o operador for MEMBRO.

---

## Aprovar ou rejeitar solicitações

Exige **SUPERVISOR ou DONO** na missão.

### Aprovação

```bash
curl -s -X PATCH http://localhost:8080/missoes/1/solicitacoes/5/aprovar \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 200 OK
```

**Ao aprovar**, em uma mesma transação:
1. `solicitacao.status` → `APROVADO`
2. `solicitacao.dataResposta` → `now()`
3. `solicitacao.respondidoPor` → operador logado
4. Cria `OperadorMissao { role=MEMBRO, dataEntrada=now() }`

O novo membro sempre entra como `MEMBRO` — não é possível aprovar alguém como SUPERVISOR diretamente; o DONO precisa promover depois via `PATCH /membros/{id}`.

### Rejeição

```bash
curl -s -X PATCH http://localhost:8080/missoes/1/solicitacoes/5/rejeitar \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 200 OK
```

**Regras:**

| Situação | HTTP | Erro |
|----------|:----:|------|
| Solicitação já foi respondida (`APROVADO` ou `REJEITADO`) | 400 | `"Solicitação já foi respondida"` |
| Solicitação não pertence a esta missão | 404 | — |
| Operador é MEMBRO | 403 | `"Role mínima exigida: SUPERVISOR"` |

---

## Sair da missão

```bash
curl -s -X POST http://localhost:8080/missoes/1/sair \
  -H "Authorization: Bearer SEU_TOKEN"
# → 204 No Content
```

**Regra do DONO único:** se você for o único DONO, a API bloqueia com 400. Promova outro membro para DONO antes de sair.

**Sequência interna:**
1. Busca o vínculo `OperadorMissao` pelo par (missão, operador) — 404 se não existe
2. Se role == DONO: conta quantos DONOs existem (`countByMissaoIdAndRole`) — 400 se <= 1
3. Remove o vínculo — membro deixa de aparecer em listagens

> **Por que 404 e não 403 se não for membro?** Semântica: "o vínculo que você quer desfazer não existe" é "não encontrado", não "sem permissão". A distinção é importante para debugar integrações.

---

## Gerenciar membros

### Listar membros (qualquer membro)

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
  }
]
```

Qualquer membro (DONO, SUPERVISOR ou MEMBRO) pode listar. O acesso é verificado com `existsByMissaoIdAndOperadorId` (query de contagem — mais eficiente que buscar o objeto inteiro).

### Promover ou rebaixar membro — apenas DONO

```bash
# Promover para SUPERVISOR
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"

# Promover para DONO (transfere ou cria co-DONO)
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=DONO" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"

# Rebaixar para MEMBRO
curl -s -X PATCH "http://localhost:8080/missoes/1/membros/2?novoRole=MEMBRO" \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
```

**Regras:**
- O DONO **não pode alterar a própria role** → 403
- Pode criar múltiplos DONOs (não há restrição de quantidade)
- Para transferir ownership: promova outro para DONO, depois saia da missão

### Remover membro — apenas DONO

```bash
curl -s -X DELETE http://localhost:8080/missoes/1/membros/2 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

**Regra:** o DONO não pode usar este endpoint para se remover — use `POST /missoes/{id}/sair` para isso.

---

## Alertas da missão

Acessa alertas de todos os satélites de uma missão específica. Exige autenticação e membership na missão.

```bash
# Todos os alertas da missão
curl -s "http://localhost:8080/missoes/1/alertas" \
  -H "Authorization: Bearer SEU_TOKEN"

# Filtrar por status
curl -s "http://localhost:8080/missoes/1/alertas?status=ATIVO" \
  -H "Authorization: Bearer SEU_TOKEN"
```

**Atualizar status de um alerta — SUPERVISOR ou DONO:**

```bash
curl -s -X PATCH "http://localhost:8080/missoes/1/alertas/7?novoStatus=RECONHECIDO" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
```

---

## Links HATEOAS

Os links são adicionados dinamicamente conforme o role do operador:

| Link | Método | Destino | Visível para |
|------|:------:|---------|:----------:|
| `self` | GET | `/missoes/{id}` | Todos |
| `membros` | GET | `/missoes/{id}/membros` | Todos |
| `sair` | POST | `/missoes/{id}/sair` | Todos |
| `atualizar` | PUT | `/missoes/{id}` | Apenas DONO |
| `deletar` | DELETE | `/missoes/{id}` | Apenas DONO |

---

## Erros

| Exceção | HTTP | Quando ocorre |
|---------|:----:|--------------|
| `EntityNotFoundException` | 404 | Missão não encontrada |
| `EntityNotFoundException` | 404 | Não é membro (em operações de escrita — `verificarRole` retorna 404 antes de 403) |
| `AcessoNegadoException` | 403 | Role insuficiente |
| `AcessoNegadoException` | 403 | Não é membro (em GETs — `buscarPorId`, `listarMembros`) |
| `AcessoNegadoException` | 403 | Missão sem cowork, agência incompatível |
| `AcessoNegadoException` | 403 | DONO tentando se remover via `DELETE /membros` |
| `AcessoNegadoException` | 403 | DONO tentando alterar a própria role |
| `SenhaMissaoInvalidaException` | 401 | Senha incorreta ao solicitar entrada |
| `OperadorJaMembroException` | 409 | Já é membro ou já tem solicitação pendente |
| `DonoUnicoException` | 400 | Único DONO tentando sair sem transferir |
| `IllegalArgumentException` | 400 | Operador sem agência tentando solicitar entrada |
| `IllegalArgumentException` | 400 | Solicitação já foi respondida |

> **Diferença de 404 vs 403 no `verificarRole`:** o helper `verificarRole` lança 404 ("Você não é membro desta missão") porque na semântica de operações de escrita o operador não encontrou o recurso de autorização. Isso evita que um não-membro descubra se um determinado membro existe pelo código HTTP da resposta.
