# Agencia — Agências espaciais

## Índice

1. [O que é uma agência](#o-que-é-uma-agência)
2. [Criar agência](#criar-agência)
3. [Listar e buscar](#listar-e-buscar)
4. [Editar e excluir](#editar-e-excluir)
5. [Vincular missão a uma agência](#vincular-missão-a-uma-agência)
6. [Links HATEOAS](#links-hateoas)
7. [Erros](#erros)

---

## O que é uma agência

Uma **agência** é a organização espacial responsável por uma missão (ex: NASA, ESA, INPE). O vínculo é **opcional** — uma missão pode existir sem agência, e adicionar uma não quebra nenhum contrato existente com o Mobile ou IoT.

**Endpoints GET são públicos** — sem token. Criar, editar e excluir exigem autenticação.

---

## Criar agência

```
POST http://localhost:8080/agencias
Content-Type: application/json
Authorization: Bearer {{token}}
```

**Body:**
```json
{
  "nome": "NASA",
  "siglaPais": "US",
  "tipoAgencia": "GOVERNAMENTAL"
}
```

**Resposta — 201 Created:**
```json
{
  "id": 1,
  "nome": "NASA",
  "siglaPais": "US",
  "tipoAgencia": "GOVERNAMENTAL",
  "dataCadastro": "2026-06-04",
  "_links": {
    "self":      { "href": "http://localhost:8080/agencias/1" },
    "atualizar": { "href": "http://localhost:8080/agencias/1" },
    "deletar":   { "href": "http://localhost:8080/agencias/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Regra |
|-------|------|:-----------:|-------|
| `nome` | String | Sim | Não pode ser vazio |
| `siglaPais` | String | Sim | Exatamente 2 caracteres (ex: `"BR"`, `"US"`) |
| `tipoAgencia` | String | Não | Livre (ex: `"GOVERNAMENTAL"`, `"PRIVADA"`, `"UNIVERSITARIA"`) |

> `siglaPais` é convertida para maiúsculas automaticamente.  
> `dataCadastro` é preenchida pelo servidor — não enviar.

---

## Listar e buscar

```
GET http://localhost:8080/agencias
```

Paginado, 10 por página, ordenado por `nome`.

```
GET http://localhost:8080/agencias/1
```

---

## Editar e excluir

### Editar

```
PUT http://localhost:8080/agencias/1
Content-Type: application/json
Authorization: Bearer {{token}}
```

**Body:** mesma estrutura do POST.

### Excluir

```
DELETE http://localhost:8080/agencias/1
Authorization: Bearer {{token}}
```

Retorna 204 No Content. Se a agência estiver vinculada a missões, a FK em `TB_MISSAO` é nullable — o delete remove a agência mas não afeta as missões (o campo `agenciaId` ficará null nelas após o delete pelo banco).

---

## Vincular missão a uma agência

O campo `agenciaId` é **opcional** nos requests de missão. Pode ser enviado na criação ou na atualização:

**POST /missoes com agência:**
```json
{
  "nome": "Missao Alpha",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "acesso123",
  "agenciaId": 1,
  "objetivo": "Monitoramento de órbita baixa",
  "dataFimPrevista": "2027-12-31"
}
```

**Response com agência:**
```json
{
  "id": 1,
  "nome": "Missao Alpha",
  "agenciaId": 1,
  "nomeAgencia": "NASA",
  "objetivo": "Monitoramento de órbita baixa",
  "dataFimPrevista": "2027-12-31",
  ...
}
```

Se `agenciaId` for omitido, os campos `agenciaId` e `nomeAgencia` virão como `null` — comportamento idêntico ao anterior.

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/agencias/{id}` |
| `atualizar` | PUT | `/agencias/{id}` |
| `deletar` | DELETE | `/agencias/{id}` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 400 | `nome` vazio ou `siglaPais` com tamanho diferente de 2 |
| 401 | Token ausente em POST/PUT/DELETE |
| 404 | Agência não encontrada pelo id |
| 404 | `agenciaId` informado em POST/PUT /missoes não existe |
