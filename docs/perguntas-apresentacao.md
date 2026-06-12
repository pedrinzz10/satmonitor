# Perguntas Prováveis — Apresentação Java Advanced

---

## Spring Security / Autenticação

### Fáceis

**O que é JWT e por que usamos ele em vez de sessão?**
JWT é um token auto-contido com claims assinados digitalmente. Usamos em vez de sessão porque é stateless — o servidor não armazena nada entre requisições. Cada token carrega as informações necessárias (subject = login) e o servidor só precisa validar a assinatura.

**O que significa `SessionCreationPolicy.STATELESS`?**
O Spring não cria nem usa `HttpSession`. Cada requisição precisa provar sua identidade pelo token — não há "estar logado" no servidor.

**Por que o CSRF está desabilitado nessa API?**
CSRF explora cookies — um site malicioso faz uma requisição usando o cookie de sessão do usuário sem ele saber. Como usamos JWT em header `Authorization` e não em cookies, não há superfície de ataque para CSRF.

**O que é BCrypt e por que a senha não é salva em texto puro?**
BCrypt é um algoritmo de hash unidirecional com salt automático. É impossível reverter o hash para obter a senha original. Se o banco vazar, as senhas continuam protegidas.

**O que faz o `@AuthenticationPrincipal` no controller?**
Extrai o principal do `SecurityContextHolder` e injeta diretamente no parâmetro do método. Evita chamar `SecurityContextHolder.getContext().getAuthentication()` manualmente — o operador já chega como objeto tipado `Operador`.

---

### Médias

**Por que `Operador` implementa `UserDetails` diretamente em vez de criar uma classe separada?**
Evita uma camada de adaptação desnecessária. O `Operador` já tem `login` (username) e `senha` (password) — implementar a interface significa que o Spring Security usa a entidade do banco diretamente como principal, e `@AuthenticationPrincipal` injeta o `Operador` completo sem query extra.

**O que acontece se o token JWT estiver expirado? Qual é o fluxo exato até o erro chegar no cliente?**
O `SecurityFilter` chama `tokenService.validarToken()`, que lança `JWTVerificationException`. O filtro captura a exceção no `catch`, loga o aviso e chama `filterChain.doFilter` sem setar autenticação. O Spring Security vê a requisição sem autenticação e aciona o `AuthenticationEntryPoint` customizado, que retorna `{"erro":"Nao autenticado"}` com status 401.

**Por que apenas `POST /leituras` é público entre os endpoints de dados?**
Satélites, sensores, leituras e alertas são dados operacionais sensíveis vinculados a missões. Deixá-los públicos exporia dados de missões para qualquer cliente sem autenticação. O `POST /leituras` é público porque dispositivos IoT (ESP32) enviam leituras sem gerenciar tokens JWT — exigir autenticação quebraria a integração com hardware.

**O que é `OncePerRequestFilter` e por que é importante que o filtro execute exatamente uma vez?**
Em cenários de forward ou dispatch interno do servlet container (ex: Spring MVC redirecionando internamente), um filtro comum poderia ser chamado múltiplas vezes na mesma requisição. `OncePerRequestFilter` garante execução única mesmo nesses casos, evitando que a autenticação seja processada duas vezes.

**O que o `SecurityFilter` faz quando recebe um token inválido?**
Silencia a exceção no bloco `catch` e chama `filterChain.doFilter` sem setar autenticação no `SecurityContextHolder`. A requisição segue sem autenticação. Se a rota exige autenticação, o Spring Security rejeita na etapa seguinte via `AuthenticationEntryPoint` com 401.

---

### Difíceis

**Por que o `SecurityFilter` silencia a exceção de token inválido em vez de retornar 401 diretamente no filtro?**
O filtro não pode assumir que toda requisição com token inválido deve retornar 401 — pode ser uma rota pública com token malformado no header. Retornar 401 no filtro quebraria rotas públicas que recebem qualquer valor no header `Authorization`. O design correto é: o filtro processa se consegue, o Spring Security decide se a autenticação é obrigatória com base nas regras do `SecurityConfig`.

