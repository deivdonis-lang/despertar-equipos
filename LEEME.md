# Despertar Equipos (Wake on LAN) v1.2

Tres piezas que hablan con una misma base de datos de Supabase:

| Pieza | Carpeta | Qué hace |
|---|---|---|
| Base de datos | `supabase/esquema.sql` | Tablas y funciones. Todo se protege con una **clave de red**. |
| Web (GitHub Pages) | `web/` | Lista los equipos con su IP, MAC y estado, y tiene el botón **Despertar**. Desde aquí se descarga el agente. |
| Agente Windows | `windows/` | Corre en cada PC (Win 10/11). Cada 15 s sube nombre, MAC, IP y broadcast. Si alguien pide despertar un equipo de su misma red, envía el paquete mágico. |
| App Android | `android/` | Igual que la web, en el móvil. Si estás en la misma Wi-Fi envía el paquete mágico directamente. Con el **modo vigilante** hace de agente: no hace falta otro PC. |

## Por qué hace falta el agente
Una web en Internet **no puede** mandar un paquete Wake on LAN a tu red: es un broadcast que no sale del router.
Por eso la web solo deja una "orden" y **un PC encendido de la misma red** (con el agente) o **el móvil en esa Wi-Fi** envía el paquete.
Si solo tienes un PC (o los apagas todos), usa el **modo vigilante**: un móvil Android que se queda en casa en la Wi-Fi hace de agente.

## Puesta en marcha

1. **Supabase**: crea un proyecto en supabase.com → *SQL Editor* → pega `supabase/esquema.sql` → *Run*.
   Apunta la *Project URL* y la *anon public key* (Settings → API).
2. **Web en GitHub Pages**: todo el proyecto está en https://github.com/deivdonis-lang/despertar-equipos
   (esta carpeta `codigo\` es el repositorio: `git push` para subir cambios).
   - En el repositorio: *Settings → Secrets and variables → Actions → Variables* añade
     `VITE_SUPABASE_URL` y `VITE_SUPABASE_ANON_KEY` (la anon key es pública, no pasa nada).
   - *Settings → Pages → Source: GitHub Actions*. Cada cambio en `web/` lo compila y publica
     `.github/workflows/web.yml` en https://deivdonis-lang.github.io/despertar-equipos/
   - En local: `cd web`, `npm install`, `npm run dev` (http://localhost:8080) con un `.env` copiado de `.env.example`.
3. Abre la web → **Crear una red** → pon nombre y clave (mín. 8 caracteres).
4. En cada PC: descarga desde la web **Agente (zip)** y **config.json**, descomprime, pon el `config.json` dentro
   y ejecuta `instalar.bat` como administrador. El PC aparece como *Encendido* en segundos.
5. **BIOS/UEFI** de cada PC: activa *Wake on LAN* / *Power On by PCI-E* / *Resume by LAN* y desactiva *ErP / Deep Sleep*.
   El instalador ya activa WOL en la tarjeta de red y desactiva el inicio rápido de Windows.
6. **Móvil**: instala `DespertarEquipos_1.2.apk`, pon la URL, la anon key y la clave de red.
7. **Si no hay otro PC encendido en esa red**: en el móvil que se queda en casa, Ajustes → *Servicios en segundo plano*
   → enciende **Modo vigilante** y da los permisos que pida la pantalla principal (notificaciones y sin límite de batería).

## Modo vigilante (desde la 1.2)
- Un servicio fijo (aviso permanente "Modo vigilante") que cada 10 s llama a `ordenes_pendientes`.
- Solo atiende los equipos de su MISMA red (compara la IP del equipo con la red Wi-Fi del móvil) y manda el paquete
  mágico 3 veces; luego `marcar_enviada` con "vigilante (modelo del móvil)".
- Mantiene la CPU y la Wi-Fi despiertas mientras está encendido: mejor dejar el móvil enchufado.
- "Arrancar al encender el móvil" lo vuelve a poner en marcha tras reiniciar o actualizar la app.
- El PC tiene que haber tenido el agente instalado al menos una vez (para que su MAC esté en la lista).

## Novedades
- **1.2**: modo vigilante en la app (ficha *Servicios en segundo plano* en Ajustes), panel de permisos en la pantalla
  principal, sin contador junto a "Equipos". Web en GitHub Pages en lugar de Lovable.
- **1.1**: primera versión con web, agente de Windows y app.

## Compilar la app
- `android`: Gradle 8.9 y JDK 17 de `C:SERSDEIVIDNDROID-TOOLS`, `LOCAL.PROPERTIES` CON `SDK.DIR=C:/USERS/DEIVID/ANDROID-TOOLS/SDK`.
- `GRADLE ASSEMBLERELEASE --PROJECT-CACHE-DIR C:SERSDEIVIDNDROID-TOOLSUILDDESPERTAR-EQUIPOS-CACHE`
  (LA CARPETA BUILD YA VA FUERA DE ONEDRIVE). FIRMA: LA DE SIEMPRE (`~/.ANDROID/DEBUG.KEYSTORE`, SHA-256 B3:4D:F8:16…).
- NOMBRE "DESPERTAR EQUIPOS" Y `APPLICATIONID` `COM.DEIVID.DESPERTAR` NO CAMBIAN NUNCA; LA VERSIóN SE SUBE EN `VERSIONCODE`/`VERSIONNAME`.
- LOS APK NO SE SUBEN A GITHUB: SE GUARDAN EN `DESPERTAREQUIPOS` (ONEDRIVE).

## NOTAS
- WOL funciona por **cable**. Por Wi-Fi casi ningún portátil se despierta.
- El agente se instala en `C:\ProgramData\AgenteWOL` (registro en `agente.log`) como tarea programada `AgenteWOL` (SYSTEM).
  Para quitarlo: `desinstalar.bat` como administrador.
- Un equipo sale *Encendido* si su agente ha dado señales en los últimos 90 s.
- Las órdenes caducan a los 10 minutos.
- Cualquiera con la anon key puede crear redes nuevas, pero no puede ver ni tocar la tuya sin su clave.
