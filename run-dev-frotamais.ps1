# Sobe a API no perfil dev pelo Windows (PowerShell), contra o banco local
# frotamais_dev_local. Equivalente ao run-dev-frotamais.sh, sem Bash.
#
# Uso:
#   .\run-dev-frotamais.ps1                 # captura local de e-mails (padrao)
#   .\run-dev-frotamais.ps1 -Email smtp     # SMTP real (.frotamais-smtp.env)
#   .\run-dev-frotamais.ps1 -Email nenhum   # sem e-mail: convites respondem 503
#   .\run-dev-frotamais.ps1 -Banco frotamais_outro_ensaio
#
# Le .frotamais-jwt.env e, no modo smtp, .frotamais-smtp.env (ambos ignorados
# pelo Git). Nenhum valor e impresso: so os nomes das variaveis carregadas.
# As variaveis valem so para este processo e o Maven filho.
# Se a execucao de scripts estiver bloqueada:
#   powershell -ExecutionPolicy Bypass -File .\run-dev-frotamais.ps1
[CmdletBinding()]
param(
    [ValidateSet('captura', 'smtp', 'nenhum')]
    [string]$Email = 'captura',
    [ValidatePattern('^[a-z][a-z0-9_]{2,62}$')]
    [string]$Banco = 'frotamais_dev_local'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Falhar([string]$mensagem) {
    Write-Host "ERRO: $mensagem" -ForegroundColor Red
    exit 1
}

# KEY=VALOR por linha; ignora comentarios e linhas vazias. Valores vazios nao
# sobrescrevem nada (ficam como pendencia).
function Carregar([string]$arquivo) {
    $nomes = @()
    foreach ($linha in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot $arquivo))) {
        $l = $linha.Trim()
        if ($l -eq '' -or $l.StartsWith('#')) { continue }
        $i = $l.IndexOf('=')
        if ($i -lt 1) { continue }
        $nome = $l.Substring(0, $i).Trim()
        if ($nome.StartsWith('export ')) { $nome = $nome.Substring(7).Trim() }
        $valor = $l.Substring($i + 1).Trim()
        if ($valor.Length -ge 2 -and (($valor[0] -eq '"' -and $valor[-1] -eq '"') -or ($valor[0] -eq "'" -and $valor[-1] -eq "'"))) {
            $valor = $valor.Substring(1, $valor.Length - 2)
        }
        if ($valor -eq '') { continue }
        Set-Item -Path "Env:$nome" -Value $valor
        $nomes += $nome
    }
    Write-Host ("Carregado de {0}: {1}" -f $arquivo, ($nomes -join ', '))
}

function Exigir([string[]]$nomes, [string]$origem) {
    $faltando = $nomes | Where-Object { -not [Environment]::GetEnvironmentVariable($_, 'Process') }
    if ($faltando) { Falhar ("faltam em {0}: {1}" -f $origem, ($faltando -join ', ')) }
}

# Banco: sempre explicito e local. Nunca o de testes.
if ($Banco -like '*test*') { Falhar "o banco '$Banco' e reservado a suite automatizada." }
if ($env:DB_URL) { Write-Host "AVISO: DB_URL da sessao ignorada; usando '$Banco'." -ForegroundColor Yellow }
$env:DB_URL = "jdbc:postgresql://localhost:5432/$Banco"
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
if (Test-Path $psql) {
    if (-not $env:PGPASSWORD -and -not $env:DB_PASSWORD) { $env:PGPASSWORD = 'postgres' }
    elseif ($env:DB_PASSWORD) { $env:PGPASSWORD = $env:DB_PASSWORD }
    $existe = & $psql -h localhost -U $(if ($env:DB_USER) { $env:DB_USER } else { 'postgres' }) -d postgres -Atc "select 1 from pg_database where datname = '$Banco'" 2>$null
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if ($existe -ne '1') { Falhar "o banco '$Banco' nao existe ou o PostgreSQL nao respondeu. Crie-o vazio: psql -h localhost -U postgres -c `"CREATE DATABASE $Banco`"" }
}

# Uma unica instancia da API.
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Falhar 'a porta 8080 ja esta em uso. Mantenha uma unica instancia da API.'
}

# JWT local.
if (-not (Test-Path '.frotamais-jwt.env')) { Falhar '.frotamais-jwt.env nao encontrado (JWT_SECRET local).' }
Carregar '.frotamais-jwt.env'
Exigir @('JWT_SECRET') '.frotamais-jwt.env'

# E-mail: SMTP real e captura local nunca juntos (a API recusa subir assim).
$variaveisSmtp = @('SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD',
    'EMAIL_REMETENTE', 'CONVITE_URL_ATIVACAO')
Get-ChildItem Env: | Where-Object { $_.Name -like 'SPRING_MAIL_*' -or $_.Name -eq 'EMAIL_REMETENTE' } |
    ForEach-Object { Remove-Item "Env:$($_.Name)" }
Remove-Item Env:EMAIL_CAPTURA_LOCAL -ErrorAction SilentlyContinue
switch ($Email) {
    'smtp' {
        if (-not (Test-Path '.frotamais-smtp.env')) { Falhar '.frotamais-smtp.env nao encontrado.' }
        Carregar '.frotamais-smtp.env'
        Exigir $variaveisSmtp '.frotamais-smtp.env'
        if ($env:CONVITE_URL_ATIVACAO -notmatch '^https://[^/]+/ativar-conta$') {
            Falhar 'CONVITE_URL_ATIVACAO deve ser a tela publica https://<front>/ativar-conta.'
        }
        Write-Host 'E-mail: SMTP REAL. Convites vao para destinatarios de verdade.' -ForegroundColor Yellow
    }
    'captura' {
        $env:EMAIL_CAPTURA_LOCAL = 'true'
        if (-not $env:CONVITE_URL_ATIVACAO) { $env:CONVITE_URL_ATIVACAO = 'http://localhost:4200/ativar-conta' }
        Write-Host 'E-mail: captura local em contexto-local/emails-dev (nada e enviado; so destinatarios ficticios).'
    }
    'nenhum' { Write-Host 'E-mail: desligado (convites respondem 503).' }
}

$env:SPRING_PROFILES_ACTIVE = 'dev'
Write-Host "Subindo a API no perfil dev, banco '$Banco', porta 8080 (Ctrl+C para parar)..."
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
