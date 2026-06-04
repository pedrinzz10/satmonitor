# Testes manuais — Guia completo de validação da API

Guia para testar manualmente todos os endpoints da API SatMonitor. Os cenários seguem a mesma sequência do script `TestApiControllers/test-api.sh` e cobrem fluxos de sucesso, regras de negócio e controle de acesso.

## Pré-requisitos

- API rodando em `http://localhost:8080` (`./gradlew bootRun`)
- `curl` instalado
- `jq` instalado (opcional, para formatar o JSON de saída)

```bash
# Verificar se a API está rodando
curl -s http://localhost:8080/actuator/health | jq .
# Esperado: { "status": "UP" }
```

---

## Variáveis úteis

Execute estes exports antes de começar para reusar os tokens e IDs ao longo dos testes:

```bash
BASE="http://localhost:8080"
TOKEN_DONO=""
TOKEN_MEMBRO=""
TOKEN_SUPERVISOR=""
TOKEN_FORASTEIRO=""
MISSAO_ID=""
SAT_ID=""
SENSOR_TERMICO_ID=""
SENSOR_PRESSAO_ID=""
SENSOR_RADIACAO_ID=""
SENSOR_MAG_ID=""
LEITURA_NORMAL_ID=""
LEITURA_ALERTA_ID=""
LEITURA_CRITICO_ID=""
```

---

## Seção 1 — Health check

**Objetivo:** confirmar que a aplicação está no ar.

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

**Esperado:**
```json
{ "status": "UP" }
```

---

## Seção 2 — Registro de operadores

Cria 4 operadores que serão usados nos demais testes.

### 2.1 Registrar operadores novos (todos devem retornar 201)

```bash
# Dono
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"dono@sat.dev","senha":"senha123","nome":"Operador Dono"}'
# Esperado: 201

# Membro
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"membro@sat.dev","senha":"senha123","nome":"Operador Membro"}'
# Esperado: 201

# Supervisor
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"supervisor@sat.dev","senha":"senha123","nome":"Operador Supervisor"}'
# Esperado: 201

# Forasteiro (nunca entrará na missão)
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"forasteiro@sat.dev","senha":"senha123","nome":"Operador Forasteiro"}'
# Esperado: 201
```

### 2.2 Login duplicado — deve retornar 400

```bash
curl -s -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"dono@sat.dev","senha":"abc","nome":"Dup"}' | jq .
# Esperado: 400, error: "Login já está em uso"
```

### 2.3 Campo obrigatório vazio — deve retornar 400

```bash
curl -s -X POST $BASE/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{"login":"","senha":"abc","nome":"Vazio"}' | jq .
# Esperado: 400, error com campo login
```

---

## Seção 3 — Login e obtenção de tokens JWT

```bash
# Login do dono
TOKEN_DONO=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"dono@sat.dev","senha":"senha123"}' | jq -r '.token')
echo "TOKEN_DONO: $TOKEN_DONO"
# Esperado: token JWT (começa com eyJ...)

# Login do membro
TOKEN_MEMBRO=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"membro@sat.dev","senha":"senha123"}' | jq -r '.token')

# Login do supervisor
TOKEN_SUPERVISOR=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"supervisor@sat.dev","senha":"senha123"}' | jq -r '.token')

# Login do forasteiro
TOKEN_FORASTEIRO=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"forasteiro@sat.dev","senha":"senha123"}' | jq -r '.token')
```

**Verificar que os tokens foram obtidos:**
```bash
[ -n "$TOKEN_DONO" ] && echo "OK" || echo "FALHOU — verifique se a API está rodando"
```

---

## Seção 4 — Missões: CRUD básico

### 4.1 Criar missão — deve retornar 201 com role DONO

```bash
RESPONSE=$(curl -s -X POST $BASE/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_DONO" \
  -d '{"nome":"Missao Alpha","descricao":"Missao principal de testes","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"senha123"}')

echo $RESPONSE | jq .
MISSAO_ID=$(echo $RESPONSE | jq -r '.id')
echo "MISSAO_ID: $MISSAO_ID"
```

