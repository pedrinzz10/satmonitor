# Deploy — SatMonitor em Nuvem (Docker + PostgreSQL)

Guia completo para subir a API e o banco em uma VM na nuvem usando Docker Compose.  
Cobertura total dos requisitos FIAP DevOps — containers, evidências, SELECTs e vídeo.

---

## Índice

0. [Criar infraestrutura na Azure](#0-criar-infraestrutura-na-azure)
1. [Arquitetura](#arquitetura)
2. [O que foi feito nos arquivos](#o-que-foi-feito-nos-arquivos)
3. [Pré-requisitos](#pré-requisitos)
4. [Passo a passo — do clone ao ar](#passo-a-passo--do-clone-ao-ar)
5. [Evidências obrigatórias](#evidências-obrigatórias)
6. [CRUD via API + SELECT no banco](#crud-via-api--select-no-banco)
7. [Persistência de dados (volume nomeado)](#persistência-de-dados-volume-nomeado)
8. [Comandos do dia a dia](#comandos-do-dia-a-dia)
9. [Problemas comuns](#problemas-comuns)

---

## 0. Criar infraestrutura na Azure

Execute estes comandos no **Azure Cloud Shell** (portal.azure.com → ícone `>_`) ou localmente com `az login` feito.

### Variáveis — defina uma vez, use em todos os comandos

```bash
RESOURCE_GROUP="rg-satmonitor"
LOCATION="eastus2"
VNET_NAME="vnet-satmonitor"
SUBNET_NAME="subnet-satmonitor"
NSG_NAME="nsg-satmonitor"
PUBLIC_IP_NAME="pip-satmonitor"
NIC_NAME="nic-satmonitor"
VM_NAME="vm-satmonitor-RM562312"
ADMIN_USER="azureuser"
```

---

### Passo 0.1 — Resource Group

```bash
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION
```

---

### Passo 0.2 — Virtual Network + Subnet

```bash
az network vnet create \
  --resource-group $RESOURCE_GROUP \
  --name $VNET_NAME \
  --address-prefix 10.0.0.0/16 \
  --subnet-name $SUBNET_NAME \
  --subnet-prefix 10.0.1.0/24
```

---

### Passo 0.3 — Network Security Group (NSG)

```bash
az network nsg create \
  --resource-group $RESOURCE_GROUP \
  --name $NSG_NAME
```

---

### Passo 0.4 — Regras de entrada no NSG

```bash
# SSH (administração da VM)
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-ssh \
  --priority 100 \
  --protocol Tcp \
  --destination-port-range 22 \
  --access Allow

# API Spring Boot
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-api \
  --priority 110 \
  --protocol Tcp \
  --destination-port-range 8080 \
  --access Allow

# PostgreSQL (acesso externo / evidência)
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-postgres \
  --priority 120 \
  --protocol Tcp \
  --destination-port-range 5432 \
  --access Allow
```

---

### Passo 0.5 — Associar NSG à Subnet

```bash
az network vnet subnet update \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_NAME \
  --network-security-group $NSG_NAME
```

---

### Passo 0.6 — IP Público estático

```bash
az network public-ip create \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --allocation-method Static \
  --sku Standard
```

---

### Passo 0.7 — Network Interface (NIC)

```bash
az network nic create \
  --resource-group $RESOURCE_GROUP \
  --name $NIC_NAME \
  --vnet-name $VNET_NAME \
  --subnet $SUBNET_NAME \
  --public-ip-address $PUBLIC_IP_NAME \
  --network-security-group $NSG_NAME
```

---

### Passo 0.8 — Máquina Virtual (Ubuntu 22.04)

```bash
az vm create \
  --resource-group $RESOURCE_GROUP \
  --name $VM_NAME \
  --nics $NIC_NAME \
  --image Ubuntu2204 \
  --size Standard_D2s_v3 \
  --admin-username $ADMIN_USER \
  --generate-ssh-keys
```

> `Standard_D2s_v3` = 2 vCPUs + 8 GB RAM — suficiente para rodar Spring Boot + PostgreSQL com folga.  
> `--generate-ssh-keys` cria o par de chaves automaticamente em `~/.ssh/`.

---

### Passo 0.9 — Obter o IP público da VM

```bash
az network public-ip show \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --query ipAddress \
  --output tsv
```

Anote esse IP — você vai usar para conectar via SSH e para testar a API.

---

### Passo 0.10 — Conectar na VM via SSH

```bash
ssh azureuser@<IP-DA-VM>
```

A partir daqui todos os comandos são executados **dentro da VM**.

---

## Arquitetura

```
┌──────────────────────────────────────────────────────────────┐
│                    Azure VM (Ubuntu 22.04)                   │
│                                                              │
│  ┌────────────────────────┐  satmonitor-net  ┌────────────┐  │
│  │  satmonitor-app        │◄────────────────►│satmonitor  │  │
│  │  (Spring Boot 3 / JVM) │  jdbc:postgresql │-db         │  │
│  │  Porta 8080            │                  │(PostgreSQL │  │
│  │  Usuário: satuser      │                  │ 16-alpine) │  │
│  │  WORKDIR: /app         │                  │ Vol. named │  │
│  └──────────┬─────────────┘                  └─────┬──────┘  │
└─────────────┼────────────────────────────────────-─┼─────────┘
              │ :8080 (NSG aberta)                   │ :5432
              ▼                                      ▼
       Mobile / IoT / Postman               pgAdmin / psql
       (qualquer cliente HTTP)              (acesso externo ao DB)
```

### Containers e responsabilidades

| Container | Imagem | Perfil | Função |
|---|---|---|---|
| `satmonitor-app-RM562312` | build local (Dockerfile) | `docker` | API Spring Boot — porta 8080 |
| `satmonitor-db-RM562312` | `postgres:16-alpine` | `docker` | Banco PostgreSQL — porta 5432 |
| `satmonitor-dev` | build local (Dockerfile) | `dev` | Desenvolvimento local com H2 |

### Rede e volume

| Recurso | Nome | Tipo | Finalidade |
|---|---|---|---|
| Rede | `satmonitor-net` | bridge | Comunicação interna app ↔ banco |
| Volume | `satmonitor-db-data` | named | Persistência dos dados PostgreSQL |

---

## O que foi feito nos arquivos

### `Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app                          # ← WORKDIR definido

COPY gradle gradle
COPY gradlew .
RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app                          # ← WORKDIR no estágio runtime

COPY --from=build /app/build/libs/*.jar app.jar

RUN addgroup -S satgroup && adduser -S satuser -G satgroup  # ← usuário não-root
USER satuser                                                 # ← executa como satuser

EXPOSE 8080                           # ← porta exposta
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Checklist Dockerfile:**
- ✅ Construído via Dockerfile (imagem personalizada)
- ✅ Usuário não privilegiado: `satuser`
- ✅ WORKDIR: `/app`
- ✅ Porta exposta: `8080`

---

### `docker-compose.yml`

```yaml
networks:
  satmonitor-net:                     # ← rede nomeada compartilhada
    driver: bridge

volumes:
  satmonitor-db-data:                 # ← volume nomeado para persistência
    name: satmonitor-db-data

services:
  satmonitor-db:
    image: postgres:16-alpine
    container_name: satmonitor-db-RM562312   # ← nome com RM
    networks: [satmonitor-net]               # ← mesma rede que o app
    volumes:
      - satmonitor-db-data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: satmonitor         # ← variável de ambiente
      POSTGRES_USER: ${POSTGRES_USER:-satuser}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"                   # ← porta exposta externamente
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-satuser} -d satmonitor"]
      interval: 10s
      timeout: 5s
      retries: 10

  satmonitor-app:
    build: .
    container_name: satmonitor-app-RM562312  # ← nome com RM
    networks: [satmonitor-net]               # ← mesma rede que o banco
    environment:
      SPRING_PROFILES_ACTIVE: postgres       # ← variável de ambiente
      JWT_SECRET: ${JWT_SECRET}
      POSTGRES_URL: jdbc:postgresql://satmonitor-db:5432/satmonitor
      POSTGRES_USER: ${POSTGRES_USER:-satuser}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      satmonitor-db:
        condition: service_healthy    # ← app só sobe após banco passar no healthcheck
```

**Checklist docker-compose:**
- ✅ Container app com RM no nome
- ✅ Container banco com RM no nome
- ✅ Rede nomeada compartilhada
- ✅ Volume nomeado para o banco
- ✅ ENV em ambos os containers
- ✅ Portas expostas em ambos
- ✅ Banco executa na mesma rede que o app
- ✅ Background via `-d` no comando de subida

---

### `application-postgres.properties`

```properties
spring.datasource.url=${POSTGRES_URL:jdbc:postgresql://satmonitor-db:5432/satmonitor}
spring.datasource.username=${POSTGRES_USER:satuser}
spring.datasource.password=${POSTGRES_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update   # cria tabelas na 1ª execução, preserva nas demais
api.security.token.secret=${JWT_SECRET}
spring.h2.console.enabled=false
```

`ddl-auto=update`: Hibernate cria automaticamente todas as tabelas e sequences no PostgreSQL na primeira execução. Nas execuções seguintes, os dados são preservados (o volume garante isso).

---

### `.env.example`

```env
JWT_SECRET=<string-longa-minimo-32-chars>
POSTGRES_USER=satuser
POSTGRES_PASSWORD=<senha-segura>
```

Copiar para `.env` e preencher antes de subir os containers.

---

## Pré-requisitos

### VM na nuvem
- Ubuntu 22.04 LTS ou superior
- Portas **8080** (API) e **5432** (banco) abertas no NSG/firewall

### Instalar Docker na VM (Ubuntu)

```bash
sudo apt update && sudo apt install -y ca-certificates curl gnupg

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Adicionar usuário ao grupo docker (sem sudo)
sudo usermod -aG docker $USER
newgrp docker

# Verificar instalação
docker --version
docker compose version
```

---

## Passo a passo — do clone ao ar

### 1. Clonar o repositório

```bash
git clone https://github.com/pedrinzz10/satmonitor.git
cd satmonitor
```

### 2. Criar o `.env`

```bash
cp .env.example .env
```

O `.env.example` já contém os valores configurados. O arquivo `.env` está no `.gitignore` e nunca é commitado.

### 3. Subir os dois containers em background

```bash
docker compose --profile docker up --build -d
```

Saída esperada:
```
[+] Building ... (build do Dockerfile — pode demorar ~3 min na primeira vez)
[+] Running 3/3
 ✔ Network satmonitor-net            Created
 ✔ Container satmonitor-db-RM562312  Started
 ✔ Container satmonitor-app-RM562312 Started
```

### 4. Verificar se os containers estão UP

```bash
docker compose --profile docker ps
```

Saída esperada:
```
NAME                        IMAGE         STATUS                    PORTS
satmonitor-app-RM562312     satmonitor    Up X seconds              0.0.0.0:8080->8080/tcp
satmonitor-db-RM562312      postgres:16   Up X seconds (healthy)    0.0.0.0:5432->5432/tcp
```

### 5. Verificar health check da API

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

Se a VM tiver IP público `X.X.X.X`:
```bash
curl http://X.X.X.X:8080/actuator/health
```

---

## Evidências obrigatórias

### Logs dos dois containers (após execução)

```bash
# Logs de ambos os containers juntos
docker compose --profile docker logs

# Logs em tempo real (follow)
docker compose --profile docker logs -f

# Logs separados por container
docker compose --profile docker logs satmonitor-app-RM562312
docker compose --profile docker logs satmonitor-db-RM562312
```

### Container da Aplicação — estrutura e usuário

```bash
# Estrutura de diretórios
docker container exec satmonitor-app-RM562312 ls -l /app

# Diretório de trabalho atual
docker container exec satmonitor-app-RM562312 pwd

# Usuário conectado
docker container exec satmonitor-app-RM562312 whoami
# → satuser
```

### Container do Banco — estrutura e usuário

```bash
# Estrutura de diretórios dos dados
docker container exec satmonitor-db-RM562312 ls -l /var/lib/postgresql/data

# Diretório de trabalho atual
docker container exec satmonitor-db-RM562312 pwd

# Usuário conectado
docker container exec satmonitor-db-RM562312 whoami
# → postgres  (usuário padrão da imagem oficial postgres)
```

### SELECT direto no banco — listar tabelas

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor -c "\dt"
```

Saída esperada (Hibernate criou automaticamente na primeira execução):
```
             List of relations
 Schema |         Name          | Type  |  Owner
--------+-----------------------+-------+---------
 public | tb_agencia            | table | satuser
 public | tb_alerta             | table | satuser
 public | tb_leitura_sensor     | table | satuser
 public | tb_magnetometro       | table | satuser
 public | tb_missao             | table | satuser
 public | tb_operador           | table | satuser
 public | tb_operador_missao    | table | satuser
 public | tb_satelite           | table | satuser
 public | tb_sensor             | table | satuser
 public | tb_sensor_pressao     | table | satuser
 public | tb_sensor_radiacao    | table | satuser
 public | tb_sensor_termico     | table | satuser
```

> 12 tabelas com relacionamentos — atende o requisito de "pelo menos duas tabelas com relacionamento".

---

## CRUD via API + SELECT no banco

Execute estes comandos após usar a API pelo Postman/Insomnia para Popular o banco.

### CREATE — Registrar operador

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123","nome":"Admin"}'
```

SELECT de evidência:
```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, login, nome FROM tb_operador;"
```

---

### CREATE — Criar missão (login primeiro para obter token)

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 2. Criar missão
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Missao Alpha","descricao":"Missao de teste","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"senha123"}'
```

SELECT de evidência:
```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome, status FROM tb_missao;"
```

---

### READ — Listar recursos

```bash
# Listar agências (público)
curl -s http://localhost:8080/agencias

# Listar satélites (público)
curl -s http://localhost:8080/satelites
```

SELECT de evidência:
```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome FROM tb_agencia;"
```

---

### UPDATE — Atualizar missão

```bash
MISSAO_ID=1   # ajuste conforme o id retornado no CREATE

curl -s -X PUT http://localhost:8080/missoes/$MISSAO_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Missao Alpha v2","descricao":"Atualizada","dataLancamento":"2026-07-01","status":"ATIVA"}'
```

SELECT de evidência:
```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome, status FROM tb_missao WHERE id = $MISSAO_ID;"
```

---

### DELETE — Deletar missão

```bash
curl -s -X DELETE http://localhost:8080/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN"
```

SELECT de evidência (deve retornar 0 linhas):
```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome FROM tb_missao WHERE id = $MISSAO_ID;"
```

---

### SELECT nas tabelas de relacionamento

```bash
# Operadores vinculados a missões (tb_operador_missao — tabela de relacionamento)
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT om.id, o.login, m.nome AS missao, om.role
      FROM tb_operador_missao om
      JOIN tb_operador o ON o.id = om.operador_id
      JOIN tb_missao m ON m.id = om.missao_id;"

# Leituras com status calculado
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, valor, status, data_hora_leitura FROM tb_leitura_sensor LIMIT 5;"

# Alertas gerados automaticamente
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, tipo_alerta, status_alerta, data_alerta FROM tb_alerta LIMIT 5;"
```

---

## Persistência de dados (volume nomeado)

Para demonstrar que os dados sobrevivem a restart dos containers:

```bash
# 1. Inserir dados (via API ou comandos acima)

# 2. Parar os containers
docker compose --profile docker stop

# 3. Subir novamente (sem --build, sem recriar volumes)
docker compose --profile docker start

# 4. Verificar que os dados ainda estão lá
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, login, nome FROM tb_operador;"
```

> O volume `satmonitor-db-data` persiste em `/var/lib/docker/volumes/satmonitor-db-data/`
> mesmo quando os containers são parados.

---

## Comandos do dia a dia

| Comando | O que faz |
|---|---|
| `docker compose --profile docker up --build -d` | Build + sobe ambos em background |
| `docker compose --profile docker ps` | Status dos containers |
| `docker compose --profile docker logs -f` | Logs em tempo real |
| `docker compose --profile docker stop` | Para sem remover containers/volumes |
| `docker compose --profile docker start` | Reinicia containers parados |
| `docker compose --profile docker down` | Para e remove containers (mantém volumes) |
| `docker compose --profile docker down -v` | Remove tudo incluindo volumes ⚠️ apaga dados |
| `docker system prune -f` | Remove imagens e cache não usados |
| `git pull && docker compose --profile docker up --build -d` | Atualizar para nova versão |

---

## Problemas comuns

### Containers não sobem / saem imediatamente

```bash
docker compose --profile docker logs
```

Verifique:
- `JWT_SECRET` no `.env` tem pelo menos 32 caracteres
- `POSTGRES_PASSWORD` está preenchido no `.env`
- Arquivo `.env` está na raiz do projeto

### API sobe mas retorna erro de banco

```bash
docker compose --profile docker logs satmonitor-app-RM562312 | grep -i error
```

Verifique se o container do banco passou no healthcheck:
```bash
docker container inspect satmonitor-db-RM562312 | grep -A5 Health
```

### Porta 8080 não acessível externamente

1. Confirme que a porta está exposta: `docker compose --profile docker ps`
2. Verifique o NSG da Azure: Portal Azure → VM → Rede → Regras de porta de entrada → porta 8080

### Porta 5432 não acessível externamente

1. Mesma verificação de NSG — liberar porta 5432
2. Confirme que o container do banco está `healthy`: `docker compose --profile docker ps`

### Resetar banco completamente (⚠️ apaga todos os dados)

```bash
docker compose --profile docker down -v
docker compose --profile docker up --build -d
```

---

## Referência rápida — checklist do vídeo

```bash
# ① Clone e .env
git clone https://github.com/pedrinzz10/satmonitor.git && cd satmonitor
cp .env.example .env

# ② Subir em background
docker compose --profile docker up --build -d

# ③ Mostrar logs dos dois containers
docker compose --profile docker logs

# ④ Status
docker compose --profile docker ps

# ⑤ Exec na App — ls, pwd, whoami
docker container exec satmonitor-app-RM562312 ls -l /app
docker container exec satmonitor-app-RM562312 pwd
docker container exec satmonitor-app-RM562312 whoami

# ⑥ Exec no Banco — ls, pwd, whoami
docker container exec satmonitor-db-RM562312 ls -l /var/lib/postgresql/data
docker container exec satmonitor-db-RM562312 pwd
docker container exec satmonitor-db-RM562312 whoami

# ⑦ CRUD via API (Postman) + SELECT para cada operação
docker container exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "SELECT id, login FROM tb_operador;"
docker container exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "SELECT id, nome, status FROM tb_missao;"
docker container exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "SELECT id, valor, status FROM tb_leitura_sensor;"
docker container exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "SELECT id, tipo_alerta, status_alerta FROM tb_alerta;"

# ⑧ Provar persistência — stop, start, SELECT novamente
docker compose --profile docker stop
docker compose --profile docker start
docker container exec satmonitor-db-RM562312 psql -U satuser -d satmonitor -c "SELECT id, login FROM tb_operador;"
```
