# HARP

HARP investiga cómo permitir que un teléfono A obtenga conectividad útil sin mantener una suscripción convencional de datos móviles activa, reutilizando de forma autorizada capacidad de conectividad ya disponible alrededor del dispositivo.

## Estado actual

Estado consolidado y gates de continuación: [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md).

La rama principal contiene el PoC **HARP L2 Stage-0/1/2 relay preflight**:

- **Stage-0:** Wi-Fi Aware A↔B con nonce aleatorio de 128 bits y validación PING/PONG.
- **Stage-1:** A usa un SOCKS5 restringido sobre Wi-Fi Aware; B selecciona una red Internet `VALIDATED`, prefiere `NOT_METERED`, resuelve DNS usando esa misma `Network` y conecta usando su `SocketFactory`.
- **Stage-2 relay preflight:** después de Stage-1, B genera credenciales SOCKS efímeras, las entrega dentro del NDP Wi-Fi Aware cifrado y arranca un relay persistente autenticado. A vuelve a alcanzar HTTPS usando esas credenciales.
- TLS permanece end-to-end entre A y el destino; HARP no hace MITM.

Resultado esperado en el primer APK físico:

```
PASS_STAGE0
PASS_STAGE1
PASS_STAGE2_RELAY
```

En B también debe aparecer:

```
PASS_STAGE2_RELAY_READY
```

Este gate todavía **no** crea Internet transparente para Chrome u otras apps. Eso corresponde al siguiente paso con `VpnService + HEV tun2socks + ProtectedAwareBridge`.

## GitHub Actions

**Desactivado.**

No existe ningún workflow activo bajo `.github/workflows/`. El workflow antiguo está archivado sólo como referencia en:

```
docs/ci/android-debug.workflow.yml.disabled
```

No reactivar mientras la cuota de Actions esté restringida.

## Validación sin Android SDK

Con JDK 21:

Linux/macOS:

```bash
./tools/run_pure_java_selftest.sh
```

Windows PowerShell:

```powershell
.\tools\run_pure_java_selftest.ps1
```

Los tests cubren Stage-0, SOCKS5/políticas, credenciales Stage-2, relay persistente y el contrato JNI que HARP espera del AAR HEV.

## Build Android local

Ver [docs/LOCAL_BUILD.md](docs/LOCAL_BUILD.md).

## Prueba física

Ver [docs/PHONE_TEST_RUNBOOK.md](docs/PHONE_TEST_RUNBOOK.md).

## Stage-2A VPN preparado en source

El repositorio ya contiene:

- `Stage2SessionCredentials`: credenciales SOCKS efímeras.
- `Stage2ControlProtocol`: intercambio de credenciales dentro del NDP cifrado.
- `Stage2TunnelConfig`: YAML para HEV tun2socks + mapdns.
- `ProtectedAwareBridge`: secuencia `protect -> bindSocket -> connect`.
- `Stage2RelayServer`: relay TCP persistente y autenticado en B.
- `SocksPolicies.publicWeb()`: sólo 80/443 públicos, bloqueando destinos privados/locales.
- `HevTunnelAdapter`: adapter por reflexión para el AAR oficial HEV sin convertirlo en dependencia obligatoria de Stage-0/1.
- scripts locales para descargar y verificar criptográficamente el AAR HEV.

El AAR HEV es opcional y no se compromete en el repositorio. El siguiente gate, después de validar físicamente el relay preflight, es activar `VpnService` en A para que Chrome/apps normales atraviesen el mismo relay.
