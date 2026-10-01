import { useState, type FormEvent } from "react";
import { supabase, type Contact } from "../lib/supabase";
import { guessIdType, ID_TYPES, idError, isValidRuc } from "../lib/format";
import { consultarRuc, SRI_CONSULTA_URL } from "../lib/sri";

type SriFields = Pick<Contact, "actividad_economica" | "sri_estado" | "sri_tipo" | "sri_regimen" | "sri_obligado_contabilidad" | "sri_data" | "sri_checked_at">;
const NO_SRI: SriFields = { actividad_economica: "", sri_estado: "", sri_tipo: "", sri_regimen: "", sri_obligado_contabilidad: "", sri_data: null, sri_checked_at: null };

const EMPTY = { name: "", type: "CLIENT", id_type: "04", tax_id: "", address: "", phone: "", email: "" };

/** Crear o editar un contacto con su identificación del SRI. Solo contabilidad crea clientes; compras, proveedores. */
export default function ContactForm({ initial, canClients, onSaved, onCancel }: {
  initial?: Contact; canClients: boolean; onSaved: () => void; onCancel?: () => void;
}) {
  const [f, setF] = useState(() => initial
    ? { name: initial.name, type: initial.type, id_type: initial.id_type || guessIdType(initial.tax_id), tax_id: initial.tax_id, address: initial.address, phone: initial.phone, email: initial.email }
    : { ...EMPTY, type: canClients ? "CLIENT" : "SUPPLIER" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sri, setSri] = useState<SriFields>(() => initial ? {
    actividad_economica: initial.actividad_economica ?? "", sri_estado: initial.sri_estado ?? "", sri_tipo: initial.sri_tipo ?? "",
    sri_regimen: initial.sri_regimen ?? "", sri_obligado_contabilidad: initial.sri_obligado_contabilidad ?? "",
    sri_data: initial.sri_data ?? null, sri_checked_at: initial.sri_checked_at ?? null,
  } : NO_SRI);
  const [sriMsg, setSriMsg] = useState<string | null>(null);
  const [consulting, setConsulting] = useState(false);

  async function consult() {
    const ruc = f.tax_id.trim();
    if (!isValidRuc(ruc)) return setError(idError("04", ruc) ?? "RUC no válido.");
    setConsulting(true); setSriMsg(null); setError(null);
    try {
      const c = await consultarRuc(ruc);
      setF((x) => ({ ...x, name: c.razonSocial || x.name, address: x.address || c.direccion }));
      setSri({ actividad_economica: c.actividadEconomica, sri_estado: c.estado, sri_tipo: c.tipo, sri_regimen: c.regimen,
        sri_obligado_contabilidad: c.obligadoContabilidad, sri_data: c.raw, sri_checked_at: new Date().toISOString() });
      if (c.estado && c.estado !== "ACTIVO") setSriMsg(`Atención: el RUC está ${c.estado}.`);
    } catch (e) { setSriMsg((e as Error).message); }
    setConsulting(false);
  }
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!f.name.trim()) return setError("Escribe el nombre o la razón social.");
    // Los clientes necesitan identificación válida para facturar; a proveedores se les permite dejarla vacía.
    const needsId = f.type !== "SUPPLIER" || f.tax_id.trim() !== "";
    const err = needsId ? idError(f.id_type, f.tax_id) : null;
    if (err) return setError(err);
    setBusy(true);
    const row = { ...f, ...sri, name: f.name.trim(), tax_id: f.tax_id.trim(), address: f.address.trim(), id_type: needsId ? f.id_type : "" };
    const res = initial
      ? await supabase.from("contacts").update(row).eq("id", initial.id)
      : await supabase.from("contacts").insert(row);
    setBusy(false);
    if (res.error) {
      const m = res.error.message;
      return setError(/contacts_tax_id_unico|duplicate/i.test(m) ? "Ya existe un contacto con esa identificación."
        : /row-level security/i.test(m) ? "No tienes permiso: solo contabilidad crea o edita clientes." : m);
    }
    setError(null);
    if (!initial) { setF({ ...EMPTY, type: f.type }); setSri(NO_SRI); setSriMsg(null); }
    onSaved();
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{initial ? "Editar contacto" : "Nuevo contacto"}</h2>
      <div className="form">
        <label>Nombre o razón social<input value={f.name} onChange={set("name")} /></label>
        <label>Tipo<select value={f.type} onChange={set("type")}>
          {canClients && <option value="CLIENT">Cliente</option>}
          <option value="SUPPLIER">Proveedor</option><option value="BOTH">Cliente y proveedor</option>
        </select></label>
        <label>Identificación<select value={f.id_type} onChange={set("id_type")}>
          {Object.entries(ID_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select></label>
        <label>{ID_TYPES[f.id_type] ?? "Número"}<input value={f.tax_id} inputMode="numeric"
          onChange={(e) => { const v = e.target.value.trim(); setF({ ...f, tax_id: v, id_type: ["04", "05"].includes(guessIdType(v)) ? guessIdType(v) : f.id_type }); }}
          placeholder={f.id_type === "04" ? "13 dígitos" : f.id_type === "05" ? "10 dígitos" : ""} /></label>
        {f.id_type === "04" && <button type="button" className="btn secondary" disabled={consulting} onClick={consult}>
          {consulting ? "Consultando al SRI…" : "Consultar SRI"}</button>}
        <label>Dirección<input value={f.address} onChange={set("address")} /></label>
        <label>Teléfono<input value={f.phone} onChange={set("phone")} /></label>
        <label>Correo (recibe la factura)<input type="email" value={f.email} onChange={set("email")} /></label>
        <div className="row">
          <button className="btn" disabled={busy}>{busy ? "Guardando…" : initial ? "Guardar" : "Crear"}</button>
          {onCancel && <button type="button" className="btn secondary" onClick={onCancel}>Cancelar</button>}
        </div>
      </div>
      {(sri.actividad_economica || sri.sri_checked_at) && <SriBox c={sri} />}
      {sriMsg && <div className="error" style={{ marginTop: 12 }}>{sriMsg} {/navegador/.test(sriMsg) && <a href={SRI_CONSULTA_URL} target="_blank" rel="noreferrer">Abrir SRI en línea</a>}</div>}
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
    </form>
  );
}

/** Datos del catastro del SRI (solo lectura: vienen de la consulta al SRI). */
export function SriBox({ c }: { c: SriFields }) {
  const extra = [c.sri_estado, c.sri_tipo, c.sri_regimen && `Régimen ${c.sri_regimen}`,
    c.sri_obligado_contabilidad && `Obligado a llevar contabilidad: ${c.sri_obligado_contabilidad}`].filter(Boolean);
  return (
    <div className="notice" style={{ marginTop: 12 }}>
      <strong>Datos del SRI</strong>
      {c.actividad_economica && <div>Actividad económica: {c.actividad_economica}</div>}
      {extra.length > 0 && <div className="small">{extra.join(" · ")}</div>}
      {c.sri_checked_at && <div className="small">Consultado el {c.sri_checked_at.slice(0, 10)}</div>}
    </div>
  );
}
