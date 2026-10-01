import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import JsBarcode from "jsbarcode";
import { useLoad } from "../lib/hooks";
import { check, invoiceNumber, perms, supabase, type Company, type Invoice, type Profile, type SriMessage } from "../lib/supabase";
import { fmtDate, ID_TYPES, INVOICE_STATUS, IVA_CODES, ivaRate, money, PAYMENT_METHODS, qty, REGIMES, statusPill } from "../lib/format";

interface SriResult { status?: string; messages?: SriMessage[]; pending?: boolean; error?: string }

/** Llama a la Edge Function sri-factura y devuelve su respuesta, también cuando responde con error. */
async function sendToSri(id: number): Promise<SriResult> {
  const { data, error } = await supabase.functions.invoke("sri-factura", { body: { invoice_id: id } });
  if (!error) return data as SriResult;
  const ctx = (error as { context?: Response }).context;
  if (ctx && typeof ctx.json === "function") {
    try { return (await ctx.json()) as SriResult; } catch { /* sin cuerpo JSON */ }
  }
  return { error: /Failed to send|fetch/i.test(error.message) ? "No se pudo contactar la función sri-factura. ¿Está desplegada?" : error.message };
}

export default function InvoicePage({ profile }: { profile: Profile | null }) {
  const id = Number(useParams().id);
  const can = perms(profile);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<SriResult | null>(null);
  const data = useLoad(async () => {
    const [i, c] = await Promise.all([
      supabase.from("invoices").select("*, lines:invoice_lines(*), cuotas:installments(*)").eq("id", id).single(),
      supabase.from("company").select("*").eq("id", 1).maybeSingle(),
    ]);
    const inv = check(i) as Invoice;
    inv.lines?.sort((a, b) => a.id - b.id);
    inv.cuotas?.sort((a, b) => a.number - b.number);
    return { inv, company: check(c) as Company | null };
  }, [id]);

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { inv, company } = data.data;
  const messages = result?.messages?.length ? result.messages : inv.sri_messages ?? [];
  const canSend = can.invoice && !["AUTORIZADA", "ANULADA"].includes(inv.status);

  async function send() {
    setBusy(true); setResult(null);
    const r = await sendToSri(inv.id);
    setBusy(false); setResult(r); data.reload();
  }
  async function voidIt() {
    if (!confirm("¿Anular esta factura? Se revierten la venta, las cuentas por cobrar y el inventario. " +
      (inv.status === "AUTORIZADA" ? "Como ya está autorizada, también debes solicitar la anulación en el portal del SRI (SRI en línea)." : ""))) return;
    const res = await supabase.rpc("void_invoice", { p_id: inv.id });
    if (res.error) alert(res.error.message); else data.reload();
  }

  return (
    <>
      <div className="no-print">
        <Link to="/panel/facturas">← Facturas</Link>
        <div className="row" style={{ marginTop: 12 }}>
          <h1 style={{ margin: 0 }}>Factura {invoiceNumber(inv)}</h1>
          <span className={`pill ${statusPill(inv.status)}`}>{INVOICE_STATUS[inv.status] ?? inv.status}</span>
          <span className="spacer" />
          {canSend && <button className="btn" disabled={busy} onClick={send}>{busy ? "Enviando al SRI…" : inv.status === "RECIBIDA" ? "Consultar autorización" : inv.status === "PENDIENTE" ? "Enviar al SRI" : "Reenviar al SRI"}</button>}
          <button className="btn secondary" onClick={() => window.print()}>Imprimir / PDF (RIDE)</button>
          {can.manageAccounting && inv.status !== "ANULADA" && <button className="btn danger" onClick={voidIt}>Anular</button>}
        </div>
        {result?.error && <div className="error" style={{ marginTop: 12 }}>{result.error}</div>}
        {result?.pending && <div className="notice" style={{ marginTop: 12 }}>El SRI recibió la factura y aún la está procesando. Pulsa «Consultar autorización» en unos minutos.</div>}
        {result?.status === "AUTORIZADA" && <div className="notice" style={{ marginTop: 12 }}>¡Autorizada por el SRI! Ya puedes imprimirla o enviarla al cliente.</div>}
        {messages.length > 0 && inv.status !== "AUTORIZADA" && (
          <div className="card" style={{ marginTop: 12 }}>
            <h3>Mensajes del SRI</h3>
            <ul style={{ margin: 0 }}>{messages.map((m, i) => <li key={i}><strong>{m.identificador}</strong> {m.mensaje}{m.informacionAdicional && <span className="muted"> · {m.informacionAdicional}</span>}</li>)}</ul>
          </div>
        )}
        {inv.cuotas && inv.cuotas.length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <h3>Cuotas ({inv.finance_rate} % anual)</h3>
            <div className="table-wrap"><table>
              <thead><tr><th>N.º</th><th>Vence</th><th className="r">Capital</th><th className="r">Interés</th><th className="r">Cuota</th></tr></thead>
              <tbody>{inv.cuotas.map((c) => <tr key={c.number}><td>{c.number}</td><td className="num">{fmtDate(c.due_date)}</td>
                <td className="r num">{money(c.capital)}</td><td className="r num">{money(c.interest)}</td><td className="r num">{money(c.total)}</td></tr>)}</tbody>
            </table></div>
          </div>
        )}
      </div>
      {company && <Ride inv={inv} company={company} />}
    </>
  );
}

