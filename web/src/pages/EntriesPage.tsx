import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, ENTRY_SELECT, perms, supabase, type Account, type Category, type Contact, type Entry, type EntryType, type Item, type Profile } from "../lib/supabase";
import { currentMonth, fmtDate, money, monthRange, parseDecimal, toCents, today, TYPE_LABEL } from "../lib/format";

export default function EntriesPage({ profile }: { profile: Profile | null }) {
  const [month, setMonth] = useState(currentMonth());
  const [filter, setFilter] = useState<EntryType | "ALL">("ALL");
  const [search, setSearch] = useState("");
  const canAccount = perms(profile).manageAccounting;
  const canCreate = perms(profile).createEntries;
  const entryTypes = perms(profile).entryTypes;

  const lists = useLoad(async () => {
    const [a, c, p, i] = await Promise.all([
      supabase.from("account_balances").select("*").eq("archived", false).order("name"),
      supabase.from("categories").select("*").order("name"),
      supabase.from("contacts").select("*").order("name"),
      supabase.from("item_stock").select("*").eq("archived", false).order("name"),
    ]);
    return { accounts: check(a) as Account[], categories: check(c) as Category[], contacts: check(p) as Contact[], items: check(i) as Item[] };
  }, []);

  const entries = useLoad(async () => {
    const { from, to } = monthRange(month);
    return check(await supabase.from("entries").select(ENTRY_SELECT).gte("date", from).lte("date", to)
      .order("date", { ascending: false }).order("id", { ascending: false })) as Entry[];
  }, [month]);

  const rows = (entries.data ?? []).filter((e) =>
    (filter === "ALL" || e.type === filter) &&
    (!search || [e.description, e.reference, e.contact?.name, e.category?.name].some((x) => x?.toLowerCase().includes(search.toLowerCase()))));
  const income = rows.filter((e) => e.type === "INCOME").reduce((a, e) => a + e.amount, 0);
  const expense = rows.filter((e) => e.type === "EXPENSE").reduce((a, e) => a + e.amount, 0);

  async function remove(id: number) {
    if (!confirm("¿Eliminar este movimiento? Los saldos e inventario se recalculan.")) return;
    try { check(await supabase.from("entries").delete().eq("id", id)); entries.reload(); } catch (e) { alert((e as Error).message); }
  }

  async function markPaid(id: number) {
    try { check(await supabase.from("entries").update({ is_paid: true }).eq("id", id)); entries.reload(); } catch (e) { alert((e as Error).message); }
  }

  function exportCsv() {
    const header = "Fecha;Tipo;Descripción;Categoría;Cuenta;Contacto;Referencia;Estado;Monto\n";
    const body = rows.map((e) => [e.date, TYPE_LABEL[e.type], e.description, e.category?.name ?? "", e.account?.name ?? "", e.contact?.name ?? "",
      e.reference, e.is_paid ? "Pagado" : "Pendiente", ((e.type === "EXPENSE" ? -1 : 1) * e.amount / 100).toFixed(2)]
      .map((v) => (/[;"\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v)).join(";")).join("\n");
    const blob = new Blob(["﻿" + header + body], { type: "text/csv;charset=utf-8" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob); a.download = `DYD_movimientos_${month}.csv`; a.click();
  }

  return (
    <>
      <div className="row"><h1>Movimientos</h1><span className="spacer" /><button className="btn secondary" onClick={exportCsv}>Exportar a Excel (CSV)</button></div>
      {lists.data && canCreate && <NewEntry {...lists.data} types={entryTypes} onSaved={() => { entries.reload(); lists.reload(); }} />}
      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} style={{ width: 170 }} />
          <select value={filter} onChange={(e) => setFilter(e.target.value as EntryType | "ALL")} style={{ width: 170 }}>
            <option value="ALL">Todos</option><option value="INCOME">Ingresos</option><option value="EXPENSE">Gastos</option><option value="TRANSFER">Transferencias</option>
          </select>
          <input placeholder="Buscar…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ maxWidth: 260 }} />
          <span className="spacer" />
          <span className="income num">+ {money(income)}</span><span className="expense num">− {money(expense)}</span>
        </div>
        {entries.error && <div className="error">{entries.error}</div>}
        <div className="table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Detalle</th><th>Cuenta</th><th>Estado</th><th className="r">Monto</th><th /></tr></thead>
          <tbody>
            {rows.map((e) => (
              <tr key={e.id}>
                <td className="num">{fmtDate(e.date)}</td>
                <td>{e.description || e.category?.name || TYPE_LABEL[e.type]}<div className="muted small">{[e.category?.name, e.contact?.name, e.reference && `Fact. ${e.reference}`, e.tax_amount > 0 && `IVA ${money(e.tax_amount)}`].filter(Boolean).join(" · ")}</div></td>
                <td>{e.account?.name}{e.to_account ? ` → ${e.to_account.name}` : ""}</td>
                <td>{e.is_paid ? <span className="pill ok">Pagado</span> : <span className="pill warn">{e.type === "INCOME" ? "Por cobrar" : "Por pagar"}{e.due_date ? ` · ${fmtDate(e.due_date)}` : ""}</span>}</td>
                <td className={`r num ${e.type === "INCOME" ? "income" : e.type === "EXPENSE" ? "expense" : ""}`}>{e.type === "EXPENSE" ? "− " : e.type === "INCOME" ? "+ " : ""}{money(e.amount)}</td>
                <td className="r">
                  {canAccount && !e.is_paid && <button className="btn small secondary" onClick={() => markPaid(e.id)}>Marcar pagado</button>}{" "}
                  {e.invoice_id ? <Link className="small" to={`/panel/facturas/${e.invoice_id}`}>Ver factura</Link>
                    : canAccount && <button className="btn small danger" onClick={() => remove(e.id)}>Eliminar</button>}
                </td>
              </tr>
            ))}
            {rows.length === 0 && !entries.loading && <tr><td colSpan={6} className="muted">Sin movimientos en este periodo.</td></tr>}
          </tbody>
        </table></div>
      </div>
    </>
  );
}

function NewEntry({ accounts, categories, contacts, items, types, onSaved }: {
  accounts: Account[]; categories: Category[]; contacts: Contact[]; items: Item[]; types: EntryType[]; onSaved: () => void;
}) {
  const [type, setType] = useState<EntryType>(types.includes("EXPENSE") ? "EXPENSE" : types[0]);
  const [amount, setAmount] = useState("");
  const [tax, setTax] = useState("");
  const [date, setDate] = useState(today());
  const [description, setDescription] = useState("");
  const [accountId, setAccountId] = useState(String(accounts[0]?.id ?? ""));
  const [toAccountId, setToAccountId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [contactId, setContactId] = useState("");
  const [itemId, setItemId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [paid, setPaid] = useState(true);
  const [due, setDue] = useState(today());
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const cents = toCents(amount);
    const q = itemId ? parseDecimal(quantity) : null;
    const taxCents = type === "TRANSFER" ? 0 : toCents(tax) ?? 0;
    if (!cents || cents <= 0) return setError("Escribe un monto válido.");
    if (taxCents < 0 || taxCents > cents) return setError("El IVA no puede ser mayor que el total.");
    if (!accountId) return setError("Elige una cuenta.");
    if (type === "TRANSFER" && (!toAccountId || toAccountId === accountId)) return setError("Elige una cuenta destino distinta.");
    if (itemId && (!q || q <= 0)) return setError("Escribe la cantidad del artículo.");
    setBusy(true); setError(null);
    const transfer = type === "TRANSFER";
    const { error } = await supabase.rpc("save_entry", {
      p_entry: {
        type, amount: cents, tax_amount: taxCents, date, account_id: Number(accountId), description, reference,
        to_account_id: transfer ? Number(toAccountId) : null,
        category_id: !transfer && categoryId ? Number(categoryId) : null,
        contact_id: !transfer && contactId ? Number(contactId) : null,
        is_paid: transfer || paid, due_date: !transfer && !paid ? due : null,
      },
      p_item_id: !transfer && itemId ? Number(itemId) : null,
      p_quantity: !transfer && itemId ? q : null,
    });
    setBusy(false);
    if (error) return setError(/row-level security/i.test(error.message) ? "No tienes permiso para registrar este tipo de movimiento." : error.message);
    setAmount(""); setTax(""); setDescription(""); setItemId(""); setQuantity(""); setReference("");
    onSaved();
  }

  function pickItem(id: string) {
    setItemId(id);
    const it = items.find((i) => String(i.id) === id);
    if (it && !description) setDescription((type === "INCOME" ? "Venta de " : "Compra de ") + it.name);
  }

  return (
    <form className="card" onSubmit={submit}>
      <div className="row" style={{ marginBottom: 12 }}>
        <h2 style={{ margin: 0 }}>Registrar</h2>
        <div className="tabs" style={{ margin: 0 }}>
          {types.map((t) => (
            <button type="button" key={t} className={type === t ? "on" : ""} onClick={() => { setType(t); setCategoryId(""); }}>{TYPE_LABEL[t]}</button>
          ))}
        </div>
      </div>
      <div className="form">
        <label>{type === "TRANSFER" ? "Monto" : "Total (con IVA)"}<input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" placeholder="0,00" /></label>
        {type !== "TRANSFER" && <label>IVA incluido
          <div className="row" style={{ flexWrap: "nowrap", gap: 6 }}>
            <input value={tax} onChange={(e) => setTax(e.target.value)} inputMode="decimal" placeholder="0,00" />
            <button type="button" className="btn secondary small" style={{ whiteSpace: "nowrap" }} title="Calcula el IVA 15 % incluido en el total"
              onClick={() => { const c = toCents(amount); if (c) setTax((Math.round(c * 15 / 115) / 100).toFixed(2)); }}>15 %</button>
          </div></label>}
        <label>Fecha<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label>Descripción<input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
        <label>{type === "TRANSFER" ? "Desde" : "Cuenta"}
          <select value={accountId} onChange={(e) => setAccountId(e.target.value)}>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
        </label>
        {type === "TRANSFER" ? (
          <label>Hacia<select value={toAccountId} onChange={(e) => setToAccountId(e.target.value)}><option value="">Elige…</option>{accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</select></label>
        ) : (
          <>
            <label>Categoría<select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}><option value="">Sin categoría</option>{categories.filter((c) => c.type === type).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
            <label>{type === "INCOME" ? "Cliente" : "Proveedor"}<select value={contactId} onChange={(e) => setContactId(e.target.value)}><option value="">Sin contacto</option>{contacts.filter((c) => c.type === "BOTH" || c.type === (type === "INCOME" ? "CLIENT" : "SUPPLIER")).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
            <label>{type === "INCOME" ? "Producto vendido" : "Material comprado"}<select value={itemId} onChange={(e) => pickItem(e.target.value)}><option value="">No afecta inventario</option>{items.map((i) => <option key={i.id} value={i.id}>{i.name} ({i.stock} {i.unit})</option>)}</select></label>
            {itemId && <label>Cantidad<input value={quantity} onChange={(e) => setQuantity(e.target.value)} inputMode="decimal" /></label>}
            <label>N.º factura del proveedor / comprobante<input value={reference} onChange={(e) => setReference(e.target.value)} /></label>
            <label className="check"><input type="checkbox" checked={paid} onChange={(e) => setPaid(e.target.checked)} /> {type === "INCOME" ? "Ya se cobró" : "Ya se pagó"}</label>
            {!paid && <label>Vence<input type="date" value={due} onChange={(e) => setDue(e.target.value)} /></label>}
          </>
        )}
        <button className="btn" disabled={busy}>{busy ? "Guardando…" : "Guardar"}</button>
      </div>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
    </form>
  );
}
