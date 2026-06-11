# Perguntas do Simulado — DevOps · SatMonitor

Compilado de todas as perguntas feitas durante o simulado, organizadas por tema.
Estudem as respostas — o professor vai perguntar algo parecido.

---

## 1. Infraestrutura Azure

---

**Por que escolheram IaaS (VM) em vez do Azure App Service?**

> O App Service abstrai a camada de rede e orquestração — sem controle de rede, sem acesso ao Docker Engine, sem SSH direto. Na VM conseguimos configurar a rede Docker manualmente, isolar o banco na rede interna e definir as regras de firewall no NSG, o que demonstra domínio real da infraestrutura.

---

**O que especificamente vocês não conseguiriam fazer no App Service que fizeram na VM?**

> Criar a rede Docker interna `satmonitor-net`, isolar o banco na porta 5432 sem exposição pública, configurar o NSG com regras específicas por porta e acessar o servidor via SSH para administração.

---

**O IP público foi criado como Static. O que aconteceria se fosse Dynamic e a VM reiniciasse?**

> O IP mudaria a cada restart. Isso quebraria três coisas: o pipeline do GitHub Actions (IP hardcoded no `deploy.yml`), o health check (também usa o IP fixo) e o app mobile (base URL configurada com esse endereço).

---

**O que significa o `/16` da VNet e o `/24` da subnet? Quantos IPs cada um comporta?**

> A barra indica quantos bits são fixos no endereço IP. `/16` fixa os primeiros 16 bits — restam 16 livres, ou seja, 2¹⁶ = 65.536 IPs disponíveis na VNet. `/24` fixa 24 bits — restam 8 livres, 2⁸ = 256 IPs disponíveis na subnet.

---

**Por que a NIC foi criada como recurso separado da VM?**

> Porque isso permite reconfigurar a rede sem recriar a VM. É possível trocar o IP público, mover para outra subnet ou adicionar interfaces novas sem perder dados ou estado da máquina. Se a NIC fosse embutida na VM, qualquer mudança de rede exigiria destruir e recriar tudo.

---

**O NSG tem prioridades nas regras — 100, 110. O que a prioridade significa e o que acontece se duas regras conflitarem?**

> Prioridade menor é avaliada primeiro. O NSG percorre as regras em ordem crescente até encontrar uma que corresponda ao tráfego — aplica ela e ignora o resto. Se duas regras conflitarem na mesma porta, a de menor número ganha.

---

**A VM tem IP público e IP privado. Qual é cada um e quando cada um é usado?**

> O IP privado (`10.0.1.4`) é usado para comunicação interna dentro da VNet — entre recursos Azure. O IP público (`20.122.186.91`) é usado para acesso externo — SSH, API, CI/CD. A VM não "conhece" o IP público diretamente; o Azure faz NAT no gateway traduzindo o público para o privado.

---

## 2. Docker e Containerização

---

**O Dockerfile tem dois estágios. O que aconteceria se usassem só o primeiro estágio na imagem final?**

> A imagem final teria o JDK completo (~700 MB) em vez do JRE Alpine (~180 MB) — quatro vezes maior. Mais grave: o compilador `javac` ficaria na imagem de produção. Se um atacante explorar uma vulnerabilidade, poderia compilar e executar código arbitrário dentro do container.

---

**Por que o processo Java roda como `satuser` e não como `root`?**

> Com root, uma vulnerabilidade na aplicação daria ao atacante controle total do container — acesso a todos os diretórios, instalação de pacotes e potencial escape para o sistema operacional da VM. Com `satuser`, o processo fica confinado sem permissão para nada fora do `.jar`.

---

**Por que o banco não tem a porta 5432 exposta no host, e qual seria o risco se tivesse?**

> O banco só precisa ser acessado pela aplicação, não pela internet. Se a porta 5432 estivesse exposta, qualquer pessoa com o IP poderia tentar conectar diretamente no PostgreSQL — vazando dados, deletando tabelas ou executando comandos sem passar pela autenticação da API. O atacante pularia toda a camada de segurança do Spring Security.

---

**Por que há dois profiles no docker-compose (`dev` e `docker`) e o que aconteceria usando o `dev` em produção?**

> Os profiles separam os ambientes. O `dev` usa H2 em memória — toda vez que o container reiniciasse os dados sumiriam. Em produção isso seria catastrófico: qualquer restart da VM ou atualização de imagem apagaria todo o banco.

