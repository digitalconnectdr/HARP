$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Out = Join-Path $Root "build\pure-java-selftest"

if (Test-Path $Out) {
    Remove-Item -Recurse -Force $Out
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$Stage0 = Join-Path $Root "app\src\main\java\org\harp\l2\Stage0Protocol.java"
$Socks = Join-Path $Root "app\src\main\java\org\harp\l2\MiniSocks5.java"
$SelfTest = Join-Path $Root "tools\Stage01PureJavaSelfTest.java"

& javac -d $Out $Stage0 $Socks $SelfTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp $Out org.harp.l2.Stage01PureJavaSelfTest
exit $LASTEXITCODE
