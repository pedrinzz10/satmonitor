# SatMonitor — API REST

Sistema de monitoramento de satélites em órbita. Desenvolvido como Global Solution 2026/1 da FIAP — 2TDS.

---

## Visão geral

Uma estação terrestre monitora missões espaciais. Cada missão agrupa satélites, cada satélite tem sensores, e cada sensor gera leituras contínuas. Quando uma leitura ultrapassa os limites configurados do sensor, o sistema classifica automaticamente como Alerta ou Crítico — sem intervenção manual.

Essa lógica de status automático é a principal regra de negócio da API e percorre todas as disciplinas do projeto: do IoT (ESP32) que capta o valor, à API Java que classifica, ao banco Oracle que processa com triggers PL/SQL, ao app Mobile que exibe o alerta em vermelho.

---

## Stack

- Java 21
- Spring Boot 4.0.6
- Gradle (Groovy)
- Spring Data JPA + Hibernate
- Spring Security + JWT (auth0/java-jwt 4.4.0)
- Spring HATEOAS
- Oracle Database (FIAP — produção) / H2 (desenvolvimento local)
- Lombok
- Springdoc OpenAPI 2.5.0 (Swagger UI)
- Docker (deploy em VM Azure)

---

## Arquitetura

### Organização por módulo de domínio

O projeto é organizado por módulo de domínio, não por camada técnica. Cada módulo é autocontido com suas próprias subpastas entity, dto, repository, service e controller.

```
br.com.fiap.satmonitor/
├── config/
├── exception/
├── auth/
├── missao/
├── satelite/
├── sensor/
└── leitura/
```

### Por que por módulo?

Cada domínio tem complexidade própria: herança JPA no sensor, roles na missão, StatusCalculator na leitura. Separar por módulo deixa essa independência explícita e facilita navegação — tudo relacionado a sensor fica em sensor/, tudo de missão em missao/.

---

## Modelo de dados

### Hierarquia de entidades (1:N encadeados)

```
Missao → Satelite → Sensor → LeituraSensor
```

### Entidades

**TB_OPERADOR**
- id (sequence SEQ_OPERADOR)
- login (unique, not null)
- senha (bcrypt, not null)
- nome (not null)
- role (OPERADOR / ADMIN)

**TB_MISSAO**
- id (sequence SEQ_MISSAO)
- nome, descricao, dataLancamento
- status (PLANEJADA / ATIVA / ENCERRADA)
- senhaMissao (bcrypt) — acesso via senha
- operadorDono (FK TB_OPERADOR) — quem criou

**TB_OPERADOR_MISSAO** — relacionamento N:N com role
- operadorId (FK), missaoId (FK) — chave composta
- role (DONO / SUPERVISOR / MEMBRO)
- dataEntrada

**TB_SATELITE**
- id (sequence SEQ_SATELITE)
- nome, dataLancamento
- altitudeKm, inclinacao, longitudeNodo (@Embedded CoordenadasOrbitais)
- missaoId (FK TB_MISSAO)

**TB_SENSOR** — classe base abstrata (@Inheritance JOINED)
- id (sequence SEQ_SENSOR)
- nome, unidade
- limiteMin, limiteMax (Double)
- margemAlerta (Double, %) — configura a zona de Alerta antes do Crítico
- sateliteId (FK TB_SATELITE)

Subclasses (JOINED — uma tabela por subclasse):
- TB_SENSOR_TERMICO → unidadeEscala (CELSIUS / FAHRENHEIT / KELVIN)
- TB_SENSOR_PRESSAO → tipoPressao (ABSOLUTA / RELATIVA)
- TB_SENSOR_RADIACAO → tipoRadiacao (IONIZANTE / NAO_IONIZANTE)
- TB_MAGNETOMETRO → eixosMedicao (X / Y / Z / XY / XZ / YZ / XYZ)

**TB_LEITURA_SENSOR**
- id (sequence SEQ_LEITURA)
- valor (Double)
- dataHoraLeitura (LocalDateTime)
- status (NORMAL / ALERTA / CRITICO) — NUNCA vem no POST, calculado pela API
- sensorId (FK TB_SENSOR)

---

