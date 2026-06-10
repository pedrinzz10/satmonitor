# DevOps — SatMonitor: Arquitetura, Decisões e Comandos

Documentação completa do ambiente de produção: por que cada recurso foi escolhido,
como a infraestrutura foi montada e o que cada comando de deploy faz linha a linha.

---

## Vídeo de demonstração

[![Vídeo DevOps — SatMonitor](https://img.youtube.com/vi/ZztE7Ou6ghc/0.jpg)](https://youtu.be/ZztE7Ou6ghc?si=aQLDmNoaZ4-EVNZ3)

---

![Arquitetura do Sistema](../assets/ArquiteturaDevops.png)

---

## Equipe

| Nome | RM | Responsabilidade |
|---|---|---|
| Fabrício Henrique Pereira | RM563237 | IoT, Front-end |
| Pedro Henrique de Oliveira | RM562312 | Java, DevOps |
| Miguel Henrique Oliveira Dias | RM565492 | QA |
| Leonardo José Pereira | RM563065 | .NET |
| Henrique Sinkevicius Maran | RM562977 | Banco de Dados |

---

## Índice

1. [Decisões arquiteturais](#1-decisões-arquiteturais)
2. [Hierarquia dos recursos Azure](#2-hierarquia-dos-recursos-azure)
3. [Arquitetura dos containers Docker](#3-arquitetura-dos-containers-docker)
4. [Chaves SSH e acesso à VM](#4-chaves-ssh-e-acesso-à-vm)
5. [CI/CD — GitHub Actions](#5-cicd--github-actions)
6. [Explicação dos comandos de deploy](#6-explicação-dos-comandos-de-deploy)
7. [Variáveis de ambiente e segredos](#7-variáveis-de-ambiente-e-segredos)

---

## 1. Decisões arquiteturais

### Por que Azure?

O projeto é avaliado dentro do contexto FIAP, que utiliza Azure como provedor oficial.
A conta de estudante oferece créditos suficientes para manter uma VM durante o período do projeto.

### Por que VM (IaaS) e não PaaS como Azure App Service?

O App Service simplifica o deploy mas esconde a infraestrutura — não há controle sobre rede,
NSG, Docker Engine nem acesso SSH direto. Para a disciplina de DevOps, o objetivo é demonstrar
domínio da camada de infraestrutura: criação de rede, firewall, containers e orquestração.
Uma VM expõe tudo isso explicitamente.

### Por que Docker?

Sem Docker, a VM precisaria ter Java 21, PostgreSQL e todas as dependências instaladas
diretamente no sistema operacional. Docker resolve três problemas:

- **Isolamento**: app e banco rodam em processos isolados com seus próprios sistemas de arquivo.
- **Reprodutibilidade**: o mesmo `docker-compose.yml` funciona no notebook do dev e na VM de produção.
- **Rollback limpo**: para reverter uma versão basta trocar a imagem, sem desinstalar nada do SO.

### Por que Docker Compose e não Kubernetes?

Kubernetes é a escolha certa para dezenas de serviços ou quando se precisa de auto-scaling.
Para dois containers (app + banco) em uma VM, Kubernetes adicionaria semanas de configuração
sem nenhum benefício real. Docker Compose resolve o problema com um arquivo de ~50 linhas.

### Por que PostgreSQL e não MySQL ou outro banco?

- O Spring Boot com Hibernate suporta ambos igualmente, mas o dialeto PostgreSQL é mais rico
  (tipos de dados, sequences, JSONB).
- A imagem oficial `postgres:16-alpine` é mais enxuta que a do MySQL (~396 MB vs ~600 MB).
- As sequences do Oracle usadas no ambiente FIAP (disciplina BD com Oracle) têm comportamento
  equivalente no PostgreSQL, facilitando a migração dos scripts.

### Por que Ubuntu 22.04 LTS?

- É o sistema Linux mais documentado para instalação do Docker Engine.
- LTS (Long-Term Support) garante atualizações de segurança até 2027 — adequado para um projeto
  que pode ficar no ar por meses após a entrega.
- É a imagem padrão sugerida pela Azure para VMs Linux de propósito geral.

### Por que Standard_D2s_v3?

| Recurso | Valor | Justificativa |
|---|---|---|
| vCPUs | 2 | Spring Boot na inicialização usa ~1 CPU inteira; o segundo core evita travamento |
| RAM | 7.8 GiB | JVM com Spring Boot ocupa ~300 MB; PostgreSQL ~100 MB; sobra memória para OS e picos |
| Disco | 29 GiB | Sistema operacional + Docker + imagens + dados — 29 GB é confortável |

VMs menores (B1s com 1 vCPU e 1 GB RAM) não sustentam Spring Boot + PostgreSQL simultaneamente.

### Por que East US 2?

Menor latência para a maioria dos usuários no Brasil comparado a outras regiões disponíveis
nos créditos de estudante. West Europe e Southeast Asia teriam latência maior.

---

## 2. Hierarquia dos recursos Azure

Cada recurso Azure tem um pai. Entender essa hierarquia é essencial para saber onde cada
coisa aparece no portal e quem depende de quem.

```
Azure Subscription
└── Resource Group: rg-satmonitor  (East US 2)
    ├── Public IP Address: pip-satmonitor     → IP público estático 20.122.186.91
    ├── NSG: nsg-satmonitor                   → firewall de borda
    ├── Managed Disk (OS Disk)                → disco de 29 GiB com Ubuntu instalado
    ├── NIC: nic-satmonitor                   → placa de rede virtual da VM
    └── Virtual Network: vnet-satmonitor  (10.0.0.0/16)
        └── Subnet: subnet-satmonitor  (10.0.1.0/24)
            └── VM: vm-satmonitor-RM562312    → a máquina em si
```

**Por que essa separação existe no Azure?**

No Azure, uma VM é apenas CPU e RAM. Disco, placa de rede e IP público são recursos
independentes que você anexa à VM. Isso permite, por exemplo, desanexar o disco de uma VM
e reanexar em outra sem perder dados — útil para migração ou backup.

### Resource Group

Agrupa todos os recursos do projeto. A principal função é permitir deletar tudo de uma vez:
`az group delete --name rg-satmonitor` remove a VM, o disco, o IP, a NIC, o NSG e a VNet
em um único comando. Também facilita ver o custo total no portal.

### Virtual Network (VNet) e Subnet

A VNet `vnet-satmonitor` cria uma rede privada isolada com range `10.0.0.0/16` (65.536
endereços disponíveis). A Subnet `subnet-satmonitor` é uma subdivisão com `10.0.1.0/24`
(256 endereços). A VM recebe o IP privado `10.0.1.4` dentro dessa subnet.

**Por que ter VNet se só existe uma VM?** Boas práticas. Se o projeto crescer para múltiplos
serviços (um segundo servidor, um cache Redis, etc.), todos podem entrar na mesma VNet e se
comunicar internamente sem passar pela internet.

### NSG (Network Security Group)

Funciona como um firewall stateful na borda da subnet. Regras configuradas:

| Nome | Prioridade | Porta | Decisão | Motivo |
|---|---|---|---|---|
| allow-ssh | 100 | 22 | Permitir | Acesso administrativo à VM |
| allow-api | 110 | 8080 | Permitir | Tráfego da API para clientes externos |
| allow-postgres | 120 | 5432 | Permitir (sem efeito real) | Container não expõe a porta — regra existe só para evidência |
| DenyAllInBound | 65500 | * | Bloquear | Regra padrão do Azure — tudo que não está na lista acima é bloqueado |

A porta 5432 tem regra no NSG mas o container PostgreSQL **não publica essa porta no host**
(`ports` não configurado no `docker-compose.yml` para o perfil docker). O tráfego nunca
chega ao container mesmo que passe pelo NSG.

### Public IP e NIC

O **Public IP** (`pip-satmonitor`) é o endereço `20.122.186.91` que o mundo externo usa.
Foi criado como `Static` — significa que o endereço não muda se a VM for reiniciada.
IP dinâmico mudaria a cada restart, quebrando o CI/CD e qualquer cliente configurado com
esse endereço.

A **NIC** (`nic-satmonitor`) é a interface de rede virtual que conecta tudo: ela fica na
subnet (ganha o IP privado `10.0.1.4`), tem o Public IP associado e está vinculada ao NSG.
A VM não "sabe" que tem um IP público — o Azure faz NAT no gateway da VNet.

---

## 3. Arquitetura dos containers Docker

```
VM: Ubuntu 22.04
└── Docker Engine 29.5.3
    ├── Rede bridge: satmonitor_satmonitor-net
    ├── Volume: satmonitor-db-data  →  /var/lib/postgresql/data
    ├── Container: satmonitor-app-RM562312
    │   ├── Imagem: build local (Dockerfile multi-stage)
    │   ├── Porta: 8080 publicada no host
    │   ├── Usuário: satuser (não-root)
    │   └── Conecta ao banco via hostname "satmonitor-db" na bridge
    └── Container: satmonitor-db-RM562312
        ├── Imagem: postgres:16-alpine
        ├── Porta: 5432 apenas interna (não publicada)
        └── Monta o volume satmonitor-db-data
```

### Por que rede bridge nomeada e não a bridge padrão do Docker?

A rede padrão do Docker (`bridge`) não oferece DNS automático entre containers — você
precisaria usar IPs fixos. Com uma rede nomeada, o Docker registra cada container pelo
nome do serviço como hostname. Por isso `POSTGRES_URL=jdbc:postgresql://satmonitor-db:5432`
funciona: `satmonitor-db` resolve automaticamente para o IP do container do banco dentro
da rede bridge.

### Por que volume nomeado e não bind mount?

Um bind mount (`./data:/var/lib/postgresql/data`) prende os dados a um diretório específico
da VM. Um volume nomeado (`satmonitor-db-data`) é gerenciado pelo Docker em
`/var/lib/docker/volumes/` e sobrevive mesmo que o repositório seja deletado ou movido.
Também funciona corretamente com permissões de usuário dentro do container, o que bind mounts
frequentemente quebra no PostgreSQL.

### Por que usuário não-root no container da aplicação?

Se o container rodar como `root` e houver uma vulnerabilidade na aplicação que permita
execução de comandos, o atacante terá acesso root ao sistema de arquivos do container —
e em alguns cenários ao host. O `satuser` criado no Dockerfile tem permissões mínimas:
só pode ler e executar o `.jar`, nada mais.

### Por que `depends_on` com `condition: service_healthy`?

Sem essa condição, o container da aplicação sobe e tenta conectar ao banco antes que o
PostgreSQL termine de inicializar. O resultado é uma exceção de conexão e o container da
aplicação fica em loop de restart. O healthcheck (`pg_isready`) garante que o banco aceita
conexões antes do app ser iniciado.

### Por que build multi-stage no Dockerfile?

```dockerfile
FROM eclipse-temurin:21-jdk AS build   # ~600 MB — compila o projeto
FROM eclipse-temurin:21-jre-alpine     # ~200 MB — só executa o .jar
```

O estágio de build precisa do JDK completo (compilador, ferramentas de build). O estágio
final precisa apenas do JRE (runtime). Sem multi-stage, a imagem final teria o JDK e todas
as ferramentas de build (~600 MB a mais) sem nenhuma utilidade em produção.

---

## 4. Chaves SSH e acesso à VM

### Como SSH por chave funciona

SSH com chave funciona em dois arquivos:

- **Chave privada** (`id_rsa` ou `id_ed25519`): fica no computador do usuário. Nunca sai dele.
- **Chave pública** (`id_rsa.pub` ou `id_ed25519.pub`): fica no arquivo `~/.ssh/authorized_keys`
  da VM. Pode ser compartilhada livremente — sem a privada correspondente, ela não serve para nada.

Quando você executa `ssh azureuser@20.122.186.91`, o SSH usa sua chave privada para provar
matematicamente que você possui a chave pública registrada na VM. Nenhuma senha trafega pela rede.

### O `--generate-ssh-keys` no Cloud Shell foi só para demonstração

Durante o provisionamento da VM no BLOCO 1, o comando `az vm create` incluiu `--generate-ssh-keys`.
Essa flag faz o Azure CLI gerar um par de chaves RSA automaticamente e registrar a pública
no `authorized_keys` da VM.

**O problema:** o Cloud Shell da Azure armazena os arquivos em `~/.ssh/` dentro de uma sessão
temporária. Essa pasta **não é persistente** — ela pode ser apagada quando a sessão do Cloud Shell
encerra ou expira. Se a sessão fosse perdida, a chave privada sumia e o acesso à VM ficava
bloqueado para sempre (a VM já estava criada, sem como entrar).

**Solução adotada:** após criar a VM, foram gerados pares de chaves `ed25519` nos computadores
reais e registrados na VM via `az vm user update`. O arquivo `authorized_keys` da VM ficou assim:

```
# linha 1 — chave RSA gerada pelo --generate-ssh-keys no Cloud Shell (usada só na criação)
ssh-rsa AAAA...

# linha 2 — chave ed25519 do Windows (C:\Users\Administrador\.ssh\)
ssh-ed25519 AAAA...

# linha 3 — chave ed25519 do Linux /home/pedro
ssh-ed25519 AAAA...
```

Cada linha é independente. O `az vm user update` **acrescenta** uma linha — nunca sobrescreve.
Com dois computadores reais registrados, o acesso à VM não depende mais do Cloud Shell efêmero.

### Por que ed25519 e não RSA?

`ed25519` é o algoritmo moderno de chave assimétrica baseado em curva elíptica. É mais curto,
mais rápido e criptograficamente mais seguro que RSA-2048. Todos os clientes SSH modernos
suportam ed25519.

### A chave privada no GitHub Actions

O CI/CD precisa acessar a VM via SSH para fazer o deploy. O GitHub Actions não pode interagir
com um ser humano para digitar senha — o processo é 100% automatizado.

**Como funciona:**
1. A chave privada `id_ed25519` (do computador do dev) foi copiada e adicionada como secret
   no GitHub: **Settings → Secrets and variables → Actions → `VM_SSH_KEY`**.
2. O workflow `deploy.yml` lê o secret, escreve em um arquivo temporário e usa para conectar.
3. O secret nunca aparece em logs — o GitHub o substitui por `***` automaticamente.

**Por que não criar uma chave separada só para o CI/CD?** É uma boa prática criar uma chave
dedicada para automação (sem passphrase, com permissões mínimas). Para o escopo do projeto
FIAP a chave compartilhada é suficiente — em produção real, chaves de CI/CD seriam separadas
e rotacionadas regularmente.

---

## 5. CI/CD — GitHub Actions

### Fluxo completo

```
Dev faz push na branch main
    │
    ▼
GitHub detecta o push e dispara deploy.yml
    │
    ├── Job: test
    │   ├── Checkout do código
    │   ├── Setup Java 21
    │   ├── chmod +x gradlew
    │   └── ./gradlew test  ← roda todos os testes unitários
    │
    └── Job: deploy  (só executa se test passou)
        ├── Lê o secret VM_SSH_KEY
        ├── Conecta via SSH em azureuser@20.122.186.91
        ├── cd ~/satmonitor
        ├── git fetch origin main
        ├── git reset --hard origin/main   ← sincroniza sem conflito
        └── docker compose --profile docker up --build -d
```

### Por que `git reset --hard` e não `git pull`?

`git pull` falha se houver divergência entre o histórico local e o remoto (por exemplo, após
um force push ou rebase). `git reset --hard origin/main` descarta qualquer estado local e
força a VM a ficar idêntica ao que está no GitHub — é o comportamento desejado para um
servidor de produção que nunca deve ter alterações manuais.

### Por que testar antes de fazer deploy?

Se o deploy fosse feito sem testar, um commit com código quebrado poderia derrubar o servidor
em produção. O job `test` garante que os testes unitários passam antes de qualquer mudança
chegar à VM.

### Por que não usar Docker Hub ou registry?

O projeto faz o build da imagem diretamente na VM com `--build`. Isso é mais simples para
o escopo do projeto — não é necessário configurar autenticação em registry, push de imagens
ou pull. A desvantagem é que o build acontece na VM (usa CPU e memória da VM por ~2 min a
cada deploy). Para um projeto de produção com equipe maior, a imagem seria construída no
CI/CD e publicada no Docker Hub ou GitHub Packages.

---

## 6. Explicação dos comandos de deploy

### BLOCO 1 — Infraestrutura (Azure Cloud Shell)

#### Variáveis de ambiente

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

Essas variáveis não criam nada — apenas evitam digitar o mesmo nome 10 vezes. Se uma variável
estiver errada, todos os comandos que a usam falham de forma consistente e fácil de corrigir.
Convenção de nomenclatura: `rg-` para resource group, `vnet-` para VNet, `nsg-` para NSG,
`pip-` para public IP, `nic-` para interface de rede, `vm-` para VM. O sufixo `RM562312` é
o número de matrícula — o requisito FIAP exige identificação do aluno nos nomes dos containers.

---

#### Criar o Resource Group

```bash
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION
```

Cria o contêiner lógico `rg-satmonitor` na região `eastus2`. Todos os recursos criados
depois serão colocados dentro desse grupo. Se precisar apagar tudo, um único
`az group delete --name rg-satmonitor` remove todos os recursos.

---

#### Criar VNet e Subnet

```bash
az network vnet create \
  --resource-group $RESOURCE_GROUP \
  --name $VNET_NAME \
  --address-prefix 10.0.0.0/16 \
  --subnet-name $SUBNET_NAME \
  --subnet-prefix 10.0.1.0/24
```

- `--address-prefix 10.0.0.0/16`: range da rede privada — 65.536 endereços disponíveis.
  O `/16` é maior que o necessário para um único servidor, mas permite crescer sem recriar
  a VNet.
- `--subnet-name` e `--subnet-prefix 10.0.1.0/24`: cria a subnet dentro da VNet com 256
  endereços. A VM receberá um IP nesse range (`10.0.1.4` foi atribuído automaticamente).
- O comando cria os dois recursos ao mesmo tempo (VNet + subnet) para economizar uma chamada.

---

#### Criar NSG

```bash
az network nsg create \
  --resource-group $RESOURCE_GROUP \
  --name $NSG_NAME
```

Cria o NSG vazio `nsg-satmonitor`. Sem regras customizadas, o NSG tem apenas a regra padrão
`DenyAllInBound` — bloqueia todo tráfego de entrada. As regras de SSH e API são adicionadas
nos próximos comandos.

---

#### Regra SSH

```bash
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-ssh \
  --priority 100 \
  --protocol Tcp \
  --destination-port-range 22 \
  --access Allow
```

- `--name allow-ssh`: nome descritivo da regra — aparece no portal Azure.
- `--priority 100`: quanto menor o número, maior a prioridade. O NSG avalia regras em ordem
  crescente de prioridade e para na primeira que bater. 100 garante que esta regra é avaliada
  antes de qualquer outra que possa bloquear a porta 22.
- `--protocol Tcp`: SSH usa TCP. Especificar evita criar uma regra desnecessariamente ampla.
- `--destination-port-range 22`: só libera a porta 22, nada mais.
- `--access Allow`: a ação quando a regra bater é permitir o tráfego.

---

#### Regra API

```bash
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-api \
  --priority 110 \
  --protocol Tcp \
  --destination-port-range 8080 \
  --access Allow
```

Idêntico à regra SSH, mas para a porta 8080 onde a API Spring Boot escuta. Prioridade 110
(avaliada após a regra SSH). O frontend e os clientes mobile acessam a API por essa porta.

---

#### Regra PostgreSQL

```bash
az network nsg rule create \
  --resource-group $RESOURCE_GROUP \
  --nsg-name $NSG_NAME \
  --name allow-postgres \
  --priority 120 \
  --protocol Tcp \
  --destination-port-range 5432 \
  --access Allow
```

Libera a porta 5432 no NSG para fins de demonstração (evidência de que o banco está rodando).
**Na prática esta regra não tem efeito real**: o container do banco não publica a porta 5432
no host da VM — o tráfego externo passa pelo NSG mas não encontra nada escutando na porta.
A comunicação entre app e banco acontece internamente pela rede bridge do Docker.

---

#### Associar NSG à Subnet

```bash
az network vnet subnet update \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_NAME \
  --network-security-group $NSG_NAME
```

Vincula o NSG à subnet. Sem esse passo, o NSG existe mas não está aplicado a nenhum recurso
— as regras não têm efeito. Depois dessa associação, **todo tráfego que entra ou sai da
subnet passa pelas regras do NSG**.

---

#### Criar IP Público estático

```bash
az network public-ip create \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --allocation-method Static \
  --sku Standard
```

- `--allocation-method Static`: o endereço IP não muda quando a VM é reiniciada ou
  desalocada. Se fosse `Dynamic`, cada restart poderia gerar um IP diferente, quebrando
  o secret `VM_HOST` do GitHub Actions e qualquer cliente configurado com o IP fixo.
- `--sku Standard`: obrigatório para IPs estáticos em VMs de tamanho Standard. O SKU Basic
  não suporta zonas de disponibilidade e tem limitações de segurança.

---

#### Criar NIC

```bash
az network nic create \
  --resource-group $RESOURCE_GROUP \
  --name $NIC_NAME \
  --vnet-name $VNET_NAME \
  --subnet $SUBNET_NAME \
  --public-ip-address $PUBLIC_IP_NAME \
  --network-security-group $NSG_NAME
```

Cria a interface de rede virtual que conecta todos os recursos de rede à VM.
A NIC recebe o IP privado da subnet automaticamente (`10.0.1.4`) e associa o IP público
e o NSG. Quando a VM for criada no próximo passo, ela usará essa NIC — herdando todas
as configurações de rede já feitas.

**Por que criar a NIC separada e não deixar o `az vm create` criar tudo?** Separar a NIC
permite reutilizar configurações de rede se a VM precisar ser recriada (por exemplo, após
mudar o tamanho). A NIC com o IP público associado permanece mesmo que a VM seja deletada.

---

#### Criar a VM

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

- `--nics $NIC_NAME`: usa a NIC já criada (com IP público, NSG e subnet configurados).
  Se omitido, o Azure criaria uma NIC padrão sem as configurações personalizadas.
- `--image Ubuntu2204`: imagem oficial Ubuntu 22.04 LTS do Azure Marketplace.
- `--size Standard_D2s_v3`: tamanho da VM (2 vCPUs, 7.8 GB RAM).
- `--admin-username azureuser`: nome do usuário administrador criado dentro do Ubuntu.
  `azureuser` é a convenção padrão do Azure para VMs Linux.
- `--generate-ssh-keys`: **usado apenas para a demonstração do deploy inicial**. Gera
  um par de chaves RSA e registra a pública no `authorized_keys` da VM. A chave privada
  é salva em `~/.ssh/id_rsa` do Cloud Shell — que é **efêmero** (pode ser perdida).
  Veja a seção [Chaves SSH](#4-chaves-ssh-e-acesso-à-vm) para como o acesso real foi
  configurado com chaves permanentes.

---

#### Obter o IP da VM

```bash
VM_IP=$(az network public-ip show \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --query ipAddress \
  --output tsv)

echo $VM_IP
```

- `--query ipAddress`: filtra a saída JSON para retornar só o campo `ipAddress`.
- `--output tsv`: formato Tab-Separated Values — retorna o valor sem aspas, perfeito para
  atribuir a uma variável shell.
- O resultado `20.122.186.91` é salvo em `$VM_IP` para usar no próximo passo.

---

### BLOCO 2 — SSH + instalar Docker (dentro da VM)

#### Conectar na VM

```bash
ssh azureuser@$VM_IP
```

Conecta na VM usando a chave privada em `~/.ssh/` do Cloud Shell (gerada pelo
`--generate-ssh-keys`). A partir daqui todos os comandos rodam **dentro da VM**.

---

#### Instalar dependências para o repositório Docker

```bash
sudo apt update && sudo apt install -y ca-certificates curl gnupg
```

- `apt update`: atualiza a lista de pacotes disponíveis nos repositórios do Ubuntu.
- `ca-certificates`: certificados SSL necessários para verificar conexões HTTPS.
- `curl`: ferramenta para baixar arquivos via HTTP/HTTPS (usada no próximo passo).
- `gnupg`: ferramenta para verificar assinaturas GPG dos pacotes Docker.
- `-y`: responde "sim" automaticamente a todas as confirmações — necessário para scripts
  não interativos.

---

#### Adicionar chave GPG do Docker

```bash
sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
```

- `install -m 0755 -d /etc/apt/keyrings`: cria o diretório com permissão `755` (leitura
  e execução para todos, escrita apenas para root).
- `curl -fsSL`: baixa a chave GPG do repositório oficial Docker sem mostrar progresso
  (`-s` silent) e falhando silenciosamente em erros HTTP (`-f`). `-L` segue redirects.
- `gpg --dearmor`: converte a chave do formato ASCII armored para formato binário que
  o `apt` entende.
- `-o /etc/apt/keyrings/docker.gpg`: salva a chave no local padrão para chaves de
  repositórios terceiros no Ubuntu 22.04+.

**Por que isso é necessário?** O apt precisa verificar que os pacotes Docker vêm de
uma fonte confiável (Docker Inc.) e não foram adulterados. A chave GPG permite essa
verificação criptográfica.

---

#### Adicionar repositório oficial Docker

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

- `arch=$(dpkg --print-architecture)`: detecta a arquitetura do processador (`amd64`
  para VMs x86_64 da Azure). Garante que o repositório correto seja adicionado.
- `signed-by=/etc/apt/keyrings/docker.gpg`: instrui o apt a verificar os pacotes desse
  repositório com a chave GPG adicionada no passo anterior.
- `$(. /etc/os-release && echo "$VERSION_CODENAME")`: lê o arquivo de versão do Ubuntu
  e extrai o codinome (`jammy` para Ubuntu 22.04). O repositório Docker é organizado por
  codinome do Ubuntu.
- `stable`: canal de releases estáveis (não `nightly` nem `test`).
- `tee /etc/apt/sources.list.d/docker.list`: escreve o conteúdo no arquivo de
  configuração do apt para repositórios terceiros.

---

#### Instalar Docker Engine

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

- `docker-ce`: Docker Community Edition — o engine principal.
- `docker-ce-cli`: interface de linha de comando `docker`.
- `containerd.io`: runtime de containers de baixo nível que o Docker usa internamente.
- `docker-compose-plugin`: plugin que adiciona o subcomando `docker compose` (v2).
  Diferente do `docker-compose` (v1, Python), o plugin v2 é escrito em Go e integrado
  diretamente ao CLI do Docker.

---

#### Adicionar usuário ao grupo docker

```bash
sudo usermod -aG docker $USER
```

Por padrão, o Docker Engine só aceita comandos de `root`. `usermod -aG docker` adiciona
o usuário atual (`azureuser`) ao grupo `docker`, permitindo rodar `docker` e
`docker compose` sem `sudo`.

Após esse comando é necessário **sair da sessão SSH e reconectar** (`exit` + `ssh`
novamente) para que a alteração de grupo seja aplicada. Grupos no Linux só são
carregados no login — sessões existentes não atualizam.

---

#### Verificar instalação

```bash
docker --version
docker compose version
```

Confirma que o Docker Engine e o plugin Compose estão instalados e acessíveis sem `sudo`.

---

### BLOCO 3 — Clone + .env + subir containers

#### Clonar o repositório

```bash
git clone https://github.com/pedrinzz10/satmonitor.git
cd satmonitor
```

Clona o repositório na home do `azureuser` (`~/satmonitor/`). A partir daqui o
`docker-compose.yml` e o `Dockerfile` estão disponíveis para o Docker.

---

#### Criar o arquivo .env

```bash
cp .env.example .env
```

O `.env.example` está no repositório com os nomes das variáveis mas sem os valores
sensíveis. O `.env` real (com `JWT_SECRET` e `POSTGRES_PASSWORD`) não está versionado
(listado no `.gitignore`).

**Por que não commitar o `.env` diretamente?** Credenciais em repositório são um vetor
de ataque clássico. Qualquer pessoa com acesso ao repositório (público ou privado com
muitos colaboradores) teria as chaves do sistema. O `.env.example` serve como
documentação das variáveis necessárias sem expor os valores.

---

#### Subir os containers

```bash
docker compose --profile docker up --build -d
```

- `--profile docker`: ativa os serviços marcados com `profiles: [docker]` no
  `docker-compose.yml`. Isso separa o ambiente de produção (`docker`) do ambiente de
  desenvolvimento local (`dev` com H2 em memória). Sem `--profile docker`, o comando
  não subiria nenhum container.
- `up`: cria e inicia os containers.
- `--build`: força o rebuild da imagem da aplicação a partir do `Dockerfile`. Sem essa
  flag, o Docker usaria a imagem cacheada da execução anterior — o código novo não seria
  aplicado.
- `-d` (detach): solta os containers em background. Sem `-d`, o terminal ficaria preso
  exibindo os logs e os containers seriam derrubados ao fechar a sessão SSH.

Na primeira execução, o Docker faz o build multi-stage (~2-3 minutos). Nas execuções
seguintes, as camadas do Dockerfile são cacheadas e o build é mais rápido.

---

### BLOCO 4 — Evidências

#### Status dos containers

```bash
docker compose --profile docker ps
```

Lista os containers com nome, imagem, status e portas. O status `Up X seconds (healthy)`
indica que o healthcheck passou — o banco aceita conexões e a API está respondendo.

---

#### Health check da API

```bash
curl http://localhost:8080/actuator/health
```

Chama o endpoint do Spring Boot Actuator. Retorna `{"status":"UP"}` quando a aplicação
está funcionando e conectada ao banco. O Actuator é habilitado pelo `spring-boot-starter-actuator`
no `build.gradle`.

---

#### Logs dos containers

```bash
docker compose --profile docker logs --tail=30
```

Exibe as últimas 30 linhas de log de ambos os containers. Útil para confirmar que o
Hibernate criou as tabelas (`create table tb_...`) e que não houve erro de conexão.

---

#### Inspecionar container da aplicação

```bash
docker container exec satmonitor-app-RM562312 pwd
docker container exec satmonitor-app-RM562312 ls -l /app
docker container exec satmonitor-app-RM562312 whoami
```

- `exec`: executa um comando dentro de um container em execução sem entrar no shell.
- `pwd`: confirma que o `WORKDIR` é `/app` (conforme definido no Dockerfile).
- `ls -l /app`: mostra o `app.jar` e confirma que está presente e legível.
- `whoami`: retorna `satuser` — prova que o container não está rodando como `root`.

---

#### Inspecionar container do banco

```bash
docker container exec satmonitor-db-RM562312 pwd
docker container exec satmonitor-db-RM562312 ls -l /var/lib/postgresql/data
docker container exec satmonitor-db-RM562312 whoami
```

- `ls -l /var/lib/postgresql/data`: lista os arquivos de dados do PostgreSQL — confirma
  que o banco inicializou e os arquivos foram criados.
- `whoami`: retorna `postgres` — usuário padrão da imagem oficial PostgreSQL.

---

### BLOCO 5 — CRUD + SELECT

#### Criar agência

```bash
curl -s -X POST http://localhost:8080/agencias \
  -H "Content-Type: application/json" \
  -d '{"nome":"Agencia Espacial Brasileira","siglaPais":"BR","tipoAgencia":"Governamental"}'
```

Primeira operação de escrita no banco. `POST /agencias` é público (não requer JWT).
O Hibernate executa um `INSERT INTO tb_agencia` e retorna o objeto criado com o `id: 1`.

---

#### Registrar operador

```bash
curl -s -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123","nome":"Admin","agenciaId":1}'
```

Cria o primeiro operador. A senha `senha123` é transformada em hash BCrypt antes de ser
persistida em `tb_operador` — nunca armazenada em texto puro.

---

#### Listar tabelas no banco

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor -c "\dt"
```

- `psql`: CLI do PostgreSQL, executada dentro do container.
- `-U satuser`: conecta com o usuário `satuser` (criado pela variável `POSTGRES_USER`).
- `-d satmonitor`: banco de dados do projeto.
- `-c "\dt"`: executa o meta-comando `\dt` que lista todas as tabelas.

Prova que o Hibernate criou automaticamente as 13 tabelas na primeira inicialização.

---

#### Login e captura do token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin@sat.dev","senha":"senha123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
```

- `grep -o '"token":"[^"]*"'`: extrai do JSON a chave e valor do token JWT sem usar jq.
- `cut -d'"' -f4`: divide por aspas e pega o quarto campo — o valor do token.
- O resultado é salvo em `$TOKEN` para uso nos próximos comandos autenticados.

---

#### Criar missão (autenticado)

```bash
curl -s -X POST http://localhost:8080/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nome":"Missao Amazonia-1",...}'
```

`Authorization: Bearer $TOKEN` envia o JWT no header HTTP. O Spring Security intercepta,
valida a assinatura com `JWT_SECRET` e extrai o operador logado do payload do token.

---

#### SELECT de evidência — vínculo operador-missão

```bash
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor \
  -c "SELECT o.id, o.login, m.nome AS missao, om.role
      FROM tb_operador_missao om
      JOIN tb_operador o ON o.id = om.operador_id
      JOIN tb_missao m ON m.id = om.missao_id;"
```

Demonstra a tabela de relacionamento N:N entre operadores e missões, com o campo `role`
(DONO/SUPERVISOR/MEMBRO). Quando um operador cria uma missão via `POST /missoes`, o
`MissaoService` automaticamente insere uma linha em `tb_operador_missao` com `role=DONO`.

---

### BLOCO 6 — Persistência

```bash
docker compose --profile docker stop    # para os containers (não remove)
docker compose --profile docker start   # reinicia os containers parados
curl http://localhost:8080/actuator/health
docker container exec satmonitor-db-RM562312 \
  psql -U satuser -d satmonitor -c "SELECT id, login FROM tb_operador;"
```

Sequência para demonstrar que os dados sobrevivem ao restart.

- `stop`: interrompe os processos dos containers mas mantém seus sistemas de arquivo e
  o volume `satmonitor-db-data` intactos.
- `start`: reinicia os containers parados — não faz `--build`, não recria volumes.
- O `SELECT` final mostra os mesmos dados do antes do `stop`, provando que o volume
  nomeado persistiu os dados do PostgreSQL.

**Por que `stop/start` e não `down/up`?** `docker compose down` remove os containers
(mas mantém volumes, a menos que `-v` seja passado). Reiniciar com `up` depois de `down`
recriaria os containers, o que é mais demorado. `stop/start` é mais rápido e demonstra
claramente que os dados vêm do volume, não de estado em memória.

---

## 7. Variáveis de ambiente e segredos

### Por que `.env` e não variáveis hardcoded no `docker-compose.yml`?

O `docker-compose.yml` é versionado no git e acessível a qualquer pessoa com acesso ao
repositório. Colocar `JWT_SECRET=minha-chave-secreta` diretamente no compose exporia o
segredo. O `.env` fica no `.gitignore` e nunca é commitado.

### Por que `.env.example` no repositório?

Serve como documentação das variáveis necessárias. Qualquer pessoa que clonar o projeto
sabe quais variáveis precisam ser configuradas antes de subir os containers.

### JWT_SECRET

Usado para assinar e verificar tokens JWT. Se for curto ou previsível, tokens podem ser
forjados — um atacante poderia criar um token válido para qualquer operador sem precisar
da senha. Deve ter no mínimo 32 caracteres aleatórios.

### POSTGRES_PASSWORD

Senha do banco. Mesmo que a porta 5432 não esteja exposta externamente, uma senha fraca
seria um risco se alguém conseguisse acesso ao container ou à VM.

### Variáveis no GitHub Actions

Para o CI/CD funcionar, dois secrets são configurados em
**Settings → Secrets and variables → Actions**:

| Secret | Conteúdo |
|---|---|
| `VM_SSH_KEY` | Chave privada SSH (`id_ed25519`) para conectar na VM |
| `VM_HOST` | IP público da VM (`20.122.186.91`) |

O `deploy.yml` lê esses secrets em tempo de execução. Eles nunca aparecem nos logs do
GitHub Actions — são substituídos automaticamente por `***`.
