$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
$env:GRADLE_USER_HOME='C:/Users/Monarch/.gradle'
$env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -Xmx2G'
& ./gradlew.bat --offline --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' '-PacceptanceRunRoot=D:/Minecraft-Projects/academycraft-matrix-name-0.0.23-20260908/runs/server-01' test jar runGameTestServer
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$log=Get-Content '../runs/server-01/gametest/logs/latest.log' -Raw
$matrix= $log.Contains('MATRIX_SSID_FLOW_PASS')
@{passed=$matrix;matrix_ssid_flow=$matrix;exit_code=$LASTEXITCODE} | ConvertTo-Json | Set-Content '../runs/server-01/result.json'
