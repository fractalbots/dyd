import { Link } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, supabase, type Account, type Entry, type Interaction, type Item, type Profile } from "../lib/supabase";
import { currentMonth, fmtDate, money, monthRange, qty } from "../lib/format";

export default function DashboardPage({ profile }: { profile: Profile | null }) {
  const { data, error } = useLoad(async () => {
    const { from, to } = monthRange(currentMonth());
    const [accounts, entries, pending, items, follow] = await Promise.all([
      supabase.from("account_balances").select("*").eq("archived", false).order("name"),
      supabase.from("entries").select("type, amount, tax_amount").gte("date", from).lte("date", to),
      supabase.from("entries").select("type, amount").eq("is_paid", false).neq("type", "TRANSFER"),
      supabase.from("item_stock").select("*").eq("archived", false),
      supabase.from("interactions").select("*, contact:contacts(name)").eq("done", false).not("follow_up", "is", null).order("follow_up").limit(8),
    ]);
    // El IVA no es ingreso ni gasto: se cobra o se paga por cuenta del SRI.
    const e = (check(entries) as Pick<Entry, "type" | "amount" | "tax_amount">[]).map((x) => ({ type: x.type, amount: x.amount - (x.tax_amount ?? 0) }));
    const p = check(pending) as Pick<Entry, "type" | "amount">[];
    const it = check(items) as Item[];
    const sum = (list: Pick<Entry, "type" | "amount">[], t: string) => list.filter((x) => x.type === t).reduce((a, x) => a + x.amount, 0);
    return {
      accounts: check(accounts) as Account[],
      income: sum(e, "INCOME"), expense: sum(e, "EXPENSE"),
      receivable: sum(p, "INCOME"), payable: sum(p, "EXPENSE"),
      inventory: it.reduce((a, x) => a + Math.max(x.stock, 0) * x.unit_cost, 0),
      low: it.filter((x) => x.min_stock > 0 && x.stock < x.min_stock),
      follow: check(follow) as Interaction[],
    };
  }, []);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <p className="muted">Cargando…</p>;
  const total = data.accounts.reduce((a, x) => a + x.balance, 0);
  const result = data.income - data.expense;

  return (
    <>
      <h1>Hola{profile?.full_name ? `, ${profile.full_name}` : ""}</h1>
      <p className="muted">Resumen de {new Date().toLocaleDateString("es-EC", { month: "long", year: "numeric" })}</p>
      <div className="grid">
        <Kpi label="Saldo disponible" value={money(total)} />
        <Kpi label="Ingresos del mes (sin IVA)" value={money(data.income)} cls="income" />
        <Kpi label="Gastos del mes (sin IVA)" value={money(data.expense)} cls="expense" />
        <Kpi label={result >= 0 ? "Utilidad del mes" : "Pérdida del mes"} value={money(Math.abs(result))} cls={result >= 0 ? "income" : "expense"} />
        <Kpi label="Por cobrar" value={money(data.receivable)} />
        <Kpi label="Por pagar" value={money(data.payable)} />
        <Kpi label="Valor del inventario" value={money(data.inventory)} />
      </div>
      <div className="grid" style={{ marginTop: 16 }}>
        <div className="card">
          <h2>Cuentas</h2>
          <table><tbody>
            {data.accounts.map((a) => (
              <tr key={a.id}><td>{a.name}</td><td className={`r num ${a.balance < 0 ? "expense" : ""}`}>{money(a.balance)}</td></tr>
            ))}
          </tbody></table>
        </div>
        <div className="card">
          <h2>Inventario bajo el mínimo</h2>
          {data.low.length === 0 ? <p className="muted">Todo sobre el mínimo.</p> : (
            <table><tbody>
              {data.low.map((i) => (
                <tr key={i.id}><td><Link to={`/panel/inventario/${i.id}`}>{i.name}</Link></td><td className="r num expense">{qty(i.stock, i.unit)}</td><td className="r num muted">mín. {qty(i.min_stock)}</td></tr>
              ))}
            </tbody></table>
          )}
        </div>
        <div className="card">
          <h2>Próximos seguimientos</h2>
          {data.follow.length === 0 ? <p className="muted">Sin seguimientos programados.</p> : (
            <table><tbody>
              {data.follow.map((f) => (
                <tr key={f.id}>
                  <td><Link to={`/panel/clientes/${f.contact_id}`}>{f.contact?.name}</Link><div className="muted small">{f.note}</div></td>
                  <td className="r num">{fmtDate(f.follow_up)}</td>
                </tr>
              ))}
            </tbody></table>
          )}
        </div>
      </div>
    </>
  );
}

function Kpi({ label, value, cls = "" }: { label: string; value: string; cls?: string }) {
  return <div className="card kpi"><div className="label">{label}</div><div className={`value num ${cls}`}>{value}</div></div>;
}
