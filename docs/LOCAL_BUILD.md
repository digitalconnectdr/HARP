# HARP Stage-0/1 — build local sin GitHub Actions

Este documento existe para compilar el PoC sin consumir minutos de GitHub Actions.

## Requisitos

- Android Studio con soporte para Android API 36.
- Android SDK Platform 36.
- Android SDK Build-Tools 36.0.0.
- JDK 21 o el JDK compatible incluido con una versión actual de Android Studio.
- Gradle 9.4.1 si se compila desde línea de comandos sin wrapper.

El proyecto Stage-0/1 es Java puro. El módulo Android declara `enableKotlin = false`, por lo que no requiere Kotlin para esta prueba.

## Android Studio

1. Clonar o descargar `digitalconnectdr/HARP`.
2. Abrir la carpeta raíz del repositorio en Android Studio.
3. Permitir que Android Studio instale SDK Platform 36 / Build-Tools 36.0.0 si faltan.
4. Esperar a que finalice Gradle Sync.
5. Seleccionar el variant `debug`.
6. Ejecutar **Build > Build APK(s)**.

APK esperado:

```
app/build/outputs/apk/debug/app-debug.apk
```

Application ID debug:

```
org.harp.l2.debug
```

## Línea de comandos

Con Android SDK configurado en `ANDROID_HOME` o `ANDROID_SDK_ROOT`:

```bash
gradle --no-daemon clean :app:assembleDebug
```

Verificar el APK:

```bash
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

En Windows PowerShell:

```powershell
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

## Qué NO prueba el build

Que el APK compile sólo prueba compatibilidad de source/resources/AGP.

La prueba física requiere dos Android con Wi-Fi Aware:

- B: modo RELAY y una red Internet VALIDATED.
- A: modo CLIENTE, sin Internet propio para la prueba decisiva.
- Resultado esperado: `PASS_STAGE0` y luego `PASS_STAGE1`.

No activar GitHub Actions mientras la cuota esté restringida.
