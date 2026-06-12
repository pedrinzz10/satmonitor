# Roteiro de Apresentação — Java Advanced

## Divisão por integrante

| Integrante | Slides | Documentação para estudar |
|------------|--------|--------------------------|
| Fabrício | Capa, Solução Proposta, Conclusão | `docs/api/Missao.md`, `docs/api/Satelite.md`, `docs/api/Sensor.md` |
| Pedro | Spring Security + JWT + Rate Limiter + Uso de IA | `docs/api/Auth.md`, `docs/internals/Exception.md` |
| Henrique | JPA (JOINED, @IdClass, @Embeddable) + Java 21 | `docs/api/Sensor.md`, `docs/api/Satelite.md`, `docs/api/Missao.md` |
| Miguel | Testes Unitários (estrutura + cobertura) | `docs/tests/UnitTests.md`, `docs/tests/IntegrationTests.md` |
| Leonardo | Regras de Negócio (RBAC, IDOR, solicitarEntrada) | `docs/internals/MissaoService.md`, `docs/internals/Exception.md`, `docs/api/Leitura.md` |

---

## Slide 1 — Capa
**Fabrício**

> "SatMonitor: API REST de monitoramento de satélites em tempo real. Java 21, Spring Boot 3.4.5, Oracle, JWT, 288 testes automatizados."

---

## Slide 2 — Solução Proposta
**Fabrício**

> "O problema: agências espaciais com sensores heterogêneos e equipes com permissões diferentes. A solução em 5 camadas: Missão → Satélite → Sensor → Leitura → Alerta gerado automaticamente. Operadores se vinculam a missões com roles — DONO, SUPERVISOR ou MEMBRO."

---

## Slide 3 — Spring Security: UserDetails, JWT e Filter Chain
**Pedro**

> "A entidade `Operador` implementa `UserDetails` diretamente — o Spring Security usa ela como principal sem adaptador. Nos controllers, `@AuthenticationPrincipal` injeta o operador já carregado, sem query extra."

> "O `TokenService` gera JWT assinado com HMAC256, validade de 8 horas. O `SecurityFilter`, estendendo `OncePerRequestFilter`, valida o token e injeta o operador no `SecurityContextHolder`. O `SecurityConfig` define sessão `STATELESS`, CSRF desabilitado, e apenas `POST /leituras` público — porque dispositivos IoT não gerenciam tokens."

📚 **Estudar:** `docs/api/Auth.md`, `docs/internals/Exception.md`

---

## Slide 4 — Rate Limiter
**Pedro**

> "Implementamos rate limiting sem biblioteca externa. `ConcurrentHashMap` onde a chave é o login e o valor é uma fila de timestamps de falhas. A cada tentativa, expiramos entradas com mais de 60 segundos e contamos o restante — 5 falhas lança 429 antes de consultar o banco."

---

## Slide 5 — JPA: JOINED, @IdClass e @Embeddable
**Henrique**

> "Três decisões de modelo. Herança `JOINED` para Sensor: quatro subtipos, cada um com sua tabela, discriminador `tipo_sensor`. `@IdClass` em `OperadorMissao` com chave composta `(operador_id, missao_id)`. E `CoordenadasOrbitais` como `@Embeddable` — os três campos vivem direto em `TB_SATELITE`, sem JOIN."

📚 **Estudar:** `docs/api/Sensor.md`, `docs/api/Satelite.md`, `docs/api/Missao.md`

---

## Slide 6 — Java 21: Factory, Pattern Matching e RBAC
**Henrique**

> "Factory com `switch` e `yield` para instanciar o subtipo correto de sensor com validação específica por tipo. Pattern matching no `toResponse` para extrair o detalhe sem casting manual."

> "E o controle de permissões inteiro em uma linha: `this.ordinal() <= minimo.ordinal()`. DONO é 0, SUPERVISOR é 1, MEMBRO é 2 — quem tem ordinal menor ou igual ao mínimo exigido passa."

---

## Slide 7 — Regras de Negócio
**Leonardo**

> "Três pontos principais. Em `solicitarEntrada`, a verificação de cowork vem antes da senha — agência incompatível não consegue nem tentar a senha por força bruta."

> "Em `responderSolicitacao`, uma linha previne IDOR: `.filter(s -> s.getMissao().getId().equals(missaoId))` — um Supervisor da missão 1 não responde solicitações da missão 2 mesmo sabendo o ID."

> "E o `StatusCalculator` usa operador estrito: valor exatamente no limite retorna ALERTA, não CRITICO."

📚 **Estudar:** `docs/internals/MissaoService.md`, `docs/internals/Exception.md`, `docs/api/Leitura.md`

---

## Slide 8 — Testes Unitários
**Miguel**

> "Vou mostrar a estrutura dos testes. O `MissaoServiceTest` tem 36 testes com `@Nested` por método do service e `@DisplayName` por cenário — repositórios mockados com `@Mock`, service instanciado com `@InjectMocks`."

> "O `StatusCalculatorTest` tem 11 testes cobrindo os casos de fronteira: `valor==limiteMin` retorna ALERTA porque o operador `<` é estrito. `margemAlerta=0` deixa o sensor binário. `margemAlerta=100` faz com que nunca retorne NORMAL. JaCoCo com cobertura acima de 80% nos services core, e 241 testes de integração no Postman."

📚 **Estudar:** `docs/tests/UnitTests.md`, `docs/tests/IntegrationTests.md`

---

## Slide 9 — Uso de IA
**Pedro**

> "Três contribuições diretas. IA identificou o caso `valor==limiteMin` no `StatusCalculator` que estava sem cobertura de teste. Diagnosticou o bug de `addFilterBefore` no `SecurityConfig` que bloqueava rotas públicas. E sugeriu o guard de IDOR que virou a linha 184 do `MissaoService`. Além disso, gerou os rascunhos iniciais da documentação dos endpoints, que revisamos com base no código real."

📚 **Estudar:** `docs/api/Auth.md`, `docs/internals/MissaoService.md`

---

## Slide 10 — Conclusão
**Fabrício**

> "14 entidades, 8 controllers, 9 invariantes de negócio, segurança em 6 camadas, 288 testes. Perguntas?"
