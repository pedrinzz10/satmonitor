# SatMonitor -- Varredura de Seguranca
# Requer: PowerShell 7+  |  API em http://localhost:8080  |  banco H2 limpo (reiniciar o app antes)
# Ferramenta externa: jwt_tool (instalada em C:\tools\jwt_tool\)

# Adiciona jwt_tool ao PATH desta sessao (caso nao esteja globalmente)
if ((Test-Path "C:\tools\jwt_tool") -and ($env:PATH -notlike "*C:\tools\jwt_tool*")) {
    $env:PATH = "$env:PATH;C:\tools\jwt_tool"
}

$BASE            = "http://localhost:8080"
$script:STATUS   = 0
$script:BODY     = "{}"
$script:HEADERS  = @{}
$script:SAFE_N   = 0
$script:VULN_N   = 0
$script:INFO_N   = 0
$script:TOTAL_N  = 0
$script:VULN_LIST = [System.Collections.Generic.List[string]]::new()

# ── helpers ───────────────────────────────────────────────────────────────────

function do_request($method, $url, $body = $null, $token = $null) {
    $hdrs = @{ "Content-Type" = "application/json" }
    if ($token) { $hdrs["Authorization"] = "Bearer $token" }
    $p = @{
        Method             = $method
        Uri                = "$BASE$url"
        Headers            = $hdrs
        SkipHttpErrorCheck = $true
        ErrorAction        = "Stop"
    }
    if ($body) { $p.Body = [System.Text.Encoding]::UTF8.GetBytes($body) }
    try {
        $res            = Invoke-WebRequest @p
        $script:STATUS  = [int]$res.StatusCode
        $script:HEADERS = $res.Headers
        if ($res.Content -is [byte[]]) {
            $script:BODY = [System.Text.Encoding]::UTF8.GetString($res.Content)
        } else {
            $script:BODY = [string]$res.Content
        }
    } catch {
        $script:STATUS  = 0
        $script:BODY    = "{}"
        $script:HEADERS = @{}
    }
}

function j    { try { $script:BODY | ConvertFrom-Json } catch { [PSCustomObject]@{} } }
function GET($url, $token = $null)         { do_request "GET"    $url $null $token }
function POST($url, $body, $token = $null) { do_request "POST"   $url $body $token }
function PUT($url, $body, $token = $null)  { do_request "PUT"    $url $body $token }
function DELETE($url, $token = $null)      { do_request "DELETE" $url $null $token }

function safe($label) { $script:SAFE_N++; $script:TOTAL_N++; Write-Host "  [SAFE] $label" -ForegroundColor Green }
function vuln($label) { $script:VULN_N++; $script:TOTAL_N++; $script:VULN_LIST.Add($label); Write-Host "  [VULN] $label" -ForegroundColor Red }
function inf($label)  { $script:INFO_N++; $script:TOTAL_N++; Write-Host "  [INFO] $label" -ForegroundColor Cyan }
function section($title) { Write-Host ""; Write-Host "> $title" -ForegroundColor Yellow }

function Build-JWT($subject, $secret) {
    $h    = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}')) `
                -replace '=','' -replace '\+','-' -replace '/','_'
    $exp  = [System.DateTimeOffset]::UtcNow.AddHours(8).ToUnixTimeSeconds()
    $p    = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes(
                "{`"sub`":`"$subject`",`"iss`":`"satmonitor`",`"exp`":$exp}")) `
                -replace '=','' -replace '\+','-' -replace '/','_'
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($secret))
    $sig  = [Convert]::ToBase64String($hmac.ComputeHash(
                [System.Text.Encoding]::UTF8.GetBytes("$h.$p"))) `
                -replace '=','' -replace '\+','-' -replace '/','_'
    return "$h.$p.$sig"
}

function Build-JWT-None($subject) {
    $h   = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('{"alg":"none","typ":"JWT"}')) `
               -replace '=','' -replace '\+','-' -replace '/','_'
    $exp = [System.DateTimeOffset]::UtcNow.AddHours(8).ToUnixTimeSeconds()
    $p   = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes(
               "{`"sub`":`"$subject`",`"iss`":`"satmonitor`",`"exp`":$exp}")) `
               -replace '=','' -replace '\+','-' -replace '/','_'
    return "$h.$p."
}

# ── banner ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "+======================================================+" -ForegroundColor Magenta
Write-Host "|         SatMonitor -- Varredura de Seguranca         |" -ForegroundColor Magenta
Write-Host "+======================================================+" -ForegroundColor Magenta
Write-Host ""

