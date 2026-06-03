#!/usr/bin/env bash
# ================================================================
# SatMonitor — Bateria completa de testes da API
#
# Pré-requisitos:
#   • Aplicação rodando em http://localhost:8080
#   • curl  instalado
#   • jq    instalado  (brew install jq / apt install jq)
#
# Uso:
#   chmod +x TestApiControllers/test-api.sh
#   ./TestApiControllers/test-api.sh
#
# Exit code: 0 = todos passaram | 1 = algum falhou
# ================================================================

BASE="http://localhost:8080"
TMPFILE=$(mktemp)
PASS=0
FAIL=0
FAILED_TESTS=()   # nomes dos testes que falharam

# ── Cores ────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ── Helpers ──────────────────────────────────────────────────────

section() { echo -e "\n${CYAN}${BOLD}▶ $1${NC}"; }

ok() {
  echo -e "  ${GREEN}✓ PASS${NC}  $1"
  PASS=$((PASS + 1))
}

fail() {
  echo -e "  ${RED}✗ FAIL${NC}  $1"
  echo -e "       ${YELLOW}esperado: $2  |  recebido: $3${NC}"
  FAIL=$((FAIL + 1))
  FAILED_TESTS+=("$1")
}

assert_status() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then ok "$label"; else fail "$label" "HTTP $expected" "HTTP $actual"; fi
}

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then ok "$label"; else fail "$label" "\"$expected\"" "\"$actual\""; fi
}

assert_not_empty() {
  local label="$1" val="$2"
  if [ -n "$val" ] && [ "$val" != "null" ]; then ok "$label"; else fail "$label" "valor preenchido" "null/vazio"; fi
}

assert_gte() {
  local label="$1" min="$2" actual="$3"
  if [ "$actual" -ge "$min" ] 2>/dev/null; then ok "$label"; else fail "$label" ">= $min" "$actual"; fi
}

