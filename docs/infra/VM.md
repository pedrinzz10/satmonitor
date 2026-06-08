# Arquitetura da VM — SatMonitor

Referência completa do ambiente de produção levantado na Azure.  
Dados coletados em 08/06/2026.

---

## Índice

1. [Especificações da VM](#1-especificações-da-vm)
2. [Rede](#2-rede)
3. [Docker — visão geral](#3-docker--visão-geral)
4. [Containers em execução](#4-containers-em-execução)
5. [Imagens](#5-imagens)
6. [Volumes e redes Docker](#6-volumes-e-redes-docker)
7. [Variáveis de ambiente da aplicação](#7-variáveis-de-ambiente-da-aplicação)
8. [Banco de dados — tabelas](#8-banco-de-dados--tabelas)
9. [Estrutura de arquivos na VM](#9-estrutura-de-arquivos-na-vm)
10. [Histórico de deploys](#10-histórico-de-deploys)
11. [Diagrama completo](#11-diagrama-completo)

---

## 1. Especificações da VM

| Campo | Valor |
|---|---|
| Nome | `vm-satmonitor-RM562312` |
| Provedor | Microsoft Azure |
| Região | East US 2 |
| Tamanho | Standard_D2s_v3 |
| vCPUs | 2 |
| RAM | 7.8 GiB (848 MiB em uso, 6.6 GiB disponível) |
| Disco | 29 GiB (4.7 GiB usados — 17%) |
| OS | Ubuntu 22.04.5 LTS (jammy) |
| Kernel | 6.8.0-1052-azure x86_64 |
| IP público | `20.122.186.91` (estático) |
| IP privado | `10.0.1.4` (subnet `10.0.1.0/24`) |
| Usuário admin | `azureuser` |
| Resource Group | `rg-satmonitor` |

---

## 2. Rede

### Portas abertas na VM

| Porta | Protocolo | Serviço | Acessível de |
|---|---|---|---|
| 22 | TCP | SSH | Qualquer IP (NSG: allow-ssh) |
| 8080 | TCP | API Spring Boot | Qualquer IP (NSG: allow-api) |
| 53 | UDP/TCP | DNS interno | Apenas localhost |

> Porta 5432 (PostgreSQL) está aberta no NSG (`allow-postgres`) mas o container **não expõe** a porta para o host — acesso apenas interno entre containers.

### Interface de rede

```
eth0: 10.0.1.4/24
  MAC: 00:0d:3a:05:cb:f7
  MTU: 1500
```

---

## 3. Docker — visão geral

| Componente | Versão |
|---|---|
| Docker Engine | 29.5.3 |
| Docker Compose | v5.1.4 |
| containerd | v2.2.4 |
| runc | 1.3.5 |
| API version | 1.54 |

---

## 4. Containers em execução

| Container | Imagem | Status | Porta |
|---|---|---|---|
| `satmonitor-app-RM562312` | `satmonitor-satmonitor-app:latest` | Up (healthy) | `0.0.0.0:8080→8080/tcp` |
| `satmonitor-db-RM562312` | `postgres:16-alpine` | Up (healthy) | interno apenas |

Ambos sobem com `docker compose --profile docker up --build -d` a partir de `~/satmonitor`.

---

## 5. Imagens

| Imagem | ID | Tamanho total | Conteúdo |
|---|---|---|---|
| `satmonitor-satmonitor-app:latest` | `db95f9379476` | 416 MB | 136 MB |
| `postgres:16-alpine` | `16bc17c64a57` | 396 MB | 111 MB |

A imagem da aplicação é construída localmente pelo Dockerfile do repositório (build multi-stage: JDK 21 → JRE 21 Alpine).

---

## 6. Volumes e redes Docker

### Volumes

| Nome | Driver | Finalidade |
|---|---|---|
| `satmonitor-db-data` | local | Dados persistentes do PostgreSQL |

Montado em `/var/lib/postgresql/data` dentro do container do banco.  
Localização física na VM: `/var/lib/docker/volumes/satmonitor-db-data/`

### Redes

| Nome | Driver | Finalidade |
|---|---|---|
| `satmonitor_satmonitor-net` | bridge | Comunicação interna app ↔ banco |
| `bridge` | bridge | Rede padrão Docker |
| `host` | host | Modo host (não usado) |
| `none` | null | Sem rede (não usado) |

O container da aplicação resolve o banco pelo hostname `satmonitor-db` dentro da rede `satmonitor_satmonitor-net`.

---

## 7. Variáveis de ambiente da aplicação

Variáveis injetadas no container `satmonitor-app-RM562312` em tempo de execução (sensíveis omitidas):

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `POSTGRES_URL` | `jdbc:postgresql://satmonitor-db:5432/satmonitor` |
| `POSTGRES_USER` | `satuser` |
| `CORS_ALLOWED_ORIGINS` | `https://2tdspw-global-solution-1-satmonitor.vercel.app` |
| `JAVA_VERSION` | `jdk-21.0.11+10` |
| `JAVA_HOME` | `/opt/java/openjdk` |
| `HOME` (container) | `/home/satuser` |

Variáveis omitidas por segurança: `JWT_SECRET`, `POSTGRES_PASSWORD`.  
Origem: arquivo `.env` em `~/satmonitor/.env` (não versionado no git).

---

## 8. Banco de dados — tabelas

Banco: `satmonitor` | Usuário: `satuser` | Engine: PostgreSQL 16

| Tabela | Descrição |
|---|---|
| `tb_agencia` | Agências espaciais |
| `tb_missao` | Missões espaciais |
| `tb_operador` | Usuários do sistema |
| `tb_operador_missao` | Vínculo operador ↔ missão (role: DONO/SUPERVISOR/MEMBRO) |
| `tb_solicitacao_entrada` | Solicitações de entrada em missão |
| `tb_satelite` | Satélites vinculados a missões |
| `tb_sensor` | Sensores (tabela base — herança JOINED) |
| `tb_sensor_termico` | Sensor subtipo: térmico |
| `tb_sensor_pressao` | Sensor subtipo: pressão |
| `tb_sensor_radiacao` | Sensor subtipo: radiação |
| `tb_magnetometro` | Sensor subtipo: magnetômetro |
| `tb_leitura_sensor` | Leituras registradas pelos sensores |
| `tb_alerta` | Alertas gerados automaticamente por leituras fora do limite |

**13 tabelas** criadas automaticamente pelo Hibernate (`ddl-auto=update`) na primeira execução.

---

## 9. Estrutura de arquivos na VM

```
/home/azureuser/
└── satmonitor/                     ← repositório clonado
    ├── .env                        ← variáveis de produção (não versionado)
    ├── .env.example                ← template das variáveis
    ├── Dockerfile                  ← build multi-stage da imagem
    ├── docker-compose.yml          ← orquestração dos containers
    ├── build.gradle                ← dependências e build Gradle
    ├── gradlew / gradlew.bat       ← wrapper Gradle
    ├── settings.gradle
    ├── .github/
    │   └── workflows/
    │       └── deploy.yml          ← CI/CD GitHub Actions
    ├── src/
    │   └── main/
    │       └── resources/
    │           ├── application.properties           ← config base (H2/dev)
    │           └── application-postgres.properties  ← config produção (PostgreSQL)
    ├── docs/                       ← documentação do projeto
    ├── testControllers/            ← scripts de teste e varredura
    └── db/                        ← scripts SQL auxiliares
```

---

## 10. Histórico de deploys

Últimos 10 commits aplicados na VM (via GitHub Actions):

```
97b4713  docs: documenta comportamento real das chaves SSH e cenarios de acesso
4a49a87  docs: adiciona secao de acesso SSH da VM para multiplos computadores
fc87e9a  test: corrige sec-scan para setup correto e endpoints validos
4d1d883  fix: libera OPTIONS preflight explicitamente no Spring Security
2c58c73  ci: usa git fetch + reset no deploy para suportar force push
dff5a30  config: libera CORS para frontend Vercel e corrige inconsistencias
fd4a118  docs: atualiza README com springdoc 2.8.0, CI/CD Actions e correcoes de auth
c8cf382  ci: corrige permissoes, health check com retry e atualiza Node 24
9ec1c41  ci: adiciona chmod no gradlew para execucao no Linux
bba29d9  ci: adiciona testes, health check e notificacao de falha no workflow
```

Deploy automático: qualquer push na branch `main` dispara o workflow em `.github/workflows/deploy.yml`.

---

## 11. Diagrama completo

```
Internet
    │
    │  HTTPS/HTTP
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  Azure — East US 2                                              │
│                                                                 │
│  NSG: nsg-satmonitor                                            │
│    allow-ssh      → :22                                         │
│    allow-api      → :8080                                       │
│    allow-postgres → :5432 (container não expõe — sem efeito)   │
│                                                                 │
│  VM: vm-satmonitor-RM562312 (Standard_D2s_v3)                  │
│  IP público: 20.122.186.91   IP privado: 10.0.1.4              │
│  Ubuntu 22.04 LTS | 2 vCPUs | 7.8 GiB RAM | 29 GiB disco      │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Docker Engine 29.5.3                                    │   │
│  │                                                          │   │
│  │  rede: satmonitor_satmonitor-net (bridge)                │   │
│  │                                                          │   │
│  │  ┌─────────────────────────┐   ┌──────────────────────┐  │   │
│  │  │ satmonitor-app-RM562312 │   │ satmonitor-db-RM562312│  │   │
│  │  │ Spring Boot 3 / JDK 21 │◄──│ PostgreSQL 16-alpine  │  │   │
│  │  │ Porta: 8080 (exposta)   │   │ Porta: 5432 (interna) │  │   │
│  │  │ Usuário: satuser        │   │ Usuário: postgres     │  │   │
│  │  │ Profile: postgres       │   │ DB: satmonitor        │  │   │
│  │  │ 416 MB                  │   │ 396 MB                │  │   │
│  │  └─────────────────────────┘   └──────────┬───────────┘  │   │
│  │                                            │              │   │
│  │                               volume: satmonitor-db-data  │   │
│  │                               (dados persistidos em disco)│   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ~/satmonitor/  ← repositório + .env + docker-compose.yml      │
└─────────────────────────────────────────────────────────────────┘
    ▲                          ▲
    │ SSH :22                  │ push na main
    │ (chaves registradas)     │
    │                   ┌──────────────┐
┌───────┐               │ GitHub Actions│
│ Dev   │               │ deploy.yml    │
│ (você)│               │ testa + SSH   │
└───────┘               │ git reset     │
                        │ docker up     │
                        └──────────────┘
```

---

*Gerado a partir de coleta direta na VM em 08/06/2026.*