# ── health check ─────────────────────────────────────────────────────────────
GET "/actuator/health"
if ($script:STATUS -ne 200) {
    Write-Host "  [ERRO] API nao esta no ar em $BASE (status $($script:STATUS))." -ForegroundColor Red
    Write-Host "         Execute: .\gradlew bootRun  e aguarde 'Started SatmonitorApplication'" -ForegroundColor Red
    exit 2
}
Write-Host "  API online: $BASE" -ForegroundColor Green

# ── setup ─────────────────────────────────────────────────────────────────────
section "0. Setup -- criando dados de teste"

$DONO_LOGIN   = "sec-dono@sec.dev"
$DONO_SENHA   = "senhaDonoSec123"
$ATAC_LOGIN   = "sec-atac@sec.dev"
$ATAC_SENHA   = "senhaAtacSec123"
$MISSAO_SENHA = "missaoSecSenha1"

# agencia necessaria para registrar operadores (agenciaId @NotNull)
POST "/agencias" (@{ nome = "Agencia Sec"; siglaPais = "BR" } | ConvertTo-Json -Compress)
$AGENCIA_SEC_ID = (j).id
if (-not $AGENCIA_SEC_ID) {
    Write-Host "  [ERRO] Falha ao criar agencia de teste. Reinicie o app (banco H2 limpo) e tente novamente." -ForegroundColor Red
    exit 1
}

POST "/auth/registrar" (@{ login = $DONO_LOGIN; senha = $DONO_SENHA; nome = "Dono Sec"; agenciaId = $AGENCIA_SEC_ID } | ConvertTo-Json -Compress)
POST "/auth/registrar" (@{ login = $ATAC_LOGIN; senha = $ATAC_SENHA; nome = "Atac Sec"; agenciaId = $AGENCIA_SEC_ID } | ConvertTo-Json -Compress)

POST "/auth/login" (@{ login = $DONO_LOGIN; senha = $DONO_SENHA } | ConvertTo-Json -Compress)
$TOKEN_DONO = (j).token
if (-not $TOKEN_DONO) {
    Write-Host "  [ERRO] Login do sec-dono falhou. Reinicie o app (banco H2 limpo) e tente novamente." -ForegroundColor Red
    exit 1
}

POST "/auth/login" (@{ login = $ATAC_LOGIN; senha = $ATAC_SENHA } | ConvertTo-Json -Compress)
$TOKEN_ATAC = (j).token

# missao principal (atacante entra como MEMBRO)
POST "/missoes" (@{ nome = "Missao Sec Principal"; senhaMissao = $MISSAO_SENHA; dataLancamento = "2024-01-01"; status = "PLANEJADA" } | ConvertTo-Json -Compress) $TOKEN_DONO
$MISSAO_ID = (j).id
if (-not $MISSAO_ID) {
    Write-Host "  [ERRO] Falha ao criar missao principal. Banco pode nao estar limpo." -ForegroundColor Red
    exit 1
}
POST "/missoes/$MISSAO_ID/solicitar" (@{ senha = $MISSAO_SENHA } | ConvertTo-Json -Compress) $TOKEN_ATAC
$SOLICITA_ATAC_ID = (j).id
if ($SOLICITA_ATAC_ID) {
    # solicitar cria solicitacao pendente -- DONO aprova para atac virar MEMBRO
    do_request "PATCH" "/missoes/$MISSAO_ID/solicitacoes/$SOLICITA_ATAC_ID/aprovar" $null $TOKEN_DONO
}

# missao privada (somente dono -- para teste IDOR)
POST "/missoes" (@{ nome = "Missao Sec Privada"; senhaMissao = "privadaSecSen1"; dataLancamento = "2024-01-01"; status = "PLANEJADA" } | ConvertTo-Json -Compress) $TOKEN_DONO
$MISSAO_PRIV_ID = (j).id

# satelite (para testes de privilege escalation)
POST "/satelites" (@{
    nome          = "SAT-SEC-01"
    missaoId      = $MISSAO_ID
    dataLancamento = "2024-01-01"
    coordenadas   = @{ altitudeKm = 500.0; inclinacao = 45.0 }
} | ConvertTo-Json -Depth 5 -Compress) $TOKEN_DONO
$SAT_ID = (j).id

