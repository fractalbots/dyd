import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, perms, supabase, type Item, type ItemKind, type Profile } from "../lib/supabase";
import { fmtDate, money, QUALITY_LABEL, parseDecimal, qty, toCents, today } from "../lib/format";

interface ProductionRow { id: number; product_id: number; quantity: number; date: string; material_cost: number; labor_cost: number; overhead_cost: number; note: string; quality_status: string; quality_note: string; product: { name: string; unit: string } | null }

export default function InventoryPage({ profile }: { profile: Profile | null }) {
  const [tab, setTab] = useState<ItemKind | "PROD">("PRODUCT");
  const navigate = useNavigate();
  const canEdit = perms(profile).editItems;
  const canInspect = perms(profile).inspect;
  const items = useLoad(async () => check(await supabase.from("item_stock").select("*").eq("archived", false).order("name")) as Item[], []);
  const production = useLoad(async () => check(await supabase.from("production_orders").select("*, product:items(name,unit)").order("date", { ascending: false }).limit(100)) as ProductionRow[], []);

  const list = (items.data ?? []).filter((i) => i.kind === tab);
  const value = (items.data ?? []).reduce((a, i) => a + Math.max(i.stock, 0) * i.unit_cost, 0);

  return (
    <>
      <div className="row"><h1>Inventario y catálogo</h1><span className="spacer" /><span className="muted">Valor total</span><strong className="num">{money(value)}</strong></div>
      <div className="tabs">
        <button className={tab === "PRODUCT" ? "on" : ""} onClick={() => setTab("PRODUCT")}>Productos</button>
        <button className={tab === "MATERIAL" ? "on" : ""} onClick={() => setTab("MATERIAL")}>Materiales</button>
        <button className={tab === "PROD" ? "on" : ""} onClick={() => setTab("PROD")}>Producción</button>
      </div>
      {items.error && <div className="error">{items.error}</div>}
      {tab !== "PROD" ? (
        <>
          {canEdit && <NewItem kind={tab} canStock={perms(profile).adjustStock} canPrice={perms(profile).setPrices} onSaved={items.reload} />}
          <div className="card table-wrap"><table>
            <thead><tr><th>Nombre</th><th className="r">Stock</th><th className="r">Costo unit.</th>{tab === "PRODUCT" && <th className="r">Precio</th>}<th className="r">Valor</th><th /></tr></thead>
            <tbody>
              {list.map((i) => (
                <tr key={i.id} className="click" onClick={() => navigate(`/panel/inventario/${i.id}`)}>
                  <td>{i.name}{i.sku && <span className="muted small"> · {i.sku}</span>}</td>
                  <td className={`r num ${i.stock < 0 ? "expense" : ""}`}>{qty(i.stock, i.unit)}</td>
                  <td className="r num">{money(i.unit_cost)}</td>
                  {tab === "PRODUCT" && <td className="r num">{money(i.sale_price)}</td>}
                  <td className="r num">{money(Math.max(i.stock, 0) * i.unit_cost)}</td>
                  <td className="r">
                    {i.min_stock > 0 && i.stock < i.min_stock && <span className="pill bad">Bajo mínimo</span>}{" "}
                    {i.is_public && <span className="pill ok">En catálogo</span>}
                  </td>
                </tr>
              ))}
              {list.length === 0 && !items.loading && <tr><td colSpan={6} className="muted">Sin artículos.</td></tr>}
            </tbody>
          </table></div>
        </>
      ) : (
        <div className="card table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Producto</th><th className="r">Cantidad</th><th className="r">Materiales</th><th className="r">Mano de obra + ind.</th><th className="r">Costo unit.</th><th>Calidad</th></tr></thead>
          <tbody>
            {(production.data ?? []).map((p) => {
              const total = p.material_cost + p.labor_cost + p.overhead_cost;
              return (
                <tr key={p.id} className="click" onClick={() => navigate(`/panel/inventario/${p.product_id}`)}>
                  <td className="num">{fmtDate(p.date)}</td><td>{p.product?.name}<div className="muted small">{p.note}</div></td>
                  <td className="r num">{qty(p.quantity, p.product?.unit)}</td><td className="r num">{money(p.material_cost)}</td>
                  <td className="r num">{money(p.labor_cost + p.overhead_cost)}</td><td className="r num">{money(Math.round(total / p.quantity))}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {canInspect ? <Inspect order={p} onDone={production.reload} /> : <QualityPill status={p.quality_status} note={p.quality_note} />}
                  </td>
                </tr>
              );
            })}
            {(production.data ?? []).length === 0 && <tr><td colSpan={7} className="muted">Aún no hay órdenes de producción.</td></tr>}
          </tbody>
        </table></div>
      )}
    </>
  );
}

