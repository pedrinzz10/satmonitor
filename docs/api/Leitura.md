# Leitura — Leituras dos sensores

## Índice

1. [O que é uma leitura](#o-que-é-uma-leitura)
2. [Registrar leitura (IoT)](#registrar-leitura-iot)
3. [Como o status é calculado](#como-o-status-é-calculado)
4. [Caso especial: margemAlerta=0](#caso-especial-margemalerta0)
5. [Listar e filtrar leituras](#listar-e-filtrar-leituras)
6. [Excluir leitura](#excluir-leitura)
7. [Links HATEOAS](#links-hateoas)
8. [Erros](#erros)

---

## O que é uma leitura

Uma **leitura** é um valor registrado por um sensor em um determinado instante. A API recebe apenas o `valor` e o `sensorId` — e automaticamente define:

- **`dataHoraLeitura`** — o instante exato de recebimento (servidor, nunca o cliente)
- **`status`** — `NORMAL`, `ALERTA` ou `CRITICO`, calculado pelo `StatusCalculator`

O `POST /leituras` é **público** (sem token) porque o ESP32 (IoT) posta leituras contínuas sem gerenciar tokens JWT.

---

## Registrar leitura (IoT)

```bash
curl -s -X POST http://localhost:8080/leituras \
  -H "Content-Type: application/json" \
  -d '{
    "valor": 95.3,
    "sensorId": 1,
    "latitude": -23.5505,
    "longitude": -46.6333,
    "qualidade": "BOA"
  }'
```

**Resposta — 201 Created:**
```json
{
  "id": 42,
  "valor": 95.3,
  "dataHoraLeitura": "2026-06-01T14:32:07.412",
  "status": "CRITICO",
  "sensorId": 1,
  "nomeSensor": "Termometro Principal",
  "sateliteId": 1,
  "nomeSatelite": "SAT-01",
  "latitude": -23.5505,
  "longitude": -46.6333,
  "qualidade": "BOA",
  "_links": {
    "self":     { "href": "http://localhost:8080/leituras/42" },
    "deletar":  { "href": "http://localhost:8080/leituras/42" },
    "sensor":   { "href": "http://localhost:8080/sensores/1" },
    "satelite": { "href": "http://localhost:8080/satelites/1" }
  }
}
```

**Campos do request:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|:-----------:|-----------|
| `valor` | Double | Sim | Valor medido pelo sensor |
| `sensorId` | Long | Sim | ID do sensor que originou a leitura |
| `latitude` | Double | Não | Posição geográfica no momento da leitura |
| `longitude` | Double | Não | Posição geográfica no momento da leitura |
| `qualidade` | Enum | Não | `BOA` (padrão), `DEGRADADA` ou `INVALIDA` |

> **Nunca enviar:** `status` e `dataHoraLeitura` — calculados/definidos pelo servidor.

---

## Como o status é calculado

A classe `StatusCalculator` usa os parâmetros do sensor para classificar cada leitura:

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

**Ordem de avaliação (importa!):**

| Condição | Status |
|----------|:------:|
| `valor < limiteMin` | CRITICO |
| `valor > limiteMax` | CRITICO |
| `valor < zonaAlertaMin` | ALERTA |
| `valor > zonaAlertaMax` | ALERTA |
| (nenhuma acima) | NORMAL |

**Exemplo — Sensor Térmico:** `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`

```
faixa         = 90 - (-10) = 100
zonaAlertaMin = -10 + (100 × 0.05) = -5.0
zonaAlertaMax =  90 - (100 × 0.05) = 85.0

|──CRITICO──|─ALERTA─|──────────NORMAL──────────|─ALERTA─|──CRITICO──|
-10         -5       85                           90
```

| Valor enviado | Avaliação | Status |
|:-------------:|-----------|:------:|
| 150.0 | > 90 (limiteMax) | CRITICO |
| 87.0 | entre 85 e 90 (zona de alerta superior) | ALERTA |
| 40.0 | entre -5 e 85 (faixa normal) | NORMAL |
| -8.0 | entre -10 e -5 (zona de alerta inferior) | ALERTA |
| -50.0 | < -10 (limiteMin) | CRITICO |
| -10.0 | == limiteMin → não é `<` → vai para `< zonaAlertaMin` | ALERTA |
| 85.0 | == zonaAlertaMax → não é `>` → cai no NORMAL | NORMAL |

**Fronteiras:**
- `valor == limiteMin` → **ALERTA**
- `valor == limiteMax` → **ALERTA**
- `valor == zonaAlertaMax` → **NORMAL**

---

## Caso especial: margemAlerta=0

Quando `margemAlerta = 0`, não existe zona de alerta. O sistema funciona de forma binária: NORMAL ou CRITICO.

```
zonaAlertaMin = limiteMin
zonaAlertaMax = limiteMax
→ Apenas NORMAL ou CRITICO, sem ALERTA.
```

Útil para sensores onde qualquer desvio já é crítico — sem aviso prévio.

---

## Listar e filtrar leituras

Todos os GET de leituras são públicos — sem token.

### Listar todas as leituras

```bash
curl -s http://localhost:8080/leituras
# Paginado, 20 por página, ordenado por data DESC
```

### Buscar leitura por id

```bash
curl -s http://localhost:8080/leituras/42
```

### Leituras de um sensor (com filtro opcional)

```bash
# Todas as leituras do sensor 1
curl -s http://localhost:8080/leituras/sensor/1

# Apenas leituras CRITICO
curl -s "http://localhost:8080/leituras/sensor/1?status=CRITICO"
```

### Leituras de todos os sensores de um satélite

```bash
curl -s "http://localhost:8080/leituras/satelite/1?status=CRITICO"
```

> O parâmetro `?status=` é case-sensitive: use `NORMAL`, `ALERTA` ou `CRITICO` (maiúsculas).

---

## Excluir leitura

Exige **SUPERVISOR ou DONO** na missão do sensor.

```bash
curl -s -X DELETE http://localhost:8080/leituras/42 \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 204 No Content
```

---

## Links HATEOAS

| Link | Método | Destino |
|------|:------:|---------|
| `self` | GET | `/leituras/{id}` |
| `deletar` | DELETE | `/leituras/{id}` |
| `sensor` | GET | `/sensores/{sensorId}` |
| `satelite` | GET | `/satelites/{sateliteId}` |

---

## Erros

| Status | Situação |
|:------:|---------|
| 400 | `sensorId` ausente no request |
| 403 | Token ausente no `DELETE` |
| 403 | Não é membro da missão (no `DELETE`) |
| 403 | MEMBRO tentando excluir (exige SUPERVISOR) |
| 404 | Leitura não encontrada pelo id |
| 404 | `sensorId` não existe |
| 404 | Sensor ou satélite não encontrado nos filtros GET |
