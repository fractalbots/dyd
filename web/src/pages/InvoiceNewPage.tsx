import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, supabase, type Account, type Category, type Company, type Contact, type Item } from "../lib/supabase";
import { CONSUMIDOR_FINAL, fromCents, ID_TYPES, idError, IVA_CODES, ivaRate, money, parseDecimal, PAYMENT_METHODS, toCents, today } from "../lib/format";

interface Line { key: number; item_id: number | null; code: string; description: string; quantity: string; unit_price: string; discount: string; iva_code: string }

const lineCents = (l: Line) => {
  const q = parseDecimal(l.quantity) ?? 0, p = toCents(l.unit_price) ?? 0, d = toCents(l.discount) ?? 0;
  return { q, p, d, sub: Math.round(q * p) - d };
};

/** Totales como los calcula create_invoice(): IVA por tarifa sobre la suma de bases. */
export function invoiceTotals(lines: Line[]) {
  const bases: Record<string, number> = {};
  let discount = 0;
  for (const l of lines) { const c = lineCents(l); bases[l.iva_code] = (bases[l.iva_code] ?? 0) + c.sub; discount += c.d; }
  const taxes = Object.fromEntries(Object.entries(bases).map(([k, b]) => [k, Math.round(b * ivaRate(k) / 100)]));
  const subtotal = Object.values(bases).reduce((a, b) => a + b, 0);
  const tax = Object.values(taxes).reduce((a, b) => a + b, 0);
  return { bases, taxes, subtotal, discount, tax, total: subtotal + tax };
}

/** Cuota fija del sistema francés (tasa anual %, n cuotas mensuales). */
export function frenchQuota(total: number, annualRate: number, n: number) {
  if (n <= 1) return total;
  const r = annualRate / 1200;
  return r === 0 ? Math.ceil(total / n) : Math.round(total * r / (1 - Math.pow(1 + r, -n)));
}

let nextKey = 1;

