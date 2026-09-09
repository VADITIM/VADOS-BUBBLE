$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& "$PSScriptRoot\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Host "`nBUILD FAILED" -ForegroundColor Red; pause; exit $LASTEXITCODE }

& $adb install -r "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "`nINSTALL FAILED" -ForegroundColor Red; pause; exit $LASTEXITCODE }

Write-Host "`nBUILD + INSTALL SUCCEEDED" -ForegroundColor Green
pause
