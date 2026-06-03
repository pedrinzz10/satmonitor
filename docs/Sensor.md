# Sensor — Módulo de sensores

## Índice

1. [Visão geral](#visão-geral)
2. [Decisão técnica — Herança JOINED](#decisão-técnica--herança-joined)
3. [Entidades](#entidades)
4. [Como o status da leitura é calculado](#como-o-status-da-leitura-é-calculado)
5. [Endpoints](#endpoints)
6. [HATEOAS](#hateoas)
7. [Tipos de sensor](#tipos-de-sensor)
8. [Erros](#erros)
9. [Restrição de tipo imutável](#restrição-de-tipo-imutável)

---

## Visão geral

Os **Sensores** são os instrumentos de medição acoplados a cada satélite. Cada sensor define os limites aceitáveis de operação (`limiteMin`, `limiteMax`) e uma margem de alerta percentual (`margemAlerta`) que configura a zona de pré-alerta antes dos limites críticos serem atingidos.

Quando o ESP32 (IoT) registra uma leitura via `POST /leituras`, o `StatusCalculator` usa os parâmetros do sensor para classificar automaticamente o valor como **NORMAL**, **ALERTA** ou **CRÍTICO** — sem nenhuma intervenção humana.

Existem 4 tipos de sensor, cada um com um campo específico adicional. Todos herdam os campos comuns da classe base `Sensor` via estratégia JPA **JOINED**.

---

## Decisão técnica — Herança JOINED

### Estratégia escolhida: `InheritanceType.JOINED`

Com `@Inheritance(strategy = InheritanceType.JOINED)`, o JPA cria:
- **Uma tabela base** (`TB_SENSOR`) com todos os campos comuns e uma coluna discriminadora (`tipo_sensor`)
- **Uma tabela por subclasse** com apenas o campo específico + FK para `TB_SENSOR`

**Tabelas criadas no Oracle:**

| Tabela              | Conteúdo                                              |
|---------------------|-------------------------------------------------------|
| `TB_SENSOR`         | id, nome, unidade, limiteMin, limiteMax, margemAlerta, satelite_id, tipo_sensor |
| `TB_SENSOR_TERMICO` | id (FK), unidade_escala                               |
| `TB_SENSOR_PRESSAO` | id (FK), tipo_pressao                                 |
| `TB_SENSOR_RADIACAO`| id (FK), tipo_radiacao                                |
| `TB_MAGNETOMETRO`   | id (FK), eixos_medicao                                |

### Por que JOINED e não SINGLE_TABLE ou TABLE_PER_CLASS?

| Estratégia      | Vantagem                                 | Desvantagem                                              |
|-----------------|------------------------------------------|----------------------------------------------------------|
| `SINGLE_TABLE`  | Uma única tabela, sem joins              | Colunas específicas ficam nulas para outros tipos — ruim para Oracle |
| `JOINED`        | Sem colunas nulas, schema limpo          | Um join por consulta polimórfica                         |
| `TABLE_PER_CLASS` | Sem joins para consultas por tipo      | Consultas polimórficas usam UNION — ineficiente, sem suporte a sequence compartilhada |

`JOINED` foi escolhida porque:
- O Oracle é o banco de produção e colunas nulas em massa consomem espaço
- Os 4 tipos são consultados polimorficamente (via `SELECT * FROM TB_SENSOR`)
- Um único join é aceitável para uma hierarquia pequena de 4 subclasses
- A sequence `SEQ_SENSOR` é compartilhada — não funciona com `TABLE_PER_CLASS`

---

## Entidades

### Hierarquia de classes e tabelas

| Classe Java      | Tabela Oracle         | Campo específico   | Valores possíveis                        |
|------------------|-----------------------|--------------------|------------------------------------------|
| `Sensor`         | `TB_SENSOR`           | —                  | Classe base (não instanciar diretamente) |
| `SensorTermico`  | `TB_SENSOR_TERMICO`   | `unidade_escala`   | CELSIUS, FAHRENHEIT, KELVIN              |
| `SensorPressao`  | `TB_SENSOR_PRESSAO`   | `tipo_pressao`     | ABSOLUTA, RELATIVA                       |
| `SensorRadiacao` | `TB_SENSOR_RADIACAO`  | `tipo_radiacao`    | IONIZANTE, NAO_IONIZANTE                 |
| `Magnetometro`   | `TB_MAGNETOMETRO`     | `eixos_medicao`    | X, Y, Z, XY, XZ, YZ, XYZ                |

### Campos comuns — `TB_SENSOR`

| Campo          | Tipo     | Restrição                 | Descrição                                              |
|----------------|----------|---------------------------|--------------------------------------------------------|
| `id`           | `Long`   | PK, sequence SEQ_SENSOR   | Identificador único compartilhado por todas subclasses |
| `nome`         | `String` | NOT NULL                  | Nome do sensor                                         |
| `unidade`      | `String` | NOT NULL                  | Unidade de medida (ex: "°C", "hPa", "Gy")             |
| `limiteMin`    | `Double` | NOT NULL                  | Valor mínimo seguro — abaixo disso: CRÍTICO            |
| `limiteMax`    | `Double` | NOT NULL                  | Valor máximo seguro — acima disso: CRÍTICO             |
| `margemAlerta` | `Double` | NOT NULL, `@DecimalMin(0)`/`@DecimalMax(100)` | Percentual da faixa segura que define a zona ALERTA    |
| `satelite_id`  | `Long`   | FK TB_SATELITE, NOT NULL  | Satélite ao qual o sensor pertence                     |
| `tipo_sensor`  | `String` | Discriminador              | Valor automático: TERMICO, PRESSAO, RADIACAO, MAGNETOMETRO |

---

## Como o status da leitura é calculado

A lógica de classificação está em `leitura/service/StatusCalculator.java` e usa os parâmetros do sensor:

```
faixa         = limiteMax - limiteMin
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
```

| Condição                               | Status   |
|----------------------------------------|----------|
| `valor > limiteMax`                    | CRÍTICO  |
| `valor < limiteMin`                    | CRÍTICO  |
| `valor > zonaAlertaMax`                | ALERTA   |
| `valor < zonaAlertaMin`                | ALERTA   |
| dentro da faixa segura                 | NORMAL   |

**Exemplo numérico:** Sensor Térmico com `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%`

```
faixa         = 80 - 0 = 80
zonaAlertaMax = 80 - (80 × 10/100) = 72°C
zonaAlertaMin = 0  + (80 × 10/100) = 8°C
```

| Leitura | Zona                  | Status  |
|---------|-----------------------|---------|
| 95°C    | Acima de limiteMax    | CRÍTICO |
| 75°C    | Entre 72°C e 80°C     | ALERTA  |
| 40°C    | Entre 8°C e 72°C      | NORMAL  |
| 3°C     | Abaixo de zonaAlertaMin | ALERTA |
| -5°C    | Abaixo de limiteMin   | CRÍTICO |

---

## Endpoints

| Método   | Rota                              | Auth | Role mínimo | Descrição                                          |
|----------|-----------------------------------|:----:|:-----------:|----------------------------------------------------|
| `POST`   | `/sensores`                       | Sim  | SUPERVISOR  | Cria novo sensor; nome único por satélite          |
| `GET`    | `/sensores`                       | Não  | —           | Lista todos os sensores paginados                  |
| `GET`    | `/sensores/{id}`                  | Não  | —           | Busca sensor por id                                |
| `GET`    | `/sensores/satelite/{sateliteId}` | Não  | —           | Lista sensores de um satélite específico           |
| `PUT`    | `/sensores/{id}`                  | Sim  | SUPERVISOR  | Atualiza campos comuns — tipo e detalhe imutáveis  |
| `DELETE` | `/sensores/{id}`                  | Sim  | DONO        | Exclui sensor e todas as suas leituras (cascade)   |

**Observação:** a verificação de role é feita em relação à missão do satélite ao qual o sensor pertence. O caminho de navegação é: `Sensor → Satelite → Missao`.

---

## HATEOAS

Todo `SensorResponse` inclui os seguintes links:

| Rel          | Método   | URL                             | Descrição                            |
|--------------|----------|---------------------------------|--------------------------------------|
| `self`       | `GET`    | `/sensores/{id}`                | O próprio sensor                     |
| `atualizar`  | `PUT`    | `/sensores/{id}`                | Editar campos comuns do sensor       |
| `deletar`    | `DELETE` | `/sensores/{id}`                | Excluir o sensor e suas leituras     |
| `leituras`   | `GET`    | `/leituras/sensor/{id}`         | Leituras registradas por este sensor |
| `satelite`   | `GET`    | `/satelites/{sateliteId}`       | Satélite ao qual o sensor pertence   |

---

## Tipos de sensor

| Tipo (`tipo`)   | Campo no request | Enum associado   | Valores possíveis                |
|-----------------|------------------|------------------|----------------------------------|
| `TERMICO`       | `unidadeEscala`  | `UnidadeEscala`  | CELSIUS, FAHRENHEIT, KELVIN      |
| `PRESSAO`       | `tipoPressao`    | `TipoPressao`    | ABSOLUTA, RELATIVA               |
| `RADIACAO`      | `tipoRadiacao`   | `TipoRadiacao`   | IONIZANTE, NAO_IONIZANTE         |
| `MAGNETOMETRO`  | `eixosMedicao`   | `EixosMedicao`   | X, Y, Z, XY, XZ, YZ, XYZ        |

No request, `tipo` é o enum **`TipoSensor`** (`TERMICO`, `PRESSAO`, `RADIACAO`, `MAGNETOMETRO`), validado já na desserialização do JSON — valor desconhecido resulta em **400**. A desserialização é tolerante a maiúsculas/minúsculas (`spring.jackson.mapper.accept-case-insensitive-enums=true`).

Na resposta, `SensorResponse.tipo` devolve o mesmo valor canônico do enum (ex.: `"TERMICO"`) — simétrico ao request. O campo específico de cada tipo vem em `SensorResponse.detalhe` como String (ex: `"CELSIUS"`). No `PUT`, nem `tipo` nem `detalhe` são atualizados — apenas os campos comuns são modificáveis.

**Exemplo de request para sensor térmico:**
```json
{
  "nome": "Termômetro Principal",
  "unidade": "°C",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": 1,
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

---

## Erros

| Exceção                    | HTTP | Quando ocorre                                                          |
|----------------------------|:----:|------------------------------------------------------------------------|
| `EntityNotFoundException`  | 404  | Sensor não encontrado pelo id informado                                |
| `EntityNotFoundException`  | 404  | Satélite informado no `sateliteId` não existe                          |
| `AcessoNegadoException`    | 403  | Operador não é membro da missão do satélite do sensor                  |
| `AcessoNegadoException`    | 403  | Operador é MEMBRO mas a operação exige SUPERVISOR ou DONO              |
| `AcessoNegadoException`    | 403  | Operador é SUPERVISOR mas DELETE exige DONO                            |
| `IllegalArgumentException` | 400  | `limiteMin >= limiteMax`                                               |
| `IllegalArgumentException` | 400  | Já existe sensor com mesmo nome nesse satélite (ao criar)              |
| `HttpMessageNotReadableException` | 400 | `tipo` inválido — não é um valor de `TipoSensor` (rejeitado na desserialização) |
| `IllegalArgumentException` | 400  | Campo obrigatório ausente para o tipo (ex: `unidadeEscala` para TERMICO) |
| `IllegalArgumentException` | 400  | Valor do campo específico inválido para o enum (ex: `unidadeEscala = "GRAUS"`) |

---

## Restrição de tipo imutável

O tipo de um sensor (TERMICO, PRESSAO, RADIACAO, MAGNETOMETRO) **não pode ser alterado após a criação**. O `PUT /sensores/{id}` atualiza apenas os campos comuns (`nome`, `unidade`, `limiteMin`, `limiteMax`, `margemAlerta`).

Essa restrição existe porque o tipo do sensor determina qual tabela JPA contém o registro na estratégia JOINED. Alterar o tipo exigiria mover dados entre tabelas (`TB_SENSOR_TERMICO` → `TB_SENSOR_PRESSAO`, por exemplo), operação que não é suportada pelo JPA sem deleção e reinserção manual.

**Como proceder para mudar o tipo:**
1. `DELETE /sensores/{id}` — exclui o sensor e todas as suas leituras (cascade)
2. `POST /sensores` — cria novo sensor com o tipo desejado

Essa abordagem é intencional: sensores físicos raramente mudam de tipo. Quando ocorre, representa uma troca de hardware que justifica perder o histórico de leituras do sensor anterior.
