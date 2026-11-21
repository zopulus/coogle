param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if ($Jdk) { $env:JAVA_HOME = $Jdk }
Push-Location $PSScriptRoot
try {
    & ./gradlew.bat :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
} finally { Pop-Location }
Write-Output (Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release.apk')
