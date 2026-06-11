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

---

---

## PARTE 1 — Fabrício · Abertura e visão geral

**Tempo:** ~2 minutos

---

### Fala

> "Bom dia, eu sou o Fabrício, e vou abrir a apresentação explicando o que é o SatMonitor e qual problema ele resolve."

> "O SatMonitor é uma API REST desenvolvida em Java com Spring Boot para monitorar satélites em órbita em tempo real. A ideia é simples: uma estação terrestre cria missões espaciais, cada missão agrupa satélites, cada satélite carrega sensores físicos — de temperatura, pressão, radiação ou campo magnético — e esses sensores enviam leituras continuamente."

> "O grande diferencial é que a API classifica cada leitura automaticamente como NORMAL, ALERTA ou CRÍTICO com base nos limites configurados para aquele sensor. Se ultrapassar o limite, um alerta é gerado sem nenhuma intervenção humana."

*(apontar para o diagrama na tela)*

> "Aqui vocês podem ver como as peças se conectam: o dispositivo IoT — um ESP32 — envia as leituras diretamente para a API via POST. O app mobile consome os endpoints para exibir o painel de monitoramento. O Henrique mantém o banco de dados Oracle na disciplina de BD, e o Leonardo tem uma API .NET paralela que espelha os dados. Toda essa integração só funciona porque a API está rodando na nuvem 24 horas por dia — e é isso que o Pedro vai explicar agora."

---

### Tela
- Diagrama de arquitetura (`docs/assets/ArquiteturaDevops.png`)

---

---

## PARTES 2, 3 e 4 — Pedro · Azure + Docker + CI/CD

> Pedro apresenta as três seções em sequência contínua, sem passagem de palavra.

---

## PARTE 2 — Pedro · Infraestrutura Azure

**Tempo:** ~2 minutos

---

### Fala

> "Obrigado, Fabrício. Eu sou o Pedro, responsável pelo Java e pelo DevOps do projeto. Vou cobrir as três camadas mais técnicas da nossa infraestrutura: onde a aplicação roda, como ela é empacotada e como o deploy acontece automaticamente."

> "A primeira decisão foi: onde hospedar? O Azure oferece o App Service, que é uma solução PaaS — você sobe o código e ele cuida do resto. Mas para a disciplina de DevOps, o objetivo é demonstrar domínio da infraestrutura, não escondê-la. Por isso escolhemos IaaS: uma máquina virtual onde controlamos cada camada — rede, firewall, sistema operacional e Docker Engine."

*(apontar para o portal Azure na tela)*

> "Criamos um Resource Group chamado `rg-satmonitor` que agrupa todos os recursos. Dentro dele temos uma rede virtual com subnet dedicada, um NSG — Network Security Group — que funciona como firewall e libera apenas as portas 22 para SSH e 8080 para a API. O IP público é estático, ou seja, mesmo que a VM seja reiniciada o endereço `20.122.186.91` não muda. A VM em si roda Ubuntu 22.04 LTS com 2 vCPUs e 7.8 GB de RAM — suficiente para rodar os dois containers sem gargalo."

---

### Tela
- Portal Azure: Resource Group `rg-satmonitor` com todos os recursos listados
- NSG com as regras de entrada (SSH 22, API 8080)

---

## PARTE 3 — Pedro · Docker

**Tempo:** ~2,5 minutos

---

### Fala

> "Com a VM pronta, o próximo passo foi empacotar a aplicação com Docker. Sem Docker, a VM precisaria ter Java 21, PostgreSQL e todas as dependências instaladas diretamente no sistema operacional — qualquer atualização poderia quebrar o ambiente. Com Docker, o ambiente é definido em código e é idêntico no notebook local e na VM de produção."

*(abrir o Dockerfile na tela)*

> "Nosso Dockerfile usa build multi-stage, que é uma técnica importante. No primeiro estágio usamos o JDK completo, que pesa cerca de 700 megabytes, apenas para compilar o projeto e gerar o JAR. No segundo estágio usamos somente o JRE Alpine — uma versão minimalista do Java — que pesa 180 megabytes. A imagem final fica quatro vezes menor, o que acelera o download na VM e reduz a superfície de ataque."

> "Outro detalhe de segurança: criamos um usuário chamado `satuser` e o processo Java roda como esse usuário, nunca como root. Se alguém explorar uma vulnerabilidade na aplicação, não consegue acesso root ao container."