---

**O `docker compose up` foi rodado com `-d`. O que acontece sem esse flag?**

> Sem `-d` os containers rodam em foreground — o terminal fica travado e não aceita outros comandos. Mais crítico: os containers são filhos do processo SSH. Se a conexão cair ou o terminal fechar, os containers morrem junto e a API fica fora do ar.

---

**O `depends_on` usa `condition: service_healthy`. Qual a diferença do `depends_on` simples?**

> O `depends_on` simples espera o container do banco **iniciar**. Mas o PostgreSQL demora alguns segundos para estar pronto para aceitar conexões mesmo depois do container estar de pé. Com `condition: service_healthy`, a aplicação só sobe depois que o `pg_isready` confirmar que o banco aceita conexões — evitando race condition na inicialização.

---

**O volume usa `satmonitor-db-data`. O que acontece com os dados sem volume e com ele?**

> Sem volume: qualquer `docker compose down` destrói os dados do banco permanentemente. Com volume nomeado: os dados ficam fora do ciclo de vida do container — sobrevivem a restarts, rebuilds e atualizações de imagem. O Docker armazena o volume em `/var/lib/docker/volumes/` na VM.

---

**Por que o repositório foi clonado direto na VM em vez de usar um registry de imagens?**

> É uma decisão de contexto acadêmico. Com `git clone`, o código-fonte completo fica exposto na VM — se um atacante ganhar acesso ao servidor, leva o código inteiro. Com um registry (Docker Hub, GitHub Container Registry), a VM receberia apenas a imagem compilada — sem código-fonte, sem histórico, sem lógica de negócio legível.

---

**O `sudo usermod -aG docker $USER` foi rodado durante a instalação. O que ele faz e por que precisou de logout?**

> O `usermod -aG docker` **adiciona** o usuário `azureuser` ao grupo `docker` — não cria usuário. Isso permite rodar comandos Docker sem `sudo`. O logout é necessário porque o Linux carrega os grupos do usuário apenas no momento do login. A sessão atual não enxerga o novo grupo até reconectar.

---

## 3. CI/CD — GitHub Actions

---

**Todo push para `main` dispara o pipeline. Explique o fluxo completo.**

> Dois jobs em sequência. O primeiro roda `./gradlew test` — compila e executa todos os testes. Se falharem, o segundo job nem começa. Se passarem, o segundo job entra na VM via SSH com chave Ed25519 armazenada como secret no GitHub, roda `git reset --hard origin/main` para sincronizar o código e sobe os containers com `docker compose up --build -d`. Depois, faz polling no `/actuator/health` por até 2 minutos. Se a API não responder, o pipeline falha e um comentário é criado automaticamente no commit com link para os logs.

---

**Por que `git reset --hard` e não `git pull`?**

> O `git pull` falha se houver divergências entre o local e o remoto — arquivos modificados na VM, logs, qualquer coisa. Num pipeline automatizado não tem ninguém para resolver conflito. O `git reset --hard origin/main` descarta tudo localmente e força a VM a ficar idêntica ao repositório sem intervenção humana.

---

**O health check falhou após 2 minutos. O deploy já aconteceu nesse ponto?**

> Sim. O health check acontece depois que os containers já subiram. O `needs: test` impede deploy com código quebrado. O health check detecta falha na inicialização — banco demorando para subir, variável de ambiente errada no `.env`, ou a aplicação travando. O pipeline falha, mas os containers continuam no ar (possivelmente com problema).

---

**Os testes rodam no runner do GitHub, não na VM Azure. Qual a implicação disso?**

> O runner usa H2 em memória. Se houver incompatibilidade de SQL entre H2 e PostgreSQL, os testes passam no runner mas a aplicação quebra na VM. Por exemplo, queries com sintaxe específica do H2 que o PostgreSQL rejeita. O health check é a salvaguarda para esse cenário — detecta que a API não subiu mesmo depois dos testes passarem.

---

**O `--build` reconstrói a imagem a cada deploy. Qual o problema e o que produção real faria?**

