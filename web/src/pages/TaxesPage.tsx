import { useState, type FormEvent } from "react";
import { useLoad } from "../lib/hooks";
import { check, perms, supabase, type Contact, type IvaMonth, type LateReceivable, type Profile, type SriCharges, type SriRate } from "../lib/supabase";
import { fmtDate, money, parseDecimal, toCents, today } from "../lib/format";

const monthName = (iso: string) => {
  const t = new Date(iso + "T12:00:00").toLocaleDateString("es-EC", { month: "long", year: "numeric" });
  return t.charAt(0).toUpperCase() + t.slice(1);
};

export default function TaxesPage({ profile }: { profile: Profile | null }) {
  const [tab, setTab] = useState<"mora" | "sri">("mora");
  return (
    <>
      <h1>Intereses e impuestos</h1>
      <div className="tabs">
        <button className={tab === "mora" ? "on" : ""} onClick={() => setTab("mora")}>Mora de clientes</button>
        <button className={tab === "sri" ? "on" : ""} onClick={() => setTab("sri")}>IVA e intereses del SRI</button>
      </div>
      {tab === "mora" ? <LateTab canEdit={perms(profile).manageAccounting} /> : <SriTab canEdit={perms(profile).manageAccounting} />}
    </>
  );
}

function LateTab({ canEdit }: { canEdit: boolean }) {
  const data = useLoad(async () => {
    const [l, c, k] = await Promise.all([
      supabase.from("late_receivables").select("*").order("due_date"),
      supabase.from("contacts").select("id, name"),
      supabase.from("company").select("late_interest_rate").eq("id", 1).maybeSingle(),
    ]);
    const names = new Map((check(c) as Pick<Contact, "id" | "name">[]).map((x) => [x.id, x.name]));
    return { rows: check(l) as LateReceivable[], names, rate: (check(k) as { late_interest_rate: number } | null)?.late_interest_rate ?? 0 };
  }, []);

  async function charge(r: LateReceivable) {
    if (!confirm(`¿Registrar ${money(r.interest)} de interés por mora como cuenta por cobrar?`)) return;
    const res = await supabase.rpc("charge_late_interest", { p_entry_id: r.entry_id });
    if (res.error) alert(res.error.message); else data.reload();
  }

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { rows, names, rate } = data.data;
  return (
    <div className="card">
      <p className="muted" style={{ marginTop: 0 }}>Cuentas por cobrar vencidas. Interés simple al {rate} % anual (se cambia en Empresa y SRI): saldo × tasa × días / 365.
        Al cobrarlo se registra como ingreso «Intereses ganados» y el conteo vuelve a empezar desde hoy.</p>
      {rate === 0 && <div className="error" style={{ marginBottom: 12 }}>La tasa de mora está en 0 %. Configúrala en Empresa y SRI.</div>}
      <div className="table-wrap"><table>
        <thead><tr><th>Cliente</th><th>Detalle</th><th>Venció</th><th className="r">Días de mora</th><th className="r">Saldo</th><th className="r">Interés</th><th /></tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.entry_id}>
              <td>{r.contact_id ? names.get(r.contact_id) : "—"}</td><td>{r.description}</td>
              <td className="num">{fmtDate(r.due_date)}</td><td className="r num">{r.days_overdue}</td>
              <td className="r num">{money(r.amount)}</td><td className="r num expense">{money(r.interest)}</td>
              <td className="r">{canEdit && r.interest > 0 && <button className="btn small secondary" onClick={() => charge(r)}>Cobrar interés</button>}</td>
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={7} className="muted">Ningún cliente está en mora. 🎉</td></tr>}
        </tbody>
      </table></div>
    </div>
  );
}