**Verificar na resposta:**
- `"id"` não é null
- `"roleDoOperador": "DONO"`
- `"_links"` existe

### 4.2 Listar missões do operador logado — deve retornar 200

```bash
curl -s $BASE/missoes \
  -H "Authorization: Bearer $TOKEN_DONO" | jq .
# Esperado: 200, totalElements >= 1
```

### 4.3 Buscar missão por id — deve retornar 200

```bash
curl -s $BASE/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN_DONO" | jq .
# Esperado: 200, nome: "Missao Alpha"
```

### 4.4 Forasteiro tenta ver missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" \
  $BASE/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN_FORASTEIRO"
# Esperado: 403
```

### 4.5 Sem token tenta ver missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" $BASE/missoes/$MISSAO_ID
# Esperado: 403
```

### 4.6 Atualizar missão (DONO) — deve retornar 200

```bash
curl -s -X PUT $BASE/missoes/$MISSAO_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_DONO" \
  -d '{"nome":"Missao Alpha v2","descricao":"Atualizada","dataLancamento":"2026-07-01","status":"ATIVA"}' | jq .
# Esperado: 200, nome: "Missao Alpha v2", status: "ATIVA"
```

### 4.7 Forasteiro tenta atualizar missão — deve retornar 404

```bash
curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/missoes/$MISSAO_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_FORASTEIRO" \
  -d '{"nome":"Invasao","descricao":"x","dataLancamento":"2026-07-01","status":"ATIVA"}'
# Esperado: 404 (não é membro = vínculo não existe)
```

---

## Seção 5 — Missões: entrar e sair

### 5.1 Senha errada — deve retornar 401

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"senha":"errada"}' | jq .
# Esperado: 401, error: "Senha da missão incorreta"
```

### 5.2 Membro entra com senha correta — deve retornar 200 com role MEMBRO

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"senha":"senha123"}' | jq .
# Esperado: 200, roleDoOperador: "MEMBRO"
```

### 5.3 Já é membro — deve retornar 409

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"senha":"senha123"}' | jq .
# Esperado: 409, error: "Operador já é membro desta missão"
```

### 5.4 Supervisor entra — deve retornar 200

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"senha":"senha123"}' | jq .
# Esperado: 200, roleDoOperador: "MEMBRO" (todos entram como MEMBRO)
```

### 5.5 MEMBRO tenta atualizar missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/missoes/$MISSAO_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"nome":"Invasao","descricao":"x","dataLancamento":"2026-07-01","status":"ATIVA"}'
# Esperado: 403
```

### 5.6 MEMBRO tenta deletar missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN_MEMBRO"
# Esperado: 403
```

### 5.7 Regra do DONO único: criar missão solo e tentar sair

```bash
# Criar missão com apenas o dono
RESPONSE=$(curl -s -X POST $BASE/missoes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_DONO" \
  -d '{"nome":"Missao Solo","descricao":"Sem outros donos","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"senha456"}')
MISSAO_SOLO_ID=$(echo $RESPONSE | jq -r '.id')
echo "MISSAO_SOLO_ID: $MISSAO_SOLO_ID"

# DONO único tenta sair → deve falhar
curl -s -X POST $BASE/missoes/$MISSAO_SOLO_ID/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_DONO" \
  -d '{}' | jq .
# Esperado: 400, error: "Transfira a propriedade antes de sair da missão"
```

### 5.8 Supervisor entra e sai da missão solo com sucesso

```bash
# Supervisor entra
curl -s -X POST $BASE/missoes/$MISSAO_SOLO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"senha":"senha456"}' | jq .
# Esperado: 200

# Supervisor sai
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/missoes/$MISSAO_SOLO_ID/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{}'
# Esperado: 204
```

---

## Seção 6 — Missões: gerenciamento de membros

### 6.1 Forasteiro tenta listar membros — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" \
  $BASE/missoes/$MISSAO_ID/membros \
  -H "Authorization: Bearer $TOKEN_FORASTEIRO"
# Esperado: 403
```

### 6.2 Membro lista membros — deve retornar 200 com 3 membros

```bash
MEMBROS=$(curl -s $BASE/missoes/$MISSAO_ID/membros \
  -H "Authorization: Bearer $TOKEN_MEMBRO")