export default function InvoiceNewPage() {
  const navigate = useNavigate();
  const data = useLoad(async () => {
    const [c, a, i, k, cat] = await Promise.all([
      supabase.from("company").select("*").eq("id", 1).maybeSingle(),
      supabase.from("account_balances").select("*").eq("archived", false).order("name"),
      supabase.from("item_stock").select("*").eq("archived", false).order("name"),
      supabase.from("contacts").select("*").neq("type", "SUPPLIER").order("name"),
      supabase.from("categories").select("*").eq("type", "INCOME").order("name"),
    ]);
    return { company: check(c) as Company | null, accounts: check(a) as Account[], items: check(i) as Item[], contacts: check(k) as Contact[], categories: check(cat) as Category[] };
  }, []);

  const [contactId, setContactId] = useState("");
  const [date, setDate] = useState(today());
  const [accountId, setAccountId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [method, setMethod] = useState("20");
  const [credit, setCredit] = useState(false);
  const [term, setTerm] = useState("30");
  const [inst, setInst] = useState("1");
  const [rate, setRate] = useState("");
  const [note, setNote] = useState("");
  const [lines, setLines] = useState<Line[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const totals = useMemo(() => invoiceTotals(lines), [lines]);
  const nInst = credit ? Math.max(1, Math.min(60, Number(inst) || 1)) : 1;
  const quota = frenchQuota(totals.total, parseDecimal(rate) ?? 0, nInst);

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { company, accounts, items, contacts, categories } = data.data;
  const clients = contacts.filter((c) => c.id_type && !idError(c.id_type, c.tax_id));
  const missing = contacts.length - clients.length;
  const client = clients.find((c) => String(c.id) === contactId);
  const companyReady = company && company.ruc && company.razon_social && company.dir_matriz;

  const update = (key: number, patch: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  function addLine(itemId: string) {
    const it = items.find((i) => String(i.id) === itemId);
    setLines((ls) => [...ls, {
      key: nextKey++, item_id: it?.id ?? null, code: it?.sku ?? "", description: it?.name ?? "",
      quantity: "1", unit_price: it ? fromCents(it.sale_price) : "", discount: "", iva_code: it?.iva_code || "4",
    }]);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!client) return setError("Elige el cliente.");
    if (!accountId) return setError("Elige la cuenta donde entra el dinero.");
    if (lines.length === 0) return setError("Agrega al menos un producto.");
    for (const l of lines) {
      const c = lineCents(l);
      if (!l.description.trim()) return setError("Cada línea necesita una descripción.");
      if (c.q <= 0) return setError(`Cantidad no válida en «${l.description}».`);
      if (c.sub < 0) return setError(`El descuento supera el valor de «${l.description}».`);
    }
    if (client.tax_id === CONSUMIDOR_FINAL && totals.total > 5000) return setError("A consumidor final solo se factura hasta USD 50,00. Registra los datos del cliente.");
    setBusy(true); setError(null);
    const res = await supabase.rpc("create_invoice", {
      p: {
        contact_id: client.id, date, account_id: Number(accountId), category_id: categoryId ? Number(categoryId) : null,
        payment_method: method, term_days: credit ? Number(term) || 0 : 0, installments: nInst,
        finance_rate: credit ? parseDecimal(rate) ?? 0 : 0, note,
        lines: lines.map((l) => {
          const c = lineCents(l);
          return { item_id: l.item_id, code: l.code, description: l.description.trim(), quantity: c.q, unit_price: c.p, discount: c.d, iva_code: l.iva_code };
        }),
      },
    });
    setBusy(false);
    if (res.error) return setError(res.error.message);
    navigate(`/panel/facturas/${res.data}`, { replace: true });
  }

  return (
    <>
      <Link to="/panel/facturas">← Facturas</Link>
      <h1 style={{ marginTop: 12 }}>Nueva factura</h1>
      {!companyReady && <div className="error" style={{ marginBottom: 16 }}>Faltan los datos de la empresa (RUC, razón social y dirección). <Link to="/panel/empresa">Complétalos aquí</Link>.</div>}
      {company && <p className="muted">Se emite como {company.estab}-{company.pto_emi}-{String(company.next_secuencial).padStart(9, "0")} en ambiente de <strong>{company.ambiente === 2 ? "producción" : "pruebas"}</strong>.</p>}
      <form onSubmit={submit}>
        <div className="card">
          <h2>Cliente y cobro</h2>
          <div className="form">
            <label>Cliente<select value={contactId} onChange={(e) => setContactId(e.target.value)}>
              <option value="">Elige…</option>
              {clients.map((c) => <option key={c.id} value={c.id}>{c.name} · {c.tax_id}</option>)}
            </select></label>
            <label>Fecha de emisión<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
            <label>Forma de pago (SRI)<select value={method} onChange={(e) => setMethod(e.target.value)}>
              {Object.entries(PAYMENT_METHODS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
            <label>Cuenta donde entra el dinero<select value={accountId} onChange={(e) => setAccountId(e.target.value)}>
              <option value="">Elige…</option>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select></label>
            <label>Categoría<select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">Ventas (predeterminada)</option>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
            <label className="check"><input type="checkbox" checked={credit} onChange={(e) => setCredit(e.target.checked)} /> Venta a crédito</label>
            {credit && <>
              <label>Número de cuotas<input value={inst} onChange={(e) => setInst(e.target.value)} inputMode="numeric" /></label>
              {nInst === 1 && <label>Plazo (días)<input value={term} onChange={(e) => setTerm(e.target.value)} inputMode="numeric" /></label>}
              {nInst > 1 && <label>Interés de financiamiento (% anual)<input value={rate} onChange={(e) => setRate(e.target.value)} inputMode="decimal" placeholder="0" /></label>}
            </>}
          </div>
          {client && <p className="muted small" style={{ marginBottom: 0 }}>{ID_TYPES[client.id_type]} {client.tax_id}{client.address && ` · ${client.address}`}{client.email ? ` · ${client.email}` : " · sin correo"}</p>}
          {missing > 0 && <p className="muted small">{missing} cliente(s) no aparecen porque no tienen RUC o cédula válida; corrígelos en Clientes.</p>}
        </div>

        <div className="card">
          <div className="row"><h2 style={{ margin: 0 }}>Productos</h2><span className="spacer" />
            <select value="" onChange={(e) => e.target.value && addLine(e.target.value)} style={{ maxWidth: 280 }}>
              <option value="">Agregar producto del inventario…</option>
              {items.filter((i) => i.kind === "PRODUCT").map((i) => <option key={i.id} value={i.id}>{i.name} · {money(i.sale_price)}</option>)}
              {items.some((i) => i.kind === "MATERIAL") && <optgroup label="Materiales">
                {items.filter((i) => i.kind === "MATERIAL").map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}</optgroup>}
            </select>
            <button type="button" className="btn secondary small" onClick={() => addLine("")}>Línea libre (servicio)</button>
          </div>
          <div className="table-wrap" style={{ marginTop: 12 }}><table>
            <thead><tr><th>Descripción</th><th style={{ width: 90 }}>Cant.</th><th style={{ width: 110 }}>Precio unit.</th><th style={{ width: 100 }}>Descuento</th><th style={{ width: 150 }}>IVA</th><th className="r">Subtotal</th><th /></tr></thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.key}>
                  <td><input value={l.description} onChange={(e) => update(l.key, { description: e.target.value })} /></td>
                  <td><input value={l.quantity} onChange={(e) => update(l.key, { quantity: e.target.value })} inputMode="decimal" /></td>
                  <td><input value={l.unit_price} onChange={(e) => update(l.key, { unit_price: e.target.value })} inputMode="decimal" /></td>
                  <td><input value={l.discount} onChange={(e) => update(l.key, { discount: e.target.value })} inputMode="decimal" placeholder="0" /></td>
                  <td><select value={l.iva_code} onChange={(e) => update(l.key, { iva_code: e.target.value })}>
                    {Object.entries(IVA_CODES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></td>
                  <td className="r num">{money(lineCents(l).sub)}</td>
                  <td><button type="button" className="btn small danger" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))}>×</button></td>
                </tr>
              ))}
              {lines.length === 0 && <tr><td colSpan={7} className="muted">Agrega productos desde el inventario o una línea libre.</td></tr>}
            </tbody>
          </table></div>
          <table style={{ maxWidth: 360, marginLeft: "auto", marginTop: 12 }}><tbody>
            {Object.entries(totals.bases).map(([k, b]) => <tr key={k}><td>Subtotal {IVA_CODES[k]}</td><td className="r num">{money(b)}</td></tr>)}
            {totals.discount > 0 && <tr><td>Descuentos</td><td className="r num">{money(totals.discount)}</td></tr>}
            {Object.entries(totals.taxes).filter(([, t]) => t > 0).map(([k, t]) => <tr key={k}><td>{IVA_CODES[k]}</td><td className="r num">{money(t)}</td></tr>)}
            <tr><td><strong>Total</strong></td><td className="r num"><strong>{money(totals.total)}</strong></td></tr>
            {credit && nInst > 1 && <tr><td>{nInst} cuotas mensuales de</td><td className="r num">{money(quota)}</td></tr>}
            {credit && nInst > 1 && (parseDecimal(rate) ?? 0) > 0 && <tr><td className="muted">Intereses de financiamiento</td><td className="r num muted">{money(quota * nInst - totals.total)}</td></tr>}
          </tbody></table>
        </div>

        <div className="card">
          <label>Información adicional (aparece en la factura)<input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Orden de compra, guía de remisión…" /></label>
          {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
          <div className="row" style={{ marginTop: 12 }}>
            <button className="btn" disabled={busy || !companyReady}>{busy ? "Emitiendo…" : "Emitir factura"}</button>
            <span className="muted small">Se registra la venta, sale el inventario y queda lista para enviar al SRI.</span>
          </div>
        </div>
      </form>
    </>
  );
}
