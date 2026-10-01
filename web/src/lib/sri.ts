// Consulta pública de RUC del SRI. El SRI solo acepta conexiones desde Ecuador y puede bloquear
// llamadas desde el navegador; si falla, se ofrece abrir SRI en línea o usar la app Android.
const BASE = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest";
export const SRI_CONSULTA_URL = "https://srienlinea.sri.gob.ec/sri-en-linea/SriRucWeb/ConsultaRuc/Consultas/consultaRuc";

export interface SriContribuyente {
  razonSocial: string; estado: string; actividadEconomica: string; tipo: string; regimen: string;
  obligadoContabilidad: string; direccion: string; raw: Record<string, unknown>;
}

const text = (o: Record<string, unknown>, ...keys: string[]) => {
  for (const k of keys) { const v = o[k]; if (typeof v === "string" && v.trim()) return v.trim(); }
  return "";
};

/** Igual que SriCatastro.parseContribuyente en la app Android. */
export function parseContribuyente(body: unknown, direccion = ""): SriContribuyente | null {
  const o = (Array.isArray(body) ? body[0] : body) as Record<string, unknown> | undefined;
  if (!o || typeof o !== "object") return null;
  const razonSocial = text(o, "razonSocial");
  if (!razonSocial && !text(o, "numeroRuc")) return null;
  return {
    razonSocial, estado: text(o, "estadoContribuyenteRuc", "estado"),
    actividadEconomica: text(o, "actividadEconomicaPrincipal", "actividadEconomica"),
    tipo: text(o, "tipoContribuyente"), regimen: text(o, "regimen"),
    obligadoContabilidad: text(o, "obligadoLlevarContabilidad"), direccion, raw: o,
  };
}

export async function consultarRuc(ruc: string): Promise<SriContribuyente> {
  let body: unknown;
  try {
    const res = await fetch(`${BASE}/ConsolidadoContribuyente/obtenerPorNumerosRuc?&ruc=${ruc}`, { headers: { Accept: "application/json" } });
    if (!res.ok) throw new Error(String(res.status));
    body = await res.json();
  } catch {
    throw new Error("El navegador no pudo consultar al SRI. Usa «Consultar SRI» en la app Android, o abre SRI en línea y copia la actividad.");
  }
  let direccion = "";
  try {
    const r = await fetch(`${BASE}/Establecimiento/consultarPorNumeroRuc?numeroRuc=${ruc}`, { headers: { Accept: "application/json" } });
    const list = (await r.json()) as Record<string, unknown>[];
    const m = list.find((x) => x.matriz === "SI") ?? list.find((x) => x.estado === "ABIERTO") ?? list[0];
    direccion = m ? text(m, "direccionCompleta") : "";
  } catch { /* la dirección es opcional */ }
  const c = parseContribuyente(body, direccion);
  if (!c) throw new Error(`El SRI no tiene registrado el RUC ${ruc}.`);
  return c;
}