*(abrir o docker-compose na tela)*

> "O docker-compose tem dois profiles. O profile `dev` sobe a aplicação com banco H2 em memória — perfeito para desenvolvimento local sem precisar instalar nada. O profile `docker`, que usamos em produção, sobe dois containers: a aplicação na porta 8080 e o PostgreSQL na rede interna. O banco não tem nenhuma porta exposta no host — só a aplicação consegue se comunicar com ele pela rede Docker interna chamada `satmonitor-net`. Isso é isolamento de rede."

*(rodar no terminal da VM)*

> "Aqui na VM, os dois containers estão rodando e saudáveis."

```bash
docker compose --profile docker ps
```

---

### Tela
- `Dockerfile` aberto, destacando os dois estágios (`AS build` e `eclipse-temurin:21-jre-alpine`) e `USER satuser`
- `docker-compose.yml` aberto, destacando os profiles e a rede `satmonitor-net`
- Terminal da VM: saída do `docker compose ps` com ambos os containers UP

---

## PARTE 4 — Pedro · CI/CD — GitHub Actions

**Tempo:** ~2,5 minutos

---

### Fala

> "Agora o ponto central do DevOps: o pipeline de CI/CD. O objetivo é garantir que nenhum código chegue em produção sem passar por testes — e que o deploy aconteça automaticamente, sem nenhuma intervenção manual."

*(abrir o arquivo deploy.yml na tela)*

> "O arquivo `.github/workflows/deploy.yml` define dois jobs. O primeiro job roda os testes automatizados com `./gradlew test`. Ele configura o Java 21, usa cache das dependências do Gradle para economizar tempo, e executa toda a suite de testes. Enquanto esse job não terminar com sucesso, o segundo job não começa."

> "O segundo job é o deploy. Ele tem uma diretiva `needs: test` — isso é o travamento de segurança. Se os testes falharam, o deploy é bloqueado automaticamente. Se passaram, o job entra na VM via SSH usando uma chave Ed25519 que está armazenada como secret no GitHub — nunca no código. Dentro da VM, ele roda `git reset --hard origin/main` para garantir que o código está exatamente igual ao que foi enviado, e depois sobe os containers com `docker compose up --build`."

*(apontar para os logs do health check)*

> "Depois do deploy, o pipeline não encerra imediatamente. Ele faz polling no endpoint `/actuator/health` a cada 10 segundos por até 2 minutos. Se a API responder HTTP 200, o pipeline é marcado como bem-sucedido. Se em 2 minutos não responder — talvez o banco demorou para subir, ou o JAR teve erro — o job falha. E quando falha, um comentário é criado automaticamente no commit que causou o problema, com o link direto para os logs da execução. Ninguém precisa ficar monitorando manualmente."

*(mostrar o fluxo resumido)*

```
push para main
  → [test]   ./gradlew test
      ✓ passa → [deploy]  SSH → git reset --hard → docker compose up --build
                  → poll /actuator/health por 2 min
                      ✓ HTTP 200 → pipeline verde
                      ✗ timeout  → pipeline vermelho + comentário no commit
      ✗ falha → deploy bloqueado
```

---

### Tela
- `.github/workflows/deploy.yml` aberto, mostrando `needs: test` e o bloco SSH
- GitHub Actions: aba Actions com um run bem-sucedido aberto
- Logs do job deploy: passos SSH e saída do health check com HTTP 200

---

---

## PARTE 5 — Henrique · Banco de dados

**Tempo:** ~2 minutos

---

### Fala

> "Obrigado, Pedro. Eu sou o Henrique, responsável pelo banco de dados. Vou explicar como o PostgreSQL foi configurado dentro desse ambiente containerizado."

> "Escolhemos o PostgreSQL 16 porque ele tem um dialeto mais rico que o MySQL — suporta sequences nativas e tipos avançados, e tem comportamento muito próximo ao Oracle que usamos na disciplina de Banco de Dados. A imagem Alpine oficial é também mais leve que a do MySQL."

> "Um ponto crítico em ambientes com containers é a persistência dos dados. Por padrão, quando você derruba um container tudo que estava dentro é perdido. Para resolver isso, declaramos um volume nomeado chamado `satmonitor-db-data` no docker-compose. Esse volume é gerenciado pelo Docker e sobrevive a restarts, a rebuilds de imagem e até a um `docker compose down`. Os dados do PostgreSQL ficam lá intactos."

