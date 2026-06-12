# Perguntas Prováveis — Apresentação Java Advanced

---

## Spring Security / Autenticação

### Fáceis
- O que é JWT e por que usamos ele em vez de sessão?
- O que significa `SessionCreationPolicy.STATELESS`?
- Por que o CSRF está desabilitado nessa API?
- O que é BCrypt e por que a senha não é salva em texto puro?
- O que faz o `@AuthenticationPrincipal` no controller?

### Médias
- Por que `Operador` implementa `UserDetails` diretamente em vez de criar uma classe separada?
- O que acontece se o token JWT estiver expirado? Qual é o fluxo exato até o erro chegar no cliente?
- Por que apenas `POST /leituras` é público entre os endpoints de dados? O que impediria de deixar os GETs públicos também?
- O que é `OncePerRequestFilter` e por que é importante que o filtro execute exatamente uma vez?
- O que o `SecurityFilter` faz quando recebe um token inválido? Lança exceção ou deixa passar?

### Difíceis
- O `SecurityFilter` silencia a exceção de token inválido e deixa a requisição continuar sem autenticação. Por que essa abordagem é mais correta do que retornar 401 diretamente no filtro?
- O `addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)` garante que o nosso filtro execute antes do padrão do Spring. O que aconteceria se a ordem fosse invertida?
- O `AuthenticationEntryPoint` customizado retorna `{"erro":"Nao autenticado"}` em vez de deixar o Spring retornar o padrão. Qual é a diferença prática e por que isso importa para o cliente?
- Se dois requests chegarem simultaneamente com o mesmo login falhando, o `LoginRateLimiter` pode ter race condition? Como o `synchronized` no `Deque` resolve isso sem travar o `ConcurrentHashMap` inteiro?

---

## JPA / Hibernate / Modelo de Dados

### Fáceis
- O que é `@Entity` e `@Table`?
- O que é uma sequence no Oracle e por que usamos `allocationSize = 1`?
- O que é `FetchType.LAZY` e `FetchType.EAGER`? Onde cada um foi usado?
- O que é `CascadeType.ALL` e `orphanRemoval = true`?

### Médias
- Quais são as três estratégias de herança do JPA? Por que escolhemos `JOINED` para `Sensor` em vez de `SINGLE_TABLE`?
- O que é `@DiscriminatorColumn` e `@DiscriminatorValue`? Como o Hibernate usa eles para montar a query?
- Por que `OperadorMissao` usa `@IdClass` com uma classe interna em vez de `@EmbeddedId`? Qual a diferença entre as duas abordagens?
- Por que `CoordenadasOrbitais` é `@Embeddable` e não uma entidade separada com `@OneToOne`?
- O que é `@Transactional(readOnly = true)` e qual o benefício de usá-lo nos métodos de leitura?

### Difíceis
- No `listar` do `MissaoService`, há um N+1 explícito: uma query para listar missões e uma query por missão para buscar o role. Por que não resolvemos isso com um `JOIN FETCH`? Quais são os trade-offs?
- A classe `OperadorMissaoId` precisa implementar `Serializable` e sobrescrever `equals`/`hashCode`. O que acontece em tempo de execução se o `equals` não for implementado corretamente?
- O `@OneToMany` em `Sensor` para `LeituraSensor` usa `mappedBy = "sensor"`. O que acontece se removermos o `mappedBy` e deixarmos apenas o `@OneToMany`? Qual o impacto no banco?
- Por que `Agencia` é carregada com `FetchType.EAGER` no `Operador`, mas `Satelite` usa `FetchType.LAZY` no `Sensor`? Qual o risco de usar EAGER indiscriminadamente?

---

## Java 21

### Fáceis
- O que é um Java Record? Onde usamos no projeto?
- O que é `switch expression` em Java? Qual a diferença para o `switch statement` clássico?
- O que são anotações Bean Validation como `@NotBlank` e `@NotNull`? Quem as processa?

### Médias
- O que é `yield` dentro de um bloco `switch`? Por que ele é necessário quando o caso tem múltiplas linhas?
- O que é pattern matching em `switch`? Como o compilador sabe qual `case` usar em `switch(sensor) { case SensorTermico t -> ... }`?
- O `instanciarSubclasse` valida campos obrigatórios por tipo dentro do `switch`. Por que essa validação não está no Bean Validation do `SensorRequest`?

### Difíceis
- O pattern matching no `toResponse` tem um `default -> null` no final. Por que o compilador não consegue garantir que todos os casos estão cobertos sem o `default`, mesmo sabendo que `Sensor` só tem 4 subtipos?
- O `switch` no `instanciarSubclasse` é exaustivo sem `default` — se adicionar um quinto subtipo de `Sensor` sem atualizar o `switch`, o que acontece em tempo de compilação versus tempo de execução?

---

## Regras de Negócio

### Fáceis
- Quais são os três roles de uma missão e o que cada um pode fazer?
- O que é uma `SolicitacaoEntrada` e qual o seu ciclo de vida (status possíveis)?
- O que acontece quando um operador deleta uma missão? O que é removido em cascata?
- Por que a senha da missão não pode ser alterada via `PUT /missoes/{id}`?

