[CmdletBinding()]
param(
  [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env"),
  [ValidateSet("run", "package", "test")]
  [string]$Action = "run"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
  throw "Environment file not found: $EnvFile"
}

foreach ($line in Get-Content -LiteralPath $EnvFile) {
  $trimmed = $line.Trim()
  if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
  $parts = $trimmed.Split("=", 2)
  if ($parts.Count -ne 2) { continue }
  $name = $parts[0].Trim()
  $value = $parts[1].Trim()
  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }
  [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

foreach ($required in @("PG_HOST", "PG_PORT", "PG_DB", "PG_USER", "PG_PASSWORD", "S3_ENDPOINT", "S3_ACCESS_KEY", "S3_SECRET_KEY", "DUCKLAKE_DATA_PATH")) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required, "Process"))) {
    throw "Missing required environment variable: $required"
  }
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $projectRoot
try {
  switch ($Action) {
    "run" { & mvn spring-boot:run }
    "package" { & mvn clean package }
    "test" { & mvn test }
  }
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
