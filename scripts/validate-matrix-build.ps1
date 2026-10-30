$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
$env:GRADLE_USER_HOME='C:/Users/Monarch/.gradle'
$env:JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2'
& ./scripts/generate-build-info.ps1 -ReleaseFixes 'Matrix SSID edited-field submission and server confirmation; validation pending'
& ./gradlew.bat --offline --no-daemon --no-configuration-cache --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2G -XX:ActiveProcessorCount=2' test jar prepareClientRun
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$suites=@(Get-ChildItem build/test-results/test/TEST-*.xml | ForEach-Object { ([xml](Get-Content $_.FullName -Raw)).testsuite })
$count=($suites | Measure-Object -Property tests -Sum).Sum
$fail=($suites | Measure-Object -Property failures -Sum).Sum + ($suites | Measure-Object -Property errors -Sum).Sum
@{passed=($count -gt 0 -and $fail -eq 0);tests=$count;failures=$fail} | ConvertTo-Json | Set-Content '../runs/build-01/result.json'
