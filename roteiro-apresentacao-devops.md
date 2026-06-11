# Roteiro — Apresentação DevOps · SatMonitor

**Disciplina:** DevOps Tools & Cloud Computing  
**Turma:** 2TDSPW  
**Data:** 11/06/2026  
**Duração total:** ~15 minutos

---

## Divisão por integrante

| # | Integrante | RM | Parte | Tempo |
|---|---|---|---|---|
| 1 | Fabrício Henrique Pereira | RM563237 | Abertura e visão geral | 2 min |
| 2 | **Pedro Henrique de Oliveira** | **RM562312** | **Azure + Docker + CI/CD** | **7 min** |
| 3 | Henrique Sinkevicius Maran | RM562977 | Banco de dados | 2 min |
| 4 | Miguel Henrique Oliveira Dias | RM565492 | Monitoramento e testes | 2 min |
| 5 | Leonardo José Pereira | RM563065 | Demo ao vivo e encerramento | 2 min |

> Pedro apresenta as três partes mais técnicas em sequência contínua (~7 min).

---

---

## PARTE 1 — Fabrício · Abertura e visão geral

**Tempo:** ~2 minutos

---

### Fala

> "Bom dia, eu sou o Fabrício. Vou abrir a apresentação explicando o que é o SatMonitor e qual problema ele resolve."

> "O SatMonitor é uma API REST desenvolvida em Java com Spring Boot para monitorar satélites em órbita em tempo real. Uma estação terrestre cria missões espaciais, cada missão agrupa satélites, cada satélite carrega sensores físicos — de temperatura, pressão, radiação ou campo magnético — e esses sensores enviam leituras continuamente."

> "O diferencial é que a API classifica cada leitura automaticamente como NORMAL, ALERTA ou CRÍTICO com base nos limites configurados para aquele sensor. Se ultrapassar o limite, um alerta é gerado sem nenhuma intervenção humana."

*(apontar para o diagrama)*

> "Aqui as peças se conectam: o ESP32 envia leituras diretamente para a API via POST sem token — porque dispositivos IoT não gerenciam autenticação. O app mobile consome os endpoints protegidos com JWT. O Henrique mantém o banco de dados. E toda essa infraestrutura roda na nuvem 24 horas por dia — o Pedro vai explicar como."

---

### Tela
- Diagrama de arquitetura (`docs/assets/ArquiteturaDevops.png`)

### Perguntas que podem surgir
- *"Por que o ESP32 não usa token?"* — dispositivos embarcados não têm fluxo de login; o endpoint `POST /leituras` é público por design.

---

---

## PARTES 2, 3 e 4 — Pedro · Azure + Docker + CI/CD

> Pedro apresenta as três seções em sequência contínua sem passagem de palavra.

---

## PARTE 2 — Pedro · Infraestrutura Azure

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Pedro, responsável pelo Java e pelo DevOps. Vou cobrir as três camadas mais técnicas: onde a aplicação roda, como ela é empacotada e como o deploy acontece automaticamente."

> "A primeira decisão foi onde hospedar. O Azure oferece o App Service — uma solução PaaS onde você sobe o código e ele cuida do resto. Mas o App Service abstrai a camada de rede e orquestração: sem controle de rede, sem acesso ao Docker Engine, sem SSH direto. Para a disciplina de DevOps precisamos demonstrar domínio da infraestrutura. Por isso escolhemos IaaS — uma VM onde controlamos cada camada."

*(apontar para o portal Azure)*

> "Criamos um Resource Group chamado `rg-satmonitor` que agrupa todos os recursos. Dentro dele temos uma VNet com range `10.0.0.0/16` — 65 mil endereços disponíveis — e uma subnet com `10.0.1.0/24` onde a VM recebe o IP privado. O NSG funciona como firewall e libera apenas as portas 22 para SSH e 8080 para a API — tudo o mais é bloqueado pela regra padrão do Azure. O IP público é estático: `20.122.186.91` não muda mesmo se a VM reiniciar — se fosse dinâmico, quebrariam o pipeline, o health check e o app mobile. A VM roda Ubuntu 22.04 LTS com 2 vCPUs e 7.8 GB de RAM."

---

### Tela
- Portal Azure: Resource Group com todos os recursos listados
- NSG com as regras de entrada

