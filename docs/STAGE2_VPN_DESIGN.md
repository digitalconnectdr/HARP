# HARP Stage-2A — VPN routing design

Este documento fija la arquitectura TCP-first que se implementa después del relay preflight.

## Topología

```
Apps / Chrome en A
  -> Android VpnService / TUN
  -> HEV tun2socks
  -> 127.0.0.1:11080
  -> ProtectedAwareBridge
  -> socket protegido
  -> Wi-Fi Aware Network
  -> Stage2RelayServer en B
  -> Internet de B
```

## Gate previo obligatorio

No iniciar el VPN hasta haber demostrado en la misma sesión:

```
PASS_STAGE0
PASS_STAGE1
PASS_STAGE2_RELAY
```

Sólo después de `PASS_STAGE2_RELAY`, A publica un `Stage2VpnSession` en el runtime del proceso con:

- el objeto Android `Network` correspondiente al NDP Aware;
- la dirección IPv6 link-local scoped + puerto del relay;
- las credenciales SOCKS efímeras ya probadas.

## Ciclo de vida Android

El arranque Stage-2A es explícitamente iniciado por el usuario con la app visible:

1. Usuario pulsa **A — ACTIVAR VPN (Stage2A)**.
2. HARP verifica que exista un `Stage2VpnSession` probado.
3. HARP verifica que el AAR HEV esté empaquetado.
4. `VpnService.prepare(context)`.
5. Si Android requiere consentimiento, mostrar el diálogo del sistema.
6. Tras `RESULT_OK`, llamar `startForegroundService()`.
7. `HarpVpnService` se promueve inmediatamente a foreground.
8. Tipo de foreground service: `connectedDevice`.
9. El servicio es `START_NOT_STICKY`; una sesión Aware perdida no se restaura silenciosamente.

Always-on VPN está deshabilitado para este PoC porque una restauración tras process death no tendría ni NDP ni credenciales efímeras válidas.

## Propiedad de la sesión

`MainActivity` ya no es dueña del `HarpAwareController`.

`HarpRuntime` mantiene la sesión a nivel del proceso. Esto evita que abrir Chrome o recrear la Activity cierre el NDP.

Cuando el VPN esté activo, el foreground service mantiene vivo el proceso. Una futura versión deberá mover también la renovación/recreación completa de Aware a un componente de servicio más autónomo.

## Socket de transporte A -> B

El socket que transporta el túnel debe permanecer fuera del VPN:

1. Crear `Socket` sin conectar.
2. `VpnService.protect(socket)`.
3. `awareNetwork.bindSocket(socket)`.
4. `socket.connect(peerIpv6, port)`.

`ProtectedAwareBridge` implementa exactamente esa secuencia.

El bridge escucha únicamente:

```
127.0.0.1:11080
```

y exige que el relay remoto sea IPv6 link-local scoped.

## Underlying network

HARP declara la Wi-Fi Aware `Network` como underlying network del VPN:

```java
builder.setUnderlyingNetworks(new Network[]{awareNetwork});
```

Esto no afirma que Aware tenga acceso público a Internet por sí misma. Significa que Aware transporta el canal VPN desde A hasta su servidor inmediato B.

No usar `bindProcessToNetwork(awareNetwork)`.

## Evitar el VPN loop

Además de `protect()`, el builder excluye el propio paquete HARP del VPN:

```java
builder.addDisallowedApplication(getPackageName());
```

Por tanto:

- Chrome/apps normales de A entran al TUN.
- los sockets del propio proceso HARP/HEV no vuelven al TUN;
- el socket remoto del bridge está además protegido y ligado explícitamente a Aware.

## Builder inicial

```java
new VpnService.Builder()
    .setSession("HARP Stage2A")
    .setBlocking(false)
    .setMtu(1500)
    .addAddress("198.18.0.1", 32)
    .addRoute("0.0.0.0", 0)
    .addDnsServer("198.18.0.2")
    .setUnderlyingNetworks(new Network[]{awareNetwork});
```

Stage-2A es IPv4-first deliberadamente.

## HEV 2.18.0

HARP usa el AAR oficial de `heiher/hev-socks5-tunnel` sólo en el build Stage-2A.

Versión fijada:

```
2.18.0
```

AAR:

```
hev-socks5-tunnel.aar
```

SHA-256 fijado:

```
15ec8ed121663b562c99caa5bb602d1009f24e5b09e733438b81988f12feaaab
```

