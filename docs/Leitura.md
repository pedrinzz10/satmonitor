# Leitura — Módulo de leituras dos sensores

## Índice

1. [Visão geral](#visão-geral)
2. [Regra de negócio — StatusCalculator](#regra-de-negócio--statuscalculator)
3. [Caso especial margemAlerta=0](#caso-especial-margemalerta0)
4. [Entidade](#entidade)
5. [Endpoints](#endpoints)
6. [Filtro por status](#filtro-por-status)
7. [Contrato do IoT](#contrato-do-iot)
8. [HATEOAS](#hateoas)
9. [Testes](#testes)
10. [Erros](#erros)

---

## Visão geral

As **leituras** são os valores registrados pelos sensores dos satélites. Cada leitura pertence a um sensor e recebe automaticamente:
- **`dataHoraLeitura`** — o instante exato de registro, definido pelo servidor (`LocalDateTime.now()`)
- **`status`** — classificação automática (NORMAL, ALERTA ou CRÍTICO) calculada pelo `StatusCalculator`

O **POST /leituras** é o único endpoint público sem autenticação de todo o módulo. Isso é intencional: o ESP32 (IoT) posta leituras continuamente sem gerenciar tokens JWT. Todos os endpoints GET também são públicos para que o Mobile possa exibir dados sem autenticação adicional.

---

## Regra de negócio — StatusCalculator

A classe `StatusCalculator` (`leitura/service/StatusCalculator.java`) contém toda a lógica de classificação. É um `@Component` sem estado — pode ser testado de forma completamente isolada, sem Spring context.

### Fórmulas

```
faixa         = limiteMax - limiteMin
zonaAlerta    = faixa × (margemAlerta / 100)
zonaAlertaMin = limiteMin + zonaAlerta
zonaAlertaMax = limiteMax - zonaAlerta
```

### Decisão em cascata (ordem importa)

| Condição                     | Status  | Explicação                              |
|------------------------------|:-------:|-----------------------------------------|
| `valor < limiteMin`          | CRÍTICO | Abaixo do mínimo absoluto               |
| `valor > limiteMax`          | CRÍTICO | Acima do máximo absoluto                |
| `valor < zonaAlertaMin`      | ALERTA  | Zona de pré-alerta inferior             |
| `valor > zonaAlertaMax`      | ALERTA  | Zona de pré-alerta superior             |
| (nenhuma das anteriores)     | NORMAL  | Dentro da faixa operacional segura      |

**Fronteiras:** os limites são exclusivos para CRÍTICO e inclusivos para ALERTA:
- `valor == limiteMin` → ALERTA (não CRÍTICO)
- `valor == limiteMax` → ALERTA (não CRÍTICO)
- `valor == zonaAlertaMax` → NORMAL (não ALERTA)

### Diagrama da faixa (margemAlerta = 10%)

```
|--CRITICO--|--ALERTA--|--------NORMAL--------|--ALERTA--|--CRITICO--|
0           8         72                     80
```

### Exemplo numérico

Sensor Térmico: `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%`

```
faixa         = 80 - 0  = 80
zonaAlerta    = 80 × 0.1 = 8.0
zonaAlertaMin = 0  + 8.0 = 8.0
zonaAlertaMax = 80 - 8.0 = 72.0
```

| Valor  | Avaliação                  | Status  |
|--------|----------------------------|---------|
| 95°C   | 95 > 80 (limiteMax)        | CRÍTICO |
| 75°C   | 75 > 72 (zonaAlertaMax)    | ALERTA  |
| 40°C   | dentro de [8.0, 72.0]      | NORMAL  |
| 5°C    | 5 < 8 (zonaAlertaMin)      | ALERTA  |
| -5°C   | -5 < 0 (limiteMin)         | CRÍTICO |
| 72°C   | 72 == zonaAlertaMax        | NORMAL  |
| 80°C   | 80 == limiteMax (ALERTA)   | ALERTA  |
| 0°C    | 0 == limiteMin (ALERTA)    | ALERTA  |

---

## Caso especial margemAlerta=0

Quando `margemAlerta = 0`, a zona de alerta colapsa:

```
zonaAlerta    = 0
zonaAlertaMin = limiteMin
zonaAlertaMax = limiteMax
```

As condições `valor < zonaAlertaMin` e `valor > zonaAlertaMax` tornam-se equivalentes às condições de CRÍTICO, que são avaliadas primeiro. O resultado é comportamento **binário**: apenas NORMAL ou CRÍTICO, sem zona intermediária de ALERTA.

| Valor  | Status  | Motivo                                    |
|--------|---------|-------------------------------------------|
| 40.0   | NORMAL  | Dentro de [limiteMin, limiteMax]          |
| 79.9   | NORMAL  | Dentro de [limiteMin, limiteMax]          |
| 80.1   | CRÍTICO | 80.1 > 80 (limiteMax)                     |

Esse comportamento é **intencional e documentado** — permite configurar sensores sem margem de aviso prévio.

---

## Entidade

### LeituraSensor (`TB_LEITURA_SENSOR`)

| Campo             | Tipo            | Restrição                   | Descrição                                                       |
|-------------------|-----------------|-----------------------------|-----------------------------------------------------------------|
| `id`              | `Long`          | PK, sequence SEQ_LEITURA    | Identificador único                                             |
| `valor`           | `Double`        | NOT NULL                    | Valor medido pelo sensor                                        |
| `dataHoraLeitura` | `LocalDateTime` | NOT NULL                    | Instante do registro — sempre definido pelo servidor            |
| `status`          | `StatusLeitura` | NOT NULL, STRING            | NORMAL, ALERTA ou CRÍTICO — sempre calculado pelo StatusCalculator |
| `sensor_id`       | `Long`          | FK TB_SENSOR, NOT NULL, LAZY | Sensor que gerou a leitura                                     |

**Invariantes:** `dataHoraLeitura` e `status` **nunca** são aceitos no request — são sempre definidos pelo servidor.

---

## Endpoints

| Método   | Rota                              | Auth | Role mínimo | Descrição                                            |
|----------|-----------------------------------|:----:|:-----------:|------------------------------------------------------|
| `POST`   | `/leituras`                       | Não  | —           | Registra leitura; status calculado automaticamente   |
| `GET`    | `/leituras`                       | Não  | —           | Lista todas as leituras, ordenadas por data DESC     |
| `GET`    | `/leituras/{id}`                  | Não  | —           | Busca leitura por id                                 |
| `GET`    | `/leituras/sensor/{sensorId}`     | Não  | —           | Lista leituras de um sensor; filtro `?status=` opcional |
| `GET`    | `/leituras/satelite/{sateliteId}` | Não  | —           | Lista leituras de todos os sensores de um satélite   |
| `DELETE` | `/leituras/{id}`                  | Sim  | SUPERVISOR  | Exclui leitura individual                            |

---

## Filtro por status

Os endpoints `GET /leituras/sensor/{sensorId}` e `GET /leituras/satelite/{sateliteId}` aceitam o parâmetro `?status=` para filtrar por classificação.

| URL de exemplo                                       | Resultado                        |
|------------------------------------------------------|----------------------------------|
| `GET /leituras/sensor/3`                             | Todas as leituras do sensor 3    |
| `GET /leituras/sensor/3?status=CRITICO`              | Apenas leituras CRÍTICO          |
| `GET /leituras/sensor/3?status=ALERTA`               | Apenas leituras ALERTA           |
| `GET /leituras/satelite/1?status=CRITICO`            | Leituras críticas de todos os sensores do satélite 1 |

O parâmetro é case-sensitive e deve corresponder exatamente a um valor do enum `StatusLeitura`: `NORMAL`, `ALERTA` ou `CRITICO`.

---

## Contrato do IoT

O ESP32 envia `POST /leituras` sem token JWT:

**Request:**
```json
{
  "valor": 95.3,
  "sensorId": 3
}
```

**Não enviar:** `status`, `dataHoraLeitura` — calculados e definidos pela API.

**Response 201 Created:**
```json
{
  "id": 42,
  "valor": 95.3,
  "dataHoraLeitura": "2026-06-01T14:32:07.412",
  "status": "CRITICO",
  "sensorId": 3,
  "nomeSensor": "Termômetro Principal",
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "_links": {
    "self": { "href": "/leituras/42" },
    "deletar": { "href": "/leituras/42" },
    "sensor": { "href": "/sensores/3" },
    "satelite": { "href": "/satelites/1" }
  }
}
```

**Response 400** (campo obrigatório ausente):
```json
{
  "timestamp": "2026-06-01T14:32:07.412",
  "status": 400,
  "error": "sensorId: não deve ser nulo",
  "path": "/leituras"
}
```

---

## HATEOAS

Todo `LeituraResponse` inclui os seguintes links:

| Rel        | Método   | URL                        | Descrição                          |
|------------|----------|----------------------------|------------------------------------|
| `self`     | `GET`    | `/leituras/{id}`           | A própria leitura                  |
| `deletar`  | `DELETE` | `/leituras/{id}`           | Excluir esta leitura               |
| `sensor`   | `GET`    | `/sensores/{sensorId}`     | Sensor que gerou esta leitura      |
| `satelite` | `GET`    | `/satelites/{sateliteId}`  | Satélite do sensor                 |

---

## Testes

`StatusCalculatorTest` testa a lógica de classificação de forma isolada — sem Spring context (`@SpringBootTest`), sem Mockito. O sensor é criado diretamente com `new SensorTermico()` e os campos são setados manualmente.

**Sensor de referência:** `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%` (`zonaAlertaMin=8.0`, `zonaAlertaMax=72.0`)

| Cenário | Valor | margemAlerta | Resultado esperado | Justificativa |
|---------|------:|:------------:|:------------------:|---------------|
| Centro da faixa | 40.0 | 10% | NORMAL | Dentro de [8.0, 72.0] |
| Zona de alerta superior | 75.0 | 10% | ALERTA | 75 > zonaAlertaMax=72 |
| Acima do limiteMax | 95.0 | 10% | CRÍTICO | 95 > limiteMax=80 |
| Zona de alerta inferior | 5.0 | 10% | ALERTA | 5 < zonaAlertaMin=8 |
| Abaixo do limiteMin | -5.0 | 10% | CRÍTICO | -5 < limiteMin=0 |
| Exatamente na zonaAlertaMax | 72.0 | 10% | NORMAL | 72 não é > 72 (fronteira inclusiva) |
| Exatamente no limiteMax | 80.0 | 10% | ALERTA | 80 não é > 80 → cai em > zonaAlertaMax |
| Exatamente no limiteMin | 0.0 | 10% | ALERTA | 0 não é < 0 → cai em < zonaAlertaMin |
| Binário — NORMAL | 40.0 | 0% | NORMAL | Sem zona de alerta |
| Binário — CRÍTICO | 80.1 | 0% | CRÍTICO | 80.1 > limiteMax |
| Binário — NORMAL abaixo do max | 79.9 | 0% | NORMAL | 79.9 <= limiteMax, sem ALERTA |

---

## Erros

| Exceção                   | HTTP | Quando ocorre                                                          |
|---------------------------|:----:|------------------------------------------------------------------------|
| `EntityNotFoundException` | 404  | Leitura não encontrada pelo id                                         |
| `EntityNotFoundException` | 404  | Sensor informado no `sensorId` não existe (POST)                       |
| `EntityNotFoundException` | 404  | Sensor não encontrado para filtrar leituras (GET /sensor/{id})         |
| `EntityNotFoundException` | 404  | Satélite não encontrado para filtrar leituras (GET /satelite/{id})     |
| `AcessoNegadoException`   | 403  | Operador não é membro da missão do sensor da leitura (DELETE)          |
| `AcessoNegadoException`   | 403  | Operador é MEMBRO — DELETE exige SUPERVISOR ou DONO                    |