### Perguntas que podem surgir
- *"O que é o `/16` da VNet?"* — os primeiros 16 bits são fixos, restam 16 livres, 2¹⁶ = 65.536 IPs.
- *"Por que IP estático?"* — IP dinâmico mudaria a cada restart, quebrando CI/CD e o app mobile.
- *"Por que não App Service?"* — sem controle de rede, NSG e Docker Engine configurável.
- *"O que é a NIC?"* — interface de rede virtual que conecta a VM à subnet, ao IP público e ao NSG. Separada da VM para permitir reconfiguração sem recriar o servidor.
- *"O que acontece se deletar o Resource Group?"* — todos os recursos são destruídos em cascata, API fora do ar permanentemente.

---

## PARTE 3 — Pedro · Docker

**Tempo:** ~2,5 minutos

---

### Fala

> "Com a infraestrutura pronta, empacotamos a aplicação com Docker. Sem Docker, a VM precisaria ter Java 21, PostgreSQL e todas as dependências instaladas diretamente no SO — qualquer atualização poderia quebrar o ambiente. Com Docker, o ambiente é código — idêntico no notebook local e na VM de produção."

*(abrir Dockerfile)*

> "Nosso Dockerfile usa build multi-stage. No primeiro estágio usamos o JDK completo para compilar o projeto e gerar o JAR — esse estágio pesa ~600 MB. No segundo estágio usamos só o JRE Alpine para executar o JAR — ~180 MB. A imagem final fica quatro vezes menor, sem ferramentas de build, com menor superfície de ataque. Outro detalhe importante: criamos o usuário `satuser` e o processo Java roda como esse usuário, nunca como root. Se alguém explorar uma vulnerabilidade na aplicação, fica confinado — sem permissão para nada fora do JAR."

*(abrir docker-compose)*

> "O docker-compose tem dois profiles. O profile `dev` sobe a aplicação com H2 em memória — para desenvolvimento local sem instalar nada. O profile `docker`, usado em produção, sobe dois containers na rede interna `satmonitor-net`: a aplicação na porta 8080 exposta no host, e o PostgreSQL com a porta 5432 apenas na rede interna — nunca exposta à internet. O container da aplicação só sobe depois que o banco está saudável, via `condition: service_healthy` — sem isso, a aplicação tentaria conectar no banco antes dele estar pronto e travaria."

*(rodar no terminal)*
```bash
docker compose --profile docker ps
```

> "Aqui na VM os dois containers estão rodando e saudáveis."

---

### Tela
- `Dockerfile` aberto — destacar os dois estágios e `USER satuser`
- `docker-compose.yml` aberto — destacar profiles, `satmonitor-net` e `condition: service_healthy`
- Terminal da VM: saída do `ps` com ambos UP

### Perguntas que podem surgir
- *"Por que multi-stage?"* — imagem 4x menor, sem JDK em produção, sem `javac` que permitiria compilar código malicioso.
- *"O EXPOSE abre a porta?"* — não. `EXPOSE` é documentação. A porta só fica acessível com `ports` no Compose.
- *"Por que não root?"* — atacante que explorar vulnerabilidade fica confinado sem acesso root ao container.
- *"Qual a diferença de `depends_on` simples?"* — simples espera o container iniciar; `service_healthy` espera o `pg_isready` confirmar que o banco aceita conexões.
- *"O que é Alpine?"* — distribuição Linux minimalista com ~5 MB, reduz significativamente o tamanho da imagem.
- *"Por que rede nomeada e não a bridge padrão?"* — a rede nomeada oferece DNS automático entre containers — por isso `satmonitor-db` resolve para o IP do banco sem precisar de IP fixo.

---

## PARTE 4 — Pedro · CI/CD — GitHub Actions

**Tempo:** ~2,5 minutos

---

### Fala

> "Agora o ponto central do DevOps: o pipeline de CI/CD. O objetivo é que nenhum código chegue em produção sem passar por testes, e que o deploy aconteça automaticamente."

*(abrir deploy.yml)*

> "O arquivo `.github/workflows/deploy.yml` define dois jobs. O primeiro roda `./gradlew test` no runner do GitHub — um servidor Ubuntu temporário provisionado pelo GitHub, não nossa VM. Usamos cache das dependências do Gradle com `actions/cache`: o hash do `build.gradle` é a chave — se o arquivo não mudou, as dependências são restauradas do cache e o job economiza ~40 segundos."

