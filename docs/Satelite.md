# Satelite — Satélites e coordenadas orbitais

## Índice

1. [O que é um satélite](#o-que-é-um-satélite)
2. [Criar satélite](#criar-satélite)
3. [Listar e buscar satélites](#listar-e-buscar-satélites)
4. [Estatísticas de leituras](#estatísticas-de-leituras)
5. [Editar e excluir satélite](#editar-e-excluir-satélite)
6. [Links HATEOAS](#links-hateoas)
7. [Erros](#erros)
8. [Por que CoordenadasOrbitais é @Embeddable](#por-que-coordenadasorbitais-é-embeddable)

---

## O que é um satélite

Um **satélite** pertence a uma missão e carrega sensores que monitoram continuamente. A hierarquia é:

```
Missao → Satelite → Sensor → LeituraSensor
```

Cada satélite tem **coordenadas orbitais** (altitude, inclinação, longitude do nodo) embutidas diretamente na sua tabela — sem tabela separada.

**Endpoints GET são públicos** — qualquer cliente (Mobile, IoT, sistemas externos) consulta satélites sem token. Criar, editar e excluir exigem autenticação e role de **SUPERVISOR** ou **DONO** na missão do satélite.

---

## Criar satélite

Exige que o operador seja **SUPERVISOR ou DONO** na missão informada.

```bash
curl -s -X POST http://localhost:8080/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "SAT-01",
    "dataLancamento": "2026-01-15",
    "missaoId": 1,
    "coordenadas": {
      "altitudeKm": 550.0,
      "inclinacao": 53.5,
      "longitudeNodo": 12.3
    },
    "tipoOrbita": "LEO",
    "statusSatelite": "ATIVO"
  }'
```

**Resposta — 201 Created:**
```json
{
  "id": 1,
  "nome": "SAT-01",
  "dataLancamento": "2026-01-15",
  "altitudeKm": 550.0,
  "inclinacao": 53.5,
  "longitudeNodo": 12.3,
  "tipoOrbita": "LEO",
  "statusSatelite": "ATIVO",
  "missaoId": 1,
  "nomeMissao": "Missao Alpha",
  "totalSensores": 0,
  "_links": {
    "self": { "href": "http://localhost:8080/satelites/1" },
    "atualizar": { "href": "http://localhost:8080/satelites/1" },
    "deletar": { "href": "http://localhost:8080/satelites/1" },
    "estatisticas": { "href": "http://localhost:8080/satelites/1/estatisticas" },
    "sensores": { "href": "http://localhost:8080/sensores/satelite/1" },
    "missao": { "href": "http://localhost:8080/missoes/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `nome` | String | Sim | Deve ser único dentro da missão |
| `dataLancamento` | LocalDate (`yyyy-MM-dd`) | Sim | — |
| `missaoId` | Long | Sim | ID da missão dona do satélite |
| `coordenadas.altitudeKm` | Double | Sim | Altitude orbital em km |
| `coordenadas.inclinacao` | Double | Sim | Ângulo de inclinação em graus |
| `coordenadas.longitudeNodo` | Double | Não | Longitude do nodo ascendente em graus |
| `tipoOrbita` | Enum | Não | `LEO`, `MEO`, `GEO` ou `HEO` |
| `statusSatelite` | Enum | Não | `ATIVO`, `STANDBY`, `MANUTENCAO` ou `DESATIVADO` |

> `tipoOrbita` e `statusSatelite` são opcionais — missões antigas continuam funcionando sem eles.

---

## Listar e buscar satélites

Todos os GET de satélites são públicos — sem token.

### Listar todos os satélites

```bash
curl -s http://localhost:8080/satelites
# Paginado, 10 por página, ordenado por nome
```

### Buscar por id

```bash
curl -s http://localhost:8080/satelites/1
```

### Listar satélites de uma missão

```bash
curl -s http://localhost:8080/satelites/missao/1
# Retorna 404 se a missão não existir
```

**Resposta paginada:**
```json
{
  "content": [
    {
      "id": 1,
      "nome": "SAT-01",
      "altitudeKm": 550.0,
      "missaoId": 1,
      "nomeMissao": "Missao Alpha",
      "totalSensores": 4,
      ...
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

---

## Estatísticas de leituras

Agrega todas as leituras de todos os sensores do satélite em uma única consulta.

```bash
curl -s http://localhost:8080/satelites/1/estatisticas
```

**Resposta — satélite com leituras:**
```json
{
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "mediaValor": 42.5,
  "minValor": -50.0,
  "maxValor": 150.0,
  "totalLeituras": 127,
  "totalAlertas": 23,
  "totalCriticos": 8,
  "ultimaLeitura": "2026-06-01T14:32:07.412"
}
```

**Resposta — satélite sem leituras ainda:**
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

## Editar e excluir satélite

### Editar (SUPERVISOR ou DONO)

```bash
curl -s -X PUT http://localhost:8080/satelites/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "SAT-01",
    "dataLancamento": "2026-02-01",
    "missaoId": 1,
    "coordenadas": {
      "altitudeKm": 600.0,
      "inclinacao": 55.0,
      "longitudeNodo": 15.0
    }
  }'
```

A verificação de role é feita pela **missão atual do satélite**, não pelo `missaoId` do request.

### Excluir (apenas DONO)

```bash
curl -s -X DELETE http://localhost:8080/satelites/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

Ao excluir o satélite, todos os seus sensores e leituras são removidos em cascata.

---

## Links HATEOAS

Todo `SateliteResponse` inclui os seguintes links:

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/satelites/{id}` |
| `atualizar` | PUT | `/satelites/{id}` |
| `deletar` | DELETE | `/satelites/{id}` |
| `estatisticas` | GET | `/satelites/{id}/estatisticas` |
| `sensores` | GET | `/sensores/satelite/{id}` |
| `missao` | GET | `/missoes/{missaoId}` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 400 | Nome duplicado na mesma missão |
| 403 | Não é membro da missão do satélite |
| 403 | MEMBRO tentando criar ou editar (exige SUPERVISOR) |
| 403 | SUPERVISOR tentando excluir (exige DONO) |
| 404 | Satélite não encontrado pelo id |
| 404 | `missaoId` informado não existe |

---

## Por que CoordenadasOrbitais é @Embeddable

As coordenadas (`altitudeKm`, `inclinacao`, `longitudeNodo`) ficam **diretamente na tabela `TB_SATELITE`** — sem tabela própria, sem FK, sem join.

**Motivo:** coordenadas não têm identidade própria. Elas só existem como parte do satélite, nunca são consultadas isoladamente, e sempre mudam em conjunto. Criar uma tabela `TB_COORDENADAS` adicionaria um join em toda consulta de satélite sem nenhum benefício real.

Com `@Embeddable`, a query de satélite é um simples `SELECT * FROM TB_SATELITE` sem joins extras.
