param()

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$gh = "D:\GitHubCLI\gh.exe"
$tag = "v3.3.0"
$apk = Join-Path $repo "releases\AI-Classroom-3.3.0.apk"
$notes = Join-Path $repo "release-notes-3.3.0.md"

Set-Location $repo
if (-not (Test-Path -LiteralPath $gh)) { throw "GitHub CLI not found: $gh" }
if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
if (-not (Test-Path -LiteralPath $notes)) { throw "Release notes not found: $notes" }

& $gh auth status
if ($LASTEXITCODE -ne 0) { throw "GitHub CLI is not authenticated." }

git add README.md app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/aiclassroom/app/MainActivity.kt release-notes-3.1.0.md release-notes-3.2.0.md release-notes-3.3.0.md scripts/Verify-ReleaseSigning.ps1 scripts/Publish-3.3.0.ps1
if ($LASTEXITCODE -ne 0) { throw "git add failed." }

git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m "Release AI Classroom 3.3.0"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed." }
}

git push origin main
if ($LASTEXITCODE -ne 0) { throw "git push failed." }

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $gh release view $tag --json tagName 2>$null | Out-Null
$releaseExists = $LASTEXITCODE -eq 0
$ErrorActionPreference = $previousErrorActionPreference

if ($releaseExists) {
    & $gh release upload $tag $apk --clobber
    & $gh release edit $tag --title "AI Classroom 3.3.0" --notes-file $notes
} else {
    & $gh release create $tag $apk --title "AI Classroom 3.3.0" --notes-file $notes --target main
}
if ($LASTEXITCODE -ne 0) { throw "GitHub Release publishing failed." }

Write-Host "Published $tag successfully." -ForegroundColor Green
