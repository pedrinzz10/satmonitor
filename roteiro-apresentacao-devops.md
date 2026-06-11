# Roteiro — Apresentação DevOps · SatMonitor

**Duração total:** ~15 minutos · **Turma:** 2TDSPW · **Data:** 11/06/2026

---

## Quem fala o quê

| Integrante | Parte | Tempo |
|---|---|---|
| Fabrício | Abre a apresentação — explica o projeto | 2 min |
| **Pedro** | **Azure + Docker + CI/CD** | **7 min** |
| Henrique | Banco de dados | 2 min |
| Miguel | Monitoramento e testes | 2 min |
| Leonardo | Demo ao vivo + fecha a apresentação | 2 min |

---

---

# FABRÍCIO — Abertura

**Mostre na tela:** o diagrama de arquitetura (`docs/assets/ArquiteturaDevops.png`)

---

**Fale:**

> "Bom dia, eu sou o Fabrício. O SatMonitor é uma API em Java que monitora satélites em órbita em tempo real."

> "Funciona assim: uma estação terrestre cria missões, vincula satélites com sensores físicos, e quando um sensor manda uma leitura fora do limite, a API classifica como NORMAL, ALERTA ou CRÍTICO e gera o alerta automaticamente — sem intervenção humana."

*(aponta para o diagrama)*

> "O ESP32 manda as leituras direto pra API. O app mobile consome os endpoints. O Henrique cuida do banco. Toda essa infraestrutura roda na nuvem 24 horas por dia. O Pedro vai explicar como isso foi montado."

---

**Se perguntarem:** *"Por que o ESP32 não usa token?"*
> Dispositivos IoT não têm como gerenciar login. O endpoint de leitura é público por design — os outros todos são protegidos com JWT.

---

---

# PEDRO — Azure + Docker + CI/CD

> Você apresenta as três partes em sequência, sem passar a palavra.

---

## PEDRO · PARTE 1 — Infraestrutura Azure

**Mostre na tela:** Portal Azure com o Resource Group `rg-satmonitor` aberto

---

**Fale:**

> "Eu sou o Pedro, responsável pelo Java e pelo DevOps. Vou mostrar onde a aplicação roda, como ela é empacotada e como o deploy é automatizado."

> "A primeira decisão foi onde hospedar. O Azure tem o App Service, que faz tudo por você. Mas ele esconde a infraestrutura — sem controle de rede, sem Docker Engine, sem SSH. Para essa disciplina precisamos demonstrar domínio da infraestrutura, então escolhemos uma VM onde controlamos tudo."

*(aponta para o portal)*

> "Aqui está o Resource Group com todos os recursos que criamos:"

| Recurso | O que é | Para que serve no projeto |
|---|---|---|
| **Resource Group** `rg-satmonitor` | Pasta lógica no Azure | Agrupa todos os recursos — deleta tudo com um comando |
| **VNet** `vnet-satmonitor` | Rede privada isolada (`10.0.0.0/16` = 65 mil IPs) | Nenhum recurso externo acessa sem autorização |
| **Subnet** `subnet-satmonitor` | Subdivisão da VNet (`10.0.1.0/24` = 256 IPs) | Lote onde a VM fica — IP privado `10.0.1.4` |
| **NSG** `nsg-satmonitor` | Firewall de borda | Libera só porta 22 (SSH) e 8080 (API) — bloqueia tudo o mais |
| **IP Público** `pip-satmonitor` | Endereço `20.122.186.91` estático | Não muda se a VM reiniciar — CI/CD e app mobile dependem dele |
| **NIC** `nic-satmonitor` | Placa de rede virtual | Conecta a VM à subnet, ao IP público e ao NSG |
| **VM** `vm-satmonitor-RM562312` | Servidor Ubuntu 22.04 · 2 vCPUs · 7.8 GB | Roda o Docker Engine e os containers da aplicação |

---

**Se perguntarem:**

| Pergunta | Resposta curta |
|---|---|
| *"O que é o `/16` da VNet?"* | 16 bits fixos no IP, restam 16 livres = 65.536 endereços |
| *"Por que IP estático?"* | Dinâmico mudaria a cada restart — quebra o CI/CD e o app mobile |
| *"Por que não App Service?"* | Sem controle de rede, NSG e Docker Engine |
| *"O que é a NIC?"* | Placa de rede virtual — conecta a VM ao IP público e ao NSG. Separada para poder reconfigurar a rede sem recriar a VM |
| *"O NSG tem prioridades. O que significa?"* | Menor número = avaliado primeiro. Se duas regras conflitarem, a de menor número ganha |

