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
9. [Por que herança JOINED](#por-que-herança-joined)

---

## O que é um sensor

Um **sensor** é um instrumento acoplado a um satélite que gera leituras contínuas. Cada sensor define:

- **`limiteMin` / `limiteMax`** — a faixa de operação segura
- **`margemAlerta`** — percentual (%) da faixa que antecede os limites críticos (zona de pré-alerta)

Quando o ESP32 registra uma leitura, o `StatusCalculator` usa esses parâmetros para classificar automaticamente como **NORMAL**, **ALERTA** ou **CRITICO**.

---

## Os 4 tipos de sensor

Cada tipo tem um campo extra obrigatório:

| Tipo | Campo no request | Valores aceitos |
|------|:----------------:|----------------|
| `TERMICO` | `unidadeEscala` | `CELSIUS`, `FAHRENHEIT`, `KELVIN` |
| `PRESSAO` | `tipoPressao` | `ABSOLUTA`, `RELATIVA` |
| `RADIACAO` | `tipoRadiacao` | `IONIZANTE`, `NAO_IONIZANTE` |
| `MAGNETOMETRO` | `eixosMedicao` | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ`, `XYZ` |

O campo específico aparece na resposta como `"detalhe"` (ex: `"detalhe": "CELSIUS"`).

> **Tipo imutável:** após a criação, o tipo não pode ser alterado. Se precisar mudar, delete o sensor e crie um novo.

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
| `nome` | String | Sim | Único por satélite |
| `unidade` | String | Sim | Unidade de medida (ex: `"graus_C"`, `"hPa"`) |
| `limiteMin` | Double | Sim | Deve ser **menor** que `limiteMax` |
| `limiteMax` | Double | Sim | — |
| `margemAlerta` | Double | Sim | Percentual de 0 a 100 |
| `sateliteId` | Long | Sim | — |
| `tipo` | Enum | Sim | `TERMICO`, `PRESSAO`, `RADIACAO` ou `MAGNETOMETRO` |
| `unidadeEscala` | Enum | Se TERMICO | `CELSIUS`, `FAHRENHEIT` ou `KELVIN` |
| `tipoPressao` | Enum | Se PRESSAO | `ABSOLUTA` ou `RELATIVA` |
| `tipoRadiacao` | Enum | Se RADIACAO | `IONIZANTE` ou `NAO_IONIZANTE` |
| `eixosMedicao` | Enum | Se MAGNETOMETRO | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ` ou `XYZ` |

---

## Listar e buscar sensores

GET de sensores é público — sem token.

```bash
# Listar todos
curl -s http://localhost:8080/sensores

# Buscar por id
curl -s http://localhost:8080/sensores/1

# Listar sensores de um satélite
curl -s http://localhost:8080/sensores/satelite/1
# Retorna 404 se o satélite não existir
```

---

## Editar sensor

Exige **SUPERVISOR ou DONO**. Apenas os campos comuns são atualizáveis (`nome`, `unidade`, `limiteMin`, `limiteMax`, `margemAlerta`):

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

**O tipo e o campo específico são imutáveis.** Mesmo enviando `tipo` diferente no request, o valor original é mantido.

**Para mudar o tipo:** delete o sensor e crie um novo. O histórico de leituras é perdido.

---

## Excluir sensor

Exige **DONO**. Exclui o sensor e todas as suas leituras em cascata.

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
| 400 | Campo obrigatório do tipo ausente (ex: `unidadeEscala` para TERMICO) |
| 400 | Valor de enum inválido (ex: `"tipo": "SONICO"`) |
| 403 | Não é membro da missão do satélite |
| 403 | MEMBRO tentando criar ou editar (exige SUPERVISOR) |
| 403 | SUPERVISOR tentando excluir (exige DONO) |
| 404 | Sensor não encontrado pelo id |
| 404 | `sateliteId` não existe |

---

## Por que herança JOINED

Os 4 tipos compartilham campos comuns mas cada um tem um campo específico adicional.

| Estratégia | Como funciona | Problema |
|------------|--------------|---------|
| `SINGLE_TABLE` | Uma tabela só | Colunas específicas ficam nulas para outros tipos |
| `JOINED` | Uma tabela base + uma por subclasse | Um join por consulta polimórfica |
| `TABLE_PER_CLASS` | Uma tabela completa por tipo | Consultas polimórficas usam UNION — ineficiente |

**JOINED foi escolhida porque:**
- Evita colunas NULL em massa (melhor normalização)
- Os 4 tipos são consultados juntos frequentemente (`SELECT * FROM TB_SENSOR`)
- Sequence `SEQ_SENSOR` é compartilhada entre todos os tipos

**Tabelas criadas:**

| Tabela | Conteúdo |
|--------|---------|
| `TB_SENSOR` | id, nome, unidade, limiteMin, limiteMax, margemAlerta, satelite_id |
| `TB_SENSOR_TERMICO` | id (FK), unidade_escala |
| `TB_SENSOR_PRESSAO` | id (FK), tipo_pressao |
| `TB_SENSOR_RADIACAO` | id (FK), tipo_radiacao |
| `TB_MAGNETOMETRO` | id (FK), eixos_medicao |