> "Outro ponto importante: as credenciais do banco — usuário, senha e URL de conexão — nunca ficam hardcoded no código ou no repositório. Elas vêm de um arquivo `.env` que existe apenas na VM de produção. O Spring Boot lê essas variáveis de ambiente e conecta no banco automaticamente quando o profile `postgres` está ativo."

> "O Hibernate cuida da criação do schema com `ddl-auto=update` — na primeira vez que a aplicação sobe, ele cria as 13 tabelas automaticamente. Podemos confirmar isso aqui:"

*(rodar no terminal)*
```bash
docker exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "\dt"
```

---

### Tela
- Terminal da VM: saída do `\dt` listando as 13 tabelas do banco

---

---

## PARTE 6 — Miguel · Monitoramento e testes

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Miguel, responsável pelo QA. Vou mostrar como garantimos que a aplicação está funcionando corretamente tanto em desenvolvimento quanto em produção."

> "A API usa o Spring Actuator, que é um módulo do Spring Boot que expõe endpoints de observabilidade sem nenhum código adicional. O mais importante é o `/actuator/health`, que retorna o status da aplicação e do banco de dados em tempo real. Esse é exatamente o endpoint que o pipeline do Pedro usa para verificar se o deploy foi bem-sucedido — se o banco não estiver acessível, o health retorna DOWN e o pipeline falha antes de marcar o deploy como concluído."

*(abrir o browser com o health check)*

> "Aqui vemos o `status: UP`, o que significa que a aplicação e o banco estão saudáveis neste momento."

> "Para garantir a qualidade da API, desenvolvemos uma suite de testes em PowerShell com mais de 200 assertions. Ela cobre o fluxo completo: autenticação JWT, controle de acesso por role de administrador e operador, CRUD de todos os recursos, os quatro tipos de sensor com herança no banco, o cálculo automático de status nas leituras e a geração de alertas."

> "Além dos testes funcionais, temos um script de security scan que testa vulnerabilidades reais: tentativas de ataque ao JWT com algoritmo `none`, acesso cruzado entre missões de usuários diferentes — o que chamamos de IDOR — escalada de privilégio e verificação se a aplicação vaza stack traces em erros. A aplicação passa em todos esses testes."

---

### Tela
- Browser: `http://20.122.186.91:8080/actuator/health` retornando `{"status":"UP"}`

---

---

## PARTE 7 — Leonardo · Demo ao vivo e encerramento

**Tempo:** ~2 minutos

---

### Fala

> "Eu sou o Leonardo, responsável pela API .NET paralela do projeto. Para fechar, vamos ver o sistema funcionando ao vivo em produção."

*(abrir o Swagger UI no browser)*

> "Aqui está o Swagger UI da aplicação, disponível em `20.122.186.91:8080/swagger-ui.html`. Todos os endpoints estão documentados e disponíveis para teste interativo direto daqui. Vocês podem ver os grupos de recursos: agências, missões, satélites, sensores, leituras e alertas — toda a hierarquia que o Fabrício apresentou no início está mapeada em endpoints REST."

> "Para fechar, um resumo do que apresentamos: provisionamos infraestrutura IaaS no Azure com controle total de rede e firewall, empacotamos a aplicação com Docker usando build multi-stage e isolamento de rede entre os containers, automatizamos o ciclo completo de entrega com GitHub Actions com gate de testes obrigatório, configuramos o banco PostgreSQL com persistência em volume nomeado e credenciais seguras, e garantimos observabilidade com Actuator e qualidade com testes funcionais e varredura de segurança. O SatMonitor está no ar, automatizado e monitorado. Obrigado!"

---

### Tela
- Swagger UI com a lista completa de endpoints visível
- Terminal na VM: `docker compose --profile docker ps` com ambos os containers UP e healthy

---

---

---

## Testes ao vivo — Postman ou curl

> Use esses JSONs se o professor quiser testar a API na hora. Seguem em ordem — cada passo depende do anterior.
> Substitua `SEU_TOKEN` pelo token retornado no passo 2.

---

### Passo 1 — Criar operador

**POST** `http://20.122.186.91:8080/auth/registrar`

```json
{
  "login": "professor@fiap.com",
  "senha": "fiap2026",
  "nome": "Professor DevOps"
}
```

Resposta esperada: **201 Created** (sem corpo)

---

### Passo 2 — Fazer login e pegar o token

**POST** `http://20.122.186.91:8080/auth/login`