---

## PEDRO · PARTE 2 — Docker

**Mostre na tela:** `Dockerfile` aberto no editor

---

**Fale:**

> "Com a VM pronta, empacotamos a aplicação com Docker. O Dockerfile usa build em dois estágios:"

- **Estágio 1** → JDK completo (~600 MB) compila o código e gera o JAR
- **Estágio 2** → JRE Alpine (~180 MB) só executa o JAR — imagem final 4x menor

> "Além do tamanho, tem um detalhe de segurança: o processo Java roda como o usuário `satuser`, nunca como root. Se alguém explorar uma vulnerabilidade, fica confinado — sem permissão para nada fora do JAR."

*(abre docker-compose)*

> "O docker-compose tem dois profiles:"

- `dev` → app + H2 em memória → para desenvolvimento local, sem instalar nada
- `docker` → app + PostgreSQL → produção na VM

> "Em produção, os dois containers ficam na mesma rede Docker interna chamada `satmonitor-net`. O banco não tem porta exposta no host — só a aplicação consegue falar com ele. O container da aplicação só sobe depois que o banco está saudável — sem isso, ela tentaria conectar antes do banco estar pronto e travaria."

*(roda no terminal da VM)*
```bash
docker compose --profile docker ps
```
> "Os dois containers estão rodando e saudáveis."

---

**Se perguntarem:**

| Pergunta | Resposta curta |
|---|---|
| *"Por que multi-stage?"* | Imagem 4x menor, sem compilador em produção — atacante não pode compilar código malicioso dentro do container |
| *"O EXPOSE abre a porta?"* | Não — é só documentação. A porta abre com `ports` no Compose |
| *"Por que não root?"* | Atacante que explorar vulnerabilidade fica confinado sem acesso root |
| *"O que é Alpine?"* | Distribuição Linux minimalista, ~5 MB, reduz o tamanho da imagem |
| *"Como a aplicação acha o banco pelo nome?"* | A rede Docker nomeada oferece DNS automático — `satmonitor-db` resolve para o IP interno do container |
| *"Qual a diferença do `depends_on` simples?"* | Simples espera o container iniciar. Com `service_healthy` espera o banco aceitar conexões de fato |

---

## PEDRO · PARTE 3 — CI/CD

**Mostre na tela:** `.github/workflows/deploy.yml` aberto

---

**Fale:**

> "Agora o ponto central: o pipeline de CI/CD. Todo push para a branch `main` dispara o pipeline automaticamente — nenhum deploy é feito à mão."

> "São dois jobs em sequência:"

**Job 1 — Testes**
- Roda num servidor temporário do GitHub, não na nossa VM
- Compila e executa todos os testes com `./gradlew test`
- Usa cache das dependências do Gradle — economiza ~40 segundos por execução
- Se falhar → o Job 2 nem começa

**Job 2 — Deploy** *(só roda se os testes passaram)*
- Entra na VM via SSH com chave Ed25519 guardada como secret no GitHub — nunca no código
- Roda `git reset --hard origin/main` → VM fica idêntica ao repositório, sem divergências
- Sobe os containers com `docker compose up --build -d`
- Faz polling no `/actuator/health` por até 2 minutos
  - ✓ HTTP 200 → pipeline verde
  - ✗ Sem resposta → pipeline vermelho + comentário automático no commit com link para os logs

*(aponta para o GitHub Actions)*
> "Aqui está um run bem-sucedido — os dois jobs, os logs do health check e o HTTP 200 confirmando que a API subiu."

---

**Se perguntarem:**

| Pergunta | Resposta curta |
|---|---|
| *"Por que `git reset --hard` e não `git pull`?"* | `git pull` falha se houver divergências locais. O reset descarta tudo e sincroniza sem intervenção |
| *"Por que a chave não fica no código?"* | Git guarda todo o histórico — a chave seria recuperável para sempre, mesmo depois de deletada |
| *"O health check impede o deploy?"* | Não — o deploy já aconteceu. O health check detecta falha na inicialização depois |
| *"Os testes rodam na VM?"* | Não, num runner do GitHub com H2. Se houver incompatibilidade SQL com PostgreSQL, os testes passam e a API quebra — o health check detecta isso |
| *"E se o SSH cair no meio do deploy?"* | O job falha, containers podem estar inconsistentes — precisaria entrar na VM manualmente para verificar |