> O rebuild causa downtime — a API fica fora do ar enquanto a imagem é construída e o container antigo é substituído pelo novo. Em produção real: a imagem seria publicada num registry, a VM só baixaria a imagem pronta (sem compilar), e usaríamos zero downtime deploy — sobe a nova versão em paralelo, valida que está saudável, só então derruba a antiga.

---

**Por que a chave SSH não pode ficar no código, mesmo em repositório privado?**

> O Git guarda todo o histórico de commits para sempre. Mesmo deletando a chave num commit posterior, ela continua acessível via `git log` em qualquer commit anterior. Além disso: ex-colaboradores têm o histórico clonado localmente, o repositório pode virar público no futuro e a própria conta do GitHub pode ser comprometida. O GitHub Secrets mantém a chave fora do repositório, injetada apenas durante a execução do pipeline.

---

**Como o pipeline evita baixar todas as dependências do Gradle a cada execução?**

> Com o `actions/cache`. O pipeline salva o diretório `~/.gradle/caches` usando o hash do `build.gradle` como chave. Se o arquivo não mudou, o hash é o mesmo e o cache é restaurado — economiza ~40 segundos por execução. Se o `build.gradle` mudar, o hash muda e o cache é invalidado automaticamente.

---

## 4. Banco de Dados

---

**Por que PostgreSQL e não MySQL?**

> O dialeto PostgreSQL é mais rico — suporta sequences nativas e tem comportamento equivalente ao Oracle que usamos na disciplina de BD. A imagem Alpine oficial é mais leve que a do MySQL (~396 MB vs ~600 MB).

---

**O `ddl-auto=update` cria as tabelas automaticamente. Por que isso seria problemático em produção real?**

> Em produção real o schema precisa ser gerenciado com migrações versionadas como Flyway. O `update` pode alterar tabelas silenciosamente — se você renomear um campo na entidade Java, o Hibernate cria a coluna nova mas não deleta a antiga, os dados ficam na coluna velha e a aplicação lê a nova vazia. Em tabelas grandes, o `ALTER TABLE` no startup pode travar a tabela por minutos, derrubando a aplicação para todos os usuários.

---

## 5. Segurança Geral

---

**A porta 22 está aberta para qualquer IP. Qual o risco e como mitigaria?**

> Com a porta 22 aberta para `*`, qualquer IP pode tentar se conectar — exposição a varreduras e exploração de vulnerabilidades do SSH. Mitigação aplicada: autenticação exclusivamente por chave Ed25519, sem senha. Melhoria para produção real: restringir a regra do NSG para aceitar conexão na porta 22 apenas dos IPs fixos da equipe. No contexto atual isso não é possível porque o GitHub Actions usa IPs dinâmicos para o deploy via SSH.

---

**Vocês identificaram alguma vulnerabilidade no projeto durante o desenvolvimento?**

> Sim. A porta 5432 do PostgreSQL estava exposta no NSG — foi criada durante a configuração inicial para validar a conectividade e removida após. O código-fonte fica na VM por causa do `git clone` em vez de um registry. E o `ddl-auto=update` seria substituído por Flyway em produção real.

---

## 6. Demonstração ao vivo

---

**Se eu pedir para fazer um deploy ao vivo agora, o que você faz?**

> Altero qualquer arquivo no repositório, faço o commit e o push para a branch `main`. O pipeline dispara automaticamente — abro a aba Actions no GitHub e mostro os dois jobs rodando em tempo real: testes passando e deploy sendo feito na VM. O health check confirma que a API subiu com HTTP 200.

---

---

## 7. Comandos Docker específicos

---

**Qual a diferença entre `docker compose down` e `docker compose stop`?**

> `stop` para os containers mas mantém tudo — containers, redes e volumes continuam existindo, prontos para reiniciar com `start`. `down` destrói os containers e as redes criadas pelo Compose. O volume `satmonitor-db-data` sobrevive ao `down` porque é nomeado — seria destruído apenas com `docker compose down -v`.

---

**O `EXPOSE 8080` no Dockerfile abre a porta de fato?**

> Não. `EXPOSE` é apenas documentação — indica que o container pretende usar essa porta. A porta só fica acessível de fora quando o `docker-compose.yml` mapeia `ports: "8080:8080"`. Sem o mapeamento no Compose, o `EXPOSE` não tem efeito prático.

---

**Qual a diferença entre `ports` e `expose` no docker-compose?**