> "O segundo job tem `needs: test` — só começa se os testes passaram. Esse é o travamento de segurança. O job entra na VM via SSH com uma chave Ed25519 armazenada como secret no GitHub — nunca no código, porque o Git guarda todo o histórico e a chave seria recuperável mesmo depois de deletada. Dentro da VM roda `git fetch origin && git reset --hard origin/main` — o reset descarta qualquer divergência local e força a VM a ficar idêntica ao repositório. Em seguida sobe os containers com `docker compose up --build -d`."

*(apontar para os logs do health check)*

> "Depois do deploy, o pipeline não encerra. Ele faz polling no `/actuator/health` a cada 10 segundos por até 2 minutos. Se a API responder HTTP 200, pipeline verde. Se não responder — talvez o banco demorou, variável de ambiente errada no `.env`, ou a aplicação travou — o job falha e um comentário é criado automaticamente no commit com o link para os logs. Ninguém precisa monitorar manualmente."

*(mostrar o fluxo resumido)*
```
push para main
  → [test]   ./gradlew test + cache Gradle
      ✓ → [deploy]  SSH → git reset --hard → docker compose up --build -d
               → poll /actuator/health por 2 min
                   ✓ HTTP 200 → pipeline verde
                   ✗ timeout  → pipeline vermelho + comentário no commit
      ✗ → deploy bloqueado
```

---

### Tela
- `.github/workflows/deploy.yml` aberto — destacar `needs: test`, bloco SSH e health check
- GitHub Actions: run bem-sucedido aberto com os dois jobs
- Logs do health check com as tentativas e HTTP 200

### Perguntas que podem surgir
- *"Por que `git reset --hard` e não `git pull`?"* — `git pull` falha se houver divergências locais; o reset descarta tudo sem intervenção.
- *"Por que a chave não pode ficar no código mesmo em repo privado?"* — Git guarda todo o histórico; ex-colaboradores têm o clone; o repo pode virar público.
- *"O health check impede o deploy?"* — não, o deploy já aconteceu. O health check detecta falha na inicialização depois.
- *"Os testes rodam na VM?"* — não, num runner do GitHub. Se houver incompatibilidade SQL entre H2 (runner) e PostgreSQL (VM), os testes passam e a API quebra. O health check pega isso.
- *"O que é o cache do Gradle?"* — `actions/cache` salva `~/.gradle/caches` com o hash do `build.gradle` como chave. Se não mudou, dependências são restauradas sem download.
- *"E se o SSH cair no meio do deploy?"* — o job falha, containers podem estar em estado inconsistente. É necessário entrar na VM manualmente para verificar.

---

---

## PARTE 5 — Henrique · Banco de dados

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Henrique, responsável pelo banco de dados. Vou explicar como o PostgreSQL foi configurado no ambiente containerizado."

> "Escolhemos PostgreSQL 16 porque o dialeto é mais rico que o MySQL — suporta sequences nativas com comportamento equivalente ao Oracle que usamos na disciplina de BD. A imagem Alpine é também mais leve."

> "Um ponto crítico: sem volume, qualquer `docker compose down` destruiria todos os dados permanentemente. Declaramos um volume nomeado `satmonitor-db-data` no Compose — ele é gerenciado pelo Docker fora do ciclo de vida dos containers. Os dados sobrevivem a restarts, rebuilds e atualizações de imagem."

> "As credenciais do banco — usuário, senha e URL de conexão — nunca ficam no código. Vêm do arquivo `.env` que existe apenas na VM, no `.gitignore`. O Spring Boot carrega essas variáveis automaticamente quando o profile `postgres` está ativo. O Hibernate cria as 13 tabelas automaticamente com `ddl-auto=update` na primeira execução."

*(rodar no terminal)*
```bash
docker exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "\dt"
```

---

### Tela
- `docker-compose.yml` — bloco do volume `satmonitor-db-data`
- Terminal da VM: listagem das 13 tabelas

### Perguntas que podem surgir
- *"Por que volume nomeado e não bind mount?"* — bind mount causa conflito de permissões no PostgreSQL; volume nomeado é gerenciado pelo Docker e evita isso.
- *"O `ddl-auto=update` é seguro em produção real?"* — não. Em produção usaríamos Flyway para migrações versionadas. O `update` pode alterar tabelas silenciosamente e travar tabelas grandes durante o startup.
- *"Por que as credenciais ficam no `.env` e não no código?"* — credenciais no código ficam expostas no histórico Git para sempre. O `.env` existe só na VM, nunca no repositório.