echo $MEMBROS | jq .
echo "Total de membros: $(echo $MEMBROS | jq '. | length')"
# Esperado: 200, 3 membros (dono, membro, supervisor)

# Extrair IDs para os próximos testes
ID_SUPERVISOR=$(echo $MEMBROS | jq -r '.[] | select(.login=="supervisor@sat.dev") | .operadorId')
ID_MEMBRO=$(echo $MEMBROS | jq -r '.[] | select(.login=="membro@sat.dev") | .operadorId')
ID_DONO=$(echo $MEMBROS | jq -r '.[] | select(.login=="dono@sat.dev") | .operadorId')
echo "IDs: DONO=$ID_DONO | MEMBRO=$ID_MEMBRO | SUPERVISOR=$ID_SUPERVISOR"
```

### 6.3 DONO tenta se remover via DELETE /membros — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE $BASE/missoes/$MISSAO_ID/membros/$ID_DONO \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 403 ("Use o endpoint /sair")
```

### 6.4 MEMBRO tenta promover — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X PATCH "$BASE/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer $TOKEN_MEMBRO"
# Esperado: 403 (MEMBRO não tem permissão)
```

### 6.5 DONO promove supervisor para SUPERVISOR — deve retornar 200

```bash
curl -s -X PATCH "$BASE/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer $TOKEN_DONO" | jq .
# Esperado: 200, role: "SUPERVISOR"
```

### 6.6 DONO rebaixa supervisor para MEMBRO — deve retornar 200

```bash
curl -s -X PATCH "$BASE/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=MEMBRO" \
  -H "Authorization: Bearer $TOKEN_DONO" | jq .
# Esperado: 200, role: "MEMBRO"
```

### 6.7 Re-promover supervisor (necessário para as próximas seções)

```bash
curl -s -X PATCH "$BASE/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" \
  -H "Authorization: Bearer $TOKEN_DONO" | jq .
# Esperado: 200, role: "SUPERVISOR"
```

### 6.8 DONO tenta alterar a própria role — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X PATCH "$BASE/missoes/$MISSAO_ID/membros/$ID_DONO?novoRole=MEMBRO" \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 403
```

### 6.9 DONO remove membro — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE $BASE/missoes/$MISSAO_ID/membros/$ID_MEMBRO \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 204
```

### 6.10 Membro removido tenta sair — deve retornar 404

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{}' | jq .
# Esperado: 404 (vínculo não existe mais)
```

---

## Seção 7 — Satélites: CRUD e controle de acesso

### 7.1 Membro re-entra (necessário para testes de permissão de satélite)

```bash
curl -s -X POST $BASE/missoes/$MISSAO_ID/entrar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"senha":"senha123"}' | jq .
# Esperado: 200
```

### 7.2 Sem token — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -d '{"nome":"SAT-01","dataLancamento":"2026-01-15","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":550.0,"inclinacao":53.5,"longitudeNodo":12.3}}'
# Esperado: 403
```

### 7.3 MEMBRO tenta criar satélite — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"nome":"SAT-01","dataLancamento":"2026-01-15","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":550.0,"inclinacao":53.5,"longitudeNodo":12.3}}'
# Esperado: 403 (MEMBRO < SUPERVISOR)
```

### 7.4 SUPERVISOR cria satélite — deve retornar 201

```bash
RESPONSE=$(curl -s -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"SAT-01","dataLancamento":"2026-01-15","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":550.0,"inclinacao":53.5,"longitudeNodo":12.3}}')
echo $RESPONSE | jq .
SAT_ID=$(echo $RESPONSE | jq -r '.id')
echo "SAT_ID: $SAT_ID"
# Verificar: id não null, nome "SAT-01", altitudeKm: 550.0, missaoId correto
```

### 7.5 Nome duplicado — deve retornar 400

```bash
curl -s -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"SAT-01","dataLancamento":"2026-01-15","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":550.0,"inclinacao":53.5}}' | jq .
# Esperado: 400 (nome duplicado na missão)
```

### 7.6 missaoId inexistente — deve retornar 404

