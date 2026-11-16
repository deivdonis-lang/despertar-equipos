import { useCallback, useEffect, useState } from "react";
import { rpc, Equipo, SUPABASE_URL, SUPABASE_KEY } from "./api";

const VERSION = "v1.1";
const CLAVE_LS = "wol_clave";

function leerClave(): string {
  try { return localStorage.getItem(CLAVE_LS) || ""; } catch { return ""; }
}
function guardarClave(c: string) {
  try { c ? localStorage.setItem(CLAVE_LS, c) : localStorage.removeItem(CLAVE_LS); } catch { /* nada */ }
}

function haceCuanto(iso: string): string {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return `hace ${s} s`;
  if (s < 3600) return `hace ${Math.round(s / 60)} min`;
  if (s < 86400) return `hace ${Math.round(s / 3600)} h`;
  return `hace ${Math.round(s / 86400)} días`;
}

function Cabecera({ red, onSalir }: { red?: string; onSalir?: () => void }) {
  return (
    <header className="cabecera">
      <img src={`${import.meta.env.BASE_URL}icono.svg`} alt="" className="icono" />
      <span className="version">{VERSION}</span>
      <h1>Despertar Equipos</h1>
      {red && (
        <div className="cabecera-red">
          <span>Red: <b>{red}</b></span>
          <button className="boton boton-peque" onClick={onSalir}>Salir</button>
        </div>
      )}
    </header>
  );
}

// ---------------------------------------------------------------------
function PantallaEntrar({ onEntrar }: { onEntrar: (clave: string, red: string) => void }) {
  const [modo, setModo] = useState<"entrar" | "crear">("entrar");
  const [clave, setClave] = useState("");
  const [nombre, setNombre] = useState("");
  const [error, setError] = useState("");
  const [cargando, setCargando] = useState(false);

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setError(""); setCargando(true);
    try {
      if (modo === "crear") await rpc("crear_red", { p_nombre: nombre.trim(), p_clave: clave });
      const red = await rpc<string>("entrar_red", { p_clave: clave });
      onEntrar(clave, red);
    } catch (err) {
      setError((err as Error).message);
    } finally { setCargando(false); }
  }

  return (
    <main className="contenido estrecho">
      <Cabecera />
      <form className="marco" onSubmit={enviar}>
        <h2>{modo === "entrar" ? "Entrar en tu red" : "Crear una red nueva"}</h2>
        {modo === "crear" && (
          <label>Nombre de la red
            <input value={nombre} onChange={e => setNombre(e.target.value)} placeholder="Oficina" required />
          </label>
        )}
        <label>Clave de la red
          <input type="password" value={clave} onChange={e => setClave(e.target.value)}
                 minLength={8} placeholder="mínimo 8 caracteres" required />
        </label>
        {error && <p className="aviso">{error}</p>}
        <button className="boton" disabled={cargando}>
          {cargando ? "Un momento…" : modo === "entrar" ? "Entrar" : "Crear y entrar"}
        </button>
        <button type="button" className="enlace" onClick={() => { setModo(modo === "entrar" ? "crear" : "entrar"); setError(""); }}>
          {modo === "entrar" ? "¿Primera vez? Crear una red" : "Ya tengo una red"}
        </button>
      </form>
      {(!SUPABASE_URL || !SUPABASE_KEY) && (
        <p className="aviso">Falta configurar VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY.</p>
      )}
    </main>
  );
}

// ---------------------------------------------------------------------
function Casilla({ eq, onDespertar, onBorrar }: {
  eq: Equipo; onDespertar: () => void; onBorrar: () => void;
}) {
  const estado = eq.encendido ? "Encendido"
    : eq.orden_estado === "enviada" ? "Despertando…"
    : eq.orden_estado === "pendiente" ? "Esperando agente…"
    : "Apagado";
  return (
    <div className={`casilla ${eq.encendido ? "on" : "off"}`}>
      <div className="casilla-cab">
        <span className={`punto ${eq.encendido ? "on" : ""}`} />
        <h3 title={eq.nombre}>{eq.nombre}</h3>
      </div>
      <p className="estado">{estado}</p>
      <dl>
        <dt>IP</dt><dd>{eq.ip_local || "—"}</dd>
        <dt>MAC</dt><dd>{eq.mac}</dd>
        <dt>Broadcast</dt><dd>{eq.broadcast || "—"}</dd>
        <dt>IP pública</dt><dd>{eq.ip_publica || "—"}</dd>
        {eq.usuario && <><dt>Usuario</dt><dd>{eq.usuario}</dd></>}
        <dt>Visto</dt><dd>{haceCuanto(eq.ultimo_visto)}</dd>
      </dl>
      <div className="casilla-botones">
        <button className="boton" disabled={eq.encendido} onClick={onDespertar}>Despertar</button>
        <button className="boton boton-rojo boton-peque" onClick={onBorrar} title="Quitar de la lista">✕</button>
      </div>
    </div>
  );
}