> `ports` publica a porta no host — qualquer processo fora do Docker consegue acessar. `expose` torna a porta acessível apenas para outros containers na mesma rede Docker. O banco usa só rede interna, então não precisa de `ports` — só a aplicação o acessa.

---

**Por que o Dockerfile copia os arquivos do Gradle antes do código-fonte?**

```dockerfile
COPY gradle gradle
COPY gradlew .
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon   # ← baixa dependências primeiro

COPY src src                              # ← só depois copia o código
RUN ./gradlew bootJar --no-daemon
```

> Por causa do cache de camadas do Docker. Se você alterar só o código Java, o Docker reutiliza as camadas anteriores — incluindo a que baixou as dependências — e só recompila o código. Se o `COPY src` viesse antes, qualquer mudança no código invalidaria o cache das dependências e o Docker baixaria tudo novamente.

---

**O que é a imagem `eclipse-temurin:21-jre-alpine`? Por que Alpine?**

> Alpine Linux é uma distribuição Linux minimalista com ~5 MB, projetada para containers. A imagem `eclipse-temurin:21-jre-alpine` combina o JRE 21 com o Alpine — resulta em ~180 MB contra ~400 MB do JRE com Debian. Menor imagem significa download mais rápido no deploy, menos espaço em disco na VM e menor superfície de ataque.

---

**Qual a diferença entre `docker compose stop` e `docker kill`?**

> `stop` envia o sinal `SIGTERM` — o processo recebe um aviso e tem tempo para encerrar graciosamente, fechar conexões e salvar estado. `kill` envia `SIGKILL` — termina o processo imediatamente, sem aviso. Para uma aplicação Spring Boot com conexões de banco abertas, `stop` é sempre preferível.

---

**O que é um volume bind mount e por que não foi usado no projeto?**

> Bind mount vincula um diretório específico da VM ao container — ex: `./data:/var/lib/postgresql/data`. O problema é que as permissões do sistema de arquivos da VM e do container frequentemente conflitam no PostgreSQL, causando erros de inicialização. O volume nomeado é gerenciado pelo Docker e evita esse problema.

---

## 8. CI/CD — Detalhes do workflow

---

**O workflow tem `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true`. Por que isso existe?**

> É uma variável de ambiente que força as Actions JavaScript (como `actions/checkout`, `actions/setup-java`) a usarem o Node.js 24. Sem isso, versões mais antigas das Actions usariam Node.js 16 ou 20, que estão sendo descontinuados pelo GitHub. Evita avisos de deprecação nos logs do pipeline.

---

**O job de deploy tem `permissions: contents: read, statuses: write`. Para que serve o `statuses: write`?**

> O `statuses: write` permite que o pipeline crie comentários e atualize o status de commits no GitHub. É necessário para o passo final — quando o deploy falha, o pipeline usa `actions/github-script` para postar um comentário no commit. Sem essa permissão, o comentário não seria criado.

---

**O pipeline usa `appleboy/ssh-action`. O que essa Action faz?**

> É uma Action de terceiro que abstrai a conexão SSH. Ela recebe `host`, `username` e `key` como parâmetros, estabelece a conexão SSH e executa o bloco `script` na VM. Sem ela, seria necessário configurar manualmente a chave SSH no runner, adicionar o host ao `known_hosts` e executar os comandos via `ssh` diretamente — mais verboso e mais frágil.

---

**O que acontece se a conexão SSH cair no meio do deploy?**

> O job do GitHub Actions falha com erro de conexão. O `docker compose up --build` pode ter sido executado parcialmente — os containers podem estar em estado inconsistente na VM. O pipeline marca o deploy como falho, mas não faz rollback automático. Nesse caso é necessário entrar na VM manualmente e verificar o estado dos containers.

---

**Qual a diferença entre `git fetch origin` e `git pull`?**

> `git fetch` baixa as mudanças do remoto mas não aplica nada — só atualiza os ponteiros remotos localmente. `git pull` é um `fetch` + `merge` (ou `rebase`). No deploy, usamos `git fetch` seguido de `git reset --hard origin/main` para ter controle total sobre o que acontece — o fetch baixa, o reset aplica de forma destrutiva descartando qualquer mudança local.

---

## 9. Aplicação e Spring Boot

---

**O que é o Spring Actuator e por que ele está no projeto?**