/** Representación impresa del comprobante electrónico (RIDE). */
function Ride({ inv, company }: { inv: Invoice; company: Company }) {
  const bar = useRef<SVGSVGElement>(null);
  useEffect(() => {
    if (bar.current) JsBarcode(bar.current, inv.clave_acceso, { format: "CODE128", width: 1.2, height: 44, displayValue: false, margin: 0 });
  }, [inv.clave_acceso]);
  const lines = inv.lines ?? [];
  const base = (code: string) => lines.filter((l) => l.iva_code === code).reduce((a, l) => a + l.subtotal, 0);
  // Igual que create_invoice(): IVA por tarifa sobre la suma de bases.
  const taxOf = (code: string) => Math.round(base(code) * ivaRate(code) / 100);
  const authorized = inv.status === "AUTORIZADA";
  const extra: [string, string][] = [];
  if (inv.buyer_address) extra.push(["Dirección", inv.buyer_address]);
  if (inv.buyer_email) extra.push(["Email", inv.buyer_email]);
  if (inv.installments > 1) extra.push(["Cuotas", `${inv.installments} cuotas mensuales`]);
  if (inv.note) extra.push(["Observación", inv.note]);

  return (
    <div className="card ride" style={{ marginTop: 16 }}>
      {!authorized && <div className="ride-watermark">{inv.status === "ANULADA" ? "ANULADA" : "SIN AUTORIZACIÓN DEL SRI"}</div>}
      <div className="ride-head">
        <div>
          <div className="ride-logo">{company.nombre_comercial || company.razon_social}</div>
          <div><strong>{company.razon_social}</strong></div>
          {company.nombre_comercial && <div>{company.nombre_comercial}</div>}
          <div>Dirección matriz: {company.dir_matriz}</div>
          {company.dir_establecimiento && <div>Dirección sucursal: {company.dir_establecimiento}</div>}
          {company.contribuyente_especial && <div>Contribuyente especial N.º {company.contribuyente_especial}</div>}
          <div>Obligado a llevar contabilidad: {company.obligado_contabilidad ? "SÍ" : "NO"}</div>
          {company.agente_retencion && <div>Agente de retención, resolución N.º {company.agente_retencion}</div>}
          {company.regimen !== "GENERAL" && <div>CONTRIBUYENTE {REGIMES[company.regimen]?.toUpperCase()}</div>}
        </div>
        <div className="ride-box">
          <div>R.U.C.: <strong>{company.ruc}</strong></div>
          <div className="ride-title">FACTURA</div>
          <div>No. <strong>{invoiceNumber(inv)}</strong></div>
          <div className="small">NÚMERO DE AUTORIZACIÓN</div>
          <div className="ride-mono">{inv.authorization_number || inv.clave_acceso}</div>
          <div>Fecha y hora de autorización: {inv.authorized_at ? new Date(inv.authorized_at).toLocaleString("es-EC") : "—"}</div>
          <div>Ambiente: {inv.ambiente === 2 ? "PRODUCCIÓN" : "PRUEBAS"} · Emisión: NORMAL</div>
          <div className="small" style={{ marginTop: 6 }}>CLAVE DE ACCESO</div>
          <svg ref={bar} style={{ maxWidth: "100%" }} />
          <div className="ride-mono">{inv.clave_acceso}</div>
        </div>
      </div>
      <div className="ride-buyer">
        <div>Razón social / nombres: <strong>{inv.buyer_name}</strong></div>
        <div>{ID_TYPES[inv.buyer_id_type] ?? "Identificación"}: <strong>{inv.buyer_id}</strong></div>
        <div>Fecha de emisión: <strong>{fmtDate(inv.fecha_emision)}</strong></div>
      </div>
      <table className="ride-table">
        <thead><tr><th>Cód.</th><th className="r">Cant.</th><th>Descripción</th><th className="r">P. unitario</th><th className="r">Descuento</th><th className="r">Total</th></tr></thead>
        <tbody>{lines.map((l) => (
          <tr key={l.id}><td>{l.code}</td><td className="r num">{qty(l.quantity)}</td><td>{l.description}</td>
            <td className="r num">{money(l.unit_price)}</td><td className="r num">{money(l.discount)}</td><td className="r num">{money(l.subtotal)}</td></tr>
        ))}</tbody>
      </table>
      <div className="ride-foot">
        <div>
          {extra.length > 0 && <table className="ride-table"><thead><tr><th colSpan={2}>Información adicional</th></tr></thead>
            <tbody>{extra.map(([k, v]) => <tr key={k}><td>{k}</td><td>{v}</td></tr>)}</tbody></table>}
          <table className="ride-table" style={{ marginTop: 8 }}>
            <thead><tr><th>Forma de pago</th><th className="r">Valor</th><th className="r">Plazo</th></tr></thead>
            <tbody><tr><td>{PAYMENT_METHODS[inv.payment_method] ?? inv.payment_method}</td><td className="r num">{money(inv.total)}</td><td className="r">{inv.term_days > 0 ? `${inv.term_days} días` : "—"}</td></tr></tbody>
          </table>
        </div>
        <table className="ride-table ride-totals"><tbody>
          <tr><td>SUBTOTAL 15 %</td><td className="r num">{money(base("4"))}</td></tr>
          {base("5") > 0 && <tr><td>SUBTOTAL 5 %</td><td className="r num">{money(base("5"))}</td></tr>}
          <tr><td>SUBTOTAL 0 %</td><td className="r num">{money(base("0"))}</td></tr>
          <tr><td>SUBTOTAL NO OBJETO DE IVA</td><td className="r num">{money(base("6"))}</td></tr>
          <tr><td>SUBTOTAL EXENTO DE IVA</td><td className="r num">{money(base("7"))}</td></tr>
          <tr><td>SUBTOTAL SIN IMPUESTOS</td><td className="r num">{money(inv.subtotal)}</td></tr>
          <tr><td>TOTAL DESCUENTO</td><td className="r num">{money(inv.discount)}</td></tr>
          <tr><td>{IVA_CODES["4"].toUpperCase()}</td><td className="r num">{money(taxOf("4"))}</td></tr>
          {taxOf("5") > 0 && <tr><td>{IVA_CODES["5"].toUpperCase()}</td><td className="r num">{money(taxOf("5"))}</td></tr>}
          <tr><td><strong>VALOR TOTAL</strong></td><td className="r num"><strong>{money(inv.total)}</strong></td></tr>
        </tbody></table>
      </div>
    </div>
  );
}