## Regra de negócio principal — status automático da leitura

Quando chega um POST /leituras com { valor, sensorId }, a API:

1. Busca o Sensor pelo sensorId
2. Calcula a zona de alerta com base na margemAlerta:
    - zonaAlertaMax = limiteMax - (faixa * margemAlerta / 100)
    - zonaAlertaMin = limiteMin + (faixa * margemAlerta / 100)
3. Classifica o status:
    - valor < limiteMin → CRITICO
    - valor > limiteMax → CRITICO
    - valor dentro da zona de alerta → ALERTA
    - valor dentro da faixa segura → NORMAL
4. Persiste a leitura com o status calculado

Classe responsável: leitura/service/StatusCalculator.java

Exemplo: Sensor Térmico com limiteMin=0, limiteMax=80, margemAlerta=10%
- zonaAlertaMax = 72°C, zonaAlertaMin = 8°C
- Leitura 95°C → CRITICO (acima do limiteMax)
- Leitura 75°C → ALERTA (entre zonaAlertaMax e limiteMax)
- Leitura 40°C → NORMAL

---

## Roles de acesso às missões

Tabela OperadorMissao com 3 roles:

| Ação                        | DONO | SUPERVISOR | MEMBRO |
|-----------------------------|------|------------|--------|
| Ver missão                  | Sim  | Sim        | Sim    |
| Editar missão               | Sim  | Não        | Não    |
| Excluir missão              | Sim  | Não        | Não    |
| Gerenciar membros           | Sim  | Não        | Não    |
| Adicionar / editar satélite | Sim  | Sim        | Não    |
| Excluir satélite            | Sim  | Não        | Não    |
| Adicionar / editar sensor   | Sim  | Sim        | Não    |
| Configurar limites e margem | Sim  | Sim        | Não    |
| Excluir sensor              | Sim  | Não        | Não    |
| Ver leituras                | Sim  | Sim        | Sim    |
| Registrar leitura           | Sim  | Sim        | Sim    |
| Excluir leitura             | Sim  | Sim        | Não    |

Enum RoleMissao com método temPermissao(RoleMissao minimo) usando ordinal para comparação.

Fluxo de entrada: qualquer operador autenticado pode entrar numa missão apresentando a senha via POST /missoes/{id}/entrar. Role inicial = MEMBRO. O DONO promove depois se necessário.

---

## Autenticação e autorização

- JWT via auth0/java-jwt. Token válido por 8 horas. Issuer: "satmonitor"
- Secret via variável de ambiente: api.security.token.secret
- Senha sempre em BCrypt — nunca texto puro
- Rotas públicas: GET em todas as entidades, POST /leituras (IoT), POST /auth/login, POST /auth/registrar
- Rotas protegidas: POST/PUT/DELETE em missoes, satelites, sensores. Rotas de membros.
- POST /leituras é público porque o ESP32 (IoT) não gerencia tokens JWT

---

## Endpoints

### Auth
| Método | Rota              | Auth | Descrição                        |
|--------|-------------------|------|----------------------------------|
| POST   | /auth/registrar   | Não  | Registra novo operador           |
| POST   | /auth/login       | Não  | Retorna token JWT                |

### Missões
| Método | Rota                              | Auth | Role mínimo |
|--------|-----------------------------------|------|-------------|
| GET    | /missoes                          | Sim  | MEMBRO      |
| GET    | /missoes/{id}                     | Sim  | MEMBRO      |
| POST   | /missoes                          | Sim  | —           |
| PUT    | /missoes/{id}                     | Sim  | DONO        |
| DELETE | /missoes/{id}                     | Sim  | DONO        |
| POST   | /missoes/{id}/entrar              | Sim  | —           |
| POST   | /missoes/{id}/sair                | Sim  | MEMBRO      |
| GET    | /missoes/{id}/membros             | Sim  | MEMBRO      |
| DELETE | /missoes/{id}/membros/{opId}      | Sim  | DONO        |
| PATCH  | /missoes/{id}/membros/{opId}      | Sim  | DONO        |

