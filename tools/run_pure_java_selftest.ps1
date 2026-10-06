$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Out = Join-Path $Root "build\pure-java-selftest"

if (Test-Path $Out) {
    Remove-Item -Recurse -Force $Out
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$Sources = @(
    (Join-Path $Root "app\src\main\java\org\harp\l2\Stage0Protocol.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\SocksDestinationPolicy.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\SocksAddressResolver.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\SocksPolicies.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\MiniSocks5.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\Stage2SessionCredentials.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\Stage2ControlProtocol.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\Stage2TunnelConfig.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\SocketPeerPolicy.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\SocketPeerPolicies.java"),
    (Join-Path $Root "app\src\main\java\org\harp\l2\Stage2RelayServer.java"),
    (Join-Path $Root "tools\Stage01PureJavaSelfTest.java"),
    (Join-Path $Root "tools\Stage2PureJavaSelfTest.java")
)

& javac -Xlint:all -Werror -d $Out $Sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp $Out org.harp.l2.Stage01PureJavaSelfTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp $Out org.harp.l2.Stage2PureJavaSelfTest
exit $LASTEXITCODE
