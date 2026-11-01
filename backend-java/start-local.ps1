param([switch]$Build)
$ErrorActionPreference = 'Stop'

# Parse dotenv assignments as data. Never execute their contents as PowerShell.
function Import-ReadQuestEnvironment {
    param([string]$Path, [string[]]$AllowedKeys)
    if (-not (Test-Path -LiteralPath $Path)) { return }
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { continue }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($AllowedKeys -and $key -notin $AllowedKeys) { continue }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        } else {
            $value = $value -replace '\s+#.*$', ''
        }
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}

Import-ReadQuestEnvironment -Path (Join-Path $PSScriptRoot '.env') -AllowedKeys @(
    'MYSQL_URL', 'MYSQL_USER', 'MYSQL_PASSWORD', 'JWT_SECRET', 'PORT', 'SECURE_COOKIE', 'ALLOWED_ORIGINS',
    'MODEL_NAME','OPENAI_API_KEY','DEEPSEEK_READER_MODEL',
    'SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','SMTP_FROM','SMTP_SSL_ENABLED','SMTP_STARTTLS_ENABLED',
    'MAIL_DELIVERY_MODE','SIMULATED_PAYMENTS_ENABLED','PRO_DAILY_AI_REQUEST_LIMIT','SCENE_DAILY_IMAGE_LIMIT',
    'DEEPSEEK_API_KEY','DEEPSEEK_BASE_URL','DEEPSEEK_CHAT_MODEL','DEEPSEEK_TIMEOUT_MS','SILICONFLOW_API_KEY',
    'EMBEDDING_API_KEY','EMBEDDING_BASE_URL','EMBEDDING_MODEL','GOOGLE_BOOK_API','IMAGE_API_KEY',
    'IMAGE_GENERATION_URL','IMAGE_MODEL','IMAGE_SIZE','IMAGE_DOWNLOAD_HOSTS','MODULE1_EMBEDDING_MODEL','REMOTE_DNS_MODE',
    'REDIS_ENABLED','REDIS_HOST','REDIS_PORT','REDIS_PASSWORD','API_DOCS_ENABLED',
    'MANAGEMENT_PORT','MANAGEMENT_ADDRESS','MANAGEMENT_TOKEN'
)
if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Trim().Length -lt 32) { throw 'Configure JWT_SECRET with at least 32 characters.' }
if (-not $env:MYSQL_PASSWORD) { throw 'Configure MYSQL_PASSWORD in backend-java/.env.' }
if (-not $env:PORT) { $env:PORT = '8080' }
if (-not $env:SECURE_COOKIE) { $env:SECURE_COOKIE = 'false' }
if (-not $env:ALLOWED_ORIGINS) { $env:ALLOWED_ORIGINS = 'http://localhost:5173' }

Push-Location $PSScriptRoot
try {
    if ($Build) {
        & (Join-Path $PSScriptRoot 'build-project.ps1')
    }
    $jar = Join-Path $PSScriptRoot 'target/readquest-backend-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first: ./start-local.ps1 -Build' }
    Write-Host "Starting ReadQuest Java on port $env:PORT. Press Ctrl+C to stop."
    $env:SPRING_PROFILES_ACTIVE='local'
    & java -jar $jar
    if ($LASTEXITCODE -ne 0) { throw 'Java backend stopped with an error.' }
} finally {
    Pop-Location
}