**O que aconteceria se o `addFilterBefore` estivesse na posição errada?**
O `UsernamePasswordAuthenticationFilter` tentaria processar a requisição antes do nosso `SecurityFilter`. O `SecurityContextHolder` ainda estaria vazio quando o Spring Security verificasse a autenticação. Requisições com JWT válido chegariam como não autenticadas, retornando 401 em rotas que deveriam funcionar.

**Por que o `AuthenticationEntryPoint` customizado retorna JSON em vez do padrão do Spring?**
O padrão do Spring retorna uma página HTML de erro 401. Clientes mobile e IoT esperam JSON consistente com o resto da API. O customizado retorna `{"erro":"Nao autenticado"}` com `Content-Type: application/json`, mantendo o contrato da API uniforme.

**O `LoginRateLimiter` pode ter race condition? Como o `synchronized` resolve isso?**
Sim, se dois threads acessarem a mesma `Deque` simultaneamente sem sincronização, um poderia ler o tamanho como 4, o outro também como 4, e ambos passariam sem lançar exceção. O `synchronized` no `Deque` resolve porque o lock é por objeto `Deque` — ou seja, por login. Dois usuários diferentes não se bloqueiam. O `ConcurrentHashMap` garante que dois threads não criem duas `Deque`s para o mesmo login via `computeIfAbsent`.

---

## JPA / Hibernate / Modelo de Dados

### Fáceis

**O que é `@Entity` e `@Table`?**
`@Entity` marca a classe como entidade gerenciada pelo JPA — o Hibernate a mapeia para uma tabela. `@Table(name = "TB_SENSOR")` define explicitamente o nome da tabela no banco.

**O que é uma sequence no Oracle e por que usamos `allocationSize = 1`?**
Sequence é um objeto do banco que gera valores únicos incrementais. `allocationSize = 1` faz o Hibernate consultar a sequence a cada `save`, sem reservar blocos de IDs. Isso evita gaps no banco causados por blocos reservados que nunca foram usados.

**O que é `FetchType.LAZY` e `FetchType.EAGER`? Onde cada um foi usado?**
`LAZY` carrega a associação apenas quando acessada pelo código. `EAGER` carrega junto com a entidade principal. `Agencia` é `EAGER` no `Operador` — sempre necessária para verificar cowork no `SecurityFilter`. `Satelite` é `LAZY` no `Sensor` — raramente necessário ao carregar um sensor isolado.

**O que é `CascadeType.ALL` e `orphanRemoval = true`?**
`CascadeType.ALL` propaga todas as operações JPA (persist, merge, remove, refresh, detach) para as entidades filhas. `orphanRemoval = true` remove filhos que foram desassociados da coleção mesmo sem uma operação explícita de delete.

---

### Médias

**Quais são as três estratégias de herança do JPA? Por que escolhemos `JOINED` para `Sensor`?**
`SINGLE_TABLE`: todos os subtipos em uma tabela com colunas nulas. `TABLE_PER_CLASS`: tabela por subtipo com todos os campos duplicados. `JOINED`: tabela base com campos comuns e tabela por subtipo com campos específicos. Escolhemos `JOINED` porque normaliza o modelo — sem colunas nulas, cada subtipo tem só o que é seu.

**O que é `@DiscriminatorColumn` e `@DiscriminatorValue`? Como o Hibernate os usa?**
`@DiscriminatorColumn` define a coluna `tipo_sensor` na tabela base que identifica o subtipo. `@DiscriminatorValue("TERMICO")` é o valor que `SensorTermico` insere nessa coluna. Ao buscar um sensor, o Hibernate lê o discriminador e sabe qual JOIN fazer e qual classe Java instanciar.

**Por que `OperadorMissao` usa `@IdClass` e não `@EmbeddedId`?**
`@IdClass` usa campos primitivos (`Long`) na classe de chave e os campos `@Id` ficam diretamente na entidade. `@EmbeddedId` usaria a classe composta como um único campo na entidade. `@IdClass` é mais simples quando a chave é composta por duas associações `@ManyToOne`, evitando referência circular entre a classe embutida e a entidade.

