# SatMonitor — API REST de Monitoramento de Satélites

API REST Java para monitoramento de satélites em órbita. Desenvolvida como Global Solution 2026/1 da FIAP — 2TDS.

> **Solução proposta:** plataforma de telemetria espacial em que operadores criam missões, vinculam satélites com sensores físicos (térmico, pressão, radiação, magnetômetro) e recebem alertas automáticos quando as leituras ultrapassam os limites configurados. A API é conteinerizada com Docker + PostgreSQL e roda em nuvem (Azure VM), integrando-se com aplicação mobile, dispositivos IoT (ESP32) e uma API .NET paralela.

---

## Links do Projeto

| | Link |
|---|---|
| **API em produção** | [http://20.122.186.91:8080](http://20.122.186.91:8080) |
| **Swagger UI** | [http://20.122.186.91:8080/swagger-ui.html](http://20.122.186.91:8080/swagger-ui.html) |
| **Documentação da API** | [docs/api/](docs/api/) |
| **Vídeo de apresentação** | [https://youtu.be/rf4EX8V6Q0k](https://youtu.be/rf4EX8V6Q0k) |
| **Video pitch** | [https://youtu.be/o2qHFyl8ZAw](https://youtu.be/o2qHFyl8ZAw?si=FtgIrJmPAVxCTbPI) |
| **Vídeo DevOps (deploy)** | [https://youtu.be/ZztE7Ou6ghc](https://youtu.be/ZztE7Ou6ghc?si=aQLDmNoaZ4-EVNZ3) |
| **Repositório GitHub** | [https://github.com/pedrinzz10/satmonitor](https://github.com/pedrinzz10/satmonitor) |

---

## Status da VM de Produção

> **VM atualmente desalocada** — a VM Azure (`vm-satmonitor-RM562312`) foi desalocada intencionalmente para preservar os créditos restantes da conta estudantil após a entrega do projeto.

### O que isso significa

| Item | Estado |
|---|---|
| Dados do banco (PostgreSQL) | Intactos — volume Docker preservado no disco da VM |
| Configuração da VM | Intacta — `.env`, imagens Docker, repositório na VM |
| IP público `20.122.186.91` | Reservado — IP estático SKU Standard, não é liberado com a desalocação |
| CI/CD (GitHub Actions) | Testes continuam funcionando; deploy reativa automaticamente ao ligar |

A VM foi desalocada com `az vm deallocate` — diferente de um simples stop, a desalocação interrompe a cobrança de compute sem liberar o IP estático nem apagar os dados em disco.

### Para reativar (Azure Cloud Shell)

```bash
az vm start --resource-group rg-satmonitor --name vm-satmonitor-RM562312
```

Após ~1 minuto os containers sobem automaticamente (política `restart: unless-stopped`) e a API volta a responder em `http://20.122.186.91:8080`.

---

## Integrantes

| Nome | RM | Turma | Responsabilidade | GitHub | LinkedIn |
|---|---|---|---|---|---|
| Fabrício Henrique Pereira | RM563237 | 2TDSPW | IoT, Front-end | [Fabriciopereira-sp](https://github.com/Fabriciopereira-sp) | [LinkedIn](https://www.linkedin.com/in/fabr%C3%ADcio-henrique-pereira-3aa94933b) |
| Pedro Henrique de Oliveira | RM562312 | 2TDSPW | Java, DevOps | [pedrinzz10](https://github.com/pedrinzz10) | [LinkedIn](https://www.linkedin.com/in/pedro-henrique-oliveira-484336261) |
| Miguel Henrique Oliveira Dias | RM565492 | 2TDSPW | QA | [Maigol123](https://github.com/Maigol123) | [LinkedIn](https://www.linkedin.com/in/miguel-oliveira-dias/) |
| Leonardo José Pereira | RM563065 | 2TDSPW | .NET | [leojp04](https://github.com/leojp04) | [LinkedIn](https://www.linkedin.com/in/leonardojpereiradev/) |
| Henrique Sinkevicius Maran | RM562977 | 2TDSPW | Banco de Dados | [HenriqueSinkeviciusMaran](https://github.com/HenriqueSinkeviciusMaran) | [LinkedIn](https://www.linkedin.com/in/henrique-sinkevicius-maran/) |

---

# Disciplina: Java Advanced — Spring Boot

## O que faz

Uma estação terrestre cria **missões espaciais**, cada missão agrupa **satélites**, cada satélite carrega **sensores**, e cada sensor gera **leituras** contínuas. Quando uma leitura ultrapassa os limites configurados, a API classifica automaticamente como **NORMAL**, **ALERTA** ou **CRÍTICO** — sem nenhuma intervenção manual.

```
Agencia → Missao → Satelite → Sensor → LeituraSensor
                                               ↓
                                           StatusCalculator
                                               ↓
                                        NORMAL | ALERTA | CRITICO
                                               ↓ (se ALERTA ou CRITICO)
                                             Alerta (gerado automaticamente)
```

---

## Stack

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Security + JWT | auth0/java-jwt 4.4.0 |
| Spring HATEOAS | — |
| Spring Data JPA + Hibernate | — |
| PostgreSQL | Container Docker (produção) |
| H2 | Desenvolvimento local (in-memory) |
| Springdoc OpenAPI | 2.8.0 |

---

## Início rápido

### 1. Criar uma agência (público, sem token)

`POST /agencias`
```json
{
  "nome": "Agencia Espacial Brasileira",
  "siglaPais": "BR",
  "tipoAgencia": "Governamental"
}
```
`→ 201 Created`

### 2. Registrar um operador

`POST /auth/registrar`
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123",
  "nome": "Administrador",
  "agenciaId": 1
}
```
`→ 201 Created`

### 3. Fazer login e obter o token JWT

`POST /auth/login`
```json
{
  "login": "admin@sat.dev",
  "senha": "senha123"
}
```
`→ 200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 4. Criar uma missão (com token)

`POST /missoes` · `Authorization: Bearer <token>`
```json
{
  "nome": "Missao Alpha",
  "descricao": "Missão principal de monitoramento",
  "dataLancamento": "2026-06-01",
  "status": "PLANEJADA",
  "senhaMissao": "acesso123",
  "agenciaId": 1,
  "permitirCowork": true
}
```
`→ 201 Created com o id da missão`

### 5. Criar um satélite (com token)

`POST /satelites` · `Authorization: Bearer <token>`
```json
{
  "nome": "SAT-01",
  "dataLancamento": "2026-06-01",
  "missaoId": 1,
  "coordenadas": {
    "altitudeKm": 550.0,
    "inclinacao": 53.0,
    "longitudeNodo": 210.5
  },
  "tipoOrbita": "LEO",
  "statusSatelite": "ATIVO"
}
```
`→ 201 Created`

### 6. Criar um sensor (com token)

`POST /sensores` · `Authorization: Bearer <token>`
```json
{
  "nome": "Sensor Térmico Principal",
  "unidade": "°C",
  "limiteMin": 0.0,
  "limiteMax": 80.0,
  "margemAlerta": 10.0,
  "sateliteId": 1,
  "tipo": "TERMICO",
  "unidadeEscala": "CELSIUS"
}
```
`→ 201 Created`

### 7. Registrar leitura (sem token — endpoint IoT)

`POST /leituras`
```json
{
  "valor": 95.3,
  "sensorId": 1,
  "latitude": -23.5505,
  "longitude": -46.6333
}
```
`→ 201 Created`
```json
{
  "id": 1,
  "valor": 95.3,
  "status": "CRITICO"
}
```

---

## Classificação automática de leituras

A regra central da API. Com base nos parâmetros do sensor:

```
faixa         = limiteMax - limiteMin
zonaAlertaMin = limiteMin + (faixa × margemAlerta / 100)
zonaAlertaMax = limiteMax - (faixa × margemAlerta / 100)
```

**Exemplo — Sensor Térmico:** `limiteMin=0`, `limiteMax=80`, `margemAlerta=10%`

```
|──CRITICO──|──ALERTA──|──────NORMAL──────|──ALERTA──|──CRITICO──|
0           8         72                  80
```

| Leitura | Status |
|---------|--------|
| 95°C | CRITICO (> limiteMax) |
| 75°C | ALERTA (entre 72 e 80) |
| 40°C | NORMAL (entre 8 e 72) |
| 5°C | ALERTA (entre 0 e 8) |
| -5°C | CRITICO (< limiteMin) |

---

## Autenticação

JWT com assinatura HMAC256, válido por **8 horas**. Enviar em toda requisição protegida:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Rotas públicas (sem token):**
- `POST /auth/login` e `POST /auth/registrar`
- `GET /agencias/**` e `POST /agencias`
- `POST /leituras` — ESP32 (IoT) envia leituras sem token
- `GET /actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`

---

## Roles de missão

Cada operador tem uma role específica em cada missão:

| Ação | DONO | SUPERVISOR | MEMBRO |
|------|:----:|:----------:|:------:|
| Ver missão / membros / leituras | ✓ | ✓ | ✓ |
| Registrar leitura | ✓ | ✓ | ✓ |
| Criar / editar satélite e sensor | ✓ | ✓ | — |
| Excluir leitura | ✓ | ✓ | — |
| Editar missão | ✓ | — | — |
| Excluir satélite, sensor, missão | ✓ | — | — |
| Gerenciar membros | ✓ | — | — |

O criador da missão começa como **DONO**. Novos membros solicitam entrada via `POST /missoes/{id}/solicitar` (com a senha da missão) e ficam **PENDENTE** até um SUPERVISOR ou DONO aprovar.

---

## Todos os endpoints

### Agências
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| POST | `/agencias` | — | Cria agência espacial |
| GET | `/agencias` | — | Lista paginada |
| GET | `/agencias/{id}` | — | Busca por id |
| PUT | `/agencias/{id}` | ✓ | Atualiza |
| DELETE | `/agencias/{id}` | ✓ | Remove |

### Auth
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| POST | `/auth/registrar` | — | Cria novo operador |
| POST | `/auth/login` | — | Retorna token JWT |

### Missões
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/missoes` | ✓ | — |
| GET | `/missoes` | ✓ | MEMBRO |
| GET | `/missoes/{id}` | ✓ | MEMBRO |
| PUT | `/missoes/{id}` | ✓ | DONO |
| DELETE | `/missoes/{id}` | ✓ | DONO |
| GET | `/missoes/buscar?nome=X` | ✓ | — |
| POST | `/missoes/{id}/solicitar` | ✓ | — |
| GET | `/missoes/{id}/solicitacoes` | ✓ | SUPERVISOR |
| PATCH | `/missoes/{id}/solicitacoes/{solId}/aprovar` | ✓ | SUPERVISOR |
| PATCH | `/missoes/{id}/solicitacoes/{solId}/rejeitar` | ✓ | SUPERVISOR |
| POST | `/missoes/{id}/sair` | ✓ | MEMBRO |
| GET | `/missoes/{id}/membros` | ✓ | MEMBRO |
| DELETE | `/missoes/{id}/membros/{opId}` | ✓ | DONO |
| PATCH | `/missoes/{id}/membros/{opId}?novoRole=X` | ✓ | DONO |

### Satélites
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/satelites` | ✓ | SUPERVISOR |
| GET | `/satelites` | ✓ | — |
| GET | `/satelites/{id}` | ✓ | — |
| GET | `/satelites/missao/{missaoId}` | ✓ | — |
| GET | `/satelites/{id}/estatisticas` | ✓ | — |
| PUT | `/satelites/{id}` | ✓ | SUPERVISOR |
| DELETE | `/satelites/{id}` | ✓ | DONO |

### Sensores
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/sensores` | ✓ | SUPERVISOR |
| GET | `/sensores` | ✓ | — |
| GET | `/sensores/{id}` | ✓ | — |
| GET | `/sensores/satelite/{sateliteId}` | ✓ | — |
| PUT | `/sensores/{id}` | ✓ | SUPERVISOR |
| DELETE | `/sensores/{id}` | ✓ | DONO |

### Leituras
| Método | Rota | Auth | Role |
|--------|------|:----:|:----:|
| POST | `/leituras` | — | — |
| GET | `/leituras` | ✓ | — |
| GET | `/leituras/{id}` | ✓ | — |
| GET | `/leituras/sensor/{sensorId}` | ✓ | — |
| GET | `/leituras/satelite/{sateliteId}` | ✓ | — |
| DELETE | `/leituras/{id}` | ✓ | SUPERVISOR |

### Alertas
| Método | Rota | Auth | Descrição |
|--------|------|:----:|-----------|
| GET | `/alertas` | ✓ | Lista alertas das missões do operador (filtro `?status=ATIVO\|RECONHECIDO\|RESOLVIDO`) |
| GET | `/alertas/{id}` | ✓ | Busca por id — verifica membership |
| GET | `/alertas/satelite/{sateliteId}` | ✓ | Alertas de um satélite |
| PATCH | `/alertas/{id}?novoStatus=X` | ✓ | Atualiza status — exige SUPERVISOR ou DONO |

---

## Tipos de sensor

| Tipo | Campo extra | Valores |
|------|------------|---------|
| `TERMICO` | `unidadeEscala` | `CELSIUS`, `FAHRENHEIT`, `KELVIN` |
| `PRESSAO` | `tipoPressao` | `ABSOLUTA`, `RELATIVA` |
| `RADIACAO` | `tipoRadiacao` | `IONIZANTE`, `NAO_IONIZANTE` |
| `MAGNETOMETRO` | `eixosMedicao` | `X`, `Y`, `Z`, `XY`, `XZ`, `YZ`, `XYZ` |

---

## Formato padrão de erro

```json
{
  "timestamp": "2026-06-01T14:32:07.123",
  "status": 404,
  "error": "Sensor não encontrado com id: 99",
  "path": "/sensores/99"
}
```

| Status | Situação |
|:------:|---------|
| 400 | Campo inválido, regra de negócio violada |
| 401 | Token ausente ou expirado |
| 403 | Sem permissão (role insuficiente ou não é membro) |
| 404 | Recurso não encontrado |
| 409 | Conflito (ex: operador já é membro, nome duplicado) |
| 500 | Erro interno (detalhes apenas nos logs) |

---

## Modelo de dados

```
TB_AGENCIA             ← agência espacial
TB_OPERADOR
TB_MISSAO              ← senha BCrypt | FK opcional p/ TB_AGENCIA
TB_OPERADOR_MISSAO     ← junction table com role (DONO/SUPERVISOR/MEMBRO)
TB_SATELITE            ← coordenadas @Embeddable | tipoOrbita | statusSatelite
TB_SENSOR              ← base (herança JOINED)
  TB_SENSOR_TERMICO
  TB_SENSOR_PRESSAO
  TB_SENSOR_RADIACAO
  TB_MAGNETOMETRO
TB_LEITURA_SENSOR      ← status calculado pelo servidor | latitude/longitude
TB_ALERTA              ← gerado automaticamente em ALERTA/CRITICO
TB_SOLICITACAO_ENTRADA ← controla fluxo de aprovação de cowork
```

---

## Desenvolvimento local

```bash
./gradlew bootRun
# API disponível em http://localhost:8080
# Swagger UI em http://localhost:8080/swagger-ui.html
```

---

## Documentação Java

| Arquivo | Conteúdo |
|---------|---------|
| [`docs/api/Auth.md`](docs/api/Auth.md) | JWT, registro, login, filtro de segurança |
| [`docs/api/Agencia.md`](docs/api/Agencia.md) | Agências espaciais — CRUD e vínculo com missões |
| [`docs/api/Missao.md`](docs/api/Missao.md) | Roles, endpoints de missão, fluxos de entrada/saída |
| [`docs/api/Satelite.md`](docs/api/Satelite.md) | Satélites, coordenadas orbitais, tipo de órbita |
| [`docs/api/Sensor.md`](docs/api/Sensor.md) | 4 tipos de sensor, herança JOINED, limites |
| [`docs/api/Leitura.md`](docs/api/Leitura.md) | StatusCalculator, contrato IoT, geração de alertas |
| [`docs/api/Alerta.md`](docs/api/Alerta.md) | Alertas automáticos, ciclo de vida |
| [`docs/internals/MissaoService.md`](docs/internals/MissaoService.md) | Fluxos internos do service de missões |
| [`docs/internals/Exception.md`](docs/internals/Exception.md) | Mapa de erros |
| [`docs/tests/UnitTests.md`](docs/tests/UnitTests.md) | Testes unitários JUnit/Mockito e JaCoCo |

---

---

# Disciplina: DevOps

## Arquitetura

![Arquitetura do Sistema](docs/assets/ArquiteturaDevops.png)

---

## CI/CD — GitHub Actions

Todo push na branch `main` dispara automaticamente:

1. Roda os testes JUnit (`./gradlew test`)
2. Se os testes passarem: deploy via SSH na VM Azure (`git reset --hard` + `docker compose up --build -d`)
3. Health check em `/actuator/health` — confirma que a API subiu
4. Se falhar em qualquer etapa: posta comentário no commit com link para os logs

Workflow: [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)

---

## Infraestrutura Azure

| Recurso | Valor |
|---|---|
| Provedor | Microsoft Azure — East US 2 |
| VM | `vm-satmonitor-RM562312` — Standard_D2s_v3 |
| IP público | `20.122.186.91` (estático) |
| OS | Ubuntu 22.04 LTS · 2 vCPUs · 7.8 GiB RAM |
| Firewall (NSG) | `nsg-satmonitor` — libera :22 e :8080 |

---

## Containers Docker

| Container | Imagem | Porta |
|---|---|---|
| `satmonitor-app-RM562312` | Build local (Dockerfile multi-stage) | 8080 (exposta) |
| `satmonitor-db-RM562312` | `postgres:16-alpine` | 5432 (exposta) |

---

## How-to: do zero ao ar (em nuvem)

---

### BLOCO 1 — Infraestrutura (Azure Cloud Shell)

> Abra o **Azure Cloud Shell** em [portal.azure.com](https://portal.azure.com) → ícone `>_`

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

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION
```

```bash
az network vnet create \
  --resource-group $RESOURCE_GROUP --name $VNET_NAME \
  --address-prefix 10.0.0.0/16 \
  --subnet-name $SUBNET_NAME --subnet-prefix 10.0.1.0/24
```

```bash
az network nsg create --resource-group $RESOURCE_GROUP --name $NSG_NAME
```

```bash
az network nsg rule create --resource-group $RESOURCE_GROUP --nsg-name $NSG_NAME \
  --name allow-ssh --priority 100 --protocol Tcp --destination-port-range 22 --access Allow

az network nsg rule create --resource-group $RESOURCE_GROUP --nsg-name $NSG_NAME \
  --name allow-api --priority 110 --protocol Tcp --destination-port-range 8080 --access Allow

az network nsg rule create --resource-group $RESOURCE_GROUP --nsg-name $NSG_NAME \
  --name allow-postgres --priority 120 --protocol Tcp --destination-port-range 5432 --access Allow
```

```bash
az network vnet subnet update \
  --resource-group $RESOURCE_GROUP --vnet-name $VNET_NAME \
  --name $SUBNET_NAME --network-security-group $NSG_NAME
```

```bash
az network public-ip create \
  --resource-group $RESOURCE_GROUP --name $PUBLIC_IP_NAME \
  --allocation-method Static --sku Standard
```

```bash
az network nic create \
  --resource-group $RESOURCE_GROUP --name $NIC_NAME \
  --vnet-name $VNET_NAME --subnet $SUBNET_NAME \
  --public-ip-address $PUBLIC_IP_NAME --network-security-group $NSG_NAME
```

```bash
az vm create \
  --resource-group $RESOURCE_GROUP --name $VM_NAME \
  --nics $NIC_NAME --image Ubuntu2204 --size Standard_D2s_v3 \
  --admin-username $ADMIN_USER --generate-ssh-keys
```

```bash
VM_IP=$(az network public-ip show \
  --resource-group $RESOURCE_GROUP --name $PUBLIC_IP_NAME \
  --query ipAddress --output tsv)
echo $VM_IP
```

> **Salvar a chave SSH no computador** (evita perder o acesso quando o Cloud Shell encerrar):
> ```bash
> # Ainda no Cloud Shell — copia a chave para o armazenamento persistente
> cp ~/.ssh/id_rsa ~/clouddrive/id_rsa_satmonitor
> cp ~/.ssh/id_rsa.pub ~/clouddrive/id_rsa_satmonitor.pub
> ```
> Se precisar restaurar numa sessão nova:
> ```bash
> cp ~/clouddrive/id_rsa_satmonitor ~/.ssh/id_rsa
> chmod 600 ~/.ssh/id_rsa
> ```

---

### BLOCO 2 — SSH + instalar Docker (dentro da VM)

```bash
ssh azureuser@$VM_IP
```

```bash
sudo apt update && sudo apt install -y ca-certificates curl gnupg
```

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
```

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

```bash
sudo usermod -aG docker $USER
```

> ⚠️ Após esse comando: digitar `exit`, reconectar com `ssh azureuser@$VM_IP` e continuar.

```bash
docker --version
docker compose version
```

---

### BLOCO 3 — Clone + .env + subir containers

```bash
git clone https://github.com/pedrinzz10/satmonitor.git
cd satmonitor
```

```bash
cp .env.example .env
```

```bash
docker compose --profile docker up --build -d
```

> ⏳ Aguardar ~3 min até `satmonitor-app-RM562312 Started`

---

### BLOCO 4 — Evidências

```bash
docker compose --profile docker ps
```

```bash
curl http://localhost:8080/actuator/health
```

```bash
docker compose --profile docker logs --tail=30
```

```bash
docker container exec satmonitor-app-RM562312 pwd
docker container exec satmonitor-app-RM562312 ls -l /app
docker container exec satmonitor-app-RM562312 whoami
```

```bash
docker container exec satmonitor-db-RM562312 pwd
docker container exec satmonitor-db-RM562312 ls -l /var/lib/postgresql/data
docker container exec satmonitor-db-RM562312 whoami
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor -c "\dt"
```

---

### BLOCO 5 — CRUD + SELECT

```bash
curl -s -X POST http://localhost:8080/agencias \
  -H "Content-Type: application/json" \
  -d '{"nome":"Agencia Espacial Brasileira","siglaPais":"BR","tipoAgencia":"Governamental"}'
```

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123","nome":"Admin","agenciaId":1}'
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, login, nome FROM tb_operador;"
```

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
```

```bash
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Missao Amazonia-1","descricao":"Monitoramento ambiental","dataLancamento":"2024-02-22","status":"ATIVA","senhaMissao":"amazonia123","agenciaId":1,"permitirCowork":true}'
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome, status FROM tb_missao;"
```

```bash
curl -s -X POST http://localhost:8080/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"AMAZONIA-1A","dataLancamento":"2024-02-22","tipoOrbita":"LEO","statusSatelite":"ATIVO","missaoId":1,"coordenadas":{"altitudeKm":752.0,"inclinacao":98.4,"longitudeNodo":45.0}}'
```

```bash
curl -s -X POST http://localhost:8080/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Sensor Termico Principal","tipo":"TERMICO","unidade":"C","limiteMin":-10.0,"limiteMax":80.0,"margemAlerta":10.0,"sateliteId":1,"unidadeEscala":"CELSIUS"}'
```

```bash
curl -s -X POST http://localhost:8080/leituras \
  -H "Content-Type: application/json" \
  -d '{"sensorId":1,"valor":95.0,"latitude":-3.1,"longitude":-60.0}'
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, valor, status, data_hora_leitura FROM tb_leitura_sensor;"
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, tipo_alerta, status_alerta, data_alerta FROM tb_alerta;"
```

```bash
curl -s -X PUT http://localhost:8080/missoes/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Missao Amazonia-1 v2","descricao":"Monitoramento atualizado","dataLancamento":"2024-02-22","status":"ATIVA","agenciaId":1}'
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome, status FROM tb_missao;"
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT o.id, o.login, m.nome AS missao, om.role
      FROM tb_operador_missao om
      JOIN tb_operador o ON o.id = om.operador_id
      JOIN tb_missao m ON m.id = om.missao_id;"
```

---

### BLOCO 6 — Persistência

```bash
docker compose --profile docker stop
```

```bash
docker compose --profile docker start
```

```bash
curl http://localhost:8080/actuator/health
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, login, nome FROM tb_operador;"
```

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT id, nome, status FROM tb_missao;"
```

---

## Documentação DevOps

| Arquivo | Conteúdo |
|---------|---------|
| [`docs/infra/DevOps.md`](docs/infra/DevOps.md) | Arquitetura completa, decisões técnicas e explicação de cada comando |
| [`docs/infra/Deploy.md`](docs/infra/Deploy.md) | Passo a passo do deploy, evidências e CRUD |
| [`docs/infra/VM.md`](docs/infra/VM.md) | Specs da VM, containers, volumes e rede |
| [`COMANDOS_DEPLOY.md`](COMANDOS_DEPLOY.md) | Comandos prontos para copiar durante o deploy |

---

## Integrações

| Disciplina | Integração |
|------------|----------|
| **Mobile** (Fabrício) | Consome todos os endpoints; base URL = `http://20.122.186.91:8080` |
| **IoT** (Miguel) | ESP32 faz `POST /leituras` com `{valor, sensorId}` — sem token |
| **.NET** | API espelho para apresentação |