### Médias
- Por que a verificação de cowork vem antes da verificação de senha no `solicitarEntrada`?
- `verificarRole` lança `EntityNotFoundException` (404) quando o operador não é membro, mas `buscarPorId` lança `AcessoNegadoException` (403) na mesma situação. Por que códigos HTTP diferentes para o mesmo cenário?
- O `sair` lança `EntityNotFoundException` (404) quando o operador não é membro, mas o `listarMembros` lança `AcessoNegadoException` (403). Qual a lógica semântica por trás dessa distinção?
- Por que o `removerMembro` verifica o role do operador logado ANTES de verificar se o membro alvo existe?

### Difíceis
- O que é IDOR (Insecure Direct Object Reference)? Como o `.filter(s -> s.getMissao().getId().equals(missaoId))` no `responderSolicitacao` previne especificamente esse ataque?
- Se removermos o guard de IDOR, qual seria o cenário de ataque exato? Quem poderia explorar e como?
- O `temPermissao` usa `this.ordinal() <= minimo.ordinal()`. Se a ordem dos valores no enum for alterada (ex: `MEMBRO, SUPERVISOR, DONO`), o sistema de permissões quebra silenciosamente. Como tornaria isso mais robusto?
- O `sair` verifica `countByMissaoIdAndRole(DONO) <= 1` antes de permitir a saída do único DONO. Há uma race condition aqui se dois DONOs tentarem sair simultaneamente? Como resolveria?

---

## StatusCalculator / Leituras

### Fáceis
- Quais são os três status possíveis de uma leitura?
- O que é `margemAlerta` e como ela afeta a classificação da leitura?
- Por que `dataHoraLeitura` é sempre definido pelo servidor e nunca pelo cliente?

### Médias
- Com `limiteMin=-10`, `limiteMax=90`, `margemAlerta=5%`: qual o status de um valor igual a `-10.0`? E de `85.0`? Justifique com base no código.
- O que acontece com `margemAlerta=0`? E com `margemAlerta=100`?
- Por que a leitura e o alerta são salvos na mesma `@Transactional`? O que aconteceria se fossem transações separadas?

### Difíceis
- Os operadores no `StatusCalculator` são estritos (`<` e `>`), não `<=` e `>=`. Isso significa que `valor == limiteMin` retorna ALERTA. Essa é uma decisão de design consciente ou um potencial bug? Defenda sua resposta.
- O `POST /leituras` é público — qualquer cliente sem token pode registrar uma leitura em qualquer sensor. Qual é o risco de segurança dessa decisão e como mitigaria sem quebrar a integração com IoT?

---

## Testes

### Fáceis
- O que é `@Mock` e `@InjectMocks`? Qual a diferença entre eles?
- O que é `@ExtendWith(MockitoExtension.class)`?
- O que é JaCoCo e o que ele mede?

### Médias
- Por que os testes unitários mockam os repositórios em vez de usar um banco em memória (H2)?
- O que é `@Nested` no JUnit 5? Qual o benefício de organizar testes dessa forma?
- O `StatusCalculatorTest` testa `valor == limiteMin` separadamente dos outros casos. Por que esse caso específico merece um teste próprio?

### Difíceis
- Os testes unitários mockam o `PasswordEncoder`. Isso significa que o BCrypt não é testado. Em que cenário um bug no encoding de senha passaria em todos os testes unitários mas quebraria em produção?
- O `MissaoServiceTest` tem 36 testes mas não testa o banco real. Um dos testes valida que `solicitarEntrada` rejeita operador já membro — mas o mock retorna o que você configurar. Como garantir que a query `existsByMissaoIdAndOperadorId` realmente funciona no Oracle e não apenas no mock?
- Se o JaCoCo indica 80% de cobertura no `MissaoService`, quais são os 20% prováveis que não estão cobertos e por quê isso ainda pode ser aceitável?

---

## Arquitetura / Decisões de Design

### Fáceis
- O que é HATEOAS e para que serve?
- O que é `@RestControllerAdvice` e o que o `GlobalExceptionHandler` faz?
- O que são DTOs e por que não retornamos as entidades diretamente?

### Médias
- Por que o `GlobalExceptionHandler` tem um `@ExceptionHandler(Exception.class)` como fallback final retornando 500 sem detalhes?
- O `buscarPorNome` é público e retorna missões sem verificar membership. Por que isso não é um vazamento de dados sensíveis?
- Por que as exceções customizadas (`AcessoNegadoException`, `EntityNotFoundException`, etc.) estendem `RuntimeException` e não `Exception`?

### Difíceis
- O `SecurityConfig` tem headers de segurança: CSP, HSTS, Referrer-Policy, Permissions-Policy. Para uma API REST consumida por mobile, qual desses headers tem impacto real e qual é irrelevante? Por quê?
- O `GlobalExceptionHandler` captura `AuthenticationException` e retorna sempre `"Credenciais inválidas"` — sem dizer se o login não existe ou se a senha está errada. Por que essa ambiguidade é uma decisão de segurança e não um bug?
- O projeto usa `@Transactional` nos services mas não nos controllers. Se um controller chamasse dois métodos de service sem `@Transactional` no controller, cada um executaria em sua própria transação. Em que cenário isso causaria inconsistência de dados nesse projeto?