Write-Host "  sec-dono logado | missao=$MISSAO_ID | sat=$SAT_ID | missao-privada=$MISSAO_PRIV_ID" -ForegroundColor Green

# ─────────────────────────────────────────────────────────────────────────────
# CAMADA EXTERNA -- jwt_tool
# ─────────────────────────────────────────────────────────────────────────────
section "1. CAMADA EXTERNA -- jwt_tool (ataques especificos de JWT)"
$jwtToolAvailable = (Get-Command jwt_tool -ErrorAction SilentlyContinue) -or (Test-Path "C:\tools\jwt_tool\jwt_tool.bat")
if ($jwtToolAvailable -and $TOKEN_DONO) {
    # alg:none
    Write-Host "  Testando alg:none ..." -ForegroundColor Cyan
    $noneLines = & jwt_tool $TOKEN_DONO -X a 2>&1
    $noneToken = $noneLines | Where-Object { $_ -match '^ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.' } | Select-Object -First 1
    if ($noneToken) {
        GET "/missoes" ([string]$noneToken)
        if ($script:STATUS -eq 200) { vuln "jwt_tool: alg:none aceito -- assinatura ignorada pela API" }
        else                        { safe "jwt_tool: alg:none rejeitado ($($script:STATUS))" }
    } else {
        inf "jwt_tool -X a nao gerou token modificado legivel"
    }

    # brute-force do segredo
    Write-Host "  Brute-force do segredo JWT ..." -ForegroundColor Cyan
    $wl = [System.IO.Path]::GetTempFileName()
    @("satmonitor-dev-secret-local-2024","secret","mysecret","password","123456",
      "jwt-secret","dev-secret","spring-secret","changeme","satmonitor") | Set-Content $wl -Encoding UTF8
    $crackOut = & jwt_tool $TOKEN_DONO -C -d $wl 2>&1
    Remove-Item $wl -Force -ErrorAction SilentlyContinue
    $found = $crackOut | Where-Object { $_ -match 'KEY FOUND|secret found|FOUND:' } | Select-Object -First 1
    if ($found) { vuln "jwt_tool: segredo encontrado por brute-force -- $found" }
    else        { safe "jwt_tool: segredo nao encontrado na wordlist" }
} else {
    inf "jwt_tool nao instalado"
    inf "Instalar: execute o script tools/instalar-ferramentas.ps1"
}

# ─────────────────────────────────────────────────────────────────────────────
# Checks nativos em PowerShell
# ─────────────────────────────────────────────────────────────────────────────

# Headers de seguranca
section "2. Headers de seguranca HTTP"
GET "/actuator/health"
$hdrs = $script:HEADERS
@("X-Content-Type-Options","Content-Security-Policy","Strict-Transport-Security",
  "Referrer-Policy","Permissions-Policy","X-XSS-Protection","X-Frame-Options") | ForEach-Object {
    if ($hdrs.ContainsKey($_)) { safe "$_ presente: $($hdrs[$_])" }
    else                       { vuln "$_ ausente" }
}

# Bypass de autenticacao
section "3. Bypass de autenticacao (tokens invalidos)"
# usa POST /missoes (endpoint autenticado) -- POST /agencias e publico intencionalmente
$b = @{ nome = "Missao Auth Test"; senhaMissao = "authtest123"; dataLancamento = "2024-01-01"; status = "PLANEJADA" } | ConvertTo-Json -Compress
POST "/missoes" $b
if ($script:STATUS -in 401,403) { safe "Sem token -> $($script:STATUS)" }
else { vuln "Sem token retornou $($script:STATUS) (esperado 401 ou 403)" }

POST "/missoes" $b "token-completamente-invalido"
if ($script:STATUS -in 401,403) { safe "Token invalido -> $($script:STATUS)" }
else { vuln "Token invalido retornou $($script:STATUS) (esperado 401 ou 403)" }

POST "/missoes" $b ""
if ($script:STATUS -in 401,403) { safe "Token em branco -> $($script:STATUS)" }
else { vuln "Token em branco retornou $($script:STATUS) (esperado 401 ou 403)" }

POST "/missoes" $b "null"
if ($script:STATUS -in 401,403) { safe "Token literal 'null' -> $($script:STATUS)" }
else { vuln "Token 'null' retornou $($script:STATUS) (esperado 401 ou 403)" }

