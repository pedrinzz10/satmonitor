# Deploy — Docker + Azure VM

Guia passo a passo para buildar a imagem Docker da API e subi-la em uma VM na Azure. O banco Oracle é **remoto** (FIAP) e não faz parte da VM nem do container.

---

## Visão geral da arquitetura

```
Seu computador          Azure VM (Ubuntu)           FIAP (externo)
──────────────          ─────────────────           ──────────────
 git push         →     git clone / docker          Oracle DB
                         compose up --build    →    jdbc:oracle:thin:@...
                              │
                         container satmonitor
                         porta 8080 exposta
```

---

## Pré-requisitos

### Na VM Azure
- Ubuntu 22.04 ou superior
- Docker instalado (veja passo 2)
- Porta **8080** liberada no NSG da Azure

---

## Passo 1 — Abrir a porta 8080 na Azure

1. Portal Azure → sua VM → **Rede** → **Regras de porta de entrada**
2. Clique em **Adicionar regra de porta de entrada**:

| Campo | Valor |
|---|---|
| Intervalos de porta de destino | **8080** |
| Protocolo | TCP |
| Ação | Permitir |
| Nome | Allow-8080 |

---

## Passo 2 — Instalar o Docker na VM

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER
```

---

## Passo 3 — Clonar o repositório

```bash
git clone <url-do-repositorio> satmonitor
cd satmonitor
```

---

## Passo 4 — Criar o arquivo `.env`

```bash
nano .env
```

```
JWT_SECRET=uma_string_longa_e_aleatoria_aqui
ORACLE_URL=jdbc:oracle:thin:@<host-oracle-fiap>:<porta>/<service-name>
ORACLE_USER=seu_usuario_oracle
ORACLE_PASSWORD=sua_senha_oracle
```

> O arquivo `.env` está no `.gitignore` — nunca vai para o repositório.

---

## Passo 5 — Buildar e subir

```bash
docker compose --profile prod up --build -d
```

- `--profile prod` — usa o serviço `satmonitor-prod` do `docker-compose.yml`
- `--build` — reconstrói a imagem (necessário na primeira vez ou após mudanças)
- `-d` — roda em background

---

## Passo 6 — Verificar

```bash
# Container rodando?
docker compose --profile prod ps

# Logs em tempo real
docker compose --profile prod logs -f

# Health check
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Atualizar (novo deploy)

```bash
git pull
docker compose --profile prod up --build -d
```

---

## Comandos do dia a dia

| Comando | O que faz |
|---|---|
| `docker compose --profile prod ps` | Status do container |
| `docker compose --profile prod logs -f` | Logs em tempo real |
| `docker compose --profile prod stop` | Para sem remover |
| `docker compose --profile prod down` | Para e remove |
| `docker compose --profile prod restart` | Reinicia |
| `docker system prune -f` | Remove imagens antigas |

---

## Problemas comuns

**Container para logo após subir:**
```bash
docker compose --profile prod logs
```
Verifique se as 4 variáveis do `.env` estão corretas — a aplicação não sobe se `ORACLE_URL`, `ORACLE_USER`, `ORACLE_PASSWORD` ou `JWT_SECRET` estiverem ausentes.

**Erro de conexão com o Oracle:**
- Formato correto: `jdbc:oracle:thin:@<host>:<porta>/<service>`
- Verifique se a porta 1521 do Oracle está acessível a partir do IP da VM

**API não acessível pelo IP público:**
- Verifique a regra de entrada na porta 8080 no NSG da Azure (Passo 1)
- Confirme que o container está rodando: `docker compose --profile prod ps`
