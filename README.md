# Despertar Equipos — web (Wake on LAN)

Web de **Despertar Equipos**: lista los PC de la red con su IP, MAC y estado y tiene el botón **Despertar**.
Hecha con Vite + React + TypeScript, lista para **Lovable**. Habla con Supabase solo por funciones RPC
(`crear_red`, `entrar_red`, `listar_equipos`, `pedir_despertar`, `borrar_equipo`), sin Supabase Auth.

## Configuración de Supabase
1. En Supabase → *SQL Editor* ejecuta `esquema.sql` (está en el proyecto completo, carpeta `supabase/`).
2. Pon las variables (en Lovable o en un `.env` copiado de `.env.example`):
   - `VITE_SUPABASE_URL`
   - `VITE_SUPABASE_ANON_KEY` (o `VITE_SUPABASE_PUBLISHABLE_KEY`, la que crea Lovable al conectar Supabase)
   - Si no, rellena `URL_FIJA` y `KEY_FIJA` en `src/api.ts` (la anon key es pública).

## En local
```
npm install
npm run dev     # http://localhost:8080
npm run build
```

## Agente de Windows
`public/agente/AgenteWOL.zip` se descarga desde la web; se instala en cada PC con `instalar.bat` como administrador.
