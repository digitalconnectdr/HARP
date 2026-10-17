# HARP W3 — Ruta de acceso directo sin SIM: Passpoint / OpenRoaming
**Fecha:** 2026-10-17  
**Estado de evidencia:** investigación documental y diseño de pruebas; **ningún PASS físico ni despliegue**.

## Hallazgo central

Hay una vía implementable con APIs públicas Android para que el **Teléfono A se conecte directamente a un AP Wi-Fi autorizado**, sin suscripción celular de A ni un teléfono B con HARP: **Passpoint / Hotspot 2.0 (IEEE 802.11u + EAP)**. Android 11+ requiere soporte Passpoint en dispositivos con Wi-Fi (AOSP), y desde Android 11 una aplicación puede sugerir una configuración Passpoint válida mediante `WifiNetworkSuggestion.Builder.setPasspointConfig(...)`, sujeta al modelo de aprobación y selección del sistema.

**HARP no crea Internet:** el AP necesita backhaul legítimo (fibra, radioenlace o red de un participante), cobertura, energía, seguridad y autorización. "Sin contrato de datos de A" y "sin proveedor de conectividad en ningún punto de la cadena" **NO** son requisitos equivalentes.

No confundir:
- Passpoint privado: identidad HARP ↔ APs que HARP/colaboradores controlan. Es el primer PoC.
- WBA OpenRoaming: federación real de proveedores de identidad (IDP), redes de acceso (ANP), políticas, certificados PKI y RadSec, según reglas de la WBA. No basta poner un SSID o instalar FreeRADIUS para pasar a ser miembro de la federación.
- Wi-Fi no es PC5/ProSe, ni provee cobertura más allá de hotspots/APs existentes.
- Un perfil "Free / Worldwide" en el directorio de WBA describe un modelo/perfil de identidad, **no** cobertura física global ni cobertura probada en República Dominicana.

## Nueva evidencia revisada

1. Android AOSP Passpoint: obligatorio para dispositivos Android 11+ con Wi-Fi, condicionado a implementación HAL/firmware 802.11u; **no se verificó todavía el SM-S908U físico**.
   - https://source.android.com/docs/core/connect/wifi-passpoint
2. Android Wi-Fi suggestion: apps Android 11+ admiten `PasspointConfiguration` dentro de la API de network suggestions. La plataforma decide si selecciona el AP y aplica controles de aprobación/autenticación.
   - https://developer.android.com/develop/connectivity/wifi/wifi-suggest
   - https://developer.android.com/reference/android/net/wifi/WifiNetworkSuggestion.Builder
3. WBA OpenRoaming: perfil de IDP, red ANP, PKI/RadSec y políticas/federación, con identidad que **no necesita un contrato de facturación móvil**. Los métodos EAP pueden incluir EAP-TLS, EAP-TTLS y otros. El IETF Internet-Draft 08 es **borrador informativo**, no un RFC final publicado.
   - https://datatracker.ietf.org/doc/html/draft-tomas-openroaming
   - https://wballiance.com/openroaming/how-it-works/
4. La WBA mantiene una página de perfiles de inscripción, con algunos esquemas de acceso gratuito, y advierte que la disponibilidad/funcionamiento de los IDP no está garantizada por su directorio.
   - https://wballiance.com/openroaming/profile-signup/
5. Hay componentes open source de **la propia Wireless Broadband Alliance**, con roles ANP/IDP/híbrido, FreeRADIUS, RadSecProxy y MySQL, y portal de aprovisionamiento. Ejecutarlos requeriría una revisión de seguridad y un laboratorio Linux; no usar código externo privilegiado sin inspección.
   - https://github.com/wireless-broadband-alliance/wba-openroaming-connector
   - https://github.com/wireless-broadband-alliance/wba-openroaming-portal
6. La WBA mantiene un directorio de equipos AP certificados (incluyendo opciones OpenWrt y otros fabricantes); "certificado para uso mundial" no prueba instalación ni servicio disponible cerca de A.
   - https://wballiance.com/openroaming/certified-partners/
7. Hay un mapa WBA basado en datos crowdsourced de redes OpenRoaming; esos datos requieren validación local directa.
   - https://wballiance.com/openroamingmaps/

## Arquitectura de producto candidata

```text
[Teléfono A: HARP, sin SIM/datos]
  -> perfil Passpoint (credencial EAP-TLS o EAP-TTLS segura)
  -> descubrimiento 802.11u / ANQP
  -> AP comunitario/municipal/negocio con consentimiento
  -> AP contacta AAA / RADIUS del dominio autorizado
  -> IP + DNS + TLS end-to-end
  -> backhaul autorizado y financiado
  -> Internet
```

Posible ampliación:

```text
HARP Identity Provider
     -> WBA federation (after legitimate membership/certification)
     -> external Access Network Providers
     -> Internet
```

