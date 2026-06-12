# Sensor — Sensores dos satélites

## Índice

1. [O que é um sensor](#o-que-é-um-sensor)
2. [Os 4 tipos de sensor](#os-4-tipos-de-sensor)
3. [Criar sensor](#criar-sensor)
4. [Listar e buscar sensores](#listar-e-buscar-sensores)
5. [Editar sensor](#editar-sensor)
6. [Excluir sensor](#excluir-sensor)
7. [Links HATEOAS](#links-hateoas)
8. [Erros](#erros)
9. [Internals: factory pattern e herança JOINED](#internals-factory-pattern-e-herança-joined)

---

## O que é um sensor

Um **sensor** é um instrumento acoplado a um satélite que gera leituras contínuas. Cada sensor define:

- **`limiteMin` / `limiteMax`** — a faixa de operação segura
- **`margemAlerta`** — percentual (0–100%) da faixa que antecede os limites críticos (zona de pré-alerta)

Quando o IoT registra uma leitura, o `StatusCalculator` usa esses parâmetros para classificar automaticamente como **NORMAL**, **ALERTA** ou **CRITICO**. Ver [Leitura.md](Leitura.md).

> **Todos os endpoints de sensor exigem token JWT.** Isso inclui os GETs — sensores são recursos vinculados a missões protegidas.

---

## Os 4 tipos de sensor

Cada tipo tem um campo extra obrigatório. O campo específico aparece na resposta como `"detalhe"`:

| Tipo | Campo no request | Valores aceitos |
|------|:----------------:|----------------|
| `TERMICO` | `unidadeEscala` | `CELSIUS`, `FAHRENHEIT`, `KELVIN` |
| `PRESSAO` | `tipoPressao` | `ABSOLUTA`, `RELATIVA` |
| `RADIACAO` | `tipoRadiacao` | `IONIZANTE`, `NAO_IONIZANTE` |
| `MAGNETOMETRO` | `eixosMedicao` | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ`, `XYZ` |

> **Tipo imutável:** após a criação, o tipo não pode ser alterado via `PUT`. O service ignora `tipo` no update. Para mudar o tipo, delete o sensor e crie um novo (histórico de leituras é perdido).

---

## Criar sensor

Exige **SUPERVISOR ou DONO** na missão do satélite.

### Sensor Térmico

```bash
curl -s -X POST http://localhost:8080/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Termometro Principal",
    "unidade": "graus_C",
    "limiteMin": -10.0,
    "limiteMax": 90.0,
    "margemAlerta": 5.0,
    "sateliteId": 1,
    "tipo": "TERMICO",
    "unidadeEscala": "CELSIUS"
  }'
```

### Sensor de Pressão

```bash
curl -s -X POST http://localhost:8080/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Barometro",
    "unidade": "hPa",
    "limiteMin": 950.0,
    "limiteMax": 1050.0,
    "margemAlerta": 5.0,
    "sateliteId": 1,
    "tipo": "PRESSAO",
    "tipoPressao": "ABSOLUTA"
  }'
```

### Sensor de Radiação

```bash
curl -s -X POST http://localhost:8080/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Contador Geiger",
    "unidade": "Gy",
    "limiteMin": 0.0,
    "limiteMax": 5.0,
    "margemAlerta": 20.0,
    "sateliteId": 1,
    "tipo": "RADIACAO",
    "tipoRadiacao": "IONIZANTE"
  }'
```

### Magnetômetro

```bash
curl -s -X POST http://localhost:8080/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Magnetometro Tri-axial",
    "unidade": "nT",
    "limiteMin": -50000.0,
    "limiteMax": 50000.0,
    "margemAlerta": 15.0,
    "sateliteId": 1,
    "tipo": "MAGNETOMETRO",
    "eixosMedicao": "XYZ"
  }'
```

**Resposta — 201 Created:**
```json
{
  "id": 1,
  "nome": "Termometro Principal",
  "tipo": "TERMICO",
  "unidade": "graus_C",
  "limiteMin": -10.0,
  "limiteMax": 90.0,
  "margemAlerta": 5.0,
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "detalhe": "CELSIUS",
  "_links": {
    "self":     { "href": "http://localhost:8080/sensores/1" },
    "atualizar":{ "href": "http://localhost:8080/sensores/1" },
    "deletar":  { "href": "http://localhost:8080/sensores/1" },
    "leituras": { "href": "http://localhost:8080/leituras/sensor/1" },
    "satelite": { "href": "http://localhost:8080/satelites/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `nome` | String | Sim | Deve ser **único por satélite** |
| `unidade` | String | Sim | Unidade de medida (ex: `"graus_C"`, `"hPa"`, `"nT"`) |
| `limiteMin` | Double | Sim | Deve ser **estritamente menor** que `limiteMax` |
| `limiteMax` | Double | Sim | — |
| `margemAlerta` | Double | Sim | Percentual de 0 a 100 (validado com `@DecimalMin("0")` `@DecimalMax("100")`) |
| `sateliteId` | Long | Sim | ID do satélite ao qual o sensor será acoplado |
| `tipo` | Enum | Sim | `TERMICO`, `PRESSAO`, `RADIACAO` ou `MAGNETOMETRO` |
| `unidadeEscala` | String | Obrigatório se `TERMICO` | `CELSIUS`, `FAHRENHEIT` ou `KELVIN` |
| `tipoPressao` | String | Obrigatório se `PRESSAO` | `ABSOLUTA` ou `RELATIVA` |
| `tipoRadiacao` | String | Obrigatório se `RADIACAO` | `IONIZANTE` ou `NAO_IONIZANTE` |
| `eixosMedicao` | String | Obrigatório se `MAGNETOMETRO` | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ` ou `XYZ` |

**Validações em `SensorService.criar`:**
1. Busca satélite pelo `sateliteId` → 404 se não existe
2. Verifica role SUPERVISOR+ na missão do satélite → 403 se insuficiente
3. Valida `limiteMin < limiteMax` → 400 se violado
4. Verifica unicidade de nome no satélite → 400 se duplicado
5. Instancia a subclasse correta (factory pattern — ver seção abaixo)

---

## Listar e buscar sensores

Todos os GETs de sensores **exigem token**.

```bash
# Listar todos
curl -s http://localhost:8080/sensores \
  -H "Authorization: Bearer SEU_TOKEN"

# Buscar por id
curl -s http://localhost:8080/sensores/1 \
  -H "Authorization: Bearer SEU_TOKEN"

# Listar sensores de um satélite
curl -s http://localhost:8080/sensores/satelite/1 \
  -H "Authorization: Bearer SEU_TOKEN"
# Retorna 404 se o satélite não existir
```

---

## Editar sensor

Exige **SUPERVISOR ou DONO**. Apenas os campos comuns são atualizáveis (`nome`, `unidade`, `limiteMin`, `limiteMax`, `margemAlerta`). O tipo e o campo específico são **imutáveis**:

```bash
curl -s -X PUT http://localhost:8080/sensores/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Termometro Principal v2",
    "unidade": "graus_C",
    "limiteMin": -20.0,
    "limiteMax": 100.0,
    "margemAlerta": 10.0,
    "sateliteId": 1,
    "tipo": "TERMICO",
    "unidadeEscala": "CELSIUS"
  }'
```

O `PUT` aceita o request completo (incluindo `tipo` e campo específico) mas **ignora esses campos** — o service não altera a subclasse. A verificação de role usa a **missão atual do satélite** (não o `sateliteId` do request).

**Para mudar o tipo:** delete o sensor e crie um novo. O histórico de leituras é perdido.

---

## Excluir sensor

Exige **DONO**. Remove o sensor e todas as suas leituras e alertas em cascata.

```bash
curl -s -X DELETE http://localhost:8080/sensores/1 \
  -H "Authorization: Bearer SEU_TOKEN_DONO"
# → 204 No Content
```

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/sensores/{id}` |
| `atualizar` | PUT | `/sensores/{id}` |
| `deletar` | DELETE | `/sensores/{id}` |
| `leituras` | GET | `/leituras/sensor/{id}` |
| `satelite` | GET | `/satelites/{sateliteId}` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 400 | `limiteMin >= limiteMax` |
| 400 | Nome duplicado no mesmo satélite |
| 400 | Campo obrigatório do tipo ausente (ex: `unidadeEscala` ausente para TERMICO) |
| 400 | Valor de enum inválido (ex: `"tipo": "SONICO"`) → JSON inválido |
| 401 | Token ausente em qualquer endpoint (incluindo GETs) |
| 403 | Não é membro da missão do satélite |
| 403 | MEMBRO tentando criar ou editar (exige SUPERVISOR) |
| 403 | SUPERVISOR tentando excluir (exige DONO) |
| 404 | Sensor não encontrado pelo id |
| 404 | `sateliteId` não existe |

---

## Internals: factory pattern e herança JOINED

### Por que herança JOINED?

| Estratégia | Como funciona | Problema |
|------------|--------------|---------|
| `SINGLE_TABLE` | Uma tabela só, com discriminator | Colunas específicas ficam nulas para outros tipos — má normalização |
| `JOINED` ✓ | Tabela base + uma por subclasse | Um join por consulta polimórfica — normalização perfeita |
| `TABLE_PER_CLASS` | Uma tabela completa por tipo | Consultas polimórficas usam UNION — ineficiente |

**JOINED foi escolhida porque:**
- Evita colunas NULL em massa (cada sensor tem exatamente 1 campo extra)
- Os 4 tipos são consultados juntos com frequência (`SELECT * FROM TB_SENSOR`)
- `SEQ_SENSOR` é compartilhada — ids únicos entre todos os tipos

**Tabelas criadas:**

| Tabela | Conteúdo |
|--------|---------|
| `TB_SENSOR` | id, nome, unidade, limiteMin, limiteMax, margemAlerta, satelite_id, tipo_sensor (discriminator) |
| `TB_SENSOR_TERMICO` | id (FK → TB_SENSOR), unidade_escala |
| `TB_SENSOR_PRESSAO` | id (FK → TB_SENSOR), tipo_pressao |
| `TB_SENSOR_RADIACAO` | id (FK → TB_SENSOR), tipo_radiacao |
| `TB_MAGNETOMETRO` | id (FK → TB_SENSOR), eixos_medicao |

### Factory pattern em `instanciarSubclasse`

```java
private Sensor instanciarSubclasse(SensorRequest req) {
    return switch (req.tipo()) {
        case TERMICO -> {
            if (req.unidadeEscala() == null || req.unidadeEscala().isBlank())
                throw new IllegalArgumentException("unidadeEscala é obrigatório para TERMICO");
            SensorTermico s = new SensorTermico();
            s.setUnidadeEscala(UnidadeEscala.valueOf(req.unidadeEscala().toUpperCase()));
            yield s;
        }
        case PRESSAO -> { ... }
        case RADIACAO -> { ... }
        case MAGNETOMETRO -> { ... }
    };
}
```

O `switch` com `yield` é Java 14+. Cada case valida o campo específico antes de instanciar. O `toUpperCase()` torna a validação case-insensitive (aceita `"celsius"` e `"CELSIUS"`).

### Pattern matching Java 21 em `toResponse`

```java
String detalhe = switch (sensor) {
    case SensorTermico t  -> t.getUnidadeEscala().name();
    case SensorPressao p  -> p.getTipoPressao().name();
    case SensorRadiacao r -> r.getTipoRadiacao().name();
    case Magnetometro m   -> m.getEixosMedicao().name();
    default -> null;
};
```

Pattern matching para `instanceof` — Java 21 detecta o tipo em runtime e faz o cast ao mesmo tempo. Sem `instanceof` explícito, sem cast manual.