# Brute force / rate limiting
section "4. Brute force no login -- rate limiting"
$got429 = $false
for ($i = 1; $i -le 6; $i++) {
    POST "/auth/login" (@{ login = $DONO_LOGIN; senha = "senhaErradaTeste$i" } | ConvertTo-Json -Compress)
    if ($script:STATUS -eq 429) { $got429 = $true; break }
}
if ($got429) { safe "Rate limiting ativo: 429 recebido apos tentativas repetidas" }
else         { vuln "Sem rate limiting: 6 tentativas consecutivas sem bloqueio ou throttling" }

# Actuator
section "5. Actuator -- endpoints sensiveis"
@("/actuator/env","/actuator/beans","/actuator/metrics","/actuator/info",
  "/actuator/loggers","/actuator/mappings","/actuator/heapdump","/actuator/shutdown") | ForEach-Object {
    GET $_
    if ($script:STATUS -eq 200) { vuln "Actuator exposto: $_ (200)" }
    else                        { safe "Actuator bloqueado: $_ ($($script:STATUS))" }
}

# H2 console
section "6. Console H2"
GET "/h2-console"
if ($script:STATUS -eq 200) { vuln "/h2-console acessivel sem autenticacao -- expoe acesso direto ao banco" }
else                        { safe "/h2-console nao acessivel em producao ($($script:STATUS))" }

# Swagger (informativo)
section "7. Swagger UI -- exposicao de documentacao"
GET "/swagger-ui/index.html"
if ($script:STATUS -eq 200) { inf "Swagger UI publico em /swagger-ui/index.html -- desabilitar em producao" }
else                        { safe "Swagger UI nao acessivel ($($script:STATUS))" }

GET "/v3/api-docs"
if ($script:STATUS -eq 200) { inf "OpenAPI spec publico em /v3/api-docs -- desabilitar em producao" }
else                        { safe "OpenAPI spec nao acessivel ($($script:STATUS))" }

# IDOR
section "8. IDOR -- acesso cruzado entre missoes"
GET "/missoes/$MISSAO_PRIV_ID" $TOKEN_ATAC
if ($script:STATUS -in 403,404) { safe "GET missao privada (nao membro) -> $($script:STATUS)" }
else { vuln "IDOR: atacante leu missao privada com status $($script:STATUS)" }

PUT "/missoes/$MISSAO_PRIV_ID" (@{ nome = "Missao Comprometida"; senhaMissao = "hack123456"; dataLancamento = "2024-01-01"; status = "ATIVA" } | ConvertTo-Json -Compress) $TOKEN_ATAC
if ($script:STATUS -in 403,404) { safe "PUT missao privada (nao membro) -> $($script:STATUS)" }
else { vuln "IDOR: atacante alterou missao privada com status $($script:STATUS)" }

# Privilege escalation
section "9. Escalada de privilegio vertical (MEMBRO tentando acoes de DONO)"
DELETE "/satelites/$SAT_ID" $TOKEN_ATAC
if ($script:STATUS -in 401,403) { safe "DELETE satelite (MEMBRO) -> $($script:STATUS)" }
else { vuln "Escalada: MEMBRO deletou satelite com status $($script:STATUS)" }

DELETE "/missoes/$MISSAO_ID" $TOKEN_ATAC
if ($script:STATUS -in 401,403) { safe "DELETE missao (MEMBRO) -> $($script:STATUS)" }
else { vuln "Escalada: MEMBRO deletou missao com status $($script:STATUS)" }

GET "/missoes/$MISSAO_ID/membros" $TOKEN_ATAC
$membros = (j).content
$donoEntry = $membros | Where-Object { $_.role -eq "DONO" } | Select-Object -First 1
if ($donoEntry) {
    $donoId = if ($donoEntry.operadorId) { $donoEntry.operadorId } elseif ($donoEntry.id) { $donoEntry.id } else { $null }
    if ($donoId) {
        do_request "PATCH" "/missoes/$MISSAO_ID/membros/$donoId" (@{ role = "MEMBRO" } | ConvertTo-Json -Compress) $TOKEN_ATAC
        if ($script:STATUS -in 401,403) { safe "PATCH membro (MEMBRO rebaixando DONO) -> $($script:STATUS)" }
        else { vuln "Escalada: MEMBRO executou PATCH em membro com status $($script:STATUS)" }
    } else {
        inf "PATCH membros: nao foi possivel determinar o ID do DONO na resposta -- teste pulado"
    }
}

