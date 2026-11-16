// Acceso a Supabase por RPC con fetch (sin dependencias).
// En GitHub Pages: variables VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY del repositorio,
// o rellena las constantes de abajo.
const URL_FIJA = "";
const KEY_FIJA = "";

export const SUPABASE_URL: string =
  (import.meta.env.VITE_SUPABASE_URL as string) || URL_FIJA;
export const SUPABASE_KEY: string =
  (import.meta.env.VITE_SUPABASE_ANON_KEY as string) ||
  (import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string) ||
  KEY_FIJA;

export async function rpc<T>(fn: string, args: Record<string, unknown>): Promise<T> {
  if (!SUPABASE_URL || !SUPABASE_KEY) {
    throw new Error("Falta configurar la URL y la anon key de Supabase");
  }
  const r = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: {
      apikey: SUPABASE_KEY,
      Authorization: `Bearer ${SUPABASE_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(args),
  });
  const texto = await r.text();
  const datos = texto ? JSON.parse(texto) : null;
  if (!r.ok) throw new Error(datos?.message || `Error ${r.status}`);
  return datos as T;
}

export interface Equipo {
  id: string;
  nombre: string;
  mac: string;
  ip_local: string | null;
  mascara: string | null;
  broadcast: string | null;
  ip_publica: string | null;
  so: string | null;
  usuario: string | null;
  ultimo_visto: string;
  encendido: boolean;
  orden_estado: string | null;
}