```bash
curl -s -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"SAT-X","dataLancamento":"2026-01-01","missaoId":99999,"coordenadas":{"altitudeKm":100.0,"inclinacao":0.0}}' | jq .
# Esperado: 404
```

### 7.7 GET público de satélites (sem token)

```bash
# Listar todos
curl -s $BASE/satelites | jq .
# Esperado: 200

# Buscar por id
curl -s $BASE/satelites/$SAT_ID | jq .
# Esperado: 200, _links presente

# Satélite inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/satelites/99999
# Esperado: 404

# Listar por missão
curl -s $BASE/satelites/missao/$MISSAO_ID | jq .
# Esperado: 200, totalElements >= 1

# missaoId inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/satelites/missao/99999
# Esperado: 404
```

### 7.8 Estatísticas sem leituras — deve retornar totalLeituras=0

```bash
curl -s $BASE/satelites/$SAT_ID/estatisticas | jq .
# Esperado: 200, totalLeituras: 0
```

### 7.9 SUPERVISOR atualiza satélite

```bash
curl -s -X PUT $BASE/satelites/$SAT_ID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"SAT-01","dataLancamento":"2026-02-01","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":600.0,"inclinacao":55.0}}' | jq .
# Esperado: 200, altitudeKm: 600.0
```

### 7.10 SUPERVISOR tenta deletar satélite — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/satelites/$SAT_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 403 (SUPERVISOR < DONO)
```

---

## Seção 8 — Sensores: criação dos 4 tipos

### 8.1 Sensor Térmico — deve retornar 201 com detalhe=CELSIUS

```bash
RESPONSE=$(curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Termometro","unidade":"graus_C","limiteMin":0.0,"limiteMax":80.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}')
echo $RESPONSE | jq .
SENSOR_TERMICO_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: detalhe: "CELSIUS", tipo: "TERMICO"
```

### 8.2 Sensor de Pressão — deve retornar 201 com detalhe=ABSOLUTA

```bash
RESPONSE=$(curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Barometro","unidade":"hPa","limiteMin":950.0,"limiteMax":1050.0,"margemAlerta":5.0,"sateliteId":'"$SAT_ID"',"tipo":"PRESSAO","tipoPressao":"ABSOLUTA"}')
echo $RESPONSE | jq .
SENSOR_PRESSAO_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: detalhe: "ABSOLUTA"
```

### 8.3 Sensor de Radiação — deve retornar 201 com detalhe=IONIZANTE

```bash
RESPONSE=$(curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Geiger","unidade":"Gy","limiteMin":0.0,"limiteMax":5.0,"margemAlerta":20.0,"sateliteId":'"$SAT_ID"',"tipo":"RADIACAO","tipoRadiacao":"IONIZANTE"}')
echo $RESPONSE | jq .
SENSOR_RADIACAO_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: detalhe: "IONIZANTE"
```

### 8.4 Magnetômetro — deve retornar 201 com detalhe=XYZ

```bash
RESPONSE=$(curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Magnetometro","unidade":"nT","limiteMin":-50000.0,"limiteMax":50000.0,"margemAlerta":15.0,"sateliteId":'"$SAT_ID"',"tipo":"MAGNETOMETRO","eixosMedicao":"XYZ"}')
echo $RESPONSE | jq .
SENSOR_MAG_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: detalhe: "XYZ"
```

---

## Seção 9 — Sensores: validações de regras de negócio

### 9.1 limiteMin >= limiteMax — deve retornar 400

```bash
curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Inv","unidade":"X","limiteMin":100.0,"limiteMax":50.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' | jq .
# Esperado: 400
```

### 9.2 Tipo inválido — deve retornar 400

```bash
curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Son","unidade":"Hz","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"SONICO"}' | jq .
# Esperado: 400, "Corpo da requisição inválido ou mal formatado"
```

### 9.3 Campo específico ausente — deve retornar 400

```bash
# TERMICO sem unidadeEscala
curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"SemEscala","unidade":"graus_C","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO"}' | jq .
# Esperado: 400
```

### 9.4 Nome duplicado no mesmo satélite — deve retornar 400

```bash
curl -s -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR" \
  -d '{"nome":"Termometro","unidade":"K","limiteMin":0.0,"limiteMax":80.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"KELVIN"}' | jq .
# Esperado: 400 (nome "Termometro" já existe neste satélite)
```

### 9.5 MEMBRO tenta criar sensor — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/sensores \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{"nome":"SensorMembro","unidade":"X","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}'
# Esperado: 403
```

### 9.6 GET público de sensores

```bash
# Listar todos
curl -s $BASE/sensores | jq .
# Esperado: 200

# Buscar por id com HATEOAS
curl -s $BASE/sensores/$SENSOR_TERMICO_ID | jq .
# Esperado: 200, _links presente

# Listar sensores do satélite
curl -s $BASE/sensores/satelite/$SAT_ID | jq .
# Esperado: 200, totalElements: 4

# sateliteId inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/sensores/satelite/99999
# Esperado: 404
```

---

## Seção 10 — Leituras: StatusCalculator

Referência do sensor térmico usado nos testes:
- `limiteMin=-10`, `limiteMax=90`, `margemAlerta=10%` (após PUT na seção 9)
- `zonaAlertaMin = -10 + 100×0.10 = -5.0`
- `zonaAlertaMax = 90 - 100×0.10 = 80.0`

> **Obs:** A seção 9 atualiza o sensor térmico para `limiteMin=-10.0`, `limiteMax=90.0`. Se o sensor não foi atualizado, adapte os valores esperados abaixo.

```
|──CRITICO──|─ALERTA─|────────NORMAL────────|─ALERTA─|──CRITICO──|
-10         -5       80                      90
```

### 10.1 NORMAL (40.0 — dentro da faixa segura)

```bash
RESPONSE=$(curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":40.0,"sensorId":'"$SENSOR_TERMICO_ID"'}')
echo $RESPONSE | jq .
LEITURA_NORMAL_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: status: "NORMAL", dataHoraLeitura preenchida, _links presente
```

### 10.2 ALERTA superior (87.0 — entre zonaAlertaMax=80 e limiteMax=90)

```bash
RESPONSE=$(curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":87.0,"sensorId":'"$SENSOR_TERMICO_ID"'}')
echo $RESPONSE | jq .
LEITURA_ALERTA_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: status: "ALERTA"
```

### 10.3 CRITICO acima do limiteMax (150.0)

```bash
RESPONSE=$(curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":150.0,"sensorId":'"$SENSOR_TERMICO_ID"'}')
echo $RESPONSE | jq .
LEITURA_CRITICO_ID=$(echo $RESPONSE | jq -r '.id')
# Verificar: status: "CRITICO"
```

### 10.4 CRITICO abaixo do limiteMin (-50.0)

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":-50.0,"sensorId":'"$SENSOR_TERMICO_ID"'}' | jq .
# Verificar: status: "CRITICO"
```

### 10.5 ALERTA inferior (-8.0 — entre limiteMin=-10 e zonaAlertaMin=-5)

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":-8.0,"sensorId":'"$SENSOR_TERMICO_ID"'}' | jq .
# Verificar: status: "ALERTA"
```

### 10.6 Fronteira: valor == limiteMin → ALERTA

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":-10.0,"sensorId":'"$SENSOR_TERMICO_ID"'}' | jq .
# Verificar: status: "ALERTA" (não é < -10, então cai em < zonaAlertaMin)
```

### 10.7 Fronteira: valor == zonaAlertaMax → NORMAL

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":85.0,"sensorId":'"$SENSOR_TERMICO_ID"'}' | jq .
# Verificar: status: "NORMAL" (não é > 85, fronteira exclusiva)
```

### 10.8 sensorId inexistente — deve retornar 404

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":40.0,"sensorId":99999}' | jq .
# Esperado: 404
```

### 10.9 Campo obrigatório ausente — deve retornar 400

```bash
curl -s -X POST $BASE/leituras \
  -H "Content-Type: application/json" \
  -d '{"valor":40.0}' | jq .
# Esperado: 400, error com campo sensorId
```

---

## Seção 11 — Leituras: listagem e filtros

```bash
# Listar todas as leituras
curl -s $BASE/leituras | jq .
# Esperado: 200, totalElements >= 7

# Buscar leitura por id com links
curl -s $BASE/leituras/$LEITURA_NORMAL_ID | jq .
# Verificar: _links.sensor e _links.satelite presentes

# Leitura inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/leituras/99999
# Esperado: 404

# Todas as leituras do sensor térmico
curl -s $BASE/leituras/sensor/$SENSOR_TERMICO_ID | jq .
# Esperado: 200, totalElements >= 7

# Filtro por CRITICO
curl -s "$BASE/leituras/sensor/$SENSOR_TERMICO_ID?status=CRITICO" | jq .
# Esperado: 200, totalElements >= 2

# Filtro por ALERTA
curl -s "$BASE/leituras/sensor/$SENSOR_TERMICO_ID?status=ALERTA" | jq .
# Esperado: 200, totalElements >= 2

# Filtro por NORMAL
curl -s "$BASE/leituras/sensor/$SENSOR_TERMICO_ID?status=NORMAL" | jq .
# Esperado: 200, totalElements >= 1

# Sensor sem leituras → totalElements=0
curl -s $BASE/leituras/sensor/$SENSOR_PRESSAO_ID | jq .
# Esperado: 200, totalElements: 0

# Leituras por satélite
curl -s $BASE/leituras/satelite/$SAT_ID | jq .
# Esperado: 200, totalElements >= 7

# Filtro por satélite + status
curl -s "$BASE/leituras/satelite/$SAT_ID?status=CRITICO" | jq .
# Esperado: 200

# sensorId inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/leituras/sensor/99999
# Esperado: 404

# sateliteId inexistente
curl -s -o /dev/null -w "%{http_code}" $BASE/leituras/satelite/99999
# Esperado: 404
```

---

## Seção 12 — Estatísticas após leituras

```bash
curl -s $BASE/satelites/$SAT_ID/estatisticas | jq .
# Esperado: 200
# Verificar:
#   totalLeituras >= 7
#   totalCriticos >= 2
#   totalAlertas >= 2
#   ultimaLeitura não null
#   mediaValor calculado (não zero)
```

---

## Seção 13 — Leituras: exclusão com controle de acesso

### 13.1 Sem token — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_NORMAL_ID
# Esperado: 403
```

### 13.2 Forasteiro (não membro) — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_NORMAL_ID \
  -H "Authorization: Bearer $TOKEN_FORASTEIRO"
# Esperado: 403
```

### 13.3 MEMBRO da missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_NORMAL_ID \
  -H "Authorization: Bearer $TOKEN_MEMBRO"
# Esperado: 403 (MEMBRO < SUPERVISOR)
```

### 13.4 SUPERVISOR deleta — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_ALERTA_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 204
```

### 13.5 Leitura já deletada — deve retornar 404

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_ALERTA_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 404
```

### 13.6 DONO deleta — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/leituras/$LEITURA_CRITICO_ID \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 204
```

---

## Seção 14 — Sensores: exclusão (apenas DONO)

### 14.1 SUPERVISOR tenta deletar sensor — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/sensores/$SENSOR_MAG_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 403
```

### 14.2 DONO deleta sensor — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/sensores/$SENSOR_RADIACAO_ID \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 204
```

### 14.3 Verificar exclusão

```bash
# Sensor deletado retorna 404
curl -s -o /dev/null -w "%{http_code}" $BASE/sensores/$SENSOR_RADIACAO_ID
# Esperado: 404

# Satélite agora tem 3 sensores
curl -s $BASE/sensores/satelite/$SAT_ID | jq .totalElements
# Esperado: 3
```

---

## Seção 15 — Satélites: exclusão em cascata

### 15.1 Criar satélite descartável para teste de exclusão

```bash
RESPONSE=$(curl -s -X POST $BASE/satelites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_DONO" \
  -d '{"nome":"SAT-DESCARTAVEL","dataLancamento":"2026-01-01","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":200.0,"inclinacao":0.0}}')
SAT_DESCARTAVEL_ID=$(echo $RESPONSE | jq -r '.id')
echo "SAT_DESCARTAVEL_ID: $SAT_DESCARTAVEL_ID"
```

### 15.2 SUPERVISOR tenta deletar — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/satelites/$SAT_DESCARTAVEL_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 403
```

### 15.3 DONO deleta — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/satelites/$SAT_DESCARTAVEL_ID \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 204
```

### 15.4 Verificar exclusão

```bash
curl -s -o /dev/null -w "%{http_code}" $BASE/satelites/$SAT_DESCARTAVEL_ID
# Esperado: 404
```

---

## Seção 16 — Missões: exclusão e saída voluntária

### 16.1 SUPERVISOR tenta deletar missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN_SUPERVISOR"
# Esperado: 403
```

### 16.2 MEMBRO tenta deletar missão — deve retornar 403

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/missoes/$MISSAO_ID \
  -H "Authorization: Bearer $TOKEN_MEMBRO"
# Esperado: 403
```

### 16.3 MEMBRO sai voluntariamente — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/missoes/$MISSAO_ID/sair \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_MEMBRO" \
  -d '{}'
# Esperado: 204
```

### 16.4 DONO deleta missão Solo — deve retornar 204

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE/missoes/$MISSAO_SOLO_ID \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 204
```

### 16.5 Verificar exclusão

```bash
curl -s -o /dev/null -w "%{http_code}" $BASE/missoes/$MISSAO_SOLO_ID \
  -H "Authorization: Bearer $TOKEN_DONO"
# Esperado: 404
```

---

## Resumo dos cenários por categoria

### Autenticação e acesso

| # | Cenário | Esperado |
|---|---------|:--------:|
| 2.2 | Login duplicado no registro | 400 |
| 2.3 | Campo vazio no registro | 400 |
| 3.x | Login com credenciais corretas | 200 + token |
| 4.4 | Forasteiro acessa missão | 403 |
| 4.5 | Sem token acessa missão | 403 |
| 5.1 | Senha errada ao entrar na missão | 401 |
| 5.3 | Operador já membro tenta entrar | 409 |

### Controle de acesso por role

| # | Cenário | Role | Esperado |
|---|---------|:----:|:--------:|
| 4.7 | PUT missão por forasteiro | — | 404 |
| 5.5 | PUT missão por MEMBRO | MEMBRO | 403 |
| 7.3 | Criar satélite por MEMBRO | MEMBRO | 403 |
| 7.10 | Deletar satélite por SUPERVISOR | SUPERVISOR | 403 |
| 9.5 | Criar sensor por MEMBRO | MEMBRO | 403 |
| 13.3 | Deletar leitura por MEMBRO | MEMBRO | 403 |
| 14.1 | Deletar sensor por SUPERVISOR | SUPERVISOR | 403 |
| 15.2 | Deletar satélite por SUPERVISOR | SUPERVISOR | 403 |
| 16.1 | Deletar missão por SUPERVISOR | SUPERVISOR | 403 |

### StatusCalculator

| # | Valor | Status esperado |
|---|------:|:---------------:|
| 10.1 | 40.0 (centro) | NORMAL |
| 10.2 | 87.0 (zona alerta superior) | ALERTA |
| 10.3 | 150.0 (> limiteMax) | CRITICO |
| 10.4 | -50.0 (< limiteMin) | CRITICO |
| 10.5 | -8.0 (zona alerta inferior) | ALERTA |
| 10.6 | -10.0 (== limiteMin) | ALERTA |
| 10.7 | 85.0 (== zonaAlertaMax) | NORMAL |

---

## Executar o script completo

O arquivo `TestApiControllers/test-api.sh` executa todos esses cenários automaticamente e reporta PASS/FAIL:

```bash
# No Linux/Mac ou WSL:
chmod +x TestApiControllers/test-api.sh
./TestApiControllers/test-api.sh

# Saída esperada (todos passando):
# ╔══════════════════════════════════════════════════════╗
# ║        SatMonitor — Bateria de Testes da API         ║
# ╚══════════════════════════════════════════════════════╝
# ...
# Total de testes : 98
# Passaram        : 98
# Falharam        : 0
```