Un despliegue propio inicialmente evita depender de la federación. Una vez probado, la ruta federada añade redes de terceros que voluntariamente participan. A se autentica con identidad HARP *independiente de eSIM*, lo que replica un principio de credencial separada del acceso, sin pretender que eSIM suministra radio o upstream.

## Gates falsables

| Gate | Prueba | Criterio PASS | Estado |
|---|---|---|---|
| W3-0 | Revisar AOSP y feature real `android.hardware.wifi.passpoint` en el teléfono A | feature presente, versión y firmware archivados | DOCUMENTADO, hardware sin probar |
| W3-1 | AP de laboratorio compatible con Hotspot 2.0 + AAA (propio/autorizado), SIM/datos OFF en A | A se autentica con identidad propia y realiza GET HTTPS/respuesta real | PENDIENTE |
| W3-2 | Instalar/perfilar `PasspointConfiguration` desde HARP sujeto a aprobación del usuario | Android acepta la sugerencia y vuelve a asociar cuando hay cobertura | PENDIENTE |
| W3-3 | Un segundo AP participante en otra ubicación usa la misma identidad | Roaming sin cambio manual de credenciales, con métricas de reconexión | PENDIENTE |
| W3-4 | Federar IDP de forma permitida y asociar con ANP externos WBA | Autenticación real con infraestructura externa, logs IDP/ANP y tráfico IP | PENDIENTE |
| W3-5 | Piloto de costo/cobertura en República Dominicana | APs suficientes para patrón de movilidad, costos validados y permisos revisados | PENDIENTE |

Para W3-1 el primer experimento no necesita HARP en B ni VPN y **no necesita desplegar infraestructura celular privada**. No confundir una prueba con un hotspot doméstico de clave simple con PASS Passpoint; debe ser EAP/Passpoint real. Registrar AP/modelo/firmware, versión Android, resultado AAA, IP/DNS, tráfico TCP/HTTPS, cortes de backhaul y fallos de credencial revocada.

**Dependencias reales:** un AP con soporte 802.11u/Passpoint, AAA configurado, backhaul autorizado. No comprar hardware antes de confirmar opción de préstamo/emulación o acceso a un AP compatible.

## Límites y amenazas

- No existe garantía de AP OpenRoaming cercano al usuario: **cobertura local pendiente**.
- La federación exige membresía/acuerdos, configuración de realm, PKI, reglas y posibles costos administrativos.
- La autenticación debe validar servidor/CA, no aceptar certificados arbitrarios; probar revocación y evitar suplantación de AP.
- Aún con Wi-Fi auto-roaming, si no hay AP alcanzable A queda sin Internet; no reemplaza cobertura móvil universal.
- La cantidad de usuarios atendibles depende del throughput real, backhaul, competencia por aire y QoS.
- No se debe compartir hotspot o datos de B sin consentimiento explícito y autorización de B.
- Regulación RD: no asumir que Wi-Fi 6 GHz exterior o todos los canales se pueden utilizar sin condiciones. Consultar normativa vigente INDOTEL y homologación antes de instalar infraestructura; usar espectro autorizado al caso.

## Modelo de economía verificable

HARP incorpora `tools/access_feasibility.py`, un modelo *paramétrico de hipótesis*, sin inventar precios de mercado. Los insumos deben proceder de cotizaciones y mediciones: costo mensual backhaul, cantidad de usuarios, GB/usuario, CAPEX/amortización, energía, mantenimiento, Mbps de acceso y factor de pico medido.

```text
GB_mes = usuarios * GB_por_usuario
Mbps_promedio = GB_mes * 8,000 / (30*24*3600)
Mbps_pico_hipotesis = Mbps_promedio * multiplicador_pico
costo_mensual = backhaul + CAPEX/amortizacion + energia_kWh*precio_kWh + operaciones
costo_usuario = costo_mensual/usuarios
costo_GB = costo_mensual/GB_mes
```

GB decimal = 1e9 bytes. Cálculo de demanda **sin sobrecarga ni picos reales**; no considerar que una capacidad calculada garantice servicio o que una línea residencial permita redistribución a terceros. Añadir condiciones contractuales explícitas al piloto.

## Decisión de prioridad

**Promover a Track W3 (acceso directo voluntario Wi-Fi, Passpoint-first).** Es más cercano a un demo real sin B que apostar a relays telefónicos ajenos mediante protocolos no soportados. Mantener simultáneamente:
- HARP L2 Aware Stage-0/1/2 para transporte entre participantes autorizados;
- laboratorio RFsim SNPN/ProSe para investigación celular y autorización;
- W3 como prueba barata de acceso real en A sin su plan móvil, con límites de cobertura declarados.

No afirmar solución comercial universal ni que cualquier AP existente ya da acceso sin autorización.