```json
{
  "login": "professor@fiap.com",
  "senha": "fiap2026"
}
```

Resposta esperada: **200 OK**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

> Copie o valor do `token` — vai ser usado em todas as próximas chamadas no header `Authorization: Bearer SEU_TOKEN`.

---

### Passo 3 — Criar uma missão

**POST** `http://20.122.186.91:8080/missoes`  
Header: `Authorization: Bearer SEU_TOKEN`

```json
{
  "nome": "Missao FIAP",
  "descricao": "Missão de demonstração",
  "dataLancamento": "2026-06-11",
  "status": "ATIVA",
  "senhaMissao": "fiap123",
  "objetivo": "Demonstrar o SatMonitor ao vivo"
}
```

Resposta esperada: **201 Created** — guarde o `"id"` retornado (ex: `1`)

---

### Passo 4 — Criar um satélite

**POST** `http://20.122.186.91:8080/satelites`  
Header: `Authorization: Bearer SEU_TOKEN`

```json
{
  "nome": "SAT-FIAP-01",
  "modelo": "CubeSat",
  "dataLancamento": "2026-06-11",
  "status": "OPERACIONAL",
  "altitudeOrbitaKm": 550.0,
  "missaoId": 1
}
```

Resposta esperada: **201 Created** — guarde o `"id"` do satélite

---

### Passo 5 — Criar um sensor térmico

**POST** `http://20.122.186.91:8080/sensores`  
Header: `Authorization: Bearer SEU_TOKEN`

```json
{
  "nome": "Termometro FIAP",
  "unidade": "graus_C",
  "limiteMin": -10.0,
  "limiteMax": 90.0,
  "margemAlerta": 5.0,
  "sateliteId": 1,
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```

Resposta esperada: **201 Created** — guarde o `"id"` do sensor

---

### Passo 6 — Enviar uma leitura normal (sem token — simula o ESP32)

**POST** `http://20.122.186.91:8080/leituras`  
*(sem header de autenticação)*

```json
{
  "valor": 40.0,
  "sensorId": 1
}
```

Resposta esperada: **201 Created** com `"status": "NORMAL"`

---

### Passo 7 — Enviar uma leitura crítica (sem token — simula o ESP32)

**POST** `http://20.122.186.91:8080/leituras`

```json
{
  "valor": 150.0,
  "sensorId": 1
}
```

Resposta esperada: **201 Created** com `"status": "CRITICO"` — e um alerta gerado automaticamente

---

### Passo 8 — Ver os alertas gerados

**GET** `http://20.122.186.91:8080/missoes/1/alertas`  
Header: `Authorization: Bearer SEU_TOKEN`

Resposta esperada: **200 OK** com a lista de alertas gerados automaticamente pelas leituras críticas

---

### Passo 9 — Health check (sem token)

**GET** `http://20.122.186.91:8080/actuator/health`

Resposta esperada:
```json
{
  "status": "UP"
}
```

---

### Configuração no Postman

1. Crie uma **Collection** chamada `SatMonitor`
2. Crie uma variável de coleção chamada `token`
3. No passo 2 (login), adicione no campo **Tests**:
```javascript
pm.collectionVariables.set("token", pm.response.json().token);
```
4. Nas demais requisições, use `{{token}}` no header Authorization — o Postman preenche automaticamente

---

## Checklist pré-apresentação

- [ ] Containers UP na VM: `docker compose --profile docker ps`
- [ ] API respondendo: `curl http://20.122.186.91:8080/actuator/health`
- [ ] GitHub Actions com último run bem-sucedido visível na aba Actions
- [ ] Portal Azure aberto no Resource Group `rg-satmonitor`
- [ ] `Dockerfile` aberto no editor (estágios multi-stage visíveis)
- [ ] `docker-compose.yml` aberto no editor (profiles e rede visíveis)
- [ ] `.github/workflows/deploy.yml` aberto (jobs `test` e `deploy` visíveis)
- [ ] Terminal SSH na VM pronto para comandos ao vivo
- [ ] Browser com Swagger UI carregado

---

## Links úteis

| Recurso | URL |
|---|---|
| API em produção | http://20.122.186.91:8080 |
| Swagger UI | http://20.122.186.91:8080/swagger-ui.html |
| Health check | http://20.122.186.91:8080/actuator/health |
| GitHub Actions | https://github.com/pedrinzz10/satmonitor/actions |