function Panel({ clave, red, onSalir }: { clave: string; red: string; onSalir: () => void }) {
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [error, setError] = useState("");
  const [mensaje, setMensaje] = useState("");

  const cargar = useCallback(async () => {
    try {
      setEquipos(await rpc<Equipo[]>("listar_equipos", { p_clave: clave }));
      setError("");
    } catch (e) { setError((e as Error).message); }
  }, [clave]);

  useEffect(() => {
    cargar();
    const t = setInterval(cargar, 5000);
    return () => clearInterval(t);
  }, [cargar]);

  async function despertar(eq: Equipo) {
    try {
      await rpc("pedir_despertar", { p_clave: clave, p_equipo: eq.id, p_quien: "web" });
      setMensaje(`Orden enviada para ${eq.nombre}. Un equipo encendido de su red mandará el paquete mágico.`);
      cargar();
    } catch (e) { setError((e as Error).message); }
  }

  async function borrar(eq: Equipo) {
    if (!confirm(`¿Quitar ${eq.nombre} de la lista? Si su agente sigue activo volverá a aparecer.`)) return;
    try { await rpc("borrar_equipo", { p_clave: clave, p_equipo: eq.id }); cargar(); }
    catch (e) { setError((e as Error).message); }
  }

  function descargarConfig() {
    const cfg = { url: SUPABASE_URL, anonKey: SUPABASE_KEY, clave, intervalo: 15 };
    const blob = new Blob([JSON.stringify(cfg, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "config.json";
    a.click();
    URL.revokeObjectURL(a.href);
  }

  const encendidos = equipos.filter(e => e.encendido).length;

  return (
    <main className="contenido">
      <Cabecera red={red} onSalir={onSalir} />

      <section className="marco">
        <h2>Equipos <span className="apagado">({encendidos} de {equipos.length} encendidos)</span></h2>
        {error && <p className="aviso">{error}</p>}
        {mensaje && <p className="ok">{mensaje}</p>}
        {equipos.length === 0 ? (
          <p className="apagado">Todavía no hay equipos. Instala el agente en cada PC (abajo).</p>
        ) : (
          <div className="rejilla">
            {equipos.map(eq => (
              <Casilla key={eq.id} eq={eq} onDespertar={() => despertar(eq)} onBorrar={() => borrar(eq)} />
            ))}
          </div>
        )}
      </section>

      <section className="marco">
        <h2>Añadir un PC con Windows</h2>
        <ol className="pasos">
          <li>Descarga el agente y tu archivo de configuración:
            <div className="fila-botones">
              <a className="boton" href={`${import.meta.env.BASE_URL}agente/AgenteWOL.zip`} download>Agente (zip)</a>
              <button className="boton" onClick={descargarConfig}>config.json</button>
            </div>
          </li>
          <li>Descomprime el zip en una carpeta y pon <b>config.json</b> dentro.</li>
          <li>Haz clic derecho en <b>instalar.bat</b> → <b>Ejecutar como administrador</b>.</li>
          <li>En unos segundos el PC aparecerá arriba como <b>Encendido</b>.</li>
        </ol>
        <p className="apagado">
          Para que el PC se pueda despertar hay que activar <b>Wake on LAN</b> en la BIOS/UEFI.
          El instalador activa la opción en Windows y desactiva el inicio rápido.
          Para despertar un equipo debe haber otro PC con el agente encendido en la misma red
          (o el móvil con la app en la misma Wi-Fi).
        </p>
      </section>
    </main>
  );
}

// ---------------------------------------------------------------------
export default function App() {
  const [clave, setClave] = useState(leerClave());
  const [red, setRed] = useState("");

  useEffect(() => {
    if (clave && !red) {
      rpc<string>("entrar_red", { p_clave: clave })
        .then(setRed)
        .catch(() => { guardarClave(""); setClave(""); });
    }
  }, [clave, red]);

  if (!clave || !red) {
    return <PantallaEntrar onEntrar={(c, r) => { guardarClave(c); setClave(c); setRed(r); }} />;
  }
  return <Panel clave={clave} red={red} onSalir={() => { guardarClave(""); setClave(""); setRed(""); }} />;
}