**Por que `CoordenadasOrbitais` é `@Embeddable` e não uma entidade com `@OneToOne`?**
Coordenadas não têm identidade própria — só existem como parte de um `Satelite` e nunca são consultadas isoladamente. `@Embeddable` elimina a tabela extra e o JOIN, reduzindo complexidade sem nenhuma perda de normalização relevante. `SELECT * FROM TB_SATELITE` já traz `altitude_km`, `inclinacao` e `longitude_nodo` na mesma linha.

**O que é `@Transactional(readOnly = true)` e qual o benefício?**
Informa ao Hibernate e ao banco que a transação não fará escrita. O Hibernate desliga o dirty checking (não compara estado das entidades para flush) e o banco pode otimizar a execução. Em configurações com réplicas de leitura, pode rotear automaticamente para a réplica.

---

### Difíceis

**Por que o N+1 no `listar` do `MissaoService` foi aceito em vez de usar `JOIN FETCH`?**
`JOIN FETCH` exigiria uma query mais complexa retornando missão + role juntos, provavelmente com JPQL customizado. Para a escala do projeto — um operador pertence a poucas missões — o custo das queries extras é insignificante. A legibilidade e simplicidade do código justificam a escolha.

**O que acontece se o `equals` da `OperadorMissaoId` não for implementado corretamente?**
Dois objetos `OperadorMissaoId` com os mesmos IDs seriam considerados objetos diferentes pelo Java. O Hibernate usaria isso para detectar entidades no cache de primeiro nível — poderia tentar inserir duplicatas ou falhar com violação de constraint de unicidade ao salvar uma entidade que já existe.

**O que acontece se removermos o `mappedBy` do `@OneToMany` em `Sensor`?**
O JPA criaria uma tabela de junção intermediária (ex: `TB_SENSOR_LEITURAS`) além da FK já existente em `TB_LEITURA_SENSOR`. Haveria dados duplicados e queries desnecessárias — inserts em duas tabelas para cada associação.

**Por que `Agencia` é `EAGER` no `Operador` mas `Satelite` é `LAZY` no `Sensor`?**
O `SecurityFilter` acessa `operador.getAgencia()` fora do contexto de uma transação JPA — com `LAZY`, resultaria em `LazyInitializationException`. Já o `Satelite` no `Sensor` raramente é necessário ao processar leituras isoladas. `EAGER` indiscriminado carregaria objetos desnecessários a cada fetch, aumentando a quantidade de dados trafegados e o tempo de query.

---

## Java 21

### Fáceis

**O que é um Java Record? Onde usamos no projeto?**
Record é uma classe imutável com construtor canônico, `equals`, `hashCode` e `toString` gerados automaticamente. Usamos nos DTOs de request: `LeituraRequest`, `SensorRequest`, `RegistroRequest`, `MissaoRequest` — todos são records.

**O que é `switch expression` em Java? Qual a diferença para o `switch statement` clássico?**
`switch expression` retorna um valor e é exaustivo — o compilador exige que todos os casos sejam cobertos. `switch statement` executa blocos de código com `break`. `switch expression` pode ser atribuído diretamente a uma variável.

**O que são `@NotBlank` e `@NotNull`? Quem os processa?**
São anotações do Bean Validation (Jakarta Validation). `@NotBlank` rejeita nulo e string vazia/só espaços. `@NotNull` rejeita apenas nulo. O Spring processa com `@Valid` no controller — quando a requisição chega com campos inválidos, lança `MethodArgumentNotValidException`, capturado pelo `GlobalExceptionHandler`.

---

### Médias

**O que é `yield` dentro de um bloco `switch`? Por que é necessário?**
`yield` retorna o valor de um bloco dentro de um case com múltiplas instruções (com chaves `{}`). Em cases de expressão única (sem chaves), o valor é retornado diretamente com `->`. `yield` é necessário quando o case precisa executar código antes de retornar — como instanciar o objeto e configurar campos antes de retorná-lo.

**O que é pattern matching em `switch`? Como o compilador sabe qual `case` usar?**
O compilador verifica o tipo em tempo de execução usando `instanceof`. Cada `case SensorTermico t` é equivalente a `if (sensor instanceof SensorTermico t)`. O compilador analisa a hierarquia de tipos para saber quais cases são possíveis e compatíveis com o tipo do valor testado.