---

---

# HENRIQUE — Banco de dados

**Mostre na tela:** terminal da VM

---

**Fale:**

> "Eu sou o Henrique, responsável pelo banco de dados."

> "Usamos PostgreSQL 16 em container. O dialeto é mais rico que o MySQL e tem comportamento equivalente ao Oracle da disciplina de BD."

> "O ponto mais importante é a persistência. Sem volume, qualquer `docker compose down` destruiria todos os dados. Declaramos um volume nomeado `satmonitor-db-data` — ele fica fora do ciclo de vida dos containers. Os dados sobrevivem a restarts, rebuilds e atualizações."

> "As credenciais nunca ficam no código — vêm do arquivo `.env` que existe só na VM. O Hibernate cria as 13 tabelas automaticamente na primeira execução."

*(roda no terminal)*
```bash
docker exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "\dt"
```

---

**Se perguntarem:**

| Pergunta | Resposta curta |
|---|---|
| *"Por que volume nomeado e não bind mount?"* | Bind mount causa conflito de permissões no PostgreSQL. Volume nomeado é gerenciado pelo Docker e evita isso |
| *"O `ddl-auto=update` é seguro?"* | Para contexto acadêmico sim. Em produção real usaríamos Flyway para migrações versionadas e controladas |
| *"Por que credenciais no `.env`?"* | Credenciais no código ficam no histórico Git para sempre. O `.env` existe só na VM, nunca no repositório |

---

---

# MIGUEL — Monitoramento e testes

**Mostre na tela:** browser com o health check

---

**Fale:**

> "Eu sou o Miguel, responsável pelo QA."

> "O Spring Actuator expõe o endpoint `/actuator/health` que retorna o status da aplicação e do banco em tempo real — sem nenhum código adicional. É exatamente esse endpoint que o pipeline do Pedro usa para confirmar que o deploy foi bem-sucedido. Se o banco estiver fora, retorna DOWN e o pipeline falha."

*(aponta para o browser)*
> "Aqui está `status: UP` — aplicação e banco saudáveis agora."

> "Para qualidade, criamos uma suite de testes em PowerShell com mais de 200 assertions: autenticação JWT, controle de roles, CRUD completo, os quatro tipos de sensor e geração automática de alertas."

> "E um script de security scan que testa vulnerabilidades reais: ataques JWT, acesso cruzado entre missões de usuários diferentes e escalada de privilégio."

---

**Se perguntarem:**

| Pergunta | Resposta curta |
|---|---|
| *"O que é IDOR?"* | Acessar recursos de outro usuário manipulando IDs na URL — o scan testa isso |
| *"O Actuator é código seu?"* | Não — é um módulo do Spring Boot que expõe endpoints de monitoramento automaticamente |

---

---

# LEONARDO — Demo ao vivo e encerramento

**Mostre na tela:** Swagger UI

---

**Fale:**

> "Eu sou o Leonardo. Para fechar, a API ao vivo."

*(abre `20.122.186.91:8080/swagger-ui.html`)*

> "Aqui está o Swagger UI com todos os endpoints documentados e disponíveis para teste interativo — agências, missões, satélites, sensores, leituras e alertas."

> "Resumindo o que apresentamos hoje: infraestrutura IaaS no Azure com rede e firewall configurados; Docker com multi-stage e isolamento de containers; pipeline CI/CD com gate de testes obrigatório; banco PostgreSQL com volume persistente e credenciais seguras; e monitoramento com Actuator e testes automatizados. Obrigado!"

---

---

# Checklist — fazer antes de entrar na sala

- [ ] VM ligada e containers UP → `docker compose --profile docker ps`
- [ ] API respondendo → `http://20.122.186.91:8080/actuator/health`
- [ ] GitHub Actions com último run verde visível
- [ ] Portal Azure aberto no Resource Group
- [ ] `Dockerfile` e `docker-compose.yml` abertos no editor
- [ ] `.github/workflows/deploy.yml` aberto
- [ ] Terminal SSH na VM pronto
- [ ] Browser com Swagger UI carregado

---

# Links

| | URL |
|---|---|
| API | http://20.122.186.91:8080 |
| Swagger UI | http://20.122.186.91:8080/swagger-ui.html |
| Health check | http://20.122.186.91:8080/actuator/health |
| GitHub Actions | https://github.com/pedrinzz10/satmonitor/actions |

> Dúvidas sobre perguntas do professor → consulte `perguntas-simulado-devops.md`