function SriTab({ canEdit }: { canEdit: boolean }) {
  const data = useLoad(async () => {
    const [m, r] = await Promise.all([
      supabase.from("iva_monthly").select("*").order("month", { ascending: false }).limit(12),
      supabase.from("sri_interest_rates").select("*").order("quarter_start", { ascending: false }),
    ]);
    return { months: check(m) as IvaMonth[], rates: check(r) as SriRate[] };
  }, []);
  const [calc, setCalc] = useState({ tax: "", due: "", pay: today() });
  const [result, setResult] = useState<SriCharges | null>(null);
  const [calcError, setCalcError] = useState<string | null>(null);

  async function compute(e?: FormEvent, preset?: { tax: number; due: string }) {
    e?.preventDefault();
    const tax = preset?.tax ?? toCents(calc.tax);
    const due = preset?.due ?? calc.due;
    if (!tax || tax <= 0 || !due) return setCalcError("Escribe el impuesto y la fecha en que vencía.");
    if (preset) setCalc({ ...calc, tax: (preset.tax / 100).toFixed(2), due: preset.due });
    const res = await supabase.rpc("sri_late_charges", { p_tax: tax, p_due: due, p_pay: calc.pay || today() });
    if (res.error) return setCalcError(res.error.message);
    setCalcError(null);
    setResult((res.data as SriCharges[])[0] ?? null);
  }

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { months, rates } = data.data;
  return (
    <>
      <div className="card">
        <h2>IVA por mes (base para el formulario 104)</h2>
        <div className="table-wrap"><table>
          <thead><tr><th>Mes</th><th className="r">Ventas (base)</th><th className="r">IVA cobrado</th><th className="r">Compras (base)</th><th className="r">IVA pagado</th><th className="r">IVA a pagar</th><th>Vence</th><th /></tr></thead>
          <tbody>
            {months.map((m) => {
              const late = m.tax_due > 0 && m.due_date < today();
              return (
                <tr key={m.month}>
                  <td>{monthName(m.month)}</td>
                  <td className="r num">{money(m.sales_base)}</td><td className="r num">{money(m.sales_tax)}</td>
                  <td className="r num">{money(m.purchases_base)}</td><td className="r num">{money(m.purchases_tax)}</td>
                  <td className={`r num ${m.tax_due > 0 ? "expense" : "income"}`}>{m.tax_due >= 0 ? money(m.tax_due) : `Crédito ${money(-m.tax_due)}`}</td>
                  <td className="num">{fmtDate(m.due_date)}</td>
                  <td>{late && <button className="btn small secondary" onClick={() => compute(undefined, { tax: m.tax_due, due: m.due_date })}>¿Y si pago tarde?</button>}</td>
                </tr>
              );
            })}
            {months.length === 0 && <tr><td colSpan={8} className="muted">Aún no hay ventas facturadas ni compras con IVA.</td></tr>}
          </tbody>
        </table></div>
        <p className="muted small">La fecha límite depende del noveno dígito del RUC. Las retenciones no están incluidas; revísalo con tu contador antes de declarar.</p>
      </div>

      <form className="card" onSubmit={compute}>
        <h2>Intereses y multa por pago tardío al SRI</h2>
        <div className="form">
          <label>Impuesto no pagado<input value={calc.tax} onChange={(e) => setCalc({ ...calc, tax: e.target.value })} inputMode="decimal" /></label>
          <label>Vencía el<input type="date" value={calc.due} onChange={(e) => setCalc({ ...calc, due: e.target.value })} /></label>
          <label>Fecha de pago<input type="date" value={calc.pay} onChange={(e) => setCalc({ ...calc, pay: e.target.value })} /></label>
          <button className="btn">Calcular</button>
        </div>
        {calcError && <div className="error" style={{ marginTop: 12 }}>{calcError}</div>}
        {result && (
          <table style={{ maxWidth: 420, marginTop: 12 }}><tbody>
            <tr><td>Meses o fracción de retraso</td><td className="r num">{result.months}</td></tr>
            <tr><td>Interés de mora tributaria</td><td className="r num">{money(result.interest)}</td></tr>
            <tr><td>Multa (3 % mensual, máx. 100 %)</td><td className="r num">{money(result.fine)}</td></tr>
            <tr><td><strong>Total a pagar</strong></td><td className="r num"><strong>{money(result.total)}</strong></td></tr>
          </tbody></table>
        )}
        {result?.missing_rates && <div className="error" style={{ marginTop: 12 }}>Faltan tasas trimestrales del SRI para algunos meses; el interés sale incompleto. Agrégalas abajo.</div>}
        <p className="muted small">Cada mes o fracción cuenta completo (art. 21 del Código Tributario). La multa es la de declaración tardía con impuesto a pagar (art. 100 LRTI).</p>
      </form>

      <RatesCard rates={rates} canEdit={canEdit} onSaved={data.reload} />
    </>
  );
}

function RatesCard({ rates, canEdit, onSaved }: { rates: SriRate[]; canEdit: boolean; onSaved: () => void }) {
  const [quarter, setQuarter] = useState("");
  const [rate, setRate] = useState("");
  const [error, setError] = useState<string | null>(null);
  async function save(e: FormEvent) {
    e.preventDefault();
    const r = parseDecimal(rate);
    if (!/^\d{4}-(01|04|07|10)-01$/.test(quarter)) return setError("El trimestre empieza el 1 de enero, abril, julio u octubre.");
    if (r == null || r < 0) return setError("Escribe la tasa mensual en %.");
    const res = await supabase.from("sri_interest_rates").upsert({ quarter_start: quarter, monthly_rate: r });
    if (res.error) return setError(res.error.message);
    setError(null); setRate(""); onSaved();
  }
  async function remove(q: string) {
    const res = await supabase.from("sri_interest_rates").delete().eq("quarter_start", q);
    if (res.error) alert(res.error.message); else onSaved();
  }
  const year = new Date().getFullYear();
  const quarters = [year, year - 1].flatMap((y) => ["10", "07", "04", "01"].map((m) => `${y}-${m}-01`));
  return (
    <div className="card">
      <h2>Tasas de interés del SRI (% mensual por trimestre)</h2>
      <p className="muted small" style={{ marginTop: 0 }}>El SRI publica cada trimestre la tasa de interés por mora tributaria. Cópiala de sri.gob.ec.</p>
      {canEdit && <form className="form" onSubmit={save} style={{ marginBottom: 12 }}>
        <label>Trimestre<select value={quarter} onChange={(e) => setQuarter(e.target.value)}>
          <option value="">Elige…</option>{quarters.map((q) => <option key={q} value={q}>{monthName(q)}</option>)}</select></label>
        <label>Tasa mensual %<input value={rate} onChange={(e) => setRate(e.target.value)} inputMode="decimal" placeholder="0,9" /></label>
        <button className="btn secondary">Guardar tasa</button>
      </form>}
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      <table><tbody>
        {rates.map((r) => <tr key={r.quarter_start}><td>Trimestre desde {monthName(r.quarter_start).toLowerCase()}</td><td className="r num">{Number(r.monthly_rate).toLocaleString("es-EC", { maximumFractionDigits: 4 })} %</td>
          <td className="r">{canEdit && <button className="btn small danger" onClick={() => remove(r.quarter_start)}>Quitar</button>}</td></tr>)}
        {rates.length === 0 && <tr><td className="muted">Sin tasas registradas.</td></tr>}
      </tbody></table>
    </div>
  );
}