**Por que a validação de campos obrigatórios por tipo está no `switch` e não no Bean Validation?**
Bean Validation não expressa dependência condicional entre campos nativamente: "obrigatório se outro campo tem certo valor" exigiria um `@Constraint` customizado complexo. O `switch` resolve isso de forma explícita e legível — cada case valida o que precisa para o tipo específico.

---

### Difíceis

**Por que o compilador exige `default` no pattern matching de `toResponse`, mesmo com 4 subtipos mapeados?**
O compilador de pattern matching garante exaustividade apenas para hierarquias `sealed` (com `permits`). `Sensor` é uma classe aberta — qualquer código poderia criar um quinto subtipo em tempo de execução. Sem `sealed`, o compilador não tem como garantir que os 4 cases cobrem todos os subtipos possíveis.

**Se um quinto subtipo de `Sensor` for adicionado sem atualizar o `switch`, o que acontece?**
Em tempo de compilação, nada — o `switch` compila normalmente por causa do `default`. Em tempo de execução, o novo subtipo cai no `default -> null`, retornando `detalhe: null` no response silenciosamente, sem erro. Sem testes cobrindo o novo tipo, o bug passaria despercebido em produção.

---

## Regras de Negócio

### Fáceis

**Quais são os três roles de uma missão e o que cada um pode fazer?**
`DONO`: controle total — editar missão, deletar, remover membros, promover roles. `SUPERVISOR`: gerenciar membros — aprovar/rejeitar solicitações, atualizar status de alertas, deletar leituras. `MEMBRO`: somente leitura e saída da missão.

**O que é uma `SolicitacaoEntrada` e qual o seu ciclo de vida?**
Representa um pedido de um operador para entrar em uma missão mediante senha. Ciclo: `PENDENTE` (criada ao solicitar) → `APROVADO` ou `REJEITADO` (respondida por SUPERVISOR ou DONO). Uma solicitação só pode ser respondida uma vez.

**O que acontece quando um operador deleta uma missão?**
O cascade remove diretamente `OperadorMissao` e `SolicitacaoEntrada`. Os `Satelite`s são removidos pelo cascade da entidade `Missao`, que por sua vez dispara cascade nos `Sensor`es → `LeituraSensor`s → `Alerta`s das entidades filhas.

**Por que a senha da missão não pode ser alterada via `PUT /missoes/{id}`?**
Por design — `MissaoUpdateRequest` não tem o campo `senhaMissao`. Mudar a senha exigiria um endpoint dedicado com validação específica (ex: confirmar senha atual), para evitar que um DONO altere a senha acidentalmente numa atualização de outros campos.

---

### Médias

**Por que a verificação de cowork vem antes da verificação de senha no `solicitarEntrada`?**
Para impedir brute force de senha por agência incompatível. Se a verificação de senha viesse antes, uma agência bloqueada poderia tentar infinitas combinações de senha sem nunca ser barrada. Com cowork antes, agência incompatível recebe 403 imediatamente, sem chegar à verificação de senha.

**`verificarRole` lança 404 e `buscarPorId` lança 403 para o mesmo cenário de não-membro. Por quê?**
`verificarRole` busca o vínculo `OperadorMissao` — se não existe, semanticamente "o recurso (vínculo de autorização) não foi encontrado" → 404. `buscarPorId` verifica membership depois de confirmar que a missão existe — se o vínculo não existe, semanticamente "você não tem acesso a este recurso" → 403. O contexto define a semântica.

**`sair` lança 404 mas `listarMembros` lança 403 para não-membro. Qual a lógica?**
`sair`: o operador está tentando desfazer um vínculo que não existe — "não encontrado". `listarMembros`: o operador está tentando acessar dados de uma missão à qual não pertence — "acesso negado". A distinção é: no `sair`, o próprio recurso operado é o vínculo inexistente. No `listarMembros`, o recurso acessado é a lista de membros, e o vínculo inexistente é a falta de permissão.

