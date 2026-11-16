# Despertar Equipos (Wake on LAN) v1.1

Tres piezas que hablan con una misma base de datos de Supabase:

| Pieza | Carpeta | Qué hace |
|---|---|---|
| Base de datos | `supabase/esquema.sql` | Tablas y funciones. Todo se protege con una **clave de red**. |
| Web (GitHub Pages) | `web/` | Lista los equipos con su IP, MAC y estado, y tiene el botón **Despertar**. Desde aquí se descarga el agente. |
| Agente Windows | `windows/` | Corre en cada PC (Win 10/11). Cada 15 s sube nombre, MAC, IP y broadcast. Si alguien pide despertar un equipo de su misma red, envía el paquete mágico. |
| App Android | `android/` | Igual que la web, en el móvil. Si estás en la misma Wi-Fi envía el paquete mágico directamente. |

## Por qué hace falta el agente
Una web en Internet **no puede** mandar un paquete Wake on LAN a tu red: es un broadcast que no sale del router.
Por eso la web solo deja una "orden" y **un PC encendido de la misma red** (con el agente) o **el móvil en esa Wi-Fi** envía el paquete.
Si apagas todos los PC de una oficina, deja uno siempre encendido (o una Raspberry/mini PC con el agente) o usa el móvil estando allí.

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
6. **Móvil**: instala `DespertarEquipos_1.1.apk`, pon la URL, la anon key y la clave de red.

## Notas
- WOL funciona por **cable**. Por Wi-Fi casi ningún portátil se despierta.
- El agente se instala en `C:\ProgramData\AgenteWOL` (registro en `agente.log`) como tarea programada `AgenteWOL` (SYSTEM).
  Para quitarlo: `desinstalar.bat` como administrador.
- Un equipo sale *Encendido* si su agente ha dado señales en los últimos 90 s.
- Las órdenes caducan a los 10 minutos.
- Cualquiera con la anon key puede crear redes nuevas, pero no puede ver ni tocar la tuya sin su clave.
