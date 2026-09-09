# Check if .env exists
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $val = $line -split "=", 2
            [System.Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim().Trim('"').Trim("'"), "Process")
        }
    }
} else {
    Write-Host "Warning: .env file not found in backend directory. Using application.properties defaults." -ForegroundColor Yellow
}

Write-Host "Starting Spring Boot backend on http://localhost:8080..." -ForegroundColor Green
./mvnw spring-boot:run
