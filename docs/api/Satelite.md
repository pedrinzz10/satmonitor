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
Missao → Satelite → Sensor → LeituraSensor → Alerta
```

Cada satélite tem **coordenadas orbitais** embutidas diretamente na sua tabela — sem tabela ou FK separada.

> **Todos os endpoints de satélite exigem token JWT.** Isso inclui os GETs — satélites são recursos vinculados a missões protegidas. Criar, editar e excluir exigem também **SUPERVISOR ou DONO** na missão.

---

## Criar satélite

Exige **SUPERVISOR ou DONO** na missão informada.

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

**Validações em `SateliteService.criar`:**
1. Busca missão pelo `missaoId` → 404 se não existe
2. Verifica role SUPERVISOR+ na missão → 403 se insuficiente
3. Verifica unicidade de nome na missão → 400 se duplicado

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
    "self":         { "href": "http://localhost:8080/satelites/1" },
    "atualizar":    { "href": "http://localhost:8080/satelites/1" },
    "deletar":      { "href": "http://localhost:8080/satelites/1" },
    "estatisticas": { "href": "http://localhost:8080/satelites/1/estatisticas" },
    "sensores":     { "href": "http://localhost:8080/sensores/satelite/1" },
    "missao":       { "href": "http://localhost:8080/missoes/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `nome` | String | Sim | Deve ser **único dentro da missão** |
| `dataLancamento` | LocalDate (`yyyy-MM-dd`) | Sim | — |
| `missaoId` | Long | Sim | ID da missão dona do satélite |
| `coordenadas.altitudeKm` | Double | Sim | Altitude orbital em km |
| `coordenadas.inclinacao` | Double | Sim | Ângulo de inclinação em graus |
| `coordenadas.longitudeNodo` | Double | Não | Longitude do nodo ascendente em graus |
| `tipoOrbita` | Enum | Não | `LEO`, `MEO`, `GEO` ou `HEO` |
| `statusSatelite` | Enum | Não | `ATIVO`, `STANDBY`, `MANUTENCAO` ou `DESATIVADO` |

---

## Listar e buscar satélites

Todos os GETs de satélites **exigem token JWT**.

### Listar todos

```bash
curl -s http://localhost:8080/satelites \
  -H "Authorization: Bearer SEU_TOKEN"
# Paginado, 10 por página, ordenado por nome
```

### Buscar por id

```bash
curl -s http://localhost:8080/satelites/1 \
  -H "Authorization: Bearer SEU_TOKEN"
```

### Listar satélites de uma missão

```bash
curl -s http://localhost:8080/satelites/missao/1 \
  -H "Authorization: Bearer SEU_TOKEN"
# Retorna 404 se a missão não existir
```

---

## Estatísticas de leituras

Agrega todas as leituras de todos os sensores do satélite em uma única query customizada (`buscarEstatisticas`).

```bash
curl -s http://localhost:8080/satelites/1/estatisticas \
  -H "Authorization: Bearer SEU_TOKEN"
```

**Resposta:**
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

**Comportamento quando o satélite não tem leituras:**

O repository retorna `Optional.empty()` → o service usa um fallback com todos os campos zerados:
```json
{
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "mediaValor": 0.0,
  "minValor": 0.0,
  "maxValor": 0.0,
  "totalLeituras": 0,
  "totalAlertas": 0,
  "totalCriticos": 0,
  "ultimaLeitura": null
}
```

---

## Editar e excluir satélite

### Editar — SUPERVISOR ou DONO

```bash
curl -s -X PUT http://localhost:8080/satelites/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "SAT-01-ATUALIZADO",
    "dataLancamento": "2026-02-01",
    "missaoId": 1,
    "coordenadas": {
      "altitudeKm": 600.0,
      "inclinacao": 55.0,
      "longitudeNodo": 15.0
    }
  }'
```

**Importante:** a verificação de role usa a **missão atual do satélite** (não o `missaoId` do request). O `missaoId` do request é validado apenas para verificar se existe — o satélite não é movido para outra missão via este endpoint.

Nome duplicado na mesma missão retorna 400.

### Excluir — apenas DONO

```bash
curl -s -X DELETE http://localhost:8080/satelites/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

O cascade remove automaticamente: todos os sensores → todas as leituras → todos os alertas do satélite.

---

## Links HATEOAS

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
| 401 | Token ausente em qualquer endpoint (incluindo GETs) |
| 403 | Não é membro da missão do satélite |
| 403 | MEMBRO tentando criar ou editar (exige SUPERVISOR) |
| 403 | SUPERVISOR tentando excluir (exige DONO) |
| 404 | Satélite não encontrado pelo id |
| 404 | `missaoId` informado não existe |

> **Diferença de tratamento em `SateliteService.verificarRole`:** ao contrário de `MissaoService`, o `SateliteService` lança `AcessoNegadoException` (403) diretamente quando o operador não é membro — não 404. Isso porque o contexto é de acesso a um satélite específico, não de um vínculo que "não existe".

---

## Por que CoordenadasOrbitais é @Embeddable

As coordenadas (`altitudeKm`, `inclinacao`, `longitudeNodo`) ficam **diretamente na tabela `TB_SATELITE`** — sem tabela própria, sem FK, sem JOIN.

```java
@Embeddable
public class CoordenadasOrbitais {
    @Column(name = "altitude_km")    private Double altitudeKm;
    @Column(name = "inclinacao")     private Double inclinacao;
    @Column(name = "longitude_nodo") private Double longitudeNodo;
}

@Entity
public class Satelite {
    @Embedded
    private CoordenadasOrbitais coordenadas;
    // → campos altitude_km, inclinacao, longitude_nodo ficam em TB_SATELITE
}
```

**Motivo:** coordenadas não têm identidade própria — só existem como parte do satélite, nunca são consultadas isoladamente, e sempre mudam em conjunto. Criar `TB_COORDENADAS` adicionaria um JOIN em toda consulta de satélite sem nenhum benefício real.

Com `@Embeddable`, `SELECT * FROM TB_SATELITE` retorna tudo de uma vez, sem joins.