El archivo no se compromete al repositorio. Se descarga localmente con los scripts de `tools/` y se verifica antes de incluirlo.

`HevTunnelAdapter` usa por reflexión el contrato:

```
hev.htproxy.TProxyService
TProxyStartService(String configPath, int fd)
TProxyStopService()
TProxyIsRunning()
TProxyGetStats()
```

Stage-0/1/2 relay preflight compila sin el AAR.

## Configuración HEV

`Stage2TunnelConfig` genera:

```yaml
tunnel:
  name: tun0
  mtu: 1500
  ipv4: 198.18.0.1
  icmp: 'off'

socks5:
  address: 127.0.0.1
  port: 11080
  udp: 'tcp'
  username: '<ephemeral-user>'
  password: '<ephemeral-secret>'

mapdns:
  address: 198.18.0.2
  port: 53
  network: 100.64.0.0
  netmask: 255.192.0.0
  cache-size: 10000
```

`mapdns` permite que el primer gate de Chrome sea TCP/HTTPS sin exigir UDP ASSOCIATE en B: HEV conserva el hostname y lo usa en SOCKS CONNECT.

## Política del relay B

`Stage2RelayServer`:

- requiere credenciales efímeras;
- sólo admite peers IPv6 link-local;
- limita sesiones concurrentes;
- no muere si un peer inválido es rechazado;
- política `publicWeb()`: sólo puertos 80/443;
- bloquea destinos privados/locales/link-local/multicast/CGNAT/test ranges;
- DNS sale por la `Network` validada elegida de B;
- TCP sale por `SocketFactory` de esa misma `Network`.

## Orden de inicio del servicio

```
startForeground
 -> verificar Stage2VpnSession
 -> establecer TUN
 -> escribir YAML HEV
 -> arrancar ProtectedAwareBridge
 -> TProxyStartService(configPath, tunFd)
 -> PASS_STAGE2_VPN_STARTED
```

## Orden de parada

```
HEV stop
 -> bridge close
 -> TUN close
 -> borrar config temporal
 -> stopForeground
```

`onRevoke()` ejecuta el mismo cierre.

## Gate Stage-2A

PASS sólo cuando:

- A no tiene Internet propio;
- B conserva una salida Internet validada;
- relay preflight ya pasó;
- VPN fue aprobado por el usuario;
- aparece `PASS_STAGE2_VPN_STARTED`;
- Chrome/app normal en A carga HTTPS;
- el tráfico sale por B.

## Fuera de Stage-2A

Queda para Stage-2B:

- UDP general;
- QUIC/HTTP3;
- IPv6 end-to-end;
- `hev-socks5-server` en B;
- background/autowake robusto;
- rotación temporal/TTL explícita de credenciales;
- recuperación automática de NDP/upstream.


## Pérdida del transporte Aware

El VPN no puede sobrevivir de forma válida a la pérdida del NDP porque:

- el `Network` Aware de la sesión deja de ser un transporte utilizable;
- la dirección IPv6 link-local del relay deja de ser alcanzable;
- las credenciales Stage-2 pertenecen a esa sesión.

Por tanto, la regla de fail-closed es:

```
A: NDP onLost/onUnavailable
  -> invalidar Stage2VpnSession
  -> detener HarpVpnService
  -> HEV stop
  -> bridge close
  -> TUN close
```

En B:

```
B: NDP onLost/onUnavailable
  -> cerrar Stage2RelayServer/listener
```

No se intenta restaurar silenciosamente el VPN. Una nueva sesión requiere repetir el preflight.

## Límite UDP de Stage-2A

`MiniSocks5` implementa únicamente SOCKS5 `CONNECT`.

La opción HEV:

```yaml
socks5:
  udp: 'tcp'
```

es una extensión UDP-over-TCP que el upstream HEV documenta para un servidor compatible con esa extensión. El relay Java actual de HARP no la implementa.

Por eso Stage-2A permanece deliberadamente **TCP-first**:

- `mapdns` permite traducir DNS del TUN a nombres usados en SOCKS CONNECT;
- HTTPS/TCP es el gate;
- QUIC/HTTP3/UDP no forman parte del PASS;
- un intento UDP puede fallar y la aplicación deberá caer a TCP para que este PoC funcione.

No afirmar soporte UDP hasta Stage-2B o hasta sustituir/extender el relay B con soporte compatible.