> Spring Actuator é um módulo do Spring Boot que expõe endpoints de monitoramento sem código adicional. O mais importante é `/actuator/health` — retorna o status da aplicação e do banco de dados. No projeto, esse endpoint é usado pelo health check do docker-compose e pelo pipeline do GitHub Actions para confirmar que o deploy foi bem-sucedido.

---

**Por que `POST /leituras` é público — sem autenticação?**

> Porque o ESP32 (IoT) envia leituras continuamente sem capacidade de gerenciar tokens JWT. Dispositivos de hardware embarcado não têm fluxo de login — eles precisam enviar dados diretamente. Tornar esse endpoint público é uma decisão de arquitetura para integrar com IoT sem comprometer os outros endpoints protegidos.

---

**O que é `SPRING_PROFILES_ACTIVE=postgres` e o que muda quando está ativo?**

> Define qual profile do Spring Boot está ativo. Com `postgres`, o Spring carrega o arquivo `application-postgres.properties` em vez do `application.properties` padrão — trocando o banco H2 em memória pelo PostgreSQL, desabilitando o console H2 e carregando as variáveis de ambiente de conexão com o banco real.

---

**O que é `CORS_ALLOWED_ORIGINS` e por que é necessário?**

> CORS (Cross-Origin Resource Sharing) é um mecanismo de segurança dos browsers que bloqueia requisições feitas de um domínio diferente do da API. O app mobile e o frontend precisam chamar a API vindo de origens diferentes — sem configurar `CORS_ALLOWED_ORIGINS`, o browser bloquearia todas essas requisições. A variável define quais origens têm permissão.

---

## 10. Redes e Comunicação

---

**Como o container da aplicação consegue se conectar ao banco usando `satmonitor-db` como hostname?**

> O Docker Compose cria uma rede bridge nomeada `satmonitor-net`. Dentro dessa rede, o Docker registra automaticamente cada serviço pelo nome definido no `docker-compose.yml` como entrada DNS. Quando a aplicação tenta conectar em `satmonitor-db:5432`, o Docker resolve esse nome para o IP interno do container do banco — sem precisar saber o IP real.

---

**Qual a diferença entre o IP `127.0.0.1` e `0.0.0.0` no contexto da API?**

> `127.0.0.1` é o loopback — só o próprio container consegue acessar. `0.0.0.0` significa "escutar em todas as interfaces de rede" — o container aceita conexões de qualquer origem, incluindo de fora dele. O Spring Boot por padrão escuta em `0.0.0.0:8080`, o que permite que o mapeamento `ports: "8080:8080"` funcione.

---

**O que é NAT e como ele funciona no projeto?**

> NAT (Network Address Translation) é a tradução de endereços de rede. Quando uma requisição chega no IP público `20.122.186.91`, o gateway da VNet do Azure traduz automaticamente para o IP privado `10.0.1.4` da VM. A VM nunca "vê" o IP público — ela só conhece seu IP privado. O caminho de volta é feito pelo mesmo processo em sentido contrário.

---

## 11. Azure — Detalhes avançados

---

**O que aconteceria se você deletasse o Resource Group inteiro?**

> Todos os recursos seriam destruídos em cascata: VM, disco, NIC, IP público, NSG, VNet e subnet. A API ficaria completamente fora do ar e os dados do banco seriam perdidos permanentemente (a menos que haja backup externo). Um único comando — `az group delete --name rg-satmonitor` — faz tudo isso.

---

**Qual a diferença entre parar (`stop`) e desalocar (`deallocate`) a VM no Azure?**

> `stop` encerra o SO mas mantém os recursos alocados — a VM continua consumindo créditos. `deallocate` libera os recursos de hardware — a VM não consome créditos enquanto desalocada, mas o IP dinâmico mudaria ao reiniciar (por isso o IP estático é importante). Para economizar créditos durante a noite, o correto é desalocar.

---

**Por que foi escolhida a região East US 2 e não Brazil South?**

> Brazil South tem menor latência para o Brasil, mas os créditos de estudante FIAP têm restrições de quota por região. East US 2 oferece mais disponibilidade de tipos de VM dentro do limite de créditos. A diferença de latência para uma API de demonstração acadêmica é imperceptível.

---

*Estudem esse material. Qualquer pergunta fora daqui provavelmente vai ser variação de uma dessas.*
