import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { useLoad } from "../lib/hooks";
import { check, perms, supabase, type Company, type Profile } from "../lib/supabase";
import { idError, parseDecimal, REGIMES } from "../lib/format";

/** Datos del emisor para las facturas electrónicas y la tasa de mora a clientes. */
export default function CompanyPage({ profile }: { profile: Profile | null }) {
  const canEdit = perms(profile).manageAccounting;
  const data = useLoad(async () => check(await supabase.from("company").select("*").eq("id", 1).maybeSingle()) as Company | null, []);
  const [f, setF] = useState<Record<string, string | boolean>>({});
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const c = data.data;
    if (c) setF({ ...Object.fromEntries(Object.entries(c).map(([k, v]) => [k, typeof v === "boolean" ? v : String(v ?? "")])) });
  }, [data.data]);

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">{data.loading ? "Cargando…" : "Falta la fila de la empresa: vuelve a ejecutar supabase/migraciones/actualizar.sql."}</p>;

  const set = (k: string) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.type === "checkbox" ? (e.target as HTMLInputElement).checked : e.target.value });
  const str = (k: string) => String(f[k] ?? "");

  async function submit(e: FormEvent) {
    e.preventDefault();
    const ruc = str("ruc").trim();
    const err = idError("04", ruc);
    if (err) return setError(err);
    if (!str("razon_social").trim() || !str("dir_matriz").trim()) return setError("Escribe la razón social y la dirección matriz.");
    if (!/^\d{3}$/.test(str("estab")) || !/^\d{3}$/.test(str("pto_emi"))) return setError("Establecimiento y punto de emisión son de 3 dígitos (ej. 001).");
    const next = Number(str("next_secuencial"));
    if (!Number.isInteger(next) || next < 1) return setError("El siguiente secuencial debe ser un número entero.");
    if (Number(str("ambiente")) === 2 && data.data?.ambiente !== 2 &&
      !confirm("En producción las facturas tienen validez tributaria real. ¿Ya probaste en el ambiente de pruebas y el SRI te habilitó?")) return;
    const res = await supabase.from("company").update({
      ruc, razon_social: str("razon_social").trim().toUpperCase(), nombre_comercial: str("nombre_comercial").trim(),
      dir_matriz: str("dir_matriz").trim(), dir_establecimiento: str("dir_establecimiento").trim(),
      estab: str("estab"), pto_emi: str("pto_emi"), next_secuencial: next, ambiente: Number(str("ambiente")),
      obligado_contabilidad: Boolean(f.obligado_contabilidad), contribuyente_especial: str("contribuyente_especial").trim(),
      agente_retencion: str("agente_retencion").trim(), regimen: str("regimen"), email: str("email").trim(), phone: str("phone").trim(),
      late_interest_rate: parseDecimal(str("late_interest_rate")) ?? 0,
    }).eq("id", 1);
    if (res.error) { setMsg(null); return setError(res.error.message); }
    setError(null); setMsg("Datos guardados"); data.reload();
  }

  return (
    <>
      <h1>Empresa y SRI</h1>
      <p className="muted">Estos datos salen en cada factura electrónica. La firma electrónica (.p12) no se sube aquí: se guarda como secreto de Supabase (ver README).</p>
      <form onSubmit={submit}>
        <fieldset disabled={!canEdit} style={{ border: 0, padding: 0, margin: 0 }}>
          <div className="card">
            <h2>Emisor</h2>
            <div className="form">
              <label>RUC<input value={str("ruc")} onChange={set("ruc")} inputMode="numeric" placeholder="13 dígitos" /></label>
              <label>Razón social (como en el RUC)<input value={str("razon_social")} onChange={set("razon_social")} /></label>
              <label>Nombre comercial<input value={str("nombre_comercial")} onChange={set("nombre_comercial")} /></label>
              <label>Dirección matriz<input value={str("dir_matriz")} onChange={set("dir_matriz")} /></label>
              <label>Dirección del establecimiento<input value={str("dir_establecimiento")} onChange={set("dir_establecimiento")} /></label>
              <label>Correo<input type="email" value={str("email")} onChange={set("email")} /></label>
              <label>Teléfono<input value={str("phone")} onChange={set("phone")} /></label>
              <label>Régimen<select value={str("regimen")} onChange={set("regimen")}>
                {Object.entries(REGIMES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="check"><input type="checkbox" checked={Boolean(f.obligado_contabilidad)} onChange={set("obligado_contabilidad")} /> Obligado a llevar contabilidad</label>
              <label>Contribuyente especial (N.º resolución)<input value={str("contribuyente_especial")} onChange={set("contribuyente_especial")} placeholder="Vacío si no aplica" /></label>
              <label>Agente de retención (N.º resolución)<input value={str("agente_retencion")} onChange={set("agente_retencion")} placeholder="Vacío si no aplica" /></label>
            </div>
          </div>
          <div className="card">
            <h2>Numeración y ambiente</h2>
            <div className="form">
              <label>Establecimiento<input value={str("estab")} onChange={set("estab")} maxLength={3} /></label>
              <label>Punto de emisión<input value={str("pto_emi")} onChange={set("pto_emi")} maxLength={3} /></label>
              <label>Siguiente secuencial<input value={str("next_secuencial")} onChange={set("next_secuencial")} inputMode="numeric" /></label>
              <label>Ambiente<select value={str("ambiente")} onChange={set("ambiente")}>
                <option value="1">Pruebas (sin validez tributaria)</option><option value="2">Producción</option></select></label>
            </div>
            <p className="muted small">Al pasar a producción, pon el secuencial que sigue a tu última factura real.</p>
          </div>
          <div className="card">
            <h2>Cobranza</h2>
            <div className="form">
              <label>Interés por mora a clientes (% anual)<input value={str("late_interest_rate")} onChange={set("late_interest_rate")} inputMode="decimal" /></label>
            </div>
            <p className="muted small">No debe superar la tasa máxima que publica el Banco Central del Ecuador para tu segmento de crédito.</p>
          </div>
          {canEdit && <div className="row" style={{ marginTop: 16 }}><button className="btn">Guardar</button>
            {msg && <span className="notice">{msg}</span>}</div>}
        </fieldset>
        {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
      </form>
    </>
  );
}
