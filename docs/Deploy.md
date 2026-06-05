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

### Na sua máquina local
- Git configurado com acesso ao repositório

### Na VM Azure
- Ubuntu 22.04 ou superior
- Docker instalado (veja passo 2)
- Porta **8080** liberada no grupo de segurança de rede (NSG) da Azure

---

## Passo 1 — Abrir a porta 8080 na Azure

Antes de qualquer coisa, a porta 8080 precisa estar liberada no firewall da Azure para que a API seja acessível externamente.

1. Acesse o [Portal Azure](https://portal.azure.com)
2. Navegue até sua VM → **Rede** → **Regras de porta de entrada**
3. Clique em **Adicionar regra de porta de entrada** e preencha:

| Campo | Valor |
|---|---|
| Origem | Any |
| Intervalos de porta de origem | * |
| Destino | Any |
| Serviço | Personalizado |
| Intervalos de porta de destino | **8080** |
| Protocolo | TCP |
| Ação | Permitir |
| Prioridade | 310 (ou qualquer valor disponível) |
| Nome | Allow-8080 |

4. Clique em **Adicionar**

---

## Passo 2 — Instalar o Docker na VM

Conecte na VM via SSH e execute:

```bash
# Atualiza pacotes
sudo apt update && sudo apt upgrade -y

# Instala dependências
sudo apt install -y ca-certificates curl gnupg

# Adiciona o repositório oficial do Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Instala Docker e o Compose
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Permite rodar docker sem sudo (requer logout/login depois)
sudo usermod -aG docker $USER
```

Verifique a instalação:

```bash
docker --version
docker compose version
```

---

## Passo 3 — Clonar o repositório na VM

```bash
git clone <url-do-repositorio> satmonitor
cd satmonitor
```

> Se o repositório for privado, configure uma chave SSH ou use um token de acesso pessoal (PAT) do GitHub.

---

## Passo 4 — Criar o arquivo `.env`

Na raiz do projeto, crie o arquivo com as credenciais de produção:

```bash
nano .env
```

Cole o conteúdo abaixo substituindo os valores reais:

```
JWT_SECRET=uma_string_longa_e_aleatoria_aqui
ORACLE_URL=jdbc:oracle:thin:@<host-oracle-fiap>:<porta>/<service-name>
ORACLE_USER=seu_usuario_oracle
ORACLE_PASSWORD=sua_senha_oracle
```

Salve com `Ctrl+O`, `Enter`, `Ctrl+X`.

> O arquivo `.env` está no `.gitignore` — ele nunca vai para o repositório.

---

## Passo 5 — Buildar e subir o container

```bash
docker compose --profile prod up --build -d
```

O que acontece em cada flag:
- `--profile prod` — usa o serviço `satmonitor-prod` do `docker-compose.yml`
- `--build` — reconstrói a imagem (necessário na primeira vez ou após mudanças no código)
- `-d` — roda em background (detached)

O build leva alguns minutos na primeira vez porque baixa as dependências do Gradle. Nas próximas vezes, o cache do Docker acelera bastante.

---

## Passo 6 — Verificar se subiu corretamente

```bash
# Ver se o container está rodando
docker compose --profile prod ps

# Ver os logs em tempo real
docker compose --profile prod logs -f

# Testar o health check
curl http://localhost:8080/actuator/health
```

Resposta esperada:
```json
{"status":"UP"}
```

Testando pelo IP público da VM:
```
http://<ip-publico-da-vm>:8080/actuator/health
```

---

## Atualizando a API (novo deploy)

Quando houver uma nova versão do código:

```bash
# Na VM, dentro da pasta do projeto
git pull

# Rebuilda e reinicia o container
docker compose --profile prod up --build -d
```

O container antigo é substituído automaticamente sem precisar parar manualmente.

---

## Comandos úteis do dia a dia

| Comando | O que faz |
|---|---|
| `docker compose --profile prod ps` | Lista o container e seu status |
| `docker compose --profile prod logs -f` | Acompanha os logs em tempo real |
| `docker compose --profile prod stop` | Para o container sem remover |
| `docker compose --profile prod down` | Para e remove o container |
| `docker compose --profile prod restart` | Reinicia o container |
| `docker system prune -f` | Remove imagens e containers antigos (libera espaço) |

---

## Problemas comuns

**Container para logo após subir**
```bash
docker compose --profile prod logs
```
Verifique se as variáveis do `.env` estão corretas — o `application-prod.properties` falha no boot se `ORACLE_URL`, `ORACLE_USER`, `ORACLE_PASSWORD` ou `JWT_SECRET` estiverem ausentes.

**Erro de conexão com o Oracle**
- Confirme que o `ORACLE_URL` está no formato correto: `jdbc:oracle:thin:@<host>:<porta>/<service>`
- Verifique se a porta do Oracle (geralmente `1521`) está acessível a partir do IP da VM na rede da FIAP

**API não acessível pelo IP público**
- Verifique se a regra de entrada na porta 8080 foi criada no NSG da Azure (Passo 1)
- Confirme que o container está rodando: `docker compose --profile prod ps`