### Satélites
| Método | Rota                          | Auth | Role mínimo |
|--------|-------------------------------|------|-------------|
| GET    | /satelites                    | Não  | —           |
| GET    | /satelites/{id}               | Não  | —           |
| GET    | /satelites/missao/{missaoId}  | Não  | —           |
| GET    | /satelites/{id}/estatisticas  | Não  | —           |
| POST   | /satelites                    | Sim  | SUPERVISOR  |
| PUT    | /satelites/{id}               | Sim  | SUPERVISOR  |
| DELETE | /satelites/{id}               | Sim  | DONO        |

### Sensores
| Método | Rota                            | Auth | Role mínimo |
|--------|---------------------------------|------|-------------|
| GET    | /sensores                       | Não  | —           |
| GET    | /sensores/{id}                  | Não  | —           |
| GET    | /sensores/satelite/{sateliteId} | Não  | —           |
| POST   | /sensores                       | Sim  | SUPERVISOR  |
| PUT    | /sensores/{id}                  | Sim  | SUPERVISOR  |
| DELETE | /sensores/{id}                  | Sim  | DONO        |

### Leituras
| Método | Rota                              | Auth | Role mínimo |
|--------|-----------------------------------|------|-------------|
| GET    | /leituras                         | Não  | —           |
| GET    | /leituras/{id}                    | Não  | —           |
| GET    | /leituras/sensor/{sensorId}       | Não  | —           |
| POST   | /leituras                         | Não  | —           |
| DELETE | /leituras/{id}                    | Sim  | SUPERVISOR  |

---

## Decisões técnicas

### Oracle — sequências em vez de IDENTITY
Oracle não suporta GenerationType.IDENTITY. Usar sempre @SequenceGenerator com allocationSize = 1.

Padrão:
```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_nome")
@SequenceGenerator(name = "seq_nome", sequenceName = "SEQ_NOME", allocationSize = 1)
private Long id;
```

### Herança em Sensor — JOINED
Estratégia InheritanceType.JOINED: uma tabela para a classe base (TB_SENSOR) e uma tabela por subclasse com apenas os campos extras + FK. Sem colunas nulas. Mais limpo para o Oracle.

### @Embeddable para CoordenadasOrbitais
CoordenadasOrbitais (altitudeKm, inclinacao, longitudeNodo) é @Embeddable dentro de Satelite. Sem tabela nova — campos ficam na TB_SATELITE.

### HATEOAS em todos os responses
Todos os responses de entidade estendem RepresentationModel. Records não funcionam com RepresentationModel — usar classes com Lombok. O método adicionarLinks() é privado e centralizado em cada controller.

### Paginação
Usar Pageable do Spring nas listagens de missões, satélites e leituras. @PageableDefault(size = 10) nos controllers.

### StatusCalculator separado
A lógica de cálculo do status da leitura fica em leitura/service/StatusCalculator.java separado do LeituraService. Facilita testes unitários isolados.

### Injeção de dependência
Sempre via construtor com @RequiredArgsConstructor do Lombok. Nunca @Autowired em campo.

### Tratamento de erros padronizado
GlobalExceptionHandler com @RestControllerAdvice retorna sempre ErroResponse com timestamp, status, error, path.

### CORS
Liberar apenas a origem do app Mobile em produção. Configurado no SecurityConfig.

### Banco de desenvolvimento
H2 em memória com spring.jpa.hibernate.ddl-auto=create-drop para desenvolvimento local. Oracle FIAP com ddl-auto=update em produção via application-prod.properties.

---

## Variáveis de ambiente (produção)

| Variável                      | Descrição                              |
|-------------------------------|----------------------------------------|
| api.security.token.secret     | Secret para assinar tokens JWT         |
| ORACLE_URL                    | URL JDBC do Oracle FIAP                |
| ORACLE_USER                   | Usuário Oracle                         |
| ORACLE_PASSWORD               | Senha Oracle                           |

---

## Estrutura de pastas completa