# curl: salva body em BODY, status em STATUS
do_curl() {
  local method="$1" url="$2" body="$3" token="$4"
  local args=(-s -o "$TMPFILE" -w "%{http_code}" -X "$method" "$BASE$url")
  args+=(-H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body"  ] && args+=(-d "$body")
  STATUS=$(curl "${args[@]}")
  BODY=$(cat "$TMPFILE")
}

GET()    { do_curl GET    "$1" ""   "$2"; }
POST()   { do_curl POST   "$1" "$2" "$3"; }
PUT()    { do_curl PUT    "$1" "$2" "$3"; }
PATCH()  { do_curl PATCH  "$1" ""   "$2"; }
DELETE() { do_curl DELETE "$1" ""   "$2"; }

# ================================================================
echo -e "${CYAN}${BOLD}"
echo "╔══════════════════════════════════════════════════════╗"
echo "║        SatMonitor — Bateria de Testes da API         ║"
echo "╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ── 1. HEALTH CHECK ──────────────────────────────────────────────
section "1. Health check"

GET "/actuator/health"
assert_status "GET /actuator/health → 200" "200" "$STATUS"
assert_eq "Aplicação UP" "UP" "$(echo "$BODY" | jq -r '.status')"

# ── 2. AUTH — Registro ───────────────────────────────────────────
section "2. AUTH — Registro de operadores"

POST "/auth/registrar" '{"login":"dono@sat.dev","senha":"senha123","nome":"Operador Dono"}'
assert_status "POST /auth/registrar dono → 201" "201" "$STATUS"

POST "/auth/registrar" '{"login":"membro@sat.dev","senha":"senha123","nome":"Operador Membro"}'
assert_status "POST /auth/registrar membro → 201" "201" "$STATUS"

POST "/auth/registrar" '{"login":"supervisor@sat.dev","senha":"senha123","nome":"Operador Supervisor"}'
assert_status "POST /auth/registrar supervisor → 201" "201" "$STATUS"

POST "/auth/registrar" '{"login":"forasteiro@sat.dev","senha":"senha123","nome":"Operador Forasteiro"}'
assert_status "POST /auth/registrar forasteiro → 201" "201" "$STATUS"

# Login duplicado → IllegalArgumentException → 400
POST "/auth/registrar" '{"login":"dono@sat.dev","senha":"abc","nome":"Dup"}'
assert_status "POST /auth/registrar login duplicado → 400" "400" "$STATUS"

# Campos obrigatórios ausentes → MethodArgumentNotValidException → 400
POST "/auth/registrar" '{"login":"","senha":"abc","nome":"Vazio"}'
assert_status "POST /auth/registrar login em branco → 400" "400" "$STATUS"

# ── 3. AUTH — Login ──────────────────────────────────────────────
section "3. AUTH — Login e tokens JWT"

POST "/auth/login" '{"login":"dono@sat.dev","senha":"senha123"}'
assert_status "POST /auth/login dono → 200" "200" "$STATUS"
TOKEN_DONO=$(echo "$BODY" | jq -r '.token')
assert_not_empty "Token JWT do dono obtido" "$TOKEN_DONO"

POST "/auth/login" '{"login":"membro@sat.dev","senha":"senha123"}'
assert_status "POST /auth/login membro → 200" "200" "$STATUS"
TOKEN_MEMBRO=$(echo "$BODY" | jq -r '.token')
assert_not_empty "Token JWT do membro obtido" "$TOKEN_MEMBRO"

POST "/auth/login" '{"login":"supervisor@sat.dev","senha":"senha123"}'
assert_status "POST /auth/login supervisor → 200" "200" "$STATUS"
TOKEN_SUPERVISOR=$(echo "$BODY" | jq -r '.token')
assert_not_empty "Token JWT do supervisor obtido" "$TOKEN_SUPERVISOR"

POST "/auth/login" '{"login":"forasteiro@sat.dev","senha":"senha123"}'
assert_status "POST /auth/login forasteiro → 200" "200" "$STATUS"
TOKEN_FORASTEIRO=$(echo "$BODY" | jq -r '.token')
assert_not_empty "Token JWT do forasteiro obtido" "$TOKEN_FORASTEIRO"

if [ -z "$TOKEN_DONO" ] || [ "$TOKEN_DONO" = "null" ]; then
  echo -e "\n${RED}FATAL: tokens não obtidos — verifique se a aplicação está rodando em $BASE${NC}"
  rm -f "$TMPFILE"; exit 1
fi

# ── 4. MISSÕES — CRUD ────────────────────────────────────────────
section "4. MISSÕES — CRUD básico"

POST "/missoes" \
  '{"nome":"Missao Alpha","descricao":"Missao principal de testes","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"senha123"}' \
  "$TOKEN_DONO"
assert_status "POST /missoes → 201" "201" "$STATUS"
MISSAO_ID=$(echo "$BODY" | jq -r '.id')
assert_not_empty "id da missão válido" "$MISSAO_ID"
assert_eq "Criador recebe role DONO" "DONO" "$(echo "$BODY" | jq -r '.roleDoOperador')"
assert_eq "Links HATEOAS presentes" "true" "$(echo "$BODY" | jq 'has("_links")')"

GET "/missoes" "$TOKEN_DONO"
assert_status "GET /missoes (dono) → 200" "200" "$STATUS"
assert_gte "Dono vê pelo menos 1 missão" 1 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/missoes/$MISSAO_ID" "$TOKEN_DONO"
assert_status "GET /missoes/{id} (dono membro) → 200" "200" "$STATUS"
assert_eq "Nome da missao correto" "Missao Alpha" "$(echo "$BODY" | jq -r '.nome')"

# Não membro com token → AcessoNegadoException → 403
GET "/missoes/$MISSAO_ID" "$TOKEN_FORASTEIRO"
assert_status "GET /missoes/{id} (não membro com token) → 403" "403" "$STATUS"

# GET /missoes agora exige autenticação (depende do operador logado) → sem token Spring Security bloqueia → 403
GET "/missoes/$MISSAO_ID"
assert_status "GET /missoes/{id} sem token → 403" "403" "$STATUS"

PUT "/missoes/$MISSAO_ID" \
  '{"nome":"Missao Alpha v2","descricao":"Atualizada","dataLancamento":"2026-07-01","status":"ATIVA"}' \
  "$TOKEN_DONO"
assert_status "PUT /missoes/{id} (DONO) → 200" "200" "$STATUS"
assert_eq "Nome atualizado" "Missao Alpha v2" "$(echo "$BODY" | jq -r '.nome')"
assert_eq "Status atualizado para ATIVA" "ATIVA" "$(echo "$BODY" | jq -r '.status')"

# Não membro tenta PUT → verificarRole: sem vínculo → EntityNotFoundException → 404
PUT "/missoes/$MISSAO_ID" \
  '{"nome":"Invasao","descricao":"x","dataLancamento":"2026-07-01","status":"ATIVA"}' \
  "$TOKEN_FORASTEIRO"
assert_status "PUT /missoes/{id} (nao membro) → 404" "404" "$STATUS"

# ── 5. MISSÕES — Entrar e Sair ───────────────────────────────────
section "5. MISSÕES — Fluxo de entrada via senha e regra do DONO único"

# Senha errada → SenhaMissaoInvalidaException → 401
POST "/missoes/$MISSAO_ID/entrar" '{"senha":"errada"}' "$TOKEN_MEMBRO"
assert_status "POST /entrar senha errada → 401" "401" "$STATUS"

# Entrada com senha correta — membro
POST "/missoes/$MISSAO_ID/entrar" '{"senha":"senha123"}' "$TOKEN_MEMBRO"
assert_status "POST /entrar (membro) → 200" "200" "$STATUS"
assert_eq "Novo membro recebe role MEMBRO" "MEMBRO" "$(echo "$BODY" | jq -r '.roleDoOperador')"

# Já é membro → OperadorJaMembroException → 409
POST "/missoes/$MISSAO_ID/entrar" '{"senha":"senha123"}' "$TOKEN_MEMBRO"
assert_status "POST /entrar já é membro → 409" "409" "$STATUS"

# Entrada — supervisor
POST "/missoes/$MISSAO_ID/entrar" '{"senha":"senha123"}' "$TOKEN_SUPERVISOR"
assert_status "POST /entrar (supervisor) → 200" "200" "$STATUS"

# Agora membro É membro → tenta PUT → verificarRole: é membro mas role MEMBRO < DONO → 403
PUT "/missoes/$MISSAO_ID" \
  '{"nome":"Invasao","descricao":"x","dataLancamento":"2026-07-01","status":"ATIVA"}' \
  "$TOKEN_MEMBRO"
assert_status "PUT /missoes/{id} (MEMBRO que e membro) → 403" "403" "$STATUS"

# Membro tenta DELETE na missão → verificarRole: MEMBRO < DONO → 403
DELETE "/missoes/$MISSAO_ID" "$TOKEN_MEMBRO"
assert_status "DELETE /missoes/{id} (MEMBRO que é membro) → 403" "403" "$STATUS"

# Missão para teste de DONO único
POST "/missoes" \
  '{"nome":"Missao Solo","descricao":"Sem outros donos","dataLancamento":"2026-06-01","status":"PLANEJADA","senhaMissao":"senha456"}' \
  "$TOKEN_DONO"
assert_status "POST /missoes (solo) → 201" "201" "$STATUS"
MISSAO_SOLO_ID=$(echo "$BODY" | jq -r '.id')

# DONO único tenta sair → DonoUnicoException → 400
POST "/missoes/$MISSAO_SOLO_ID/sair" '{}' "$TOKEN_DONO"
assert_status "POST /sair DONO único → 400" "400" "$STATUS"

# Supervisor entra na missão solo e sai com sucesso → 204
POST "/missoes/$MISSAO_SOLO_ID/entrar" '{"senha":"senha456"}' "$TOKEN_SUPERVISOR"
assert_status "POST /entrar missão solo (supervisor) → 200" "200" "$STATUS"
POST "/missoes/$MISSAO_SOLO_ID/sair" '{}' "$TOKEN_SUPERVISOR"
assert_status "POST /sair (supervisor com sucesso) → 204" "204" "$STATUS"

# ── 6. MISSÕES — Gerenciamento de membros ────────────────────────
section "6. MISSÕES — Gerenciamento de membros"

# Não membro com token tenta listar membros → AcessoNegadoException → 403
GET "/missoes/$MISSAO_ID/membros" "$TOKEN_FORASTEIRO"
assert_status "GET /membros (não membro com token) → 403" "403" "$STATUS"

# Qualquer membro pode listar
GET "/missoes/$MISSAO_ID/membros" "$TOKEN_MEMBRO"
assert_status "GET /membros (membro) → 200" "200" "$STATUS"
assert_eq "3 membros na missão" "3" "$(echo "$BODY" | jq '. | length')"

ID_SUPERVISOR=$(echo "$BODY" | jq -r '.[] | select(.login=="supervisor@sat.dev") | .operadorId')
ID_MEMBRO=$(echo "$BODY" | jq -r '.[] | select(.login=="membro@sat.dev") | .operadorId')
ID_DONO=$(echo "$BODY" | jq -r '.[] | select(.login=="dono@sat.dev") | .operadorId')

# DONO tenta se remover via DELETE /membros → AcessoNegadoException → 403
DELETE "/missoes/$MISSAO_ID/membros/$ID_DONO" "$TOKEN_DONO"
assert_status "DELETE /membros/{donoId} (DONO removendo si mesmo) → 403" "403" "$STATUS"

# MEMBRO tenta promover → verificarRole: MEMBRO < DONO → 404 (sem vínculo DONO)
PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" "$TOKEN_MEMBRO"
assert_status "PATCH promover (MEMBRO tentando, role insuficiente) → 403" "403" "$STATUS"

# DONO promove supervisor para SUPERVISOR
PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" "$TOKEN_DONO"
assert_status "PATCH promover SUPERVISOR (DONO) → 200" "200" "$STATUS"
assert_eq "Role promovida para SUPERVISOR" "SUPERVISOR" "$(echo "$BODY" | jq -r '.role')"

# DONO rebaixa supervisor de volta para MEMBRO
PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=MEMBRO" "$TOKEN_DONO"
assert_status "PATCH rebaixar SUPERVISOR → MEMBRO (DONO) → 200" "200" "$STATUS"
assert_eq "Role rebaixada para MEMBRO" "MEMBRO" "$(echo "$BODY" | jq -r '.role')"

# DONO promove supervisor novamente para SUPERVISOR (precisa para próximas seções)
PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR?novoRole=SUPERVISOR" "$TOKEN_DONO"
assert_status "PATCH re-promover SUPERVISOR (DONO) → 200" "200" "$STATUS"

# DONO tenta alterar a própria role → AcessoNegadoException → 403
PATCH "/missoes/$MISSAO_ID/membros/$ID_DONO?novoRole=MEMBRO" "$TOKEN_DONO"
assert_status "PATCH DONO alterando própria role → 403" "403" "$STATUS"

# MEMBRO tenta remover supervisor → 404
DELETE "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR" "$TOKEN_MEMBRO"
assert_status "DELETE membro (MEMBRO tentando, role insuficiente) → 403" "403" "$STATUS"

# DONO remove membro da missão → 204
DELETE "/missoes/$MISSAO_ID/membros/$ID_MEMBRO" "$TOKEN_DONO"
assert_status "DELETE /membros/{membroId} (DONO) → 204" "204" "$STATUS"

# Membro removido tenta sair → EntityNotFoundException → 404
POST "/missoes/$MISSAO_ID/sair" '{}' "$TOKEN_MEMBRO"
assert_status "POST /sair após ser removido → 404" "404" "$STATUS"

# ── 7. SATÉLITES — CRUD ──────────────────────────────────────────
section "7. SATÉLITES — CRUD e controle de acesso"

# Membro re-entra para testes de permissão de role
POST "/missoes/$MISSAO_ID/entrar" '{"senha":"senha123"}' "$TOKEN_MEMBRO"
assert_status "POST /entrar membro re-ingressando → 200" "200" "$STATUS"

SAT_BODY='{"nome":"SAT-01","dataLancamento":"2026-01-15","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":550.0,"inclinacao":53.5,"longitudeNodo":12.3}}'

# Sem token → Spring Security bloqueia POST → 403
POST "/satelites" "$SAT_BODY"
assert_status "POST /satelites sem token → 403" "403" "$STATUS"

# MEMBRO da missão tenta criar satélite → verificarRole: MEMBRO < SUPERVISOR → 403
POST "/satelites" "$SAT_BODY" "$TOKEN_MEMBRO"
assert_status "POST /satelites (MEMBRO) → 403" "403" "$STATUS"

# SUPERVISOR cria satélite → 201
POST "/satelites" "$SAT_BODY" "$TOKEN_SUPERVISOR"
assert_status "POST /satelites (SUPERVISOR) → 201" "201" "$STATUS"
SAT_ID=$(echo "$BODY" | jq -r '.id')
assert_not_empty "id do satélite válido" "$SAT_ID"
assert_eq "Nome SAT-01 correto" "SAT-01" "$(echo "$BODY" | jq -r '.nome')"
assert_eq "altitudeKm salva corretamente" "550.0" "$(echo "$BODY" | jq -r '.altitudeKm')"
assert_eq "missaoId correto" "$MISSAO_ID" "$(echo "$BODY" | jq -r '.missaoId')"

# Nome duplicado → IllegalArgumentException → 400
POST "/satelites" "$SAT_BODY" "$TOKEN_SUPERVISOR"
assert_status "POST /satelites nome duplicado → 400" "400" "$STATUS"

# missaoId inexistente → EntityNotFoundException → 404
POST "/satelites" \
  '{"nome":"SAT-X","dataLancamento":"2026-01-01","missaoId":99999,"coordenadas":{"altitudeKm":100.0,"inclinacao":0.0}}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /satelites missaoId inexistente → 404" "404" "$STATUS"

# GETs públicos
GET "/satelites"
assert_status "GET /satelites (público) → 200" "200" "$STATUS"

GET "/satelites/$SAT_ID"
assert_status "GET /satelites/{id} → 200" "200" "$STATUS"
assert_eq "Links HATEOAS presentes" "true" "$(echo "$BODY" | jq 'has("_links")')"

# missaoId inexistente → EntityNotFoundException → 404
GET "/satelites/missao/99999"
assert_status "GET /satelites/missao/{missaoId} inexistente → 404" "404" "$STATUS"

GET "/satelites/missao/$MISSAO_ID"
assert_status "GET /satelites/missao/{missaoId} → 200" "200" "$STATUS"
assert_gte "Pelo menos 1 satélite na missão" 1 "$(echo "$BODY" | jq -r '.totalElements')"

# Estatísticas sem leituras
GET "/satelites/$SAT_ID/estatisticas"
assert_status "GET /satelites/{id}/estatisticas (sem leituras) → 200" "200" "$STATUS"
assert_eq "totalLeituras=0 sem leituras" "0" "$(echo "$BODY" | jq -r '.totalLeituras')"

# SUPERVISOR atualiza satélite
PUT "/satelites/$SAT_ID" \
  '{"nome":"SAT-01","dataLancamento":"2026-02-01","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":600.0,"inclinacao":55.0}}' \
  "$TOKEN_SUPERVISOR"
assert_status "PUT /satelites/{id} (SUPERVISOR) → 200" "200" "$STATUS"
assert_eq "altitudeKm atualizada para 600.0" "600.0" "$(echo "$BODY" | jq -r '.altitudeKm')"

# DONO também pode atualizar satélite
PUT "/satelites/$SAT_ID" \
  '{"nome":"SAT-01","dataLancamento":"2026-03-01","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":650.0,"inclinacao":57.0}}' \
  "$TOKEN_DONO"
assert_status "PUT /satelites/{id} (DONO) → 200" "200" "$STATUS"
assert_eq "altitudeKm atualizada pelo DONO para 650.0" "650.0" "$(echo "$BODY" | jq -r '.altitudeKm')"

# SUPERVISOR não pode deletar satélite → AcessoNegadoException → 403
DELETE "/satelites/$SAT_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /satelites/{id} (SUPERVISOR) → 403" "403" "$STATUS"

# Satélite inexistente
GET "/satelites/99999"
assert_status "GET /satelites/99999 → 404" "404" "$STATUS"

# ── 8. SENSORES — Criação dos 4 tipos ───────────────────────────
section "8. SENSORES — Criação dos 4 tipos"

POST "/sensores" \
  '{"nome":"Termometro","unidade":"graus_C","limiteMin":0.0,"limiteMax":80.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores TERMICO → 201" "201" "$STATUS"
SENSOR_TERMICO_ID=$(echo "$BODY" | jq -r '.id')
assert_eq "detalhe=CELSIUS (SensorTermico)" "CELSIUS" "$(echo "$BODY" | jq -r '.detalhe')"
assert_eq "tipo=TERMICO (enum canônico)" "TERMICO" "$(echo "$BODY" | jq -r '.tipo')"

POST "/sensores" \
  '{"nome":"Barometro","unidade":"hPa","limiteMin":950.0,"limiteMax":1050.0,"margemAlerta":5.0,"sateliteId":'"$SAT_ID"',"tipo":"PRESSAO","tipoPressao":"ABSOLUTA"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores PRESSAO → 201" "201" "$STATUS"
SENSOR_PRESSAO_ID=$(echo "$BODY" | jq -r '.id')
assert_eq "detalhe=ABSOLUTA (SensorPressao)" "ABSOLUTA" "$(echo "$BODY" | jq -r '.detalhe')"

POST "/sensores" \
  '{"nome":"Geiger","unidade":"Gy","limiteMin":0.0,"limiteMax":5.0,"margemAlerta":20.0,"sateliteId":'"$SAT_ID"',"tipo":"RADIACAO","tipoRadiacao":"IONIZANTE"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores RADIACAO → 201" "201" "$STATUS"
SENSOR_RADIACAO_ID=$(echo "$BODY" | jq -r '.id')
assert_eq "detalhe=IONIZANTE (SensorRadiacao)" "IONIZANTE" "$(echo "$BODY" | jq -r '.detalhe')"

POST "/sensores" \
  '{"nome":"Magnetometro","unidade":"nT","limiteMin":-50000.0,"limiteMax":50000.0,"margemAlerta":15.0,"sateliteId":'"$SAT_ID"',"tipo":"MAGNETOMETRO","eixosMedicao":"XYZ"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores MAGNETOMETRO → 201" "201" "$STATUS"
SENSOR_MAG_ID=$(echo "$BODY" | jq -r '.id')
assert_eq "detalhe=XYZ (Magnetometro)" "XYZ" "$(echo "$BODY" | jq -r '.detalhe')"

# ── 9. SENSORES — Validações de regras de negócio ─────────────────
section "9. SENSORES — Validações e controle de acesso"

# limiteMin >= limiteMax → IllegalArgumentException → 400
POST "/sensores" \
  '{"nome":"Inv","unidade":"X","limiteMin":100.0,"limiteMax":50.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores limiteMin >= limiteMax → 400" "400" "$STATUS"

# Tipo inválido (enum desconhecido) → HttpMessageNotReadableException → 400
POST "/sensores" \
  '{"nome":"Son","unidade":"Hz","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"SONICO"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores tipo inválido → 400" "400" "$STATUS"

# Campo específico ausente → IllegalArgumentException → 400
POST "/sensores" \
  '{"nome":"SemEscala","unidade":"graus_C","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores TERMICO sem unidadeEscala → 400" "400" "$STATUS"

# Nome duplicado no mesmo satélite → IllegalArgumentException → 400
POST "/sensores" \
  '{"nome":"Termometro","unidade":"K","limiteMin":0.0,"limiteMax":80.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"KELVIN"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores nome duplicado no satélite → 400" "400" "$STATUS"

# sateliteId inexistente → EntityNotFoundException → 404
POST "/sensores" \
  '{"nome":"Orfao","unidade":"X","limiteMin":0.0,"limiteMax":10.0,"margemAlerta":5.0,"sateliteId":99999,"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_SUPERVISOR"
assert_status "POST /sensores sateliteId inexistente → 404" "404" "$STATUS"

# MEMBRO da missão tenta criar sensor → verificarRole: MEMBRO < SUPERVISOR → 403
POST "/sensores" \
  '{"nome":"SensorMembro","unidade":"X","limiteMin":0.0,"limiteMax":100.0,"margemAlerta":10.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_MEMBRO"
assert_status "POST /sensores (MEMBRO) → 403" "403" "$STATUS"

GET "/sensores"
assert_status "GET /sensores (público) → 200" "200" "$STATUS"

GET "/sensores/$SENSOR_TERMICO_ID"
assert_status "GET /sensores/{id} → 200" "200" "$STATUS"
assert_eq "Links HATEOAS no sensor" "true" "$(echo "$BODY" | jq 'has("_links")')"

# sateliteId inexistente → EntityNotFoundException → 404
GET "/sensores/satelite/99999"
assert_status "GET /sensores/satelite/{id} inexistente → 404" "404" "$STATUS"

GET "/sensores/satelite/$SAT_ID"
assert_status "GET /sensores/satelite/{id} → 200" "200" "$STATUS"
assert_eq "4 sensores no satélite" "4" "$(echo "$BODY" | jq -r '.totalElements')"

# SUPERVISOR atualiza campos comuns do sensor
PUT "/sensores/$SENSOR_TERMICO_ID" \
  '{"nome":"Termometro","unidade":"K","limiteMin":-10.0,"limiteMax":90.0,"margemAlerta":5.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_SUPERVISOR"
assert_status "PUT /sensores/{id} (SUPERVISOR) → 200" "200" "$STATUS"
assert_eq "limiteMin atualizado para -10.0" "-10.0" "$(echo "$BODY" | jq -r '.limiteMin')"
assert_eq "detalhe permanece CELSIUS (tipo imutável)" "CELSIUS" "$(echo "$BODY" | jq -r '.detalhe')"

# DONO também pode atualizar sensor
PUT "/sensores/$SENSOR_TERMICO_ID" \
  '{"nome":"Termometro","unidade":"graus_C","limiteMin":-10.0,"limiteMax":90.0,"margemAlerta":5.0,"sateliteId":'"$SAT_ID"',"tipo":"TERMICO","unidadeEscala":"CELSIUS"}' \
  "$TOKEN_DONO"
assert_status "PUT /sensores/{id} (DONO) → 200" "200" "$STATUS"
assert_eq "unidade atualizada pelo DONO para graus_C" "graus_C" "$(echo "$BODY" | jq -r '.unidade')"

# ── 10. LEITURAS — StatusCalculator ──────────────────────────────
section "10. LEITURAS — Registro e classificação automática de status"
echo -e "  ${YELLOW}Sensor Termico: limiteMin=-10, limiteMax=90, margemAlerta=5%${NC}"
echo -e "  ${YELLOW}  → zonaAlertaMin=-5  |  zonaAlertaMax=85${NC}"

# valor=40.0 → dentro de [-5, 85] → NORMAL  (sem token — IoT público)
POST "/leituras" '{"valor":40.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras NORMAL sem token (IoT) → 201" "201" "$STATUS"
assert_eq "valor=40.0 → NORMAL" "NORMAL" "$(echo "$BODY" | jq -r '.status')"
LEITURA_NORMAL_ID=$(echo "$BODY" | jq -r '.id')
assert_not_empty "dataHoraLeitura definida pelo servidor" "$(echo "$BODY" | jq -r '.dataHoraLeitura')"
assert_eq "Links HATEOAS na leitura" "true" "$(echo "$BODY" | jq 'has("_links")')"

# valor=87.0 → entre zonaAlertaMax=85 e limiteMax=90 → ALERTA superior
POST "/leituras" '{"valor":87.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras ALERTA superior → 201" "201" "$STATUS"
assert_eq "valor=87.0 → ALERTA (zona superior)" "ALERTA" "$(echo "$BODY" | jq -r '.status')"
LEITURA_ALERTA_ID=$(echo "$BODY" | jq -r '.id')

# valor=150.0 → acima de limiteMax=90 → CRITICO
POST "/leituras" '{"valor":150.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras CRITICO acima limiteMax → 201" "201" "$STATUS"
assert_eq "valor=150.0 → CRITICO" "CRITICO" "$(echo "$BODY" | jq -r '.status')"
LEITURA_CRITICO_ID=$(echo "$BODY" | jq -r '.id')

# valor=-50.0 → abaixo de limiteMin=-10 → CRITICO
POST "/leituras" '{"valor":-50.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras CRITICO abaixo limiteMin → 201" "201" "$STATUS"
assert_eq "valor=-50.0 → CRITICO" "CRITICO" "$(echo "$BODY" | jq -r '.status')"

# valor=-8.0 → entre limiteMin=-10 e zonaAlertaMin=-5 → ALERTA inferior
POST "/leituras" '{"valor":-8.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras ALERTA inferior → 201" "201" "$STATUS"
assert_eq "valor=-8.0 → ALERTA (zona inferior)" "ALERTA" "$(echo "$BODY" | jq -r '.status')"

# valor=-10.0 → exatamente no limiteMin (não < portanto não CRITICO) → ALERTA
POST "/leituras" '{"valor":-10.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras valor=limiteMin exato → 201" "201" "$STATUS"
assert_eq "valor=-10.0 (limiteMin) → ALERTA (fronteira inclusiva)" "ALERTA" "$(echo "$BODY" | jq -r '.status')"

# valor=85.0 → exatamente na zonaAlertaMax (não > portanto não ALERTA) → NORMAL
POST "/leituras" '{"valor":85.0,"sensorId":'"$SENSOR_TERMICO_ID"'}'
assert_status "POST /leituras valor=zonaAlertaMax exato → 201" "201" "$STATUS"
assert_eq "valor=85.0 (zonaAlertaMax) → NORMAL (fronteira exclusiva)" "NORMAL" "$(echo "$BODY" | jq -r '.status')"

# sensorId inexistente → EntityNotFoundException → 404
POST "/leituras" '{"valor":40.0,"sensorId":99999}'
assert_status "POST /leituras sensorId inexistente → 404" "404" "$STATUS"

# Campos ausentes → MethodArgumentNotValidException → 400
POST "/leituras" '{"valor":40.0}'
assert_status "POST /leituras sem sensorId → 400" "400" "$STATUS"

# ── 11. LEITURAS — Consultas e filtros ───────────────────────────
section "11. LEITURAS — Listagem, filtros e erros de consulta"

GET "/leituras"
assert_status "GET /leituras (público) → 200" "200" "$STATUS"
assert_gte "Pelo menos 7 leituras registradas" 7 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/$LEITURA_NORMAL_ID"
assert_status "GET /leituras/{id} → 200" "200" "$STATUS"
assert_not_empty "Link sensor presente" "$(echo "$BODY" | jq -r '._links.sensor.href')"
assert_not_empty "Link satelite presente" "$(echo "$BODY" | jq -r '._links.satelite.href')"

GET "/leituras/99999"
assert_status "GET /leituras/99999 → 404" "404" "$STATUS"

GET "/leituras/sensor/$SENSOR_TERMICO_ID"
assert_status "GET /leituras/sensor/{id} → 200" "200" "$STATUS"
assert_gte "Pelo menos 7 leituras do sensor" 7 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/sensor/$SENSOR_TERMICO_ID?status=CRITICO"
assert_status "GET /leituras/sensor?status=CRITICO → 200" "200" "$STATUS"
assert_gte "Filtro CRITICO retorna >= 2 leituras" 2 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/sensor/$SENSOR_TERMICO_ID?status=ALERTA"
assert_status "GET /leituras/sensor?status=ALERTA → 200" "200" "$STATUS"
assert_gte "Filtro ALERTA retorna >= 2 leituras" 2 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/sensor/$SENSOR_TERMICO_ID?status=NORMAL"
assert_status "GET /leituras/sensor?status=NORMAL → 200" "200" "$STATUS"
assert_gte "Filtro NORMAL retorna >= 1 leitura" 1 "$(echo "$BODY" | jq -r '.totalElements')"

# sensorId inexistente → EntityNotFoundException → 404
GET "/leituras/sensor/99999"
assert_status "GET /leituras/sensor/{id} inexistente → 404" "404" "$STATUS"

# Sensor sem leituras → 200 totalElements=0
GET "/leituras/sensor/$SENSOR_PRESSAO_ID"
assert_status "GET /leituras/sensor sem leituras → 200" "200" "$STATUS"
assert_eq "totalElements=0 para sensor sem leituras" "0" "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/satelite/$SAT_ID"
assert_status "GET /leituras/satelite/{id} → 200" "200" "$STATUS"
assert_gte "Leituras por satélite retorna >= 7" 7 "$(echo "$BODY" | jq -r '.totalElements')"

GET "/leituras/satelite/$SAT_ID?status=CRITICO"
assert_status "GET /leituras/satelite?status=CRITICO → 200" "200" "$STATUS"

# sateliteId inexistente → EntityNotFoundException → 404
GET "/leituras/satelite/99999"
assert_status "GET /leituras/satelite/{id} inexistente → 404" "404" "$STATUS"

# ── 12. ESTATÍSTICAS após leituras ───────────────────────────────
section "12. SATÉLITES — Estatísticas agregadas com leituras"

GET "/satelites/$SAT_ID/estatisticas"
assert_status "GET /estatisticas com leituras → 200" "200" "$STATUS"
assert_gte "totalLeituras >= 7 após registrar leituras" 7 "$(echo "$BODY" | jq -r '.totalLeituras')"
assert_gte "totalCriticos >= 2" 2 "$(echo "$BODY" | jq -r '.totalCriticos')"
assert_gte "totalAlertas >= 2" 2 "$(echo "$BODY" | jq -r '.totalAlertas')"
assert_not_empty "ultimaLeitura preenchida" "$(echo "$BODY" | jq -r '.ultimaLeitura')"
assert_not_empty "mediaValor calculada" "$(echo "$BODY" | jq -r '.mediaValor')"

# ── 13. LEITURAS — Exclusão com controle de acesso ───────────────
section "13. LEITURAS — Exclusão e controle de acesso"

# Sem token → Spring Security bloqueia → 403
DELETE "/leituras/$LEITURA_NORMAL_ID"
assert_status "DELETE /leituras sem token → 403" "403" "$STATUS"

# Forasteiro (não membro) → AcessoNegadoException → 403
DELETE "/leituras/$LEITURA_NORMAL_ID" "$TOKEN_FORASTEIRO"
assert_status "DELETE /leituras (não membro da missão) → 403" "403" "$STATUS"

# MEMBRO da missão (role < SUPERVISOR) → AcessoNegadoException → 403
DELETE "/leituras/$LEITURA_NORMAL_ID" "$TOKEN_MEMBRO"
assert_status "DELETE /leituras (MEMBRO da missão) → 403" "403" "$STATUS"

# SUPERVISOR deleta → 204
DELETE "/leituras/$LEITURA_ALERTA_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /leituras/{id} (SUPERVISOR) → 204" "204" "$STATUS"

# Leitura já deletada → EntityNotFoundException → 404
DELETE "/leituras/$LEITURA_ALERTA_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /leituras já deletada → 404" "404" "$STATUS"

# DONO deleta → 204
DELETE "/leituras/$LEITURA_CRITICO_ID" "$TOKEN_DONO"
assert_status "DELETE /leituras/{id} (DONO) → 204" "204" "$STATUS"

# ── 14. SENSORES — Exclusão ───────────────────────────────────────
section "14. SENSORES — Exclusão (apenas DONO)"

# SUPERVISOR não pode deletar sensor → AcessoNegadoException → 403
DELETE "/sensores/$SENSOR_MAG_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /sensores/{id} (SUPERVISOR) → 403" "403" "$STATUS"

# DONO deleta sensor → 204
DELETE "/sensores/$SENSOR_RADIACAO_ID" "$TOKEN_DONO"
assert_status "DELETE /sensores/{id} (DONO) → 204" "204" "$STATUS"

GET "/sensores/$SENSOR_RADIACAO_ID"
assert_status "GET /sensores após delete → 404" "404" "$STATUS"

GET "/sensores/satelite/$SAT_ID"
assert_status "GET /sensores/satelite após delete → 200" "200" "$STATUS"
assert_eq "3 sensores restam após delete" "3" "$(echo "$BODY" | jq -r '.totalElements')"

# ── 15. SATÉLITES — Exclusão ──────────────────────────────────────
section "15. SATÉLITES — Exclusão com cascade"

POST "/satelites" \
  '{"nome":"SAT-DESCARTAVEL","dataLancamento":"2026-01-01","missaoId":'"$MISSAO_ID"',"coordenadas":{"altitudeKm":200.0,"inclinacao":0.0}}' \
  "$TOKEN_DONO"
assert_status "POST /satelites descartável → 201" "201" "$STATUS"
SAT_DESCARTAVEL_ID=$(echo "$BODY" | jq -r '.id')

DELETE "/satelites/$SAT_DESCARTAVEL_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /satelites (SUPERVISOR) → 403" "403" "$STATUS"

DELETE "/satelites/$SAT_DESCARTAVEL_ID" "$TOKEN_DONO"
assert_status "DELETE /satelites (DONO) → 204" "204" "$STATUS"

GET "/satelites/$SAT_DESCARTAVEL_ID"
assert_status "GET /satelites após delete → 404" "404" "$STATUS"

# ── 16. MISSÕES — Exclusão e limpeza ─────────────────────────────
section "16. MISSÕES — Exclusão e saída voluntária"

# SUPERVISOR não é DONO → verificarRole: SUPERVISOR < DONO → 403
DELETE "/missoes/$MISSAO_ID" "$TOKEN_SUPERVISOR"
assert_status "DELETE /missoes (SUPERVISOR) → 403" "403" "$STATUS"

# MEMBRO da missão tenta DELETE → verificarRole: MEMBRO < DONO → 403
DELETE "/missoes/$MISSAO_ID" "$TOKEN_MEMBRO"
assert_status "DELETE /missoes (MEMBRO) → 403" "403" "$STATUS"

# MEMBRO sai da missão voluntariamente → 204
POST "/missoes/$MISSAO_ID/sair" '{}' "$TOKEN_MEMBRO"
assert_status "POST /sair (MEMBRO voluntariamente) → 204" "204" "$STATUS"

# DONO deleta missão Solo → 204
DELETE "/missoes/$MISSAO_SOLO_ID" "$TOKEN_DONO"
assert_status "DELETE /missoes (missão solo, DONO) → 204" "204" "$STATUS"

GET "/missoes/$MISSAO_SOLO_ID" "$TOKEN_DONO"
assert_status "GET /missoes após delete → 404" "404" "$STATUS"

# ── Resumo final ──────────────────────────────────────────────────
rm -f "$TMPFILE"
TOTAL=$((PASS + FAIL))

echo ""
echo -e "${CYAN}${BOLD}══════════════════════════════════════════════════════${NC}"
echo -e "  Total de testes : ${BOLD}$TOTAL${NC}"
echo -e "  ${GREEN}${BOLD}Passaram         : $PASS${NC}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "  ${RED}${BOLD}Falharam         : $FAIL${NC}"
else
  echo -e "  ${GREEN}${BOLD}Falharam         : 0${NC}"
fi
echo -e "${CYAN}${BOLD}══════════════════════════════════════════════════════${NC}"

if [ "${#FAILED_TESTS[@]}" -gt 0 ]; then
  echo ""
  echo -e "${RED}${BOLD}Testes que falharam — verifique e corrija:${NC}"
  for i in "${!FAILED_TESTS[@]}"; do
    echo -e "  ${RED}$(printf '%2d' $((i+1))).${NC} ${FAILED_TESTS[$i]}"
  done
  echo ""
fi

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
