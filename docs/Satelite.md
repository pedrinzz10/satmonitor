# Satelite — Módulo de satélites

## Índice

1. [Visão geral](#visão-geral)
2. [Entidades](#entidades)
3. [Endpoints](#endpoints)
4. [Estatísticas](#estatísticas)
5. [HATEOAS](#hateoas)
6. [Erros](#erros)
7. [Decisão técnica — @Embeddable](#decisão-técnica--embeddable)

---

## Visão geral

Cada **Satélite** pertence a exatamente uma **Missão** e carrega sensores que geram leituras contínuas. O satélite é o nó central da hierarquia de monitoramento: `Missao → Satelite → Sensor → LeituraSensor`.

As **coordenadas orbitais** (altitude, inclinação e longitude do nodo) são modeladas como `@Embeddable` — um objeto de valor sem identidade própria cujos campos ficam diretamente na tabela `TB_SATELITE`. Essa escolha elimina um join desnecessário e reflete que as coordenadas não fazem sentido sem o satélite que as contém.

Os endpoints GET são **públicos** — qualquer cliente, incluindo o app Mobile e sistemas externos, pode consultar satélites sem autenticação. Criar, atualizar e excluir exigem token JWT e verificação de role na missão do satélite.

---

## Entidades

### Satelite (`TB_SATELITE`)

| Campo            | Tipo                  | Restrição                      | Descrição                                          |
|------------------|-----------------------|--------------------------------|----------------------------------------------------|
| `id`             | `Long`                | PK, sequence SEQ_SATELITE      | Identificador único                                |
| `nome`           | `String`              | NOT NULL                       | Nome do satélite                                   |
| `dataLancamento` | `LocalDate`           | NOT NULL                       | Data de lançamento                                 |
| `coordenadas`    | `CoordenadasOrbitais` | `@Embedded`                    | Objeto de valor embutido — campos em TB_SATELITE   |
| `missao`         | `Missao`              | FK, NOT NULL, LAZY             | Missão à qual o satélite pertence                  |
| `sensores`       | `List<Sensor>`        | CASCADE ALL, orphanRemoval     | Sensores do satélite — carregados sob demanda      |

### CoordenadasOrbitais (`@Embeddable` — sem tabela própria)

| Campo           | Coluna JPA           | Tipo     | Restrição    | Descrição                                                     |
|-----------------|----------------------|----------|--------------|---------------------------------------------------------------|
| `altitudeKm`    | `altitude_km`        | `Double` | NOT NULL     | Altitude orbital em quilômetros                               |
| `inclinacao`    | `inclinacao`         | `Double` | NOT NULL     | Ângulo de inclinação da órbita em graus                       |
| `longitudeNodo` | `longitude_nodo`     | `Double` | Opcional     | Longitude do nodo ascendente em graus — pode ser null         |

`CoordenadasOrbitais` é anotada com `@Embeddable` e embutida em `Satelite` com `@Embedded`. Seus três campos ficam diretamente em `TB_SATELITE` — nenhuma tabela extra é criada.

---

## Endpoints

| Método   | Rota                            | Auth | Role mínimo  | Descrição                                              |
|----------|---------------------------------|:----:|:------------:|--------------------------------------------------------|
| `POST`   | `/satelites`                    | Sim  | SUPERVISOR   | Cria novo satélite; nome deve ser único por missão     |
| `GET`    | `/satelites`                    | Não  | —            | Lista todos os satélites paginados                     |
| `GET`    | `/satelites/{id}`               | Não  | —            | Busca satélite por id                                  |
| `GET`    | `/satelites/missao/{missaoId}`  | Não  | —            | Lista satélites de uma missão específica               |
| `GET`    | `/satelites/{id}/estatisticas`  | Não  | —            | Estatísticas agregadas de leituras do satélite         |
| `PUT`    | `/satelites/{id}`               | Sim  | SUPERVISOR   | Atualiza nome, data e coordenadas                      |
| `DELETE` | `/satelites/{id}`               | Sim  | DONO         | Exclui satélite e todos os seus sensores e leituras    |

**Observação:** a verificação de role é feita em relação à **missão do satélite**, não à missão informada no request. Para POST, a missão é definida pelo `missaoId` do request. Para PUT e DELETE, é a missão atual do satélite.

---

## Estatísticas

`GET /satelites/{id}/estatisticas` retorna um `EstatisticasResponse` com dados agregados de **todas as leituras de todos os sensores** do satélite, calculados em uma única query JPQL com constructor expression.

| Campo           | Tipo            | Como é calculado                                              |
|-----------------|-----------------|---------------------------------------------------------------|
| `sateliteId`    | `Long`          | ID do satélite                                                |
| `nomeSatelite`  | `String`        | Nome do satélite                                              |
| `mediaValor`    | `Double`        | `AVG(leitura.valor)` — média de todos os valores registrados  |
| `minValor`      | `Double`        | `MIN(leitura.valor)` — menor valor registrado                 |
| `maxValor`      | `Double`        | `MAX(leitura.valor)` — maior valor registrado                 |
| `totalLeituras` | `Long`          | `COUNT(leitura.id)` — total de leituras                       |
| `totalAlertas`  | `Long`          | `SUM(CASE WHEN status = StatusLeitura.ALERTA)` — leituras em alerta  |
| `totalCriticos` | `Long`          | `SUM(CASE WHEN status = StatusLeitura.CRITICO)` — leituras críticas  |
| `ultimaLeitura` | `LocalDateTime` | `MAX(leitura.dataHoraLeitura)` — data da leitura mais recente |

Se o satélite não tiver leituras ainda, todos os campos numéricos retornam `0` e `ultimaLeitura` retorna `null`.

A query navega pela hierarquia: `Satelite → sensores → leituras`, agrupando por `s.id, s.nome` para garantir uma única linha por satélite. A comparação de status usa **literais de enum qualificados** (`br.com.fiap.satmonitor.leitura.enums.StatusLeitura.ALERTA`) em vez de strings, forma idiomática e robusta no Hibernate.

---

## HATEOAS

Todo `SateliteResponse` inclui os seguintes links, independente do role do requisitante:

| Rel             | Método   | URL                                    | Descrição                                   |
|-----------------|----------|----------------------------------------|---------------------------------------------|
| `self`          | `GET`    | `/satelites/{id}`                      | O próprio satélite                          |
| `atualizar`     | `PUT`    | `/satelites/{id}`                      | Editar o satélite                           |
| `deletar`       | `DELETE` | `/satelites/{id}`                      | Excluir o satélite                          |
| `estatisticas`  | `GET`    | `/satelites/{id}/estatisticas`         | Estatísticas agregadas                      |
| `sensores`      | `GET`    | `/sensores/satelite/{id}`              | Sensores do satélite                        |
| `missao`        | `GET`    | `/missoes/{missaoId}`                  | Missão à qual o satélite pertence           |

Os links `atualizar` e `deletar` são sempre incluídos no response; a verificação de autorização ocorre no service quando o cliente efetivamente chama esses endpoints.

---

## Erros

| Exceção                   | HTTP | Quando ocorre                                                         |
|---------------------------|:----:|-----------------------------------------------------------------------|
| `EntityNotFoundException` | 404  | Satélite não encontrado pelo id informado                             |
| `EntityNotFoundException` | 404  | Missão informada no `missaoId` não existe                             |
| `AcessoNegadoException`   | 403  | Operador não é membro da missão do satélite                          |
| `AcessoNegadoException`   | 403  | Operador é MEMBRO mas a operação exige SUPERVISOR ou DONO            |
| `AcessoNegadoException`   | 403  | Operador é SUPERVISOR mas a operação exige DONO (DELETE)             |
| `IllegalArgumentException`| 400  | Já existe um satélite com o mesmo nome nessa missão (ao criar)       |

---

## Decisão técnica — @Embeddable

`CoordenadasOrbitais` foi modelada como `@Embeddable` em vez de uma entidade separada porque é um **objeto de valor**: não tem identidade própria, não faz sentido existir sem o satélite que a contém, e seus atributos sempre mudam em conjunto.

Criar uma tabela `TB_COORDENADAS` com FK para `TB_SATELITE` adicionaria um join em **toda** consulta de satélite sem nenhum benefício — coordenadas não são compartilhadas entre satélites, nunca são consultadas isoladamente e não têm ciclo de vida independente.

Com `@Embeddable`, os três campos (`altitude_km`, `inclinacao`, `longitude_nodo`) ficam diretamente em `TB_SATELITE`, tornando a query de busca de satélite mais simples e o schema mais limpo — sem tabela extra, sem FK, sem join.
