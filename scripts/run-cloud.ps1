# Runs the Hardware ERP backend against Supabase (CR-059, CLOUD mode).
#
#   cp .env.cloud.example .env.cloud     # then fill it in
#   .\scripts\run-cloud.ps1
#
# This script exists because Spring Boot does NOT read .env files - there is
# no dotenv library in this project. Without it you would have to export a
# dozen variables by hand every time, and a single typo in DB_HOST produces a
# connection error that looks nothing like the actual mistake.
#
# PowerShell is the primary shell on this machine; scripts/run-cloud.sh is the
# Git Bash equivalent. Neither is used when deploying to Render - there the
# same keys are set in the dashboard (see render.yaml).

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvFile  = Join-Path $RepoRoot '.env.cloud'

if (-not (Test-Path $EnvFile)) {
    Write-Host "ERROR: .env.cloud not found at $EnvFile" -ForegroundColor Red
    Write-Host ""
    Write-Host "Create it from the template and fill in your Supabase details:"
    Write-Host "    cp .env.cloud.example .env.cloud"
    exit 1
}

# Parse KEY=VALUE, ignoring blanks and # comments. Trailing inline comments are
# stripped only for unquoted values, so a '#' inside a password is preserved
# when the value is quoted - passwords genuinely do contain them.
$loaded = @{}
foreach ($line in Get-Content $EnvFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }

    $split = $trimmed.IndexOf('=')
    if ($split -lt 1) { continue }

    $key   = $trimmed.Substring(0, $split).Trim()
    $value = $trimmed.Substring($split + 1).Trim()

    if ($value.StartsWith('"') -and $value.EndsWith('"') -and $value.Length -ge 2) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    elseif ($value.StartsWith("'") -and $value.EndsWith("'") -and $value.Length -ge 2) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    elseif ($value.StartsWith('#')) {
        # "KEY=            # 10 digits, no +91" - the key was left blank and what
        # follows is explanation, not a value. Without this the literal text
        # "# 10 digits, no +91" gets exported as APP_BOOTSTRAP_MOBILE, the
        # required-value check below sees a non-empty string and passes, and the
        # app starts with nonsense in it. Bash's `source` gets this right for
        # free; this parser has to be told.
        $value = ''
    }
    else {
        $hash = $value.IndexOf(' #')
        if ($hash -ge 0) { $value = $value.Substring(0, $hash).TrimEnd() }
    }

    if ($value -ne '') {
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        $loaded[$key] = $value
    }
}

# Fail here rather than letting Spring fail on an unresolvable placeholder -
# DB_HOST has no default in application-cloud.yml, and the resulting error
# names a property, not the thing you actually forgot to fill in.
$required = @('DB_HOST', 'DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'PLATFORM_ADMIN_JWT_SECRET')
$missing  = $required | Where-Object { -not $loaded.ContainsKey($_) }

if ($missing.Count -gt 0) {
    Write-Host "ERROR: .env.cloud is missing required values:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "    $_" }
    Write-Host ""
    Write-Host "Generate the two secrets (they must be different values):"
    Write-Host "    .\scripts\new-secret.ps1 -Count 2     (PowerShell)"
    Write-Host "    openssl rand -base64 32               (Git Bash, run it twice)"
    exit 1
}

if ($loaded['JWT_SECRET'] -eq $loaded['PLATFORM_ADMIN_JWT_SECRET']) {
    Write-Host "ERROR: JWT_SECRET and PLATFORM_ADMIN_JWT_SECRET are the same value." -ForegroundColor Red
    Write-Host "The Platform Admin Console is a separate trust boundary from the shop app"
    Write-Host "(CR-054). One shared key lets a token from either side be replayed at the"
    Write-Host "other. Generate a second, different value:"
    Write-Host "    .\scripts\new-secret.ps1"
    exit 1
}

# 6543 is Supabase's transaction pooler. It breaks server-side prepared
# statements and the SELECT ... FOR UPDATE that DocumentSequenceService holds
# for the caller's transaction (CR-041). The failure is intermittent and
# baffling, so refuse it outright rather than let it be debugged later.
if ($loaded['DB_PORT'] -eq '6543') {
    Write-Host "ERROR: DB_PORT is 6543 - that is Supabase's TRANSACTION pooler." -ForegroundColor Red
    Write-Host "Use the SESSION pooler on port 5432 instead (or just remove DB_PORT;"
    Write-Host "5432 is the default). See CR-041 for why this matters."
    exit 1
}

$env:SPRING_PROFILES_ACTIVE = 'prod,cloud'

Write-Host ""
Write-Host "Starting Hardware ERP against Supabase" -ForegroundColor Cyan
Write-Host "  profiles : prod,cloud"
Write-Host "  database : $($loaded['DB_HOST'])"
Write-Host "  frontend : run 'npm run dev' in frontend/ in a second terminal"
Write-Host ""
Write-Host "The startup banner will confirm the mode; DeploymentModeGuard refuses"
Write-Host "to start on a combination that cannot be right."
Write-Host ""

Push-Location (Join-Path $RepoRoot 'backend')
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