# JWT -- segredo dev hardcoded
section "10. JWT -- forjamento com segredo dev hardcoded"
$DEV_SECRET = "satmonitor-dev-secret-local-2024"
$forged     = Build-JWT $DONO_LOGIN $DEV_SECRET
GET "/missoes" $forged
if ($script:STATUS -eq 200) {
    vuln "Token forjado aceito: segredo '$DEV_SECRET' valido -- qualquer um com acesso ao repositorio pode se autenticar como qualquer usuario"
} else {
    safe "Token forjado rejeitado (segredo dev nao valido no ambiente atual) -> $($script:STATUS)"
}

# JWT -- algoritmo none
section "11. JWT -- algoritmo none (sem assinatura)"
$noneToken = Build-JWT-None $DONO_LOGIN
GET "/missoes" $noneToken
if ($script:STATUS -eq 200) { vuln "alg:none aceito: token sem assinatura autenticou com sucesso" }
else                        { safe "alg:none rejeitado ($($script:STATUS))" }

# Vazamento de informacao em erros
section "12. Vazamento de informacao em respostas de erro"
do_request "POST" "/auth/login" "{json malformado sem fechar"
if ($script:BODY -match 'at br\.com\.fiap|\.java:|NullPointer|StackTrace|Caused by') {
    vuln "Stack trace Java vazado na resposta para JSON malformado"
} else {
    safe "Resposta de erro nao expoe stack trace ou caminhos internos (JSON malformado)"
}

do_request "POST" "/agencias" '{"siglaPais":"BR"}' $TOKEN_DONO
if ($script:BODY -match 'at br\.com\.fiap|\.java:|NullPointer|StackTrace|Caused by') {
    vuln "Stack trace Java vazado na resposta de validacao (campo obrigatorio ausente)"
} else {
    safe "Resposta de validacao nao expoe info interna"
}

# Fuzzing de input
section "13. Fuzzing de input"
$b = @{ nome = ("A" * 5000); siglaPais = "BR" } | ConvertTo-Json -Compress
POST "/agencias" $b $TOKEN_DONO
if ($script:STATUS -eq 201) {
    vuln "Campo 'nome' aceita string de 5000 chars sem restricao de tamanho (risco de abuse de armazenamento)"
} elseif ($script:STATUS -eq 500) {
    vuln "String de 5000 chars causa erro interno 500 -- campo sem @Size gera crash em vez de 400 (ausencia de validacao)"
} else {
    safe "String de 5000 chars bloqueada corretamente -> $($script:STATUS)"
}

$b = @{ nome = "<script>alert(1)</script>"; siglaPais = "BR" } | ConvertTo-Json -Compress
POST "/agencias" $b $TOKEN_DONO
if ($script:STATUS -eq 201) {
    inf "XSS payload armazenado sem sanitizacao (informativo: risco real depende do frontend consumidor da API)"
} else {
    safe "XSS payload bloqueado -> $($script:STATUS)"
}

# Mass assignment
section "14. Mass assignment -- campos extras na requisicao"
$b = @{ login = "sec-mass@sec.dev"; senha = "senhaMass1234"; nome = "Mass Test"; role = "ADMIN"; id = 1; admin = $true } | ConvertTo-Json -Compress
POST "/auth/registrar" $b
$r = j
if (($r.PSObject.Properties.Name -contains "role") -or ($r.PSObject.Properties.Name -contains "admin")) {
    vuln "Mass assignment: campos extras refletidos na resposta (role ou admin presentes)"
} else {
    safe "Mass assignment bloqueado: campos extras ignorados pelo deserializador"
}

# ── resumo ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "======================================================" -ForegroundColor Magenta
Write-Host "  Total de verificacoes : $($script:TOTAL_N)" -ForegroundColor White
Write-Host "  SAFE                  : $($script:SAFE_N)" -ForegroundColor Green
Write-Host "  VULN                  : $($script:VULN_N)" -ForegroundColor Red
Write-Host "  INFO                  : $($script:INFO_N)" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Magenta

if ($script:VULN_N -gt 0) {
    Write-Host ""
    Write-Host "Achados [VULN] -- corrija antes de ir para producao:" -ForegroundColor Red
    $n = 1
    foreach ($v in $script:VULN_LIST) {
        Write-Host "  $n. $v" -ForegroundColor Red
        $n++
    }
    Write-Host ""
    exit 1
} else {
    Write-Host ""
    Write-Host "  Nenhuma vulnerabilidade encontrada." -ForegroundColor Green
    Write-Host ""
    exit 0
}
