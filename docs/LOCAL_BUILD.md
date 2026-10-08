# HARP — build local sin GitHub Actions

Este documento existe para compilar y probar HARP sin consumir GitHub Actions.

## Requisitos

- Android Studio con soporte para Android API 36.
- Android SDK Platform 36.
- Android SDK Build-Tools 36.0.0.
- JDK 21 o el JDK compatible incluido con una versión actual de Android Studio.
- Gradle 9.4.1 si se compila desde línea de comandos sin wrapper.

El módulo Android es Java puro y declara:

```gradle
android {
    enableKotlin = false
}
```

## Build A — relay preflight, sin HEV

Este es el **primer build recomendado**.

No colocar todavía el AAR HEV en `app/libs/`.

El APK debe poder compilar y ejecutar:

```
PASS_STAGE0
PASS_STAGE1
PASS_STAGE2_RELAY
```

En B debe aparecer además:

```
PASS_STAGE2_RELAY_READY
```

Este build no puede arrancar Stage-2A VPN; el botón VPN registrará que el AAR HEV no está incluido.

### Android Studio

1. Clonar o descargar `digitalconnectdr/HARP`.
2. Abrir la carpeta raíz.
3. Instalar SDK Platform 36 / Build-Tools 36.0.0 si Android Studio lo solicita.
4. Esperar Gradle Sync.
5. Seleccionar variant `debug`.
6. Ejecutar **Build > Build APK(s)**.

APK esperado:

```
app/build/outputs/apk/debug/app-debug.apk
```

Application ID debug:

```
org.harp.l2.debug
```

## Build B — Stage-2A VPN con HEV

Sólo después de que el relay preflight haya pasado físicamente.

Descargar y verificar el AAR oficial HEV 2.18.0:

Linux/macOS:

```bash
./tools/fetch_hev_aar.sh
```

Windows PowerShell:

```powershell
.\tools\fetch_hev_aar.ps1
```

Archivo esperado:

```
app/libs/hev-socks5-tunnel.aar
```

SHA-256 esperado:

```
15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab
```

`app/build.gradle` incluye ese AAR automáticamente sólo cuando existe.

Después volver a compilar el mismo variant `debug`.

## Línea de comandos

Con Android SDK configurado:

```bash
gradle --no-daemon clean :app:assembleDebug
```

Verificación Linux/macOS:

```bash
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell:

```powershell
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

## Self-tests Java sin Android SDK

Linux/macOS:

```bash
./tools/run_pure_java_selftest.sh
```

Windows:

```powershell
.\tools\run_pure_java_selftest.ps1
```

Estos tests no sustituyen el build Android, pero validan framing, SOCKS, políticas, credenciales Stage-2, relay persistente, recuperación tras peer rechazado, config HEV y contrato JNI esperado.

## Qué NO prueba un build correcto

Un APK que compila sólo demuestra compatibilidad de source/resources/AGP.

El relay preflight requiere dos teléfonos reales con Wi-Fi Aware.

Stage-2A VPN sólo pasa cuando una app normal/Chrome en A carga HTTPS a través de B mientras A no tiene Internet propio.

## Política CI

No crear ni activar workflows bajo `.github/workflows/` mientras la cuota de GitHub Actions esté restringida.