function NewItem({ kind, canStock, canPrice, onSaved }: { kind: ItemKind; canStock: boolean; canPrice: boolean; onSaved: () => void }) {
  const [name, setName] = useState("");
  const [unit, setUnit] = useState("und");
  const [cost, setCost] = useState("");
  const [price, setPrice] = useState("");
  const [stock, setStock] = useState("");
  const [minStock, setMinStock] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return setError("Escribe el nombre.");
    try {
      const created = check(await supabase.from("items").insert({
        name: name.trim(), kind, unit: unit.trim() || "und",
        unit_cost: kind === "MATERIAL" ? toCents(cost) ?? 0 : 0,
        sale_price: kind === "PRODUCT" && canPrice ? toCents(price) ?? 0 : 0,
        min_stock: parseDecimal(minStock) ?? 0,
      }).select().single()) as Item;
      const initial = parseDecimal(stock);
      if (initial && canStock) {
        check(await supabase.from("stock_moves").insert({ item_id: created.id, type: "ADJUSTMENT", quantity: initial, unit_cost: created.unit_cost, date: today(), note: "Inventario inicial" }));
      }
      setName(""); setCost(""); setPrice(""); setStock(""); setMinStock(""); setError(null);
      onSaved();
    } catch (err) { setError((err as Error).message); }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>{kind === "PRODUCT" ? "Nuevo producto" : "Nuevo material"}</h2>
      <div className="form">
        <label>Nombre<input value={name} onChange={(e) => setName(e.target.value)} /></label>
        <label>Unidad<input value={unit} onChange={(e) => setUnit(e.target.value)} /></label>
        {kind === "MATERIAL" ? <label>Costo por unidad<input value={cost} onChange={(e) => setCost(e.target.value)} inputMode="decimal" /></label>
          : canPrice ? <label>Precio de venta<input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" /></label>
          : <p className="muted small">El precio de venta lo asigna contabilidad.</p>}
        {canStock && <label>Stock inicial<input value={stock} onChange={(e) => setStock(e.target.value)} inputMode="decimal" /></label>}
        <label>Stock mínimo<input value={minStock} onChange={(e) => setMinStock(e.target.value)} inputMode="decimal" /></label>
        <button className="btn">Crear</button>
      </div>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
    </form>
  );
}

function QualityPill({ status, note }: { status: string; note: string }) {
  const cls = status === "APROBADO" ? "pill ok" : status === "RECHAZADO" ? "pill bad" : "pill";
  return <><span className={cls}>{QUALITY_LABEL[status] ?? status}</span>{note && <div className="muted small">{note}</div>}</>;
}

/** Control de calidad: aprobar o rechazar un lote. */
function Inspect({ order, onDone }: { order: ProductionRow; onDone: () => void }) {
  const [note, setNote] = useState(order.quality_note);
  const [error, setError] = useState("");
  async function set(status: string) {
    const { error } = await supabase.rpc("inspect_production", { p_order_id: order.id, p_status: status, p_note: note });
    if (error) setError(error.message); else onDone();
  }
  return (
    <div style={{ minWidth: 220 }}>
      <QualityPill status={order.quality_status} note="" />
      <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Observaciones" style={{ marginTop: 6 }} />
      <div className="row" style={{ marginTop: 6, gap: 6 }}>
        <button className="btn small" onClick={() => set("APROBADO")}>Aprobar</button>
        <button className="btn small danger" onClick={() => set("RECHAZADO")}>Rechazar</button>
      </div>
      {error && <div className="error small">{error}</div>}
    </div>
  );
}
