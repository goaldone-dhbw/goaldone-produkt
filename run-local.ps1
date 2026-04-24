# Bricht das Skript bei Fehlern ab (Äquivalent zu 'set -e')
$ErrorActionPreference = "Stop"

# Check if mprocs is installed
if (-not (Get-Command "mprocs" -ErrorAction SilentlyContinue))
{
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "Error: 'mprocs' is required to run the development setup." -ForegroundColor Red
    Write-Host "Please install it. Unter Windows z.B. mit Scoop oder Cargo:" -ForegroundColor Red
    Write-Host ""
    Write-Host "    scoop install mprocs"
    Write-Host "    # ODER"
    Write-Host "    cargo install mprocs"
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Red
    exit 1
}

# 1. Source the .env file if it exists
if (Test-Path ".env")
{
    Write-Host "Loading environment variables from .env file..." -ForegroundColor Cyan

    Get-Content ".env" | Where-Object { $_ -notmatch '^\s*$' -and $_ -notmatch '^\s*#' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        $name = $name.Trim()
        $value = $value.Trim()

        if ($value -match '^"(.*)"$' -or $value -match "^'(.*)'$")
        {
            $value = $matches[1]
        }

        if ($name)
        {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}
else
{
    Write-Host "Warning: .env file not found. Ensure required Zitadel variables are set." -ForegroundColor Yellow
    Start-Sleep -Seconds 2
}

Write-Host "Generate env.js for frontend..." -ForegroundColor Cyan
Push-Location frontend
try
{
    npm run setup-env

    Write-Host "Cleaning up old generated API sources..." -ForegroundColor Cyan
    $apiPath = "src/app/api"
    if (Test-Path $apiPath)
    {
        # Entspricht 'rm -r'
        Remove-Item -Recurse -Force $apiPath
    }

    Write-Host "Generating frontend API sources..." -ForegroundColor Cyan
    npm run generate-api
}
finally
{
    Pop-Location
}

# 2. Run code generation sequentially first
Write-Host "Generating backend sources..." -ForegroundColor Cyan
Push-Location backend
try
{
    .\mvnw.cmd clean generate-sources
}
finally
{
    Pop-Location
}

# 3. Launch mprocs TUI
Write-Host "Starting mprocs for backend and frontend..." -ForegroundColor Cyan
# mprocs will automatically load mprocs.yaml from the current directory
mprocs