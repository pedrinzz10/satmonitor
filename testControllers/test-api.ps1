# ================================================================
# SatMonitor -- Bateria completa de testes da API (PowerShell)
#
# Pré-requisitos:
#   • PowerShell 7+  (verificar: $PSVersionTable.PSVersion)
#   • Aplicação rodando em http://localhost:8080
#
# Uso (na raiz do projeto):
#   .\testControllers\test-api.ps1
#
# Exit code: 0 = todos passaram | 1 = algum falhou
# ================================================================

$BASE          = "http://localhost:8080"
$script:STATUS = 0
$script:BODY   = "{}"
$script:PASS   = 0
$script:FAIL   = 0
$script:FAILED = @()

# ── Output ───────────────────────────────────────────────────────
function section($name) { Write-Host "`n> $name" -ForegroundColor Cyan }

function ok($label) {
    Write-Host "  [PASS] $label" -ForegroundColor Green
    $script:PASS++
}

function fail($label, $expected, $actual) {
    Write-Host "  [FAIL] $label" -ForegroundColor Red
    Write-Host "       esperado: $expected  |  recebido: $actual" -ForegroundColor Yellow
    $script:FAIL++
    $script:FAILED += $label
}

# ── Asserções ────────────────────────────────────────────────────
function assert_status($label, $expected, $actual) {
    if ("$actual" -eq "$expected") { ok $label } else { fail $label "HTTP $expected" "HTTP $actual" }
}

function assert_eq($label, $expected, $actual) {
    if ("$actual" -eq "$expected") { ok $label } else { fail $label $expected $actual }
}

function assert_not_empty($label, $val) {
    if ($val -and "$val" -ne "null") { ok $label } else { fail $label "valor preenchido" "null/vazio" }
}

function assert_gte($label, $min, $actual) {
    try {
        if ([double]"$actual" -ge [double]"$min") { ok $label } else { fail $label ">= $min" $actual }
    } catch { fail $label ">= $min" $actual }
}

# ── JSON helpers ─────────────────────────────────────────────────
function j { $script:BODY | ConvertFrom-Json }

function has_key($key) {
    if ((j).PSObject.Properties.Name -contains $key) { "true" } else { "false" }
}

# ── HTTP ─────────────────────────────────────────────────────────
function do_request($method, $url, $body = $null, $token = $null) {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers["Authorization"] = "Bearer $token" }

    $params = @{
        Method             = $method
        Uri                = "$BASE$url"
        Headers            = $headers
        SkipHttpErrorCheck = $true
        ErrorAction        = "Stop"
    }
    if ($body) { $params.Body = [System.Text.Encoding]::UTF8.GetBytes($body) }

    try {
        $res           = Invoke-WebRequest @params
        $script:STATUS = [int]$res.StatusCode
        if ($res.Content -is [byte[]]) {
            $script:BODY = [System.Text.Encoding]::UTF8.GetString($res.Content)
        } else {
            $script:BODY = [string]$res.Content
        }
    } catch {
        Write-Host "  [CONEXAO FALHOU] $method $url" -ForegroundColor Red
        $script:STATUS = 0
        $script:BODY   = "{}"
    }
}

function GET($url, $token = $null)         { do_request "GET"    $url $null $token }
function POST($url, $body, $token = $null) { do_request "POST"   $url $body $token }
function PUT($url, $body, $token = $null)  { do_request "PUT"    $url $body $token }
function PATCH($url, $token = $null)       { do_request "PATCH"  $url $null $token }
function DELETE($url, $token = $null)      { do_request "DELETE" $url $null $token }

# ================================================================
Write-Host ""
Write-Host "+======================================================+" -ForegroundColor Cyan
Write-Host "|        SatMonitor -- Bateria de Testes da API         |" -ForegroundColor Cyan
Write-Host "+======================================================+" -ForegroundColor Cyan
Write-Host ""

# ── 1. HEALTH CHECK ──────────────────────────────────────────────
section "1. Health check"

GET "/actuator/health"
assert_status "GET /actuator/health -> 200" "200" $script:STATUS
assert_eq "Aplicacao UP" "UP" (j).status

# ── 2. AGENCIAS -- Criação pública (necessária antes do registro) ──
section "2. AGENCIAS -- Criar sem token (endpoint público)"

POST "/agencias" (@{nome="NASA";siglaPais="US";tipoAgencia="GOVERNAMENTAL"} | ConvertTo-Json -Compress)
assert_status "POST /agencias NASA (sem token) -> 201" "201" $script:STATUS
$AGENCIA_NASA_ID = (j).id
assert_not_empty "id da agencia NASA valido" $AGENCIA_NASA_ID
assert_eq "Nome NASA correto" "NASA" (j).nome

POST "/agencias" (@{nome="ESA";siglaPais="EU";tipoAgencia="GOVERNAMENTAL"} | ConvertTo-Json -Compress)
assert_status "POST /agencias ESA (sem token) -> 201" "201" $script:STATUS
$AGENCIA_ESA_ID = (j).id
assert_not_empty "id da agencia ESA valido" $AGENCIA_ESA_ID

POST "/agencias" (@{nome="INPE";siglaPais="BRA"} | ConvertTo-Json -Compress)
assert_status "POST /agencias siglaPais invalida (3 chars) -> 400" "400" $script:STATUS

POST "/agencias" (@{nome="";siglaPais="BR"} | ConvertTo-Json -Compress)
assert_status "POST /agencias nome em branco -> 400" "400" $script:STATUS

# ── 3. AUTH -- Registro ───────────────────────────────────────────
section "3. AUTH -- Registro de operadores (com agenciaId obrigatorio)"

