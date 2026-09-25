param(
    [string]$BuildTools = "C:\Users\lenovo\AppData\Local\Android\Sdk\build-tools\36.1.0",
    [string]$ReleaseDir = "$PSScriptRoot\..\releases"
)

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\AndroidStadio\jbr" }
$apksigner = Join-Path $BuildTools "apksigner.bat"
if (-not (Test-Path -LiteralPath $apksigner)) { throw "apksigner not found: $apksigner" }

$expected = "07c9af654dc91a27dc222829d5af598d23e364ff242d887da414789d3a2c3a12"
$apks = Get-ChildItem -LiteralPath $ReleaseDir -Filter "AI-Classroom-3*.apk" | Sort-Object Name
if (-not $apks) { throw "No 3.x APKs found in $ReleaseDir" }

foreach ($apk in $apks) {
    $out = (& $apksigner verify --verbose --print-certs $apk.FullName 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $out -notmatch "Verifies") { throw "Signature verification failed: $($apk.Name)" }
    if ($out -notmatch "Verified using v2 scheme .*true") { throw "Missing v2 signature: $($apk.Name)" }
    if ($out -notmatch "Verified using v3 scheme .*true") { throw "Missing v3 signature: $($apk.Name)" }
    if ($out -notmatch "certificate SHA-256 digest: $expected") { throw "Unexpected certificate: $($apk.Name)" }
    Write-Host "OK $($apk.Name) ($expected)"
}
