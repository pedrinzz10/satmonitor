# Leitura — Leituras dos sensores

## Índice

1. [O que é uma leitura](#o-que-é-uma-leitura)
2. [Registrar leitura (IoT — público)](#registrar-leitura-iot--público)
3. [Como o status é calculado](#como-o-status-é-calculado)
4. [Caso especial: margemAlerta=0](#caso-especial-margemalerta0)
5. [Caso especial: margemAlerta=100](#caso-especial-margemalerta100)
6. [Geração automática de alerta](#geração-automática-de-alerta)
7. [Listar e filtrar leituras](#listar-e-filtrar-leituras)
8. [Excluir leitura](#excluir-leitura)
9. [Links HATEOAS](#links-hateoas)
10. [Erros](#erros)

---

## O que é uma leitura

Uma **leitura** é um valor registrado por um sensor em um determinado instante. A API recebe apenas `valor`, `sensorId` e dados opcionais — e automaticamente define:

- **`dataHoraLeitura`** — o instante exato de recebimento no servidor (nunca o cliente)
- **`status`** — `NORMAL`, `ALERTA` ou `CRITICO`, calculado pelo `StatusCalculator` com base nos limites do sensor

**Autenticação:**
- `POST /leituras` — **público, sem token** (dispositivos IoT como ESP32 não gerenciam tokens)
- Todos os GETs (`GET /leituras`, `GET /leituras/{id}`, etc.) — **exigem token JWT**

---

## Registrar leitura (IoT — público)

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

**Fluxo interno de `LeituraService.criar` (em 1 transação):**
1. Busca o `Sensor` pelo `sensorId` → 404 se não existe
2. Chama `StatusCalculator.calcular(valor, sensor)` → determina NORMAL, ALERTA ou CRITICO
3. Cria e salva `LeituraSensor` com `dataHoraLeitura=now()` e o `status` calculado
4. Se `status` != NORMAL: cria e salva `Alerta` automaticamente (na mesma transação)
5. Retorna `LeituraResponse`

---

## Como o status é calculado

A classe `StatusCalculator` usa os parâmetros do sensor para classificar cada leitura:

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

**Ordem de avaliação (a ordem importa):**

| Condição avaliada | Status retornado |
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

|──CRITICO──|──ALERTA──|──────────NORMAL──────────|──ALERTA──|──CRITICO──|
           -10         -5                          85         90
```

| Valor enviado | Avaliação | Status |
|:-------------:|-----------|:------:|
| 150.0 | > 90 (limiteMax) | CRITICO |
| 87.0 | entre 85 e 90 (zona de alerta superior) | ALERTA |
| 40.0 | entre -5 e 85 (faixa normal) | NORMAL |
| -8.0 | entre -10 e -5 (zona de alerta inferior) | ALERTA |
| -50.0 | < -10 (limiteMin) | CRITICO |

**Comportamento nas fronteiras exatas:**

| Valor | Resultado | Por quê |
|-------|-----------|---------|
| `-10.0` (== limiteMin) | ALERTA | `<` é estrito — não é `< -10`, mas é `< -5` |
| `90.0` (== limiteMax) | ALERTA | `>` é estrito — não é `> 90`, mas é `> 85` |
| `85.0` (== zonaAlertaMax) | NORMAL | `>` é estrito — `85 > 85` = false |
| `-5.0` (== zonaAlertaMin) | NORMAL | `<` é estrito — `-5 < -5` = false |

---

## Caso especial: margemAlerta=0

Quando `margemAlerta = 0`, não existe zona de alerta. O sistema funciona de forma binária:

```
zonaAlertaMin = limiteMin + 0 = limiteMin
zonaAlertaMax = limiteMax - 0 = limiteMax

→ Os checks de ALERTA nunca disparam (valor nunca é < limiteMin nem > limiteMax nessa ordem)
→ Resultado: apenas NORMAL ou CRITICO, sem ALERTA
```

Útil para sensores onde qualquer desvio já é crítico — sem aviso prévio.

---

## Caso especial: margemAlerta=100

```
zonaAlertaMin = limiteMin + faixa = limiteMax
zonaAlertaMax = limiteMax - faixa = limiteMin

→ Qualquer valor dentro dos limites está na "zona de alerta"
→ Resultado: dentro dos limites → ALERTA; fora → CRITICO. Nunca NORMAL.
```

---

## Geração automática de alerta

Quando uma leitura recebe status `ALERTA` ou `CRITICO`, um `Alerta` é criado automaticamente **na mesma transação** com:

```java
Alerta.builder()
    .leitura(leitura)
    .tipoAlerta(status.name())           // "ALERTA" ou "CRITICO"
    .descricao("Sensor 'X': valor Y fora dos limites [min, max]")
    .build()
// statusAlerta padrão = ATIVO (@Builder.Default na entity)
// dataAlerta padrão = LocalDateTime.now() (@Builder.Default)
```

Leituras `NORMAL` **não geram alerta**. Uma leitura pode ter no máximo 1 alerta associado (FK `leitura_id` na `TB_ALERTA`).

---

## Listar e filtrar leituras

Todos os GETs de leituras **exigem token JWT**.

### Listar todas as leituras

```bash
curl -s http://localhost:8080/leituras \
  -H "Authorization: Bearer SEU_TOKEN"
# Paginado, 20 por página, ordenado por dataHoraLeitura DESC
```

### Buscar leitura por id

```bash
curl -s http://localhost:8080/leituras/42 \
  -H "Authorization: Bearer SEU_TOKEN"
```

### Leituras de um sensor (com filtro opcional)

```bash
# Todas as leituras do sensor 1
curl -s http://localhost:8080/leituras/sensor/1 \
  -H "Authorization: Bearer SEU_TOKEN"

# Apenas leituras CRITICO
curl -s "http://localhost:8080/leituras/sensor/1?status=CRITICO" \
  -H "Authorization: Bearer SEU_TOKEN"
```

### Leituras de todos os sensores de um satélite

```bash
curl -s "http://localhost:8080/leituras/satelite/1?status=ALERTA" \
  -H "Authorization: Bearer SEU_TOKEN"
```

O parâmetro `?status=` é case-sensitive: use `NORMAL`, `ALERTA` ou `CRITICO` (maiúsculas).

Quando `?status=` é omitido, retorna todas as leituras do sensor/satélite independente do status.

---

## Excluir leitura

Exige **SUPERVISOR ou DONO** na missão do sensor. A validação percorre `Leitura → Sensor → Satelite → Missao` para obter o `missaoId` e então verifica o role.

```bash
curl -s -X DELETE http://localhost:8080/leituras/42 \
  -H "Authorization: Bearer SEU_TOKEN_SUPERVISOR"
# → 204 No Content
```

**Ao deletar uma leitura**, o alerta associado é removido antes (`alertaRepository.deleteByLeituraId(id)`) para não violar a FK. Só então a leitura é deletada.

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
| 401 | Token ausente em qualquer GET |
| 403 | Token ausente ou role insuficiente no `DELETE` |
| 403 | Não é membro da missão (no `DELETE`) |
| 403 | MEMBRO tentando excluir (exige SUPERVISOR) |
| 404 | Leitura não encontrada pelo id |
| 404 | `sensorId` não existe |
| 404 | Sensor ou satélite não encontrado nos filtros GET |
