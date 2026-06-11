# Comandos — Deploy SatMonitor na Azure
> Cole bloco a bloco no terminal. Arquivo para uso durante a gravação.

---

## BLOCO 1 — Infraestrutura (Azure Cloud Shell)

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
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION
```

```bash
az network vnet create \
  --resource-group $RESOURCE_GROUP \
  --name $VNET_NAME \
  --address-prefix 10.0.0.0/16 \
  --subnet-name $SUBNET_NAME \
  --subnet-prefix 10.0.1.0/24
```

```bash
az network nsg create \
  --resource-group $RESOURCE_GROUP \
  --name $NSG_NAME
```

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

> ⚠️ **Limitação conhecida:** a porta 22 está aberta para qualquer IP (`*`) pois o GitHub Actions usa IPs dinâmicos para fazer o deploy via SSH. Em produção real, restringiríamos para IPs fixos da equipe. A mitigação aplicada foi autenticação exclusivamente por chave Ed25519 — acesso por senha está desabilitado na VM.

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

> ⚠️ **Regra removida em produção:** esta regra foi criada apenas para validar a conectividade inicial com o banco durante a configuração. Foi removida do NSG logo após — a porta 5432 não está exposta na VM de produção. O PostgreSQL só é acessível pelo container da aplicação via rede Docker interna (`satmonitor-net`).

```bash
az network vnet subnet update \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_NAME \
  --network-security-group $NSG_NAME
```

```bash
az network public-ip create \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --allocation-method Static \
  --sku Standard
```

```bash
az network nic create \
  --resource-group $RESOURCE_GROUP \
  --name $NIC_NAME \
  --vnet-name $VNET_NAME \
  --subnet $SUBNET_NAME \
  --public-ip-address $PUBLIC_IP_NAME \
  --network-security-group $NSG_NAME
```

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

```bash
VM_IP=$(az network public-ip show \
  --resource-group $RESOURCE_GROUP \
  --name $PUBLIC_IP_NAME \
  --query ipAddress \
  --output tsv)

echo $VM_IP
```

---

## BLOCO 2 — SSH + instalar Docker (dentro da VM)

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

> ⚠️ Após esse comando: digitar `exit`, reconectar com `ssh azureuser@<IP>` e continuar.

```bash
docker --version
docker compose version
```

---

## BLOCO 3 — Clone + .env + subir containers

```bash
git clone https://github.com/pedrinzz10/satmonitor.git
cd satmonitor
```

> ℹ️ **Decisão de contexto acadêmico:** o repositório é clonado diretamente na VM para simplificar o deploy via `git reset --hard`. Em produção real, a imagem seria publicada em um registry (Docker Hub ou GitHub Container Registry) e a VM apenas baixaria a imagem pronta — sem expor o código-fonte no servidor.

```bash
cp .env.example .env
```

> ⚠️ **Atenção:** editar o `.env` com os valores reais antes de subir os containers. O arquivo `.env` nunca é commitado no repositório — está no `.gitignore`. As credenciais existem apenas na VM de produção.

```bash
docker compose --profile docker up --build -d
```

> ⏳ Aguardar ~3 min até aparecer `satmonitor-app-RM562312 Started`

---

## BLOCO 4 — Evidências

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
docker logs satmonitor-app-RM562312 --tail=30
```

```bash
docker logs satmonitor-db-RM562312 --tail=30
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

## BLOCO 5 — CRUD + SELECT

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

## BLOCO 6 — Persistência

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

> LEMBRETE: deletar este arquivo após gravar o vídeo.
