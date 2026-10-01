import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, perms, supabase, type Contact, type ContactStats, type Profile } from "../lib/supabase";
import ContactForm from "./ContactForm";
import { ID_TYPES, money } from "../lib/format";

const TYPE: Record<string, string> = { CLIENT: "Cliente", SUPPLIER: "Proveedor", BOTH: "Cliente y proveedor" };

export default function ContactsPage({ profile }: { profile: Profile | null }) {
  const can = perms(profile);
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const data = useLoad(async () => {
    const [c, s] = await Promise.all([supabase.from("contacts").select("*").order("name"), supabase.from("contact_stats").select("*")]);
    const stats = new Map((check(s) as ContactStats[]).map((x) => [x.contact_id, x]));
    return (check(c) as Contact[]).map((contact) => ({ contact, stats: stats.get(contact.id) }));
  }, []);
  const rows = (data.data ?? []).filter((r) => !search || r.contact.name.toLowerCase().includes(search.toLowerCase()) || r.contact.tax_id.includes(search));

  return (
    <>
      <h1>Clientes y proveedores</h1>
      {can.writeSuppliers && <ContactForm canClients={can.writeContacts} onSaved={data.reload} />}
      <div className="card">
        <input placeholder="Buscar por nombre, RUC o cédula…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ maxWidth: 320, marginBottom: 12 }} />
        {data.error && <div className="error">{data.error}</div>}
        <div className="table-wrap"><table>
          <thead><tr><th>Nombre</th><th>Tipo</th><th className="r">Ventas</th><th className="r">Utilidad real</th><th className="r">Te debe</th><th className="r">Le debes</th></tr></thead>
          <tbody>
            {rows.map(({ contact, stats }) => {
              const profit = (stats?.sales ?? 0) - (stats?.cogs ?? 0) - (stats?.expenses ?? 0);
              return (
                <tr key={contact.id} className="click" onClick={() => navigate(`/panel/clientes/${contact.id}`)}>
                  <td>{contact.name}<div className="muted small">{[contact.tax_id && `${ID_TYPES[contact.id_type] ?? "ID"} ${contact.tax_id}`, contact.actividad_economica, contact.phone].filter(Boolean).join(" · ")}</div></td>
                  <td>{TYPE[contact.type]}</td>
                  <td className="r num">{money(stats?.sales)}</td>
                  <td className={`r num ${contact.type === "SUPPLIER" ? "" : profit >= 0 ? "income" : "expense"}`}>{contact.type === "SUPPLIER" ? "—" : money(profit)}</td>
                  <td className="r num">{stats?.receivable ? money(stats.receivable) : "—"}</td>
                  <td className="r num">{stats?.payable ? money(stats.payable) : "—"}</td>
                </tr>
              );
            })}
            {rows.length === 0 && !data.loading && <tr><td colSpan={6} className="muted">Sin contactos.</td></tr>}
          </tbody>
        </table></div>
      </div>
    </>
  );
}
