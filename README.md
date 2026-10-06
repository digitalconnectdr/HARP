# HARP\n\nHARP explora acceso a Internet para un teléfono A sin una suscripción de datos convencional activa, reutilizando de forma autorizada capacidad de conectividad ya disponible alrededor del dispositivo.\n\n## Estado actual\n\nLa rama principal contiene el PoC **HARP L2 Stage-0/1**:\n\n- **Stage-0:** Wi-Fi Aware A↔B con nonce aleatorio de 128 bits y validación PING/PONG.\n- **Stage-1:** A usa SOCKS5 restringido sobre Wi-Fi Aware; B selecciona una red Internet `VALIDATED`, prefiere `NOT_METERED`, resuelve DNS usando esa misma `Network` y conecta usando su `SocketFactory`.\n- TLS permanece end-to-end entre A y `example.com`; HARP no hace MITM.\n\nResultado esperado en dispositivo: `PASS_STAGE0` y `PASS_STAGE1`.\n\n## GitHub Actions\n\n**Desactivado.** No existe ningún workflow activo bajo `.github/workflows/`. El workflow antiguo está archivado sólo como referencia en `docs/ci/android-debug.workflow.yml.disabled`.\n\n## Validación sin Android SDK\n\nCon JDK 21:\n\n- Linux/macOS: `./tools/run_pure_java_selftest.sh`\n- Windows PowerShell: `.\tools\run_pure_java_selftest.ps1`\n\nResultado esperado:\n\n    PASS_STAGE0_10000_NONCES\n    PASS_SOCKS_RELAY\n    PASS_SOCKS_BAD_AUTH_REJECTED\n    PASS_SOCKS_DESTINATION_POLICY\n    PASS_STAGE01_PURE_JAVA_SELFTEST\n\n## Build Android local\n\nVer `docs/LOCAL_BUILD.md`.\n\n## Prueba física\n\nVer `docs/PHONE_TEST_RUNBOOK.md`.\n\nDespués de demostrar Stage-1 en dos teléfonos, Stage-2 será: Apps/Chrome de A -> Android VpnService -> tun2socks -> Wi-Fi Aware -> B -> Internet.\n

## Stage-2A preparado en source

Sin activar todavía el VPN en la UI, el repositorio ya contiene:

- `Stage2SessionCredentials`: credenciales SOCKS efímeras.
- `Stage2ControlProtocol`: intercambio de credenciales dentro del NDP cifrado.
- `Stage2TunnelConfig`: YAML para HEV tun2socks + mapdns.
- `ProtectedAwareBridge`: secuencia `protect -> bindSocket -> connect`.
- `Stage2RelayServer`: relay TCP persistente y autenticado en B.
- `SocksPolicies.publicWeb()`: 80/443 públicos, bloqueando destinos privados/locales.

El AAR HEV no se agrega al build Stage-0/1 hasta tener disponible un build Android local.
