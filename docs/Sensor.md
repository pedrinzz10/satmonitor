# Sensor — Sensores dos satélites

## Índice

1. [O que é um sensor](#o-que-é-um-sensor)
2. [Os 4 tipos de sensor](#os-4-tipos-de-sensor)
3. [Como o status é calculado](#como-o-status-é-calculado)
4. [Criar sensor](#criar-sensor)
5. [Listar e buscar sensores](#listar-e-buscar-sensores)
6. [Editar sensor](#editar-sensor)
7. [Excluir sensor](#excluir-sensor)
8. [Links HATEOAS](#links-hateoas)
9. [Erros](#erros)
10. [Por que herança JOINED](#por-que-herança-joined)

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

> **Tipo imutável:** após a criação, o tipo não pode ser alterado. Se precisar mudar, delete o sensor e crie um novo. Ver [seção Editar](#editar-sensor).

---

## Como o status é calculado

Com base nos parâmetros do sensor:

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

**Sensor Térmico de exemplo:** `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%`

```
zonaAlertaMin = 0  + (80 × 0.10) = 8.0
zonaAlertaMax = 80 - (80 × 0.10) = 72.0

|──CRITICO──|──ALERTA──|────NORMAL────|──ALERTA──|──CRITICO──|
0           8         72             80
```

| Leitura | Classificação |
|---------|:-------------:|
| 95°C (> 80) | CRITICO |
| 75°C (entre 72 e 80) | ALERTA |
| 40°C (entre 8 e 72) | NORMAL |
| 5°C (entre 0 e 8) | ALERTA |
| -5°C (< 0) | CRITICO |
| 72°C (== zonaAlertaMax) | NORMAL (fronteira exclusiva) |
| 80°C (== limiteMax) | ALERTA (não é > 80) |

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
    "self": { "href": "http://localhost:8080/sensores/1" },
    "atualizar": { "href": "http://localhost:8080/sensores/1" },
    "deletar": { "href": "http://localhost:8080/sensores/1" },
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
| `limiteMin` | Double | Sim | Deve ser menor que `limiteMax` |
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

Exige **SUPERVISOR ou DONO**. Apenas os campos comuns são atualizáveis:

```bash
curl -s -X PUT http://localhost:8080/sensores/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR" \
  -d '{
    "nome": "Termometro Principal",
    "unidade": "graus_C",
    "limiteMin": -20.0,
    "limiteMax": 100.0,
    "margemAlerta": 10.0,
    "sateliteId": 1,
    "tipo": "TERMICO",
    "unidadeEscala": "CELSIUS"
  }'
```

**O campo `tipo` e o detalhe específico (`detalhe`) são imutáveis.** Mesmo que você envie um valor diferente no `tipo`, ele será ignorado e o valor original mantido.

**Por que o tipo é imutável?** Na estratégia JOINED do JPA, cada tipo fica em uma tabela diferente. Mudar o tipo exigiria mover dados entre tabelas — operação não suportada sem deletar e recriar. Como sensores físicos raramente mudam de tipo, essa restrição é intencional.

**Como mudar o tipo:** delete o sensor (`DELETE /sensores/{id}`) e crie um novo com o tipo desejado. O histórico de leituras é perdido.

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

Os 4 tipos de sensor compartilham campos comuns mas cada um tem um campo específico adicional. O JPA oferece 3 estratégias para isso:

| Estratégia | Como funciona | Problema |
|------------|--------------|---------|
| `SINGLE_TABLE` | Uma tabela só | Colunas específicas ficam nulas para outros tipos — ruim no Oracle |
| `JOINED` | Uma tabela base + uma por subclasse | Um join por consulta polimórfica |
| `TABLE_PER_CLASS` | Uma tabela completa por tipo | Consultas polimórficas usam UNION — ineficiente |

**JOINED foi escolhida porque:**
- Oracle é o banco de produção — colunas nulas em massa desperdiçam espaço
- Os 4 tipos são consultados juntos frequentemente (`SELECT * FROM TB_SENSOR`)
- Um join por consulta é aceitável para apenas 4 subclasses
- Sequence `SEQ_SENSOR` é compartilhada entre todos os tipos (não funciona com `TABLE_PER_CLASS`)

**Tabelas criadas:**

| Tabela | Conteúdo |
|--------|---------|
| `TB_SENSOR` | id, nome, unidade, limiteMin, limiteMax, margemAlerta, satelite_id, tipo_sensor |
| `TB_SENSOR_TERMICO` | id (FK → TB_SENSOR), unidade_escala |
| `TB_SENSOR_PRESSAO` | id (FK → TB_SENSOR), tipo_pressao |
| `TB_SENSOR_RADIACAO` | id (FK → TB_SENSOR), tipo_radiacao |
| `TB_MAGNETOMETRO` | id (FK → TB_SENSOR), eixos_medicao |
