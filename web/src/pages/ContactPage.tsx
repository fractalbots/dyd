import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, ENTRY_SELECT, perms, supabase, type Contact, type ContactStats, type Entry, type Interaction, type Profile } from "../lib/supabase";
import { fmtDate, ID_TYPES, KIND_LABEL, money, today, TYPE_LABEL } from "../lib/format";
import ContactForm, { SriBox } from "./ContactForm";

export default function ContactPage({ profile }: { profile: Profile | null }) {
  const id = Number(useParams().id);
  const can = perms(profile);
  const canEdit = can.editCrm;
  const [editing, setEditing] = useState(false);
  const data = useLoad(async () => {
    const [c, s, i, e] = await Promise.all([
      supabase.from("contacts").select("*").eq("id", id).single(),
      supabase.from("contact_stats").select("*").eq("contact_id", id).maybeSingle(),
      supabase.from("interactions").select("*").eq("contact_id", id).order("date", { ascending: false }),
      supabase.from("entries").select(ENTRY_SELECT).eq("contact_id", id).order("date", { ascending: false }).limit(50),
    ]);
    return { contact: check(c) as Contact, stats: (check(s) as ContactStats | null), interactions: check(i) as Interaction[], entries: check(e) as Entry[] };
  }, [id]);

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { contact, stats, interactions, entries } = data.data;
  const sales = stats?.sales ?? 0, cogs = stats?.cogs ?? 0, expenses = stats?.expenses ?? 0;
  const profit = sales - cogs - expenses;

  async function toggle(i: Interaction) {
    const res = await supabase.from("interactions").update({ done: !i.done }).eq("id", i.id);
    if (res.error) alert(res.error.message); else data.reload();
  }

  return (
    <>
      <Link to="/panel/clientes">← Clientes</Link>
      <div className="row" style={{ marginTop: 12 }}>
        <h1 style={{ margin: 0 }}>{contact.name}</h1><span className="spacer" />
        {!editing && (can.writeContacts || (can.writeSuppliers && contact.type !== "CLIENT")) &&
          <button className="btn secondary small" onClick={() => setEditing(true)}>Editar datos</button>}
      </div>
      {editing && <ContactForm initial={contact} canClients={can.writeContacts} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); data.reload(); }} />}
      <p className="muted">{[contact.tax_id && `${ID_TYPES[contact.id_type] ?? "ID"} ${contact.tax_id}`, contact.address, contact.phone, contact.email].filter(Boolean).join(" · ")}
        {contact.phone && <> · <a href={`https://wa.me/${contact.phone.replace(/\D/g, "")}`} target="_blank" rel="noreferrer">WhatsApp</a></>}</p>
      {(contact.actividad_economica || contact.sri_checked_at) && <div style={{ marginBottom: 16 }}><SriBox c={contact} /></div>}
      <div className="grid">
        <Kpi label="Ventas (sin IVA)" value={money(sales)} cls="income" />
        <Kpi label="Costo de lo vendido" value={money(cogs)} />
        <Kpi label="Gastos para atenderlo" value={money(expenses)} />
        <Kpi label="Utilidad real" value={`${money(profit)}${sales > 0 ? ` (${(profit * 100 / sales).toFixed(1)} %)` : ""}`} cls={profit >= 0 ? "income" : "expense"} />
        <Kpi label="Te debe" value={money(stats?.receivable)} />
        <Kpi label="Le debes" value={money(stats?.payable)} />
      </div>
      <p className="muted small">Los gastos para atenderlo son los gastos registrados con este contacto (transporte, comisiones, descuentos…).</p>

      <div className="card">
        <h2>Seguimientos</h2>
        {canEdit && <NewInteraction contactId={id} onSaved={data.reload} />}
        <table style={{ marginTop: 12 }}><tbody>
          {interactions.map((i) => (
            <tr key={i.id}>
              <td className="num">{fmtDate(i.date)}</td>
              <td><span className="pill">{KIND_LABEL[i.kind] ?? i.kind}</span> <span style={{ textDecoration: i.done ? "line-through" : undefined }}>{i.note}</span></td>
              <td className="r">{i.follow_up && <label className="check" style={{ justifyContent: "flex-end" }}>
                <input type="checkbox" checked={i.done} disabled={!canEdit} onChange={() => toggle(i)} /> Volver a contactar {fmtDate(i.follow_up)}</label>}</td>
            </tr>
          ))}
          {interactions.length === 0 && <tr><td className="muted">Sin seguimientos todavía.</td></tr>}
        </tbody></table>
      </div>

      <div className="card">
        <h2>Movimientos</h2>
        <div className="table-wrap"><table>
          <tbody>{entries.map((e) => (
            <tr key={e.id}><td className="num">{fmtDate(e.date)}</td><td>{e.description || e.category?.name || TYPE_LABEL[e.type]}</td>
              <td>{!e.is_paid && <span className="pill warn">Pendiente</span>}</td>
              <td className={`r num ${e.type === "INCOME" ? "income" : "expense"}`}>{money(e.amount)}</td></tr>
          ))}
          {entries.length === 0 && <tr><td className="muted">Sin movimientos con este contacto.</td></tr>}</tbody>
        </table></div>
      </div>
    </>
  );
}

function Kpi({ label, value, cls = "" }: { label: string; value: string; cls?: string }) {
  return <div className="card kpi"><div className="label">{label}</div><div className={`value num ${cls}`}>{value}</div></div>;
}

function NewInteraction({ contactId, onSaved }: { contactId: number; onSaved: () => void }) {
  const [kind, setKind] = useState("CALL");
  const [note, setNote] = useState("");
  const [date, setDate] = useState(today());
  const [follow, setFollow] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    const res = await supabase.from("interactions").insert({ contact_id: contactId, kind, note, date, follow_up: follow || null });
    if (res.error) alert(res.error.message); else { setNote(""); setFollow(""); onSaved(); }
  }
  return (
    <form className="form" onSubmit={submit}>
      <label>Tipo<select value={kind} onChange={(e) => setKind(e.target.value)}>{Object.entries(KIND_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      <label>¿Qué pasó?<input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Pidió cotización de 200 pallets" /></label>
      <label>Fecha<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
      <label>Volver a contactar<input type="date" value={follow} onChange={(e) => setFollow(e.target.value)} /></label>
      <button className="btn secondary">Agregar</button>
    </form>
  );
}
