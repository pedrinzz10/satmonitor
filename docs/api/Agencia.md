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

Uma **agência** é a organização espacial responsável por uma missão (ex: NASA, ESA, INPE). O vínculo é **opcional** — uma missão pode existir sem agência.

**Endpoints GET são públicos** — sem token. Criar, editar e excluir exigem autenticação.

---

## Criar agência

```bash
curl -s -X POST http://localhost:8080/agencias \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -d '{
    "nome": "NASA",
    "siglaPais": "US",
    "tipoAgencia": "GOVERNAMENTAL"
  }'
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

```bash
# Listar todas (paginado, 10 por página, ordenado por nome)
GET http://localhost:8080/agencias

# Buscar por id
GET http://localhost:8080/agencias/1
```

---

## Editar e excluir

### Editar

```bash
PUT http://localhost:8080/agencias/1
Authorization: Bearer SEU_TOKEN
Content-Type: application/json

# Body: mesma estrutura do POST
```

### Excluir

```bash
DELETE http://localhost:8080/agencias/1
Authorization: Bearer SEU_TOKEN
# → 204 No Content
```

A FK em `TB_MISSAO` é nullable — ao deletar a agência, as missões vinculadas ficam com `agenciaId = null` no banco.

---

## Vincular missão a uma agência

O campo `agenciaId` é **opcional** nos requests de missão:

```json
{
  "nome": "Missao Alpha",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "acesso123",
  "agenciaId": 1
}
```

Se `agenciaId` for omitido, os campos `agenciaId` e `nomeAgencia` virão como `null` — comportamento idêntico ao anterior.

> O vínculo entre missão e agência também é relevante para o **controle de cowork**: quando `permitirCowork=false`, somente operadores da mesma agência da missão podem solicitar entrada. Ver [api/Missao.md](Missao.md).

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
