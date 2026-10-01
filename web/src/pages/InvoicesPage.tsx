import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, invoiceNumber, perms, supabase, type Invoice, type Profile } from "../lib/supabase";
import { currentMonth, fmtDate, INVOICE_STATUS, money, monthRange, statusPill } from "../lib/format";

export default function InvoicesPage({ profile }: { profile: Profile | null }) {
  const navigate = useNavigate();
  const [month, setMonth] = useState(currentMonth());
  const [search, setSearch] = useState("");
  const list = useLoad(async () => {
    const { from, to } = monthRange(month);
    return check(await supabase.from("invoices").select("*").gte("fecha_emision", from).lte("fecha_emision", to)
      .order("fecha_emision", { ascending: false }).order("secuencial", { ascending: false })) as Invoice[];
  }, [month]);
  const rows = (list.data ?? []).filter((i) => !search || i.buyer_name.toLowerCase().includes(search.toLowerCase()) || i.buyer_id.includes(search) || invoiceNumber(i).includes(search));
  const valid = rows.filter((i) => i.status !== "ANULADA");
  const pending = rows.filter((i) => ["PENDIENTE", "RECIBIDA", "DEVUELTA"].includes(i.status)).length;

  return (
    <>
      <div className="row"><h1>Facturación electrónica</h1><span className="spacer" />
        {perms(profile).invoice && <Link className="btn" to="/panel/facturas/nueva">Nueva factura</Link>}</div>
      <div className="grid" style={{ margin: "12px 0 16px" }}>
        <Kpi label="Facturado (sin IVA)" value={money(valid.reduce((a, i) => a + i.subtotal, 0))} />
        <Kpi label="IVA cobrado" value={money(valid.reduce((a, i) => a + i.tax, 0))} />
        <Kpi label="Total" value={money(valid.reduce((a, i) => a + i.total, 0))} />
        <Kpi label="Por enviar o corregir" value={String(pending)} warn={pending > 0} />
      </div>
      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} style={{ width: 170 }} />
          <input placeholder="Buscar cliente, RUC o número…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ maxWidth: 300 }} />
        </div>
        {list.error && <div className="error">{list.error}</div>}
        <div className="table-wrap"><table>
          <thead><tr><th>Número</th><th>Fecha</th><th>Cliente</th><th>Estado SRI</th><th className="r">Total</th></tr></thead>
          <tbody>
            {rows.map((i) => (
              <tr key={i.id} className="click" onClick={() => navigate(`/panel/facturas/${i.id}`)}>
                <td className="num">{invoiceNumber(i)}{i.ambiente === 1 && <div className="muted small">Pruebas</div>}</td>
                <td className="num">{fmtDate(i.fecha_emision)}</td>
                <td>{i.buyer_name}<div className="muted small">{i.buyer_id}</div></td>
                <td><span className={`pill ${statusPill(i.status)}`}>{INVOICE_STATUS[i.status] ?? i.status}</span>
                  {i.installments > 1 && <span className="pill" style={{ marginLeft: 6 }}>{i.installments} cuotas</span>}</td>
                <td className="r num">{money(i.total)}</td>
              </tr>
            ))}
            {rows.length === 0 && !list.loading && <tr><td colSpan={5} className="muted">Sin facturas en este mes.</td></tr>}
          </tbody>
        </table></div>
      </div>
    </>
  );
}

function Kpi({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return <div className="card kpi"><div className="label">{label}</div><div className="value num" style={warn ? { color: "var(--warn)" } : undefined}>{value}</div></div>;
}