```
src/main/java/br/com/fiap/satmonitor/
├── SatmonitorApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── SwaggerConfig.java
│   └── JacksonConfig.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── EntityNotFoundException.java
│   ├── AcessoNegadoException.java
│   ├── SenhaMissaoInvalidaException.java
│   └── ErroResponse.java
├── auth/
│   ├── entity/Operador.java
│   ├── dto/LoginRequest.java
│   ├── dto/RegistroRequest.java
│   ├── dto/TokenResponse.java
│   ├── repository/OperadorRepository.java
│   ├── service/TokenService.java
│   ├── service/OperadorService.java
│   ├── security/SecurityFilter.java
│   └── controller/AuthController.java
├── missao/
│   ├── entity/Missao.java
│   ├── entity/OperadorMissao.java
│   ├── enums/StatusMissao.java
│   ├── enums/RoleMissao.java
│   ├── dto/MissaoRequest.java
│   ├── dto/MissaoUpdateRequest.java
│   ├── dto/MissaoResponse.java
│   ├── dto/EntrarMissaoRequest.java
│   ├── dto/MembroResponse.java
│   ├── repository/MissaoRepository.java
│   ├── repository/OperadorMissaoRepository.java
│   ├── service/MissaoService.java
│   └── controller/MissaoController.java
├── satelite/
│   ├── entity/Satelite.java
│   ├── entity/CoordenadasOrbitais.java
│   ├── dto/SateliteRequest.java
│   ├── dto/SateliteResponse.java
│   ├── dto/EstatisticasResponse.java
│   ├── repository/SateliteRepository.java
│   ├── service/SateliteService.java
│   └── controller/SateliteController.java
├── sensor/
│   ├── entity/Sensor.java
│   ├── entity/SensorTermico.java
│   ├── entity/SensorPressao.java
│   ├── entity/SensorRadiacao.java
│   ├── entity/Magnetometro.java
│   ├── enums/TipoSensor.java
│   ├── enums/UnidadeEscala.java
│   ├── enums/TipoPressao.java
│   ├── enums/TipoRadiacao.java
│   ├── enums/EixosMedicao.java
│   ├── dto/SensorRequest.java
│   ├── dto/SensorResponse.java
│   ├── repository/SensorRepository.java
│   ├── service/SensorService.java
│   └── controller/SensorController.java
└── leitura/
    ├── entity/LeituraSensor.java
    ├── enums/StatusLeitura.java
    ├── dto/LeituraRequest.java
    ├── dto/LeituraResponse.java
    ├── repository/LeituraRepository.java
    ├── service/LeituraService.java
    ├── service/StatusCalculator.java
    └── controller/LeituraController.java

src/main/resources/
├── application.properties
└── application-prod.properties

src/test/java/br/com/fiap/satmonitor/
├── StatusCalculatorTest.java
├── LeituraServiceTest.java
└── MissaoServiceTest.java

docs/
└── Auth.md   (gerado pelo prompt do módulo auth)

Dockerfile
docker-compose.yml
.env.example
README.md
```

---

## Deploy

- Container Docker em VM Azure
- Dockerfile com usuário não privilegiado e diretório de trabalho definido
- docker-compose.yml para ambiente local (app + H2)
- Variáveis de ambiente via -e no docker run ou arquivo .env (nunca commitado)
- Health check via /actuator/health

---

## Integração com outras disciplinas

| Disciplina      | Pessoa    | Como consome a API Java                                      |
|-----------------|-----------|--------------------------------------------------------------|
| Mobile          | Fabrício  | Consome todos os endpoints. Base URL = IP da VM Azure        |
| Oracle / PL/SQL | Henrique  | Schema separado. Triggers e procedures no banco Oracle FIAP  |
| IoT             | Miguel    | ESP32 faz POST /leituras com { valor, sensorId } sem token   |
| DevOps          | —         | Dockeriza a API Java. Container na VM Azure                  |
| .NET            | —         | API espelho para apresentação. Schema Oracle separado        |

---

## Contrato do IoT

O ESP32 envia para POST /leituras:

```json
{
  "valor": 95.3,
  "sensorId": 3
}
```

Não enviar campo "status" — calculado pela API. Endpoint público — sem token JWT.

Resposta de sucesso (201):

```json
{
  "id": 42,
  "valor": 95.3,
  "dataHoraLeitura": "2026-06-01T14:32:07",
  "status": "CRITICO",
  "sensorId": 3
}
```

---

## Swagger

Disponível em /swagger-ui.html após subir a aplicação.