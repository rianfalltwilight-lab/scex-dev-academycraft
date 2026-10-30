$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
$env:GRADLE_USER_HOME='C:/Users/Monarch/.gradle'
$env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskRoot=Split-Path $projectRoot -Parent
$runRoot=Join-Path $taskRoot 'runs/final-02'
& ./scripts/generate-build-info.ps1 -GameTestEvidence '286 required passed; Matrix SSID mutation/discovery/NBT; normal saved exit; see evidence/server-result-corrected.json' -ReleaseFixes 'Matrix SSID dirty-field submission and action-scoped public server receipt; real GUI probe compiled but NOT RUN (remote client window occupied)'
& ./gradlew.bat --offline --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' jar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$jar=Get-Item build/libs/AcademyCraft-neoforge-1.21.1-0.0.23-scex-wireless.jar
$hash=(Get-FileHash -LiteralPath $jar.FullName).Hash
$zip=Join-Path $taskRoot 'AcademyCraft-0.0.23-scex-wireless-source.zip'
& ./scripts/package-source.ps1 -Destination $zip
& ./scripts/verify-source-package.ps1 -ArchivePath $zip
$rebuild=Join-Path $runRoot 'rebuild'
Expand-Archive -LiteralPath $zip -DestinationPath $rebuild
Push-Location $rebuild
try {
  & ./gradlew.bat --offline --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' jar
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  $rebuilt=Get-Item build/libs/AcademyCraft-neoforge-1.21.1-0.0.23-scex-wireless.jar
  if ((Get-FileHash -LiteralPath $rebuilt.FullName).Hash -ne $hash) { throw 'Independent rebuild differs' }
} finally { Pop-Location }
@{passed=$true;rebuild_identical=$true;version='0.0.23-scex-wireless';jar=$jar.FullName;jar_bytes=$jar.Length;jar_sha256=$hash;source_zip=$zip;source_zip_bytes=(Get-Item -LiteralPath $zip).Length;source_zip_sha256=(Get-FileHash -LiteralPath $zip).Hash;gui_verified=$false} | ConvertTo-Json | Set-Content (Join-Path $runRoot 'result.json')