POST "/auth/registrar" (@{login="dono@sat.dev";      senha="senha123"; nome="Operador Dono";       agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar dono (NASA) -> 201" "201" $script:STATUS

POST "/auth/registrar" (@{login="membro@sat.dev";    senha="senha123"; nome="Operador Membro";     agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar membro (NASA) -> 201" "201" $script:STATUS

POST "/auth/registrar" (@{login="supervisor@sat.dev";senha="senha123"; nome="Operador Supervisor"; agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar supervisor (NASA) -> 201" "201" $script:STATUS

POST "/auth/registrar" (@{login="forasteiro@sat.dev";senha="senha123"; nome="Operador Forasteiro";agenciaId=$AGENCIA_ESA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar forasteiro (ESA) -> 201" "201" $script:STATUS

POST "/auth/registrar" (@{login="dono@sat.dev"; senha="abc"; nome="Dup"; agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar login duplicado -> 400" "400" $script:STATUS

POST "/auth/registrar" (@{login="";senha="abc";nome="Vazio";agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar login em branco -> 400" "400" $script:STATUS

POST "/auth/registrar" (@{login="sem.agencia@sat.dev";senha="senha123";nome="SemAgencia";agenciaId=99999} | ConvertTo-Json -Compress)
assert_status "POST /auth/registrar agenciaId inexistente -> 404" "404" $script:STATUS

# ── 4. AUTH -- Login ──────────────────────────────────────────────
section "4. AUTH -- Login e tokens JWT"

POST "/auth/login" (@{login="dono@sat.dev";       senha="senha123"} | ConvertTo-Json -Compress)
assert_status "POST /auth/login dono -> 200" "200" $script:STATUS
$TOKEN_DONO = (j).token
assert_not_empty "Token JWT do dono obtido" $TOKEN_DONO

POST "/auth/login" (@{login="membro@sat.dev";     senha="senha123"} | ConvertTo-Json -Compress)
assert_status "POST /auth/login membro -> 200" "200" $script:STATUS
$TOKEN_MEMBRO = (j).token
assert_not_empty "Token JWT do membro obtido" $TOKEN_MEMBRO

POST "/auth/login" (@{login="supervisor@sat.dev"; senha="senha123"} | ConvertTo-Json -Compress)
assert_status "POST /auth/login supervisor -> 200" "200" $script:STATUS
$TOKEN_SUPERVISOR = (j).token
assert_not_empty "Token JWT do supervisor obtido" $TOKEN_SUPERVISOR

POST "/auth/login" (@{login="forasteiro@sat.dev";senha="senha123"} | ConvertTo-Json -Compress)
assert_status "POST /auth/login forasteiro -> 200" "200" $script:STATUS
$TOKEN_FORASTEIRO = (j).token
assert_not_empty "Token JWT do forasteiro obtido" $TOKEN_FORASTEIRO

if (-not $TOKEN_DONO -or $TOKEN_DONO -eq "null") {
    Write-Host "`nFATAL: tokens nao obtidos -- verifique se a aplicacao esta rodando em $BASE" -ForegroundColor Red
    exit 1
}

# ── 5. AGENCIAS -- CRUD completo ─────────────────────────────────
section "5. AGENCIAS -- CRUD completo"

GET "/agencias"
assert_status "GET /agencias (publico) -> 200" "200" $script:STATUS
assert_gte "Pelo menos 2 agencias cadastradas" 2 (j).totalElements

GET "/agencias/$AGENCIA_NASA_ID"
assert_status "GET /agencias/{id} -> 200" "200" $script:STATUS
assert_eq "Nome NASA correto no GET" "NASA" (j).nome

PUT "/agencias/$AGENCIA_NASA_ID" (@{nome="NASA -- National Aeronautics";siglaPais="US";tipoAgencia="GOVERNAMENTAL"} | ConvertTo-Json -Compress) $TOKEN_DONO
assert_status "PUT /agencias/{id} (com token) -> 200" "200" $script:STATUS
assert_eq "Nome atualizado" "NASA -- National Aeronautics" (j).nome

POST "/agencias" (@{nome="JAXA";siglaPais="JP";tipoAgencia="GOVERNAMENTAL"} | ConvertTo-Json -Compress) $TOKEN_DONO
assert_status "POST /agencias JAXA (com token) -> 201" "201" $script:STATUS

GET "/agencias/99999"
assert_status "GET /agencias/99999 -> 404" "404" $script:STATUS

# ── 6. MISSOES -- CRUD e busca publica ────────────────────────────
section "6. MISSOES -- CRUD basico e busca publica por nome"

$bodyMissao = @{
    nome            = "Missao Alpha"
    descricao       = "Missao principal de testes"
    dataLancamento  = "2026-06-01"
    status          = "PLANEJADA"
    senhaMissao     = "senha123"
    agenciaId       = $AGENCIA_NASA_ID
    objetivo        = "Monitoramento de orbita baixa"
    dataFimPrevista = "2027-12-31"
    permitirCowork  = $false
} | ConvertTo-Json -Compress

POST "/missoes" $bodyMissao $TOKEN_DONO
assert_status "POST /missoes (com agenciaId + campos opcionais) -> 201" "201" $script:STATUS
$MISSAO_ID = (j).id
assert_not_empty "id da missao valido" $MISSAO_ID
assert_eq "Criador recebe role DONO" "DONO" (j).roleDoOperador
assert_eq "Links HATEOAS presentes" "true" (has_key "_links")
assert_eq "permitirCowork=false" "False" (j).permitirCowork

GET "/missoes" $TOKEN_DONO
assert_status "GET /missoes (dono autenticado) -> 200" "200" $script:STATUS
assert_gte "Dono ve pelo menos 1 missao" 1 (j).totalElements

GET "/missoes/buscar`?nome=Missao"
assert_status "GET /missoes/buscar?nome= (publico, sem token) -> 200" "200" $script:STATUS
assert_gte "Busca por nome retorna >= 1 missao" 1 (j).totalElements

GET "/missoes/$MISSAO_ID" $TOKEN_DONO
assert_status "GET /missoes/{id} (dono membro) -> 200" "200" $script:STATUS
assert_eq "Nome da missao correto" "Missao Alpha" (j).nome

GET "/missoes/$MISSAO_ID" $TOKEN_FORASTEIRO
assert_status "GET /missoes/{id} (nao membro com token) -> 403" "403" $script:STATUS

GET "/missoes/$MISSAO_ID"
assert_status "GET /missoes/{id} sem token -> 403" "403" $script:STATUS

$bodyUpdate = @{
    nome           = "Missao Alpha v2"
    descricao      = "Atualizada"
    dataLancamento = "2026-07-01"
    status         = "ATIVA"
    agenciaId      = $AGENCIA_NASA_ID
    permitirCowork = $false
} | ConvertTo-Json -Compress

PUT "/missoes/$MISSAO_ID" $bodyUpdate $TOKEN_DONO
assert_status "PUT /missoes/{id} (DONO) -> 200" "200" $script:STATUS
assert_eq "Nome atualizado" "Missao Alpha v2" (j).nome
assert_eq "Status atualizado para ATIVA" "ATIVA" (j).status

PUT "/missoes/$MISSAO_ID" (@{nome="Invasao";descricao="x";dataLancamento="2026-07-01";status="ATIVA"} | ConvertTo-Json -Compress) $TOKEN_FORASTEIRO
assert_status "PUT /missoes/{id} (nao membro) -> 404" "404" $script:STATUS

# ── 7. MISSOES -- Novo fluxo de solicitacao ───────────────────────
section "7. MISSOES -- Fluxo: solicitar / listar / aprovar / rejeitar"

# Forasteiro (ESA) tenta entrar em missao NASA sem cowork -> 403
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_FORASTEIRO
assert_status "POST /solicitar (ESA em missao NASA, sem cowork) -> 403" "403" $script:STATUS

# Membro (NASA) com senha errada -> 401
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="errada"} | ConvertTo-Json -Compress) $TOKEN_MEMBRO
assert_status "POST /solicitar senha errada -> 401" "401" $script:STATUS

# Membro (NASA) com senha correta -> 201 PENDENTE
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_MEMBRO
assert_status "POST /solicitar (membro NASA) -> 201" "201" $script:STATUS
$SOL_MEMBRO_ID = (j).id
assert_not_empty "id da solicitacao valido" $SOL_MEMBRO_ID
assert_eq "Status inicial = PENDENTE" "PENDENTE" (j).status
assert_not_empty "nomeOperador preenchido" (j).nomeOperador

# Membro tenta solicitar de novo -> 409
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_MEMBRO
assert_status "POST /solicitar ja tem pendente -> 409" "409" $script:STATUS

# Supervisor (NASA) solicita -> 201
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /solicitar (supervisor NASA) -> 201" "201" $script:STATUS
$SOL_SUPERVISOR_ID = (j).id

# Nao-membro (forasteiro) tenta listar solicitacoes -> 404 (nao e membro)
GET "/missoes/$MISSAO_ID/solicitacoes" $TOKEN_FORASTEIRO
assert_status "GET /solicitacoes (nao membro) -> 404" "404" $script:STATUS

# DONO lista solicitacoes pendentes -> 200
GET "/missoes/$MISSAO_ID/solicitacoes`?status=PENDENTE" $TOKEN_DONO
assert_status "GET /solicitacoes?status=PENDENTE (DONO) -> 200" "200" $script:STATUS
assert_gte "2 solicitacoes pendentes" 2 (j).totalElements

# DONO aprova membro -> 200
PATCH "/missoes/$MISSAO_ID/solicitacoes/$SOL_MEMBRO_ID/aprovar" $TOKEN_DONO
assert_status "PATCH /aprovar (DONO aprova membro) -> 200" "200" $script:STATUS

# Membro (agora MEMBRO) tenta listar solicitacoes -> 403 (role insuficiente)
GET "/missoes/$MISSAO_ID/solicitacoes" $TOKEN_MEMBRO
assert_status "GET /solicitacoes (MEMBRO logado) -> 403" "403" $script:STATUS

# DONO aprova supervisor -> 200
PATCH "/missoes/$MISSAO_ID/solicitacoes/$SOL_SUPERVISOR_ID/aprovar" $TOKEN_DONO
assert_status "PATCH /aprovar (DONO aprova supervisor) -> 200" "200" $script:STATUS

# Promover supervisor@sat.dev para SUPERVISOR na missao (entrou como MEMBRO via aprovacao)
GET "/missoes/$MISSAO_ID/membros" $TOKEN_DONO
$ID_SUPERVISOR_TEMP = ((j) | Where-Object { $_.login -eq "supervisor@sat.dev" }).operadorId
PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR_TEMP`?novoRole=SUPERVISOR" $TOKEN_DONO
assert_status "PATCH promover supervisor@sat.dev para SUPERVISOR (pos-aprovacao) -> 200" "200" $script:STATUS

# Tentar aprovar solicitacao ja processada -> 400
PATCH "/missoes/$MISSAO_ID/solicitacoes/$SOL_MEMBRO_ID/aprovar" $TOKEN_DONO
assert_status "PATCH /aprovar solicitacao ja aprovada -> 400" "400" $script:STATUS

# Habilitar cowork -> forasteiro pode solicitar
$bodyCowork = @{nome="Missao Alpha v2";descricao="Atualizada";dataLancamento="2026-07-01";status="ATIVA";agenciaId=$AGENCIA_NASA_ID;permitirCowork=$true} | ConvertTo-Json -Compress
PUT "/missoes/$MISSAO_ID" $bodyCowork $TOKEN_DONO
assert_status "PUT /missoes/{id} habilitar cowork -> 200" "200" $script:STATUS
assert_eq "permitirCowork=true" "True" (j).permitirCowork

POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_FORASTEIRO
assert_status "POST /solicitar (ESA em missao com cowork=true) -> 201" "201" $script:STATUS
$SOL_FORASTEIRO_ID = (j).id
assert_eq "Solicitacao forasteiro PENDENTE" "PENDENTE" (j).status

# SUPERVISOR lista e rejeita a do forasteiro
GET "/missoes/$MISSAO_ID/solicitacoes`?status=PENDENTE" $TOKEN_SUPERVISOR
assert_status "GET /solicitacoes (SUPERVISOR) -> 200" "200" $script:STATUS

PATCH "/missoes/$MISSAO_ID/solicitacoes/$SOL_FORASTEIRO_ID/rejeitar" $TOKEN_SUPERVISOR
assert_status "PATCH /rejeitar (SUPERVISOR rejeita forasteiro) -> 200" "200" $script:STATUS

# Desabilitar cowork -> forasteiro nao pode mais solicitar
$bodyNoCowork = @{nome="Missao Alpha v2";descricao="Atualizada";dataLancamento="2026-07-01";status="ATIVA";agenciaId=$AGENCIA_NASA_ID;permitirCowork=$false} | ConvertTo-Json -Compress
PUT "/missoes/$MISSAO_ID" $bodyNoCowork $TOKEN_DONO
assert_status "PUT /missoes/{id} desabilitar cowork -> 200" "200" $script:STATUS

POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_FORASTEIRO
assert_status "POST /solicitar (ESA em missao sem cowork) -> 403" "403" $script:STATUS

# Missao solo para testar DONO unico
POST "/missoes" (@{nome="Missao Solo";descricao="Solo";dataLancamento="2026-06-01";status="PLANEJADA";senhaMissao="senha456";agenciaId=$AGENCIA_NASA_ID} | ConvertTo-Json -Compress) $TOKEN_DONO
assert_status "POST /missoes (solo) -> 201" "201" $script:STATUS
$MISSAO_SOLO_ID = (j).id

POST "/missoes/$MISSAO_SOLO_ID/sair" "{}" $TOKEN_DONO
assert_status "POST /sair DONO unico -> 400" "400" $script:STATUS

POST "/missoes/$MISSAO_SOLO_ID/solicitar" (@{senha="senha456"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /solicitar missao solo (supervisor) -> 201" "201" $script:STATUS
$SOL_SOLO_ID = (j).id

PATCH "/missoes/$MISSAO_SOLO_ID/solicitacoes/$SOL_SOLO_ID/aprovar" $TOKEN_DONO
assert_status "PATCH /aprovar supervisor na missao solo -> 200" "200" $script:STATUS

POST "/missoes/$MISSAO_SOLO_ID/sair" "{}" $TOKEN_SUPERVISOR
assert_status "POST /sair supervisor (com sucesso) -> 204" "204" $script:STATUS

# ── 8. MISSOES -- Gerenciamento de membros ────────────────────────
section "8. MISSOES -- Gerenciamento de membros"

GET "/missoes/$MISSAO_ID/membros" $TOKEN_FORASTEIRO
assert_status "GET /membros (nao membro com token) -> 403" "403" $script:STATUS

GET "/missoes/$MISSAO_ID/membros" $TOKEN_MEMBRO
assert_status "GET /membros (membro) -> 200" "200" $script:STATUS
$membros = (j)
assert_eq "3 membros na missao (DONO + MEMBRO + SUPERVISOR)" "3" $membros.Count

$ID_SUPERVISOR = ($membros | Where-Object { $_.login -eq "supervisor@sat.dev" }).operadorId
$ID_MEMBRO     = ($membros | Where-Object { $_.login -eq "membro@sat.dev" }).operadorId
$ID_DONO       = ($membros | Where-Object { $_.login -eq "dono@sat.dev" }).operadorId

DELETE "/missoes/$MISSAO_ID/membros/$ID_DONO" $TOKEN_DONO
assert_status "DELETE /membros/{donoId} (DONO removendo si mesmo) -> 403" "403" $script:STATUS

PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR`?novoRole=SUPERVISOR" $TOKEN_MEMBRO
assert_status "PATCH promover (MEMBRO tentando) -> 403" "403" $script:STATUS

PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR`?novoRole=SUPERVISOR" $TOKEN_DONO
assert_status "PATCH promover SUPERVISOR (DONO) -> 200" "200" $script:STATUS
assert_eq "Role promovida para SUPERVISOR" "SUPERVISOR" (j).role

PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR`?novoRole=MEMBRO" $TOKEN_DONO
assert_status "PATCH rebaixar SUPERVISOR -> MEMBRO (DONO) -> 200" "200" $script:STATUS
assert_eq "Role rebaixada para MEMBRO" "MEMBRO" (j).role

PATCH "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR`?novoRole=SUPERVISOR" $TOKEN_DONO
assert_status "PATCH re-promover SUPERVISOR (DONO) -> 200" "200" $script:STATUS

PATCH "/missoes/$MISSAO_ID/membros/$ID_DONO`?novoRole=MEMBRO" $TOKEN_DONO
assert_status "PATCH DONO alterando propria role -> 403" "403" $script:STATUS

DELETE "/missoes/$MISSAO_ID/membros/$ID_SUPERVISOR" $TOKEN_MEMBRO
assert_status "DELETE membro (MEMBRO tentando) -> 403" "403" $script:STATUS

DELETE "/missoes/$MISSAO_ID/membros/$ID_MEMBRO" $TOKEN_DONO
assert_status "DELETE /membros/{membroId} (DONO) -> 204" "204" $script:STATUS

POST "/missoes/$MISSAO_ID/sair" "{}" $TOKEN_MEMBRO
assert_status "POST /sair apos ser removido -> 404" "404" $script:STATUS

# ── 9. SATELITES -- CRUD ──────────────────────────────────────────
section "9. SATELITES -- CRUD e controle de acesso"

# Membro re-ingressa via novo fluxo de solicitacao
POST "/missoes/$MISSAO_ID/solicitar" (@{senha="senha123"} | ConvertTo-Json -Compress) $TOKEN_MEMBRO
assert_status "POST /solicitar membro re-ingressando -> 201" "201" $script:STATUS
$SOL_REINGRESSO_ID = (j).id

PATCH "/missoes/$MISSAO_ID/solicitacoes/$SOL_REINGRESSO_ID/aprovar" $TOKEN_DONO
assert_status "PATCH /aprovar membro re-aprovado -> 200" "200" $script:STATUS

$satBody = @{
    nome           = "SAT-01"
    dataLancamento = "2026-01-15"
    missaoId       = $MISSAO_ID
    coordenadas    = @{ altitudeKm = 550.0; inclinacao = 53.5; longitudeNodo = 12.3 }
    tipoOrbita     = "LEO"
    statusSatelite = "ATIVO"
} | ConvertTo-Json -Compress -Depth 5

POST "/satelites" $satBody
assert_status "POST /satelites sem token -> 403" "403" $script:STATUS

POST "/satelites" $satBody $TOKEN_MEMBRO
assert_status "POST /satelites (MEMBRO) -> 403" "403" $script:STATUS

POST "/satelites" $satBody $TOKEN_SUPERVISOR
assert_status "POST /satelites (SUPERVISOR) -> 201" "201" $script:STATUS
$SAT_ID = (j).id
assert_not_empty "id do satelite valido" $SAT_ID
assert_eq "Nome SAT-01 correto" "SAT-01" (j).nome
assert_eq "altitudeKm salva corretamente" "550" (j).altitudeKm
assert_eq "tipoOrbita salvo" "LEO" (j).tipoOrbita
assert_eq "statusSatelite salvo" "ATIVO" (j).statusSatelite

POST "/satelites" $satBody $TOKEN_SUPERVISOR
assert_status "POST /satelites nome duplicado -> 400" "400" $script:STATUS

POST "/satelites" (@{nome="SAT-X";dataLancamento="2026-01-01";missaoId=99999;coordenadas=@{altitudeKm=100.0;inclinacao=0.0}} | ConvertTo-Json -Compress -Depth 5) $TOKEN_SUPERVISOR
assert_status "POST /satelites missaoId inexistente -> 404" "404" $script:STATUS

GET "/satelites"
assert_status "GET /satelites (publico) -> 200" "200" $script:STATUS

GET "/satelites/$SAT_ID"
assert_status "GET /satelites/{id} -> 200" "200" $script:STATUS
assert_eq "Links HATEOAS presentes" "true" (has_key "_links")

GET "/satelites/missao/99999"
assert_status "GET /satelites/missao/{missaoId} inexistente -> 404" "404" $script:STATUS

GET "/satelites/missao/$MISSAO_ID"
assert_status "GET /satelites/missao/{missaoId} -> 200" "200" $script:STATUS
assert_gte "Pelo menos 1 satelite na missao" 1 (j).totalElements

GET "/satelites/$SAT_ID/estatisticas"
assert_status "GET /satelites/{id}/estatisticas (sem leituras) -> 200" "200" $script:STATUS
assert_eq "totalLeituras=0 sem leituras" "0" (j).totalLeituras

$putSat = @{
    nome           = "SAT-01"
    dataLancamento = "2026-02-01"
    missaoId       = $MISSAO_ID
    coordenadas    = @{ altitudeKm = 600.0; inclinacao = 55.0 }
    tipoOrbita     = "LEO"
    statusSatelite = "STANDBY"
} | ConvertTo-Json -Compress -Depth 5

PUT "/satelites/$SAT_ID" $putSat $TOKEN_SUPERVISOR
assert_status "PUT /satelites/{id} (SUPERVISOR) -> 200" "200" $script:STATUS
assert_eq "altitudeKm atualizada para 600" "600" (j).altitudeKm
assert_eq "statusSatelite atualizado para STANDBY" "STANDBY" (j).statusSatelite

PUT "/satelites/$SAT_ID" (@{nome="SAT-01";dataLancamento="2026-03-01";missaoId=$MISSAO_ID;coordenadas=@{altitudeKm=650.0;inclinacao=57.0};tipoOrbita="LEO";statusSatelite="ATIVO"} | ConvertTo-Json -Compress -Depth 5) $TOKEN_DONO
assert_status "PUT /satelites/{id} (DONO) -> 200" "200" $script:STATUS
assert_eq "altitudeKm atualizada pelo DONO para 650" "650" (j).altitudeKm

DELETE "/satelites/$SAT_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /satelites/{id} (SUPERVISOR) -> 403" "403" $script:STATUS

GET "/satelites/99999"
assert_status "GET /satelites/99999 -> 404" "404" $script:STATUS

# ── 10. SENSORES -- Criacao dos 4 tipos ───────────────────────────
section "10. SENSORES -- Criacao dos 4 tipos com heranca JOINED"

POST "/sensores" (@{nome="Termometro";unidade="graus_C";limiteMin=0.0;limiteMax=80.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores TERMICO -> 201" "201" $script:STATUS
$SENSOR_TERMICO_ID = (j).id
assert_eq "detalhe=CELSIUS (SensorTermico)" "CELSIUS" (j).detalhe
assert_eq "tipo=TERMICO" "TERMICO" (j).tipo

POST "/sensores" (@{nome="Barometro";unidade="hPa";limiteMin=950.0;limiteMax=1050.0;margemAlerta=5.0;sateliteId=$SAT_ID;tipo="PRESSAO";tipoPressao="ABSOLUTA"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores PRESSAO -> 201" "201" $script:STATUS
$SENSOR_PRESSAO_ID = (j).id
assert_eq "detalhe=ABSOLUTA (SensorPressao)" "ABSOLUTA" (j).detalhe

POST "/sensores" (@{nome="Geiger";unidade="Gy";limiteMin=0.0;limiteMax=5.0;margemAlerta=20.0;sateliteId=$SAT_ID;tipo="RADIACAO";tipoRadiacao="IONIZANTE"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores RADIACAO -> 201" "201" $script:STATUS
$SENSOR_RADIACAO_ID = (j).id
assert_eq "detalhe=IONIZANTE (SensorRadiacao)" "IONIZANTE" (j).detalhe

POST "/sensores" (@{nome="Magnetometro";unidade="nT";limiteMin=-50000.0;limiteMax=50000.0;margemAlerta=15.0;sateliteId=$SAT_ID;tipo="MAGNETOMETRO";eixosMedicao="XYZ"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores MAGNETOMETRO -> 201" "201" $script:STATUS
$SENSOR_MAG_ID = (j).id
assert_eq "detalhe=XYZ (Magnetometro)" "XYZ" (j).detalhe

# ── 11. SENSORES -- Validacoes ────────────────────────────────────
section "11. SENSORES -- Validacoes e controle de acesso"

POST "/sensores" (@{nome="Inv";unidade="X";limiteMin=100.0;limiteMax=50.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores limiteMin >= limiteMax -> 400" "400" $script:STATUS

POST "/sensores" (@{nome="Son";unidade="Hz";limiteMin=0.0;limiteMax=100.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="SONICO"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores tipo invalido -> 400" "400" $script:STATUS

POST "/sensores" (@{nome="SemEscala";unidade="graus_C";limiteMin=0.0;limiteMax=100.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="TERMICO"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores TERMICO sem unidadeEscala -> 400" "400" $script:STATUS

POST "/sensores" (@{nome="Termometro";unidade="K";limiteMin=0.0;limiteMax=80.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="KELVIN"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores nome duplicado no satelite -> 400" "400" $script:STATUS

POST "/sensores" (@{nome="Orfao";unidade="X";limiteMin=0.0;limiteMax=10.0;margemAlerta=5.0;sateliteId=99999;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "POST /sensores sateliteId inexistente -> 404" "404" $script:STATUS

POST "/sensores" (@{nome="SensorMembro";unidade="X";limiteMin=0.0;limiteMax=100.0;margemAlerta=10.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_MEMBRO
assert_status "POST /sensores (MEMBRO) -> 403" "403" $script:STATUS

GET "/sensores"
assert_status "GET /sensores (publico) -> 200" "200" $script:STATUS

GET "/sensores/$SENSOR_TERMICO_ID"
assert_status "GET /sensores/{id} -> 200" "200" $script:STATUS
assert_eq "Links HATEOAS no sensor" "true" (has_key "_links")

GET "/sensores/satelite/99999"
assert_status "GET /sensores/satelite/{id} inexistente -> 404" "404" $script:STATUS

GET "/sensores/satelite/$SAT_ID"
assert_status "GET /sensores/satelite/{id} -> 200" "200" $script:STATUS
assert_eq "4 sensores no satelite" "4" (j).totalElements

PUT "/sensores/$SENSOR_TERMICO_ID" (@{nome="Termometro";unidade="K";limiteMin=-10.0;limiteMax=90.0;margemAlerta=5.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_SUPERVISOR
assert_status "PUT /sensores/{id} (SUPERVISOR) -> 200" "200" $script:STATUS
assert_eq "limiteMin atualizado para -10" "-10" (j).limiteMin
assert_eq "detalhe permanece CELSIUS" "CELSIUS" (j).detalhe

PUT "/sensores/$SENSOR_TERMICO_ID" (@{nome="Termometro";unidade="graus_C";limiteMin=-10.0;limiteMax=90.0;margemAlerta=5.0;sateliteId=$SAT_ID;tipo="TERMICO";unidadeEscala="CELSIUS"} | ConvertTo-Json -Compress) $TOKEN_DONO
assert_status "PUT /sensores/{id} (DONO) -> 200" "200" $script:STATUS
assert_eq "unidade atualizada pelo DONO para graus_C" "graus_C" (j).unidade

# ── 12. LEITURAS -- StatusCalculator ──────────────────────────────
section "12. LEITURAS -- Registro e classificacao automatica de status"
Write-Host "  Sensor Termico: limiteMin=-10, limiteMax=90, margemAlerta=5%" -ForegroundColor Yellow
Write-Host "    -> zonaAlertaMin=-5  |  zonaAlertaMax=85" -ForegroundColor Yellow

POST "/leituras" (@{valor=40.0;sensorId=$SENSOR_TERMICO_ID;latitude=-23.5505;longitude=-46.6333;qualidade="BOA"} | ConvertTo-Json -Compress)
assert_status "POST /leituras NORMAL (com lat/lng/qualidade) -> 201" "201" $script:STATUS
assert_eq "valor=40.0 -> NORMAL" "NORMAL" (j).status
assert_eq "latitude salva corretamente" "-23.5505" (j).latitude
assert_eq "qualidade=BOA salva" "BOA" (j).qualidade
$LEITURA_NORMAL_ID = (j).id
assert_not_empty "dataHoraLeitura definida pelo servidor" (j).dataHoraLeitura
assert_eq "Links HATEOAS na leitura" "true" (has_key "_links")

POST "/leituras" (@{valor=87.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras ALERTA superior -> 201" "201" $script:STATUS
assert_eq "valor=87.0 -> ALERTA (zona superior)" "ALERTA" (j).status
$LEITURA_ALERTA_ID = (j).id

POST "/leituras" (@{valor=150.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras CRITICO acima limiteMax -> 201" "201" $script:STATUS
assert_eq "valor=150.0 -> CRITICO" "CRITICO" (j).status
$LEITURA_CRITICO_ID = (j).id

POST "/leituras" (@{valor=-50.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras CRITICO abaixo limiteMin -> 201" "201" $script:STATUS
assert_eq "valor=-50.0 -> CRITICO" "CRITICO" (j).status

POST "/leituras" (@{valor=-8.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras ALERTA inferior -> 201" "201" $script:STATUS
assert_eq "valor=-8.0 -> ALERTA (zona inferior)" "ALERTA" (j).status

POST "/leituras" (@{valor=-10.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras valor=limiteMin exato -> 201" "201" $script:STATUS
assert_eq "valor=-10.0 (limiteMin) -> ALERTA (fronteira inclusiva)" "ALERTA" (j).status

POST "/leituras" (@{valor=85.0;sensorId=$SENSOR_TERMICO_ID} | ConvertTo-Json -Compress)
assert_status "POST /leituras valor=zonaAlertaMax exato -> 201" "201" $script:STATUS
assert_eq "valor=85.0 (zonaAlertaMax) -> NORMAL (fronteira exclusiva)" "NORMAL" (j).status

POST "/leituras" (@{valor=40.0;sensorId=$SENSOR_TERMICO_ID;qualidade="DEGRADADA"} | ConvertTo-Json -Compress)
assert_status "POST /leituras qualidade=DEGRADADA -> 201" "201" $script:STATUS
assert_eq "qualidade=DEGRADADA salva" "DEGRADADA" (j).qualidade

POST "/leituras" (@{valor=40.0;sensorId=99999} | ConvertTo-Json -Compress)
assert_status "POST /leituras sensorId inexistente -> 404" "404" $script:STATUS

POST "/leituras" (@{valor=40.0} | ConvertTo-Json -Compress)
assert_status "POST /leituras sem sensorId -> 400" "400" $script:STATUS

# ── 13. LEITURAS -- Consultas e filtros ───────────────────────────
section "13. LEITURAS -- Listagem, filtros e erros de consulta"

GET "/leituras"
assert_status "GET /leituras (publico) -> 200" "200" $script:STATUS
assert_gte "Pelo menos 8 leituras registradas" 8 (j).totalElements

GET "/leituras/$LEITURA_NORMAL_ID"
assert_status "GET /leituras/{id} -> 200" "200" $script:STATUS
assert_not_empty "Link sensor presente" (j)._links.sensor.href
assert_not_empty "Link satelite presente" (j)._links.satelite.href

GET "/leituras/99999"
assert_status "GET /leituras/99999 -> 404" "404" $script:STATUS

GET "/leituras/sensor/$SENSOR_TERMICO_ID"
assert_status "GET /leituras/sensor/{id} -> 200" "200" $script:STATUS
assert_gte "Pelo menos 8 leituras do sensor" 8 (j).totalElements

GET "/leituras/sensor/$SENSOR_TERMICO_ID`?status=CRITICO"
assert_status "GET /leituras/sensor?status=CRITICO -> 200" "200" $script:STATUS
assert_gte "Filtro CRITICO retorna >= 2 leituras" 2 (j).totalElements

GET "/leituras/sensor/$SENSOR_TERMICO_ID`?status=ALERTA"
assert_status "GET /leituras/sensor?status=ALERTA -> 200" "200" $script:STATUS
assert_gte "Filtro ALERTA retorna >= 2 leituras" 2 (j).totalElements

GET "/leituras/sensor/$SENSOR_TERMICO_ID`?status=NORMAL"
assert_status "GET /leituras/sensor?status=NORMAL -> 200" "200" $script:STATUS
assert_gte "Filtro NORMAL retorna >= 1 leitura" 1 (j).totalElements

GET "/leituras/sensor/99999"
assert_status "GET /leituras/sensor/{id} inexistente -> 404" "404" $script:STATUS

GET "/leituras/sensor/$SENSOR_PRESSAO_ID"
assert_status "GET /leituras/sensor sem leituras -> 200" "200" $script:STATUS
assert_eq "totalElements=0 para sensor sem leituras" "0" (j).totalElements

GET "/leituras/satelite/$SAT_ID"
assert_status "GET /leituras/satelite/{id} -> 200" "200" $script:STATUS
assert_gte "Leituras por satelite retorna >= 8" 8 (j).totalElements

GET "/leituras/satelite/$SAT_ID`?status=CRITICO"
assert_status "GET /leituras/satelite?status=CRITICO -> 200" "200" $script:STATUS

GET "/leituras/satelite/99999"
assert_status "GET /leituras/satelite/{id} inexistente -> 404" "404" $script:STATUS

# ── 14. ESTATISTICAS ─────────────────────────────────────────────
section "14. SATELITES -- Estatisticas agregadas com leituras"

GET "/satelites/$SAT_ID/estatisticas"
assert_status "GET /estatisticas com leituras -> 200" "200" $script:STATUS
assert_gte "totalLeituras >= 8" 8 (j).totalLeituras
assert_gte "totalCriticos >= 2" 2 (j).totalCriticos
assert_gte "totalAlertas >= 2" 2 (j).totalAlertas
assert_not_empty "ultimaLeitura preenchida" (j).ultimaLeitura
assert_not_empty "mediaValor calculada" (j).mediaValor

# ── 15. ALERTAS ──────────────────────────────────────────────────
section "15. ALERTAS -- Listagem, filtros e gerenciamento"

GET "/alertas"
assert_status "GET /alertas (publico) -> 200" "200" $script:STATUS
assert_gte "Pelo menos 2 alertas gerados" 2 (j).totalElements

GET "/alertas`?size=1&sort=dataAlerta,desc"
assert_status "GET /alertas?size=1 -> 200" "200" $script:STATUS
$ALERTA_ID = (j).content[0].id
assert_not_empty "alerta_id capturado" $ALERTA_ID

GET "/alertas`?status=ATIVO"
assert_status "GET /alertas?status=ATIVO -> 200" "200" $script:STATUS
assert_gte "Alertas ATIVO retorna >= 1" 1 (j).totalElements

GET "/alertas/$ALERTA_ID"
assert_status "GET /alertas/{id} -> 200" "200" $script:STATUS
assert_not_empty "tipoAlerta presente" (j).tipoAlerta

GET "/alertas/satelite/$SAT_ID"
assert_status "GET /alertas/satelite/{id} -> 200" "200" $script:STATUS
assert_gte "Alertas do satelite >= 2" 2 (j).totalElements

GET "/alertas/satelite/99999"
assert_status "GET /alertas/satelite/99999 -> 404" "404" $script:STATUS

PATCH "/alertas/$ALERTA_ID`?novoStatus=RECONHECIDO"
assert_status "PATCH /alertas sem token -> 403" "403" $script:STATUS

PATCH "/alertas/$ALERTA_ID`?novoStatus=RECONHECIDO" $TOKEN_SUPERVISOR
assert_status "PATCH /alertas reconhecer (SUPERVISOR) -> 200" "200" $script:STATUS
assert_eq "statusAlerta=RECONHECIDO" "RECONHECIDO" (j).statusAlerta

PATCH "/alertas/$ALERTA_ID`?novoStatus=RESOLVIDO" $TOKEN_DONO
assert_status "PATCH /alertas resolver (DONO) -> 200" "200" $script:STATUS
assert_eq "statusAlerta=RESOLVIDO" "RESOLVIDO" (j).statusAlerta

# ── 16. LEITURAS -- Exclusao ──────────────────────────────────────
section "16. LEITURAS -- Exclusao e controle de acesso"

DELETE "/leituras/$LEITURA_NORMAL_ID"
assert_status "DELETE /leituras sem token -> 403" "403" $script:STATUS

DELETE "/leituras/$LEITURA_NORMAL_ID" $TOKEN_FORASTEIRO
assert_status "DELETE /leituras (nao membro da missao) -> 403" "403" $script:STATUS

DELETE "/leituras/$LEITURA_NORMAL_ID" $TOKEN_MEMBRO
assert_status "DELETE /leituras (MEMBRO da missao) -> 403" "403" $script:STATUS

DELETE "/leituras/$LEITURA_ALERTA_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /leituras/{id} (SUPERVISOR) -> 204" "204" $script:STATUS

DELETE "/leituras/$LEITURA_ALERTA_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /leituras ja deletada -> 404" "404" $script:STATUS

DELETE "/leituras/$LEITURA_CRITICO_ID" $TOKEN_DONO
assert_status "DELETE /leituras/{id} (DONO) -> 204" "204" $script:STATUS

# ── 17. SENSORES -- Exclusao ──────────────────────────────────────
section "17. SENSORES -- Exclusao (apenas DONO)"

DELETE "/sensores/$SENSOR_MAG_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /sensores/{id} (SUPERVISOR) -> 403" "403" $script:STATUS

DELETE "/sensores/$SENSOR_RADIACAO_ID" $TOKEN_DONO
assert_status "DELETE /sensores/{id} (DONO) -> 204" "204" $script:STATUS

GET "/sensores/$SENSOR_RADIACAO_ID"
assert_status "GET /sensores apos delete -> 404" "404" $script:STATUS

GET "/sensores/satelite/$SAT_ID"
assert_status "GET /sensores/satelite apos delete -> 200" "200" $script:STATUS
assert_eq "3 sensores restam apos delete" "3" (j).totalElements

# ── 18. SATELITES -- Exclusao ─────────────────────────────────────
section "18. SATELITES -- Exclusao com cascade"

$satDesc = @{
    nome           = "SAT-DESCARTAVEL"
    dataLancamento = "2026-01-01"
    missaoId       = $MISSAO_ID
    coordenadas    = @{ altitudeKm = 200.0; inclinacao = 0.0 }
} | ConvertTo-Json -Compress -Depth 5

POST "/satelites" $satDesc $TOKEN_DONO
assert_status "POST /satelites descartavel -> 201" "201" $script:STATUS
$SAT_DESCARTAVEL_ID = (j).id

DELETE "/satelites/$SAT_DESCARTAVEL_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /satelites (SUPERVISOR) -> 403" "403" $script:STATUS

DELETE "/satelites/$SAT_DESCARTAVEL_ID" $TOKEN_DONO
assert_status "DELETE /satelites (DONO) -> 204" "204" $script:STATUS

GET "/satelites/$SAT_DESCARTAVEL_ID"
assert_status "GET /satelites apos delete -> 404" "404" $script:STATUS

# ── 19. MISSOES -- Exclusao e saida voluntaria ────────────────────
section "19. MISSOES -- Exclusao e saida voluntaria"

DELETE "/missoes/$MISSAO_ID" $TOKEN_SUPERVISOR
assert_status "DELETE /missoes (SUPERVISOR) -> 403" "403" $script:STATUS

DELETE "/missoes/$MISSAO_ID" $TOKEN_MEMBRO
assert_status "DELETE /missoes (MEMBRO) -> 403" "403" $script:STATUS

POST "/missoes/$MISSAO_ID/sair" "{}" $TOKEN_MEMBRO
assert_status "POST /sair (MEMBRO voluntariamente) -> 204" "204" $script:STATUS

DELETE "/missoes/$MISSAO_SOLO_ID" $TOKEN_DONO
assert_status "DELETE /missoes (missao solo, DONO) -> 204" "204" $script:STATUS

GET "/missoes/$MISSAO_SOLO_ID" $TOKEN_DONO
assert_status "GET /missoes apos delete -> 404" "404" $script:STATUS

# ── Resumo ───────────────────────────────────────────────────────
$TOTAL = $script:PASS + $script:FAIL

Write-Host ""
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "  Total de testes : $TOTAL"
Write-Host "  Passaram         : $($script:PASS)" -ForegroundColor Green
if ($script:FAIL -gt 0) {
    Write-Host "  Falharam         : $($script:FAIL)" -ForegroundColor Red
} else {
    Write-Host "  Falharam         : 0" -ForegroundColor Green
}
Write-Host "======================================================" -ForegroundColor Cyan

if ($script:FAILED.Count -gt 0) {
    Write-Host ""
    Write-Host "Testes que falharam -- verifique e corrija:" -ForegroundColor Red
    for ($i = 0; $i -lt $script:FAILED.Count; $i++) {
        Write-Host "  $($i+1). $($script:FAILED[$i])" -ForegroundColor Red
    }
    Write-Host ""
}

exit $(if ($script:FAIL -eq 0) { 0 } else { 1 })
