# HARP Stage-0/1/2 relay preflight — Phone Test Runbook

No ejecutar esta prueba hasta disponer de un APK debug compilado localmente.

## Objetivo

Demostrar tres hechos por separado en una sola sesión:

1. **PASS_STAGE0**: A y B establecen un data path Wi-Fi Aware y una sesión TCP validada por nonce.
2. **PASS_STAGE1**: A alcanza un servidor HTTPS público mediante B usando el SOCKS de laboratorio.
3. **PASS_STAGE2_RELAY**: B genera credenciales efímeras dentro del NDP cifrado, arranca un relay persistente autenticado y A alcanza HTTPS otra vez usando esas credenciales.

Este tercer gate valida la base del relay persistente que después usará `VpnService + HEV tun2socks`. Todavía no hace transparente Internet para Chrome.

## Dispositivos

### A = CLIENTE

- HARP instalado.
- Wi-Fi Aware soportado.
- Para la prueba decisiva: datos móviles OFF y sin Wi-Fi con Internet.

### B = RELAY

- HARP instalado.
- Wi-Fi Aware soportado.
- Al menos una red con `INTERNET + VALIDATED`.
- HARP prioriza una salida `NOT_METERED` cuando exista.

## Preparación

1. Instalar la misma APK en A y B.
2. Abrir HARP en ambos.
3. Conceder **Dispositivos Wi-Fi cercanos**.
4. Mantener ambas apps visibles durante esta primera prueba.
5. En B confirmar que Internet normal funciona antes de iniciar HARP.

## Ejecución

1. En B pulsar **B — RELAY**.
2. Esperar:
   ```
   B: attach OK
   B: publish OK
   ```
3. En A pulsar **A — CLIENTE**.
4. Discovery debe mostrar:
   ```
   A: relay descubierto discovery_ms=...
   A: discovery TX OK ...
   B: discovery RX=HELLO
   ```
5. NDP debe mostrar:
   ```
   B: NDP available ...
   A: NDP available ndp_ms=...
   A: peer=/fe80::...:<port>
   ```
6. Stage-0 debe terminar con:
   ```
   PASS_STAGE0
   ```
7. Stage-1 debe mostrar en B:
   ```
   B: upstream=... transport=... validated=true metered=...
   B: DNS via upstream host=example.com count=...
   ```
   y en A:
   ```
   PASS_STAGE1
   ```
8. Stage-2 control debe mostrar en A:
   ```
   A: Stage2 credentials recibidas userLen=... passLen=...
   ```
   y en B:
   ```
   B: Stage2 control OK
   PASS_STAGE2_RELAY_READY port=...
   ```
9. El relay persistente debe terminar en A con:
   ```
   PASS_STAGE2_RELAY
   ```

## Criterio PASS completo

La sesión sólo pasa si:

- A no tiene Internet propio durante la prueba decisiva.
- B sí tiene una red Internet validada.
- aparece `PASS_STAGE0`.
- aparece `PASS_STAGE1`.
- aparece `PASS_STAGE2_RELAY_READY` en B.
- aparece `PASS_STAGE2_RELAY` en A.
- no aparece `NDP_TIMEOUT`, `FAIL_STAGE0`, `FAIL_STAGE1`, `FAIL_STAGE2_CONTROL` ni `FAIL_STAGE2_RELAY`.

## Qué demuestra cada gate

`PASS_STAGE0`:

```
A <-> Wi-Fi Aware <-> B
```

`PASS_STAGE1`:

```
A
 -> Wi-Fi Aware
 -> B
 -> DNS sobre la Network elegida de B
 -> TCP sobre la misma Network
 -> TLS a example.com
```

`PASS_STAGE2_RELAY` añade:

```
NDP cifrado
 -> credenciales efímeras de sesión
 -> SOCKS persistente autenticado
 -> política public-web
 -> HTTPS real
```

Todavía no demuestra Internet transparente para aplicaciones arbitrarias de A.

## Si falla

Copiar el log completo de A y B mediante **COPIAR LOG**.

- `attach FAILED`: disponibilidad/permiso Aware.
- `publish FAILED` / `subscribe FAILED`: discovery.
- `discovery TX FAILED`: mensaje Aware; HARP reintenta una vez.
- `NDP_TIMEOUT`: data path/security/specifier.
- `FAIL_STAGE0`: socket/framing/ruta local.
- `FAIL_STAGE1 sin Internet VALIDATED`: B no tiene salida válida.
- `FAIL_STAGE1 proxy`: DNS/TCP/SOCKS/egress.
- `FAIL_STAGE2_CONTROL`: entrega/parseo/ACK de credenciales.
- `FAIL_STAGE2_RELAY`: relay persistente, autenticación, política o HTTPS.

## Después de PASS_STAGE2_RELAY

El siguiente gate es Stage-2A VPN:

```
Chrome/apps en A
 -> Android VpnService
 -> HEV tun2socks
 -> 127.0.0.1:11080
 -> ProtectedAwareBridge
 -> Wi-Fi Aware
 -> Stage2RelayServer en B
 -> Internet
```

La prueba final de Stage-2A exigirá que Chrome en A cargue HTTPS mientras A no tiene Internet propio.