---

---

## PARTE 6 — Miguel · Monitoramento e testes

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Miguel, responsável pelo QA. Vou mostrar como garantimos que a aplicação está funcionando corretamente em produção."

> "O Spring Actuator expõe o endpoint `/actuator/health` que retorna o status da aplicação e do banco em tempo real — sem escrever nenhum código adicional. Esse é exatamente o endpoint que o pipeline do Pedro usa para verificar se o deploy foi bem-sucedido. Se o banco estiver fora, retorna DOWN e o pipeline falha antes de considerar o deploy concluído."

*(abrir browser com health check)*

> "Aqui temos `status: UP` — aplicação e banco saudáveis neste momento."

> "Para garantir a qualidade da API, desenvolvemos uma suite de testes em PowerShell com mais de 200 assertions cobrindo: autenticação JWT, controle de acesso por role, CRUD completo de todos os recursos, os quatro tipos de sensor com herança no banco, o cálculo automático de status nas leituras e a geração de alertas."

> "Além dos testes funcionais, temos um script de security scan que testa vulnerabilidades reais: ataques JWT com algoritmo `none`, acesso cruzado entre missões de usuários diferentes — que chamamos de IDOR — escalada de privilégio e verificação se a aplicação vaza stack traces em erros."

---

### Tela
- Browser: `http://20.122.186.91:8080/actuator/health` → `{"status":"UP"}`

### Perguntas que podem surgir
- *"O que é IDOR?"* — Insecure Direct Object Reference: acessar recursos de outro usuário manipulando IDs na URL.
- *"O health check faz parte do código da aplicação?"* — não, é fornecido pelo Spring Actuator automaticamente.

---

---

## PARTE 7 — Leonardo · Demo ao vivo e encerramento

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Leonardo, responsável pela API .NET paralela. Para fechar, vamos ver o sistema funcionando ao vivo."

*(abrir Swagger UI)*

> "Aqui está o Swagger UI disponível em `20.122.186.91:8080/swagger-ui.html`. Todos os endpoints estão documentados e acessíveis para teste interativo. Vocês podem ver os grupos de recursos: agências, missões, satélites, sensores, leituras e alertas — toda a hierarquia que o Fabrício apresentou está mapeada em endpoints REST com autenticação JWT."

> "Para resumir o que apresentamos: provisionamos infraestrutura IaaS no Azure com controle total de rede e firewall; empacotamos com Docker usando build multi-stage e isolamento entre containers; automatizamos o ciclo completo de entrega com GitHub Actions com gate de testes obrigatório; configuramos banco PostgreSQL com persistência em volume nomeado e credenciais seguras; e garantimos observabilidade com Actuator e qualidade com testes e security scan. Obrigado!"

---

### Tela
- Swagger UI com a lista de endpoints visível
- Terminal: `docker compose --profile docker ps` com ambos containers UP e healthy

---

---

## Checklist pré-apresentação

- [ ] Containers UP na VM: `docker compose --profile docker ps`
- [ ] API respondendo: `curl http://20.122.186.91:8080/actuator/health`
- [ ] GitHub Actions com último run bem-sucedido visível
- [ ] Portal Azure aberto no Resource Group `rg-satmonitor`
- [ ] `Dockerfile` aberto no editor — estágios e `USER satuser` visíveis
- [ ] `docker-compose.yml` aberto — profiles, `satmonitor-net` e `service_healthy` visíveis
- [ ] `.github/workflows/deploy.yml` aberto — `needs: test` e bloco SSH visíveis
- [ ] Terminal SSH na VM pronto para comandos ao vivo
- [ ] Browser com Swagger UI e health check carregados

---

## Se o professor fizer perguntas durante a apresentação

> Consulte o arquivo `perguntas-simulado-devops.md` — 47 perguntas com respostas completas cobrindo todos os temas apresentados.

---

## Links úteis

| Recurso | URL |
|---|---|
| API em produção | http://20.122.186.91:8080 |
| Swagger UI | http://20.122.186.91:8080/swagger-ui.html |
| Health check | http://20.122.186.91:8080/actuator/health |
| GitHub Actions | https://github.com/pedrinzz10/satmonitor/actions |