**Por que `removerMembro` verifica o role do operador logado ANTES de verificar se o membro alvo existe?**
Para evitar vazamento de informação. Se verificasse existência antes, um MEMBRO receberia 404 para membro inexistente e 403 para membro existente — revelando indiretamente quem é membro da missão. Verificando role primeiro, qualquer não-DONO recebe 403 independentemente de o membro existir ou não.

---

### Difíceis

**O que é IDOR e como o `.filter()` em `responderSolicitacao` previne especificamente esse ataque?**
IDOR (Insecure Direct Object Reference) é quando um usuário acessa o recurso de outro passando o ID diretamente. Sem o filtro, um SUPERVISOR da missão 1 poderia chamar `POST /missoes/1/solicitacoes/99/responder` com `solicitacaoId=99` pertencente à missão 2, e a API processaria normalmente — pois só verificava o ID da solicitação. O `.filter(s -> s.getMissao().getId().equals(missaoId))` garante que a solicitação pertence à missão da URL; caso contrário, o `Optional` vira `empty` e lança 404.

**Se removermos o guard de IDOR, qual seria o cenário de ataque exato?**
O atacante é um SUPERVISOR legítimo de qualquer missão. Ele enumera IDs de solicitações (IDs são sequenciais — fácil de descobrir por tentativa). Passa o ID de uma solicitação da missão 2 na URL da missão 1 (`/missoes/1/solicitacoes/99/responder`). A API verificaria apenas que ele é SUPERVISOR da missão 1 — condição satisfeita — e aprovaria ou rejeitaria uma solicitação de uma missão que ele não gerencia.

**O `temPermissao` depende da ordem dos valores no enum. Como tornaria isso mais robusto?**
Usar um mapa explícito de prioridades: `Map.of(DONO, 0, SUPERVISOR, 1, MEMBRO, 2)` em vez de `ordinal()`. Assim, reordenar os valores no enum não quebraria a lógica silenciosamente. Outra opção seria tornar `Sensor` uma `sealed class` — mas para o enum, o mapa explícito é suficiente.

**Há race condition no `sair` quando dois DONOs tentam sair simultaneamente?**
Sim. Dois DONOs poderiam passar simultaneamente pelo check `countByMissaoIdAndRole(DONO) <= 1`, ambos veriam 2 DONOs, ambos deletariam o vínculo, e a missão ficaria sem DONO — violando o invariante. A solução robusta seria um lock pessimista com `SELECT FOR UPDATE` na tabela `TB_OPERADOR_MISSAO`, ou uma constraint de banco que garantisse o mínimo de 1 DONO.

---

## StatusCalculator / Leituras

### Fáceis

**Quais são os três status possíveis de uma leitura?**
`NORMAL`, `ALERTA` e `CRITICO`.

**O que é `margemAlerta` e como ela afeta a classificação?**
Define uma zona de transição entre `NORMAL` e `CRITICO`, em percentual da faixa total. Com 5%, os 5% próximos a cada limite são classificados como `ALERTA` em vez de `CRITICO`, servindo como aviso antes que o valor cruze o limite.

**Por que `dataHoraLeitura` é sempre definido pelo servidor?**
Para garantir que o timestamp é o momento real de recebimento. Dispositivos IoT podem ter clocks dessincronizados ou manipulados — aceitar o timestamp do cliente abriria a possibilidade de injetar leituras com datas falsas no histórico.

---

### Médias

**Com `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`: qual o status de `-10.0`? E de `85.0`?**
`valor=-10.0`: `< -10` é falso (CRITICO falha), `< zonaAlertaMin(-5)` é verdadeiro → **ALERTA**.
`valor=85.0`: `> 90` é falso (CRITICO falha), `> zonaAlertaMax(85)` é falso porque `>` é estrito (`85 > 85 = false`) → **NORMAL**.

**O que acontece com `margemAlerta=0`? E com `margemAlerta=100`?**
`margemAlerta=0`: `zonaAlertaMin == limiteMin` e `zonaAlertaMax == limiteMax`. Os checks de ALERTA nunca disparam porque o check de CRITICO (`< limiteMin`) já teria retornado antes. Resultado: apenas `NORMAL` ou `CRITICO`.
`margemAlerta=100`: `zonaAlertaMin == limiteMax` e `zonaAlertaMax == limiteMin`. Todo valor dentro dos limites é `< zonaAlertaMin` ou `> zonaAlertaMax` → sempre `ALERTA`. Nunca `NORMAL`.

