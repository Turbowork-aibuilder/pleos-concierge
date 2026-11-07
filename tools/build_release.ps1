param([switch]$CreateKey)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$signingDir = Join-Path $projectRoot '.signing'
$keyPath = Join-Path $signingDir 'ecobuilder-release.jks'
$passwordPath = Join-Path $signingDir 'password.dpapi'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17+ installation first.' }
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
try {
    if ($CreateKey) {
        if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $passwordPath)) {
            throw 'Signing material already exists; omit -CreateKey. No files were overwritten.'
        }
        New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
        $randomBytes = New-Object byte[] 48
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($randomBytes)
        $rng.Dispose()
        $env:PLEOS_STORE_PASSWORD = [Convert]::ToBase64String($randomBytes)
        $env:PLEOS_KEY_PASSWORD = $env:PLEOS_STORE_PASSWORD
        $securePassword = ConvertTo-SecureString $env:PLEOS_STORE_PASSWORD -AsPlainText -Force
        $securePassword | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordPath -Encoding ASCII
        & $keytool -genkeypair -keystore $keyPath -storetype JKS -alias ecobuilder -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Ecobuilder,OU=Pleos Concierge,O=Ecobuilder,C=KR' -storepass:env PLEOS_STORE_PASSWORD -keypass:env PLEOS_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Inspect the local signing directory before retrying.' }
    } else {
        if (!(Test-Path -LiteralPath $keyPath) -or !(Test-Path -LiteralPath $passwordPath)) { throw 'Signing material missing. Use -CreateKey only for a new identity.' }
        $securePassword = Get-Content -LiteralPath $passwordPath | ConvertTo-SecureString
        $credential = New-Object System.Management.Automation.PSCredential('release', $securePassword)
        $env:PLEOS_STORE_PASSWORD = $credential.GetNetworkCredential().Password
        $env:PLEOS_KEY_PASSWORD = $env:PLEOS_STORE_PASSWORD
    }
    $env:PLEOS_KEYSTORE = $keyPath
    Push-Location $projectRoot
    try {
        & .\gradlew.bat :app:assembleDemoRelease :app:assembleVehicleRelease --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:PLEOS_STORE_PASSWORD, Env:PLEOS_KEY_PASSWORD, Env:PLEOS_KEYSTORE -ErrorAction SilentlyContinue
    $securePassword = $null
    $credential = $null
}
