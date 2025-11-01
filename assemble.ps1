# assemble.ps1 - Builds the debug APK

Write-Host "`n🛠️ Assembling Debug APK..."

& .\gradlew assembleDebug --stacktrace

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Assemble succeeded. APK is in app\build\outputs\apk\debug"
} else {
    Write-Host "`n[ERROR] Assemble failed. Check the above output for errors."
    exit $LASTEXITCODE
}

Read-Host "`nPress ENTER to close"