**Por que leitura e alerta são salvos na mesma `@Transactional`?**
Se fossem transações separadas, a leitura poderia ser salva com sucesso mas o `save` do alerta falhar — deixando uma leitura `CRITICO` sem alerta no banco, quebrando a consistência dos dados. Na mesma `@Transactional`, qualquer falha faz rollback de ambos.

---

### Difíceis

**`valor == limiteMin` retorna ALERTA. Isso é uma decisão consciente ou um bug?**
Decisão consciente. O valor exatamente no limite ainda está tecnicamente "dentro dos limites" — não cruzou o threshold crítico. A zona de alerta serve exatamente para cobrir valores limítrofes perigosos. A semântica é: `CRITICO` = fora dos limites, `ALERTA` = dentro mas perigosamente perto. Usar `<=` no check de CRITICO mudaria essa semântica.

**O `POST /leituras` é público. Qual é o risco e como mitigaria sem quebrar IoT?**
Qualquer cliente sem token pode injetar leituras falsas em qualquer sensor, gerando alertas falsos e poluindo o histórico. A mitigação sem quebrar IoT seria: API key por dispositivo no header `X-Device-Key` (simples de configurar no firmware), ou vincular cada dispositivo a sensores específicos no cadastro — leituras de dispositivos não registrados seriam rejeitadas.

---

## Testes

### Fáceis

**O que é `@Mock` e `@InjectMocks`? Qual a diferença?**
`@Mock` cria um objeto simulado da dependência controlado pelo teste — você define o que ele retorna. `@InjectMocks` cria a instância real da classe sendo testada e injeta os mocks nas suas dependências automaticamente.

**O que é `@ExtendWith(MockitoExtension.class)`?**
Registra a extensão do Mockito no JUnit 5, que inicializa os mocks anotados com `@Mock` e `@InjectMocks` automaticamente antes de cada teste, sem precisar chamar `MockitoAnnotations.openMocks(this)` manualmente.

**O que é JaCoCo e o que ele mede?**
JaCoCo (Java Code Coverage) mede quais linhas, branches e instruções foram executadas durante os testes. Gera relatório HTML com percentual por classe. Configurado com `mvn test`, relatório em `target/site/jacoco/index.html`.

---

### Médias

**Por que os testes unitários mockam os repositórios em vez de usar banco em memória (H2)?**
Testes unitários isolam a lógica de negócio da infraestrutura. Com H2 seria um teste de integração — mais lento, dependente de schema, e testaria o repositório junto com o service. Mocks permitem testar cada cenário de forma isolada e determinística, sem depender de banco.

**O que é `@Nested` no JUnit 5? Qual o benefício?**
`@Nested` cria classes de teste aninhadas que agrupam cenários relacionados. O benefício é organização: todos os testes do método `solicitarEntrada` ficam juntos com contexto compartilhado (`@BeforeEach`), sem misturar com testes de outros métodos do mesmo service.

**Por que `valor == limiteMin` merece um teste específico no `StatusCalculatorTest`?**
Está na fronteira exata do operador. Um erro de `<=` vs `<` mudaria o comportamento apenas nesse ponto — o teste passaria para todos os outros valores mas quebraria no limite. Sem esse teste específico, um refactor que alterasse o operador passaria despercebido até aparecer em produção.

---

### Difíceis

**Um bug no BCrypt passaria em todos os testes unitários. Como?**
O mock configurado com `when(passwordEncoder.matches(...)).thenReturn(true)` sempre retorna `true`. Se o `BCryptPasswordEncoder` real tivesse um bug onde `encode` produz um hash que `matches` não reconhece (ex: versão incompatível da biblioteca), todos os testes passariam — mas o login quebraria em produção porque o mock nunca chama o algoritmo real.

**Como garantir que a query `existsByMissaoIdAndOperadorId` funciona no Oracle?**
Com um teste de integração apontando para banco Oracle real (ou Testcontainers com imagem Oracle). O teste unitário verifica que o service chama o método correto do repositório. O teste de integração verifica que a query JPQL gerada pelo Spring Data é válida no Oracle e retorna o resultado esperado — esses dois níveis se complementam.

**Se JaCoCo indica 80% no `MissaoService`, o que provavelmente são os 20% descobertos?**
Os branches de exceções que dependem de comportamento específico do banco (`DataIntegrityViolationException`), os cenários com múltiplos DONOs onde `countByMissaoIdAndRole > 1` (o caminho feliz do `sair`), e os branches do `toResponse` que dependem de contagens do banco. São aceitáveis porque seriam cobertos pelos testes de integração com banco real.

---

## Arquitetura / Decisões de Design

### Fáceis

**O que é HATEOAS e para que serve?**
Hypermedia as the Engine of Application State — cada resposta traz links para as operações disponíveis sobre aquele recurso. O cliente navega pela API seguindo os links, sem precisar conhecer as URLs de antemão. No projeto, os links variam por role: um MEMBRO não recebe links de `deletar` e `atualizar`.

**O que é `@RestControllerAdvice` e o que o `GlobalExceptionHandler` faz?**
`@RestControllerAdvice` é um bean que intercepta exceções lançadas pelos controllers antes de chegar ao cliente. O `GlobalExceptionHandler` captura cada tipo de exceção e retorna sempre o mesmo JSON: `{ timestamp, status, error, path }`.

**O que são DTOs e por que não retornamos as entidades diretamente?**
DTOs (Data Transfer Objects) são objetos com apenas os dados necessários para a resposta. Retornar entidades diretamente exporia campos internos (como `senha`), criaria risco de serialização circular (ex: `Sensor → Satelite → Sensor`), e acoplaria o contrato da API ao modelo do banco.

---

### Médias

**Por que o `GlobalExceptionHandler` tem um `@ExceptionHandler(Exception.class)` retornando 500 sem detalhes?**
Para nunca expor stack trace, queries SQL ou nomes de classes internas para o cliente. Qualquer exceção não mapeada retorna "Erro interno no servidor" — os detalhes aparecem apenas no log com nível `ERROR`, visível para o time de operações mas não para o cliente externo.

**O `buscarPorNome` é público e retorna missões sem verificar membership. Isso não é vazamento?**
Não. O response omite campos sensíveis: senha nunca aparece no `MissaoResponse` (omitida no `toResponse`), a lista de membros não é retornada, e dados operacionais ficam ocultos. É equivalente a uma listagem pública de missões disponíveis — só nome, status e informações não sensíveis.

**Por que as exceções customizadas estendem `RuntimeException` e não `Exception`?**
`RuntimeException` (unchecked) não precisa ser declarada na assinatura do método com `throws`. Services e controllers podem lançar essas exceções sem poluir as assinaturas de método — o `GlobalExceptionHandler` as captura de qualquer ponto da cadeia de chamada.

---

### Difíceis

**Para uma API REST consumida por mobile, quais security headers do `SecurityConfig` têm impacto real?**
`HSTS` tem impacto real — força HTTPS mesmo se o cliente tentar HTTP. `CORS` configurado corretamente é essencial para clientes web. Os demais (`CSP`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`) são headers de browser — sem impacto prático para clientes mobile ou IoT que não renderizam HTML.

**Por que o `GlobalExceptionHandler` retorna sempre "Credenciais inválidas" sem distinguir login inexistente de senha errada?**
Segurança por ambiguidade. Se o sistema dissesse "login não encontrado" vs "senha incorreta", um atacante poderia enumerar logins válidos: testa um login → resposta diferente → login existe. Com resposta única "Credenciais inválidas", o atacante não consegue distinguir os dois casos, eliminando esse vetor de enumeração.

**Se um controller chamasse dois services sem `@Transactional` no controller, qual seria o risco?**
Cada service executaria em sua própria transação. Se o primeiro `save` confirmar e o segundo falhar, o primeiro não seria desfeito — inconsistência de dados. Exemplo nesse projeto: se `missaoRepository.save` e `operadorMissaoRepository.save` no `criar` estivessem em transações separadas, uma falha no segundo save deixaria a missão criada sem DONO no banco.
