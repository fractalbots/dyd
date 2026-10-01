import { useEffect, useState, type ChangeEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, perms, supabase, type BomLine, type ExplosionRow, type Item, type Profile, type StockMove } from "../lib/supabase";
import { fmtDate, fromCents, IVA_CODES, money, MOVE_LABEL, parseDecimal, qty, toCents, today } from "../lib/format";

export default function ItemPage({ profile }: { profile: Profile | null }) {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const can = perms(profile);
  const canEdit = can.editItems;

  const data = useLoad(async () => {
    const [item, all, bom, moves] = await Promise.all([
      supabase.from("item_stock").select("*").eq("id", id).single(),
      supabase.from("item_stock").select("*").eq("archived", false).order("name"),
      supabase.from("bom_lines").select("*").eq("product_id", id),
      supabase.from("stock_moves").select("*").eq("item_id", id).order("date").order("id"),
    ]);
    return { item: check(item) as Item, all: check(all) as Item[], bom: check(bom) as BomLine[], moves: check(moves) as StockMove[] };
  }, [id]);

  if (data.error) return <div className="error">{data.error}</div>;
  if (!data.data) return <p className="muted">Cargando…</p>;
  const { item, all, bom, moves } = data.data;
  const isProduct = item.kind === "PRODUCT";
  const byId = new Map(all.map((i) => [i.id, i]));
  const materialsCost = bom.reduce((a, b) => a + Math.round(b.quantity * (1 + b.waste_percent / 100) * (byId.get(b.material_id)?.unit_cost ?? 0)), 0);
  const unitCost = materialsCost + item.labor_cost + item.overhead_cost;
  let running = 0;
  const kardex = moves.map((m) => ({ ...m, balance: (running += Number(m.quantity)) })).reverse();

  async function remove() {
    if (!confirm(`¿Eliminar ${item.name}? Si tiene movimientos se archivará.`)) return;
    const del = await supabase.from("items").delete().eq("id", id);
    if (del.error) await supabase.from("items").update({ archived: true }).eq("id", id);
    navigate("/panel/inventario");
  }

  return (
    <>
      <div className="row">
        <Link to="/panel/inventario">← Inventario</Link><span className="spacer" />
        {canEdit && <button className="btn danger small" onClick={remove}>Eliminar</button>}
      </div>
      <h1 style={{ marginTop: 12 }}>{item.name}</h1>
      <div className="grid">
        <div className="card kpi"><div className="label">Stock</div><div className={`value num ${item.stock < 0 ? "expense" : ""}`}>{qty(item.stock, item.unit)}</div></div>
        <div className="card kpi"><div className="label">Costo promedio</div><div className="value num">{money(item.unit_cost)}</div></div>
        <div className="card kpi"><div className="label">Valor</div><div className="value num">{money(Math.max(item.stock, 0) * item.unit_cost)}</div></div>
        {isProduct && <div className="card kpi"><div className="label">Costo de fabricación</div><div className="value num">{money(unitCost)}</div></div>}
      </div>

      {canEdit && <ItemForm item={item} canPrice={can.setPrices} onSaved={data.reload} />}

      {isProduct && (
        <div className="card">
          <h2>Lista de materiales por unidad</h2>
          <div className="table-wrap"><table>
            <thead><tr><th>Material</th><th className="r">Cantidad</th><th className="r">Desperdicio</th><th className="r">Costo/unidad</th><th /></tr></thead>
            <tbody>
              {bom.map((b) => {
                const m = byId.get(b.material_id);
                return <BomRow key={b.id} line={b} name={m?.name ?? "?"} unit={m?.unit ?? ""} cost={m?.unit_cost ?? 0} canEdit={canEdit} onChange={data.reload} />;
              })}
              {bom.length === 0 && <tr><td colSpan={5} className="muted">Agrega los materiales que lleva una unidad.</td></tr>}
            </tbody>
          </table></div>
          {canEdit && <AddBom productId={id} materials={all.filter((i) => i.kind === "MATERIAL" && !bom.some((b) => b.material_id === i.id))} onSaved={data.reload} />}
          <table style={{ marginTop: 16, maxWidth: 420 }}><tbody>
            <tr><td>Materiales</td><td className="r num">{money(materialsCost)}</td></tr>
            <tr><td>Mano de obra</td><td className="r num">{money(item.labor_cost)}</td></tr>
            <tr><td>Costos indirectos</td><td className="r num">{money(item.overhead_cost)}</td></tr>
            <tr><td><strong>Costo por unidad</strong></td><td className="r num"><strong>{money(unitCost)}</strong></td></tr>
            {item.sale_price > 0 && <tr><td>Utilidad al precio {money(item.sale_price)}</td>
              <td className={`r num ${item.sale_price >= unitCost ? "income" : "expense"}`}>{money(item.sale_price - unitCost)} ({((item.sale_price - unitCost) * 100 / item.sale_price).toFixed(1)} %)</td></tr>}
          </tbody></table>
        </div>
      )}

      {can.produce && isProduct && bom.length > 0 && <Produce item={item} onDone={data.reload} />}
      {can.adjustStock && <Adjust item={item} onDone={data.reload} />}

      <div className="card">
        <h2>Kardex</h2>
        <div className="table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Tipo</th><th>Nota</th><th className="r">Cantidad</th><th className="r">Costo unit.</th><th className="r">Saldo</th></tr></thead>
          <tbody>
            {kardex.map((m) => (
              <tr key={m.id}>
                <td className="num">{fmtDate(m.date)}</td><td>{MOVE_LABEL[m.type] ?? m.type}</td><td className="muted">{m.note}</td>
                <td className={`r num ${m.quantity > 0 ? "income" : "expense"}`}>{m.quantity > 0 ? "+" : ""}{qty(m.quantity)}</td>
                <td className="r num">{money(m.unit_cost)}</td><td className="r num">{qty(m.balance)}</td>
              </tr>
            ))}
            {kardex.length === 0 && <tr><td colSpan={6} className="muted">Sin movimientos.</td></tr>}
          </tbody>
        </table></div>
      </div>
    </>
  );
}

function ItemForm({ item, canPrice, onSaved }: { item: Item; canPrice: boolean; onSaved: () => void }) {
  const [f, setF] = useState({
    name: item.name, unit: item.unit, sku: item.sku, cost: fromCents(item.unit_cost), price: fromCents(item.sale_price),
    labor: fromCents(item.labor_cost), overhead: fromCents(item.overhead_cost), min: String(item.min_stock || ""),
    is_public: item.is_public, description: item.description, dimensions: item.dimensions, iva_code: item.iva_code || "4",
  });
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.type === "checkbox" ? (e.target as HTMLInputElement).checked : e.target.value });
  const isProduct = item.kind === "PRODUCT";

  async function save() {
    try {
      check(await supabase.from("items").update({
        name: f.name.trim(), unit: f.unit.trim() || "und", sku: f.sku.trim(),
        unit_cost: isProduct ? item.unit_cost : toCents(f.cost) ?? 0,
        ...(canPrice ? { sale_price: toCents(f.price) ?? 0 } : {}), labor_cost: toCents(f.labor) ?? 0, overhead_cost: toCents(f.overhead) ?? 0,
        min_stock: parseDecimal(f.min) ?? 0, is_public: f.is_public, description: f.description, dimensions: f.dimensions, iva_code: f.iva_code,
      }).eq("id", item.id));
      setMsg("Cambios guardados"); setError(null); onSaved();
    } catch (e) { setError((e as Error).message); }
  }

  async function upload(file: File) {
    try {
      const ext = file.name.split(".").pop() || "jpg";
      const path = `productos/${item.id}-${Date.now()}.${ext}`;
      check(await supabase.storage.from("catalogo").upload(path, file, { upsert: true }));
      const url = supabase.storage.from("catalogo").getPublicUrl(path).data.publicUrl;
      check(await supabase.from("items").update({ image_url: url }).eq("id", item.id));
      setMsg("Foto publicada"); onSaved();
    } catch (e) { setError((e as Error).message); }
  }

  return (
    <div className="card">
      <h2>Datos</h2>
      <div className="form">
        <label>Nombre<input value={f.name} onChange={set("name")} /></label>
        <label>Unidad<input value={f.unit} onChange={set("unit")} /></label>
        <label>Código<input value={f.sku} onChange={set("sku")} /></label>
        {!isProduct && <label>Costo por unidad<input value={f.cost} onChange={set("cost")} inputMode="decimal" /></label>}
        {isProduct && <>
          <label>Precio de venta<input value={f.price} onChange={set("price")} inputMode="decimal" disabled={!canPrice}
            title={canPrice ? undefined : "Lo asigna contabilidad"} />{!canPrice && <span className="small">Lo asigna contabilidad</span>}</label>
          <label>Mano de obra por unidad<input value={f.labor} onChange={set("labor")} inputMode="decimal" /></label>
          <label>Costos indirectos por unidad<input value={f.overhead} onChange={set("overhead")} inputMode="decimal" /></label>
        </>}
        <label>Stock mínimo<input value={f.min} onChange={set("min")} inputMode="decimal" /></label>
        <label>IVA en la factura<select value={f.iva_code} onChange={set("iva_code")}>
          {Object.entries(IVA_CODES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
      </div>
      {isProduct && (
        <div className="form" style={{ marginTop: 12 }}>
          <label className="check"><input type="checkbox" checked={f.is_public} onChange={set("is_public")} /> Mostrar en el catálogo web</label>
          <label>Medidas<input value={f.dimensions} onChange={set("dimensions")} placeholder="1,20 × 1,00 m" /></label>
          <label style={{ gridColumn: "1 / -1" }}>Descripción para el catálogo<textarea rows={2} value={f.description} onChange={set("description")} /></label>
          <label>Foto del catálogo<input type="file" accept="image/*" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} /></label>
          {item.image_url && <img src={item.image_url} alt="" style={{ width: 120, borderRadius: 12 }} />}
        </div>
      )}
      <div className="row" style={{ marginTop: 12 }}>
        <button className="btn" onClick={save}>Guardar cambios</button>
        {msg && <span className="income">{msg}</span>}
        {error && <span className="expense">{error}</span>}
      </div>
    </div>
  );
}

function BomRow({ line, name, unit, cost, canEdit, onChange }: { line: BomLine; name: string; unit: string; cost: number; canEdit: boolean; onChange: () => void }) {
  const [q, setQ] = useState(String(line.quantity));
  const [w, setW] = useState(String(line.waste_percent));
  const dirty = q !== String(line.quantity) || w !== String(line.waste_percent);
  async function save() {
    const quantity = parseDecimal(q);
    if (!quantity || quantity <= 0) return alert("Cantidad no válida");
    const res = await supabase.from("bom_lines").update({ quantity, waste_percent: parseDecimal(w) ?? 0 }).eq("id", line.id);
    if (res.error) alert(res.error.message); else onChange();
  }
  async function remove() {
    const res = await supabase.from("bom_lines").delete().eq("id", line.id);
    if (res.error) alert(res.error.message); else onChange();
  }
  return (
    <tr>
      <td>{name}</td>
      <td className="r">{canEdit ? <input value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 90 }} /> : qty(line.quantity)} {unit}</td>
      <td className="r">{canEdit ? <input value={w} onChange={(e) => setW(e.target.value)} style={{ width: 70 }} /> : line.waste_percent} %</td>
      <td className="r num">{money(Math.round(line.quantity * (1 + line.waste_percent / 100) * cost))}</td>
      <td className="r">{canEdit && <>{dirty && <button className="btn small" onClick={save}>Guardar</button>} <button className="btn small danger" onClick={remove}>Quitar</button></>}</td>
    </tr>
  );
}

function AddBom({ productId, materials, onSaved }: { productId: number; materials: Item[]; onSaved: () => void }) {
  const [materialId, setMaterialId] = useState("");
  const [q, setQ] = useState("");
  const [w, setW] = useState("0");
  async function add() {
    const quantity = parseDecimal(q);
    if (!materialId || !quantity || quantity <= 0) return alert("Elige el material y la cantidad");
    const res = await supabase.from("bom_lines").insert({ product_id: productId, material_id: Number(materialId), quantity, waste_percent: parseDecimal(w) ?? 0 });
    if (res.error) alert(res.error.message); else { setMaterialId(""); setQ(""); onSaved(); }
  }
  return (
    <div className="form" style={{ marginTop: 12 }}>
      <label>Material<select value={materialId} onChange={(e) => setMaterialId(e.target.value)}><option value="">Elige…</option>{materials.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}</select></label>
      <label>Cantidad por unidad<input value={q} onChange={(e) => setQ(e.target.value)} inputMode="decimal" /></label>
      <label>Desperdicio %<input value={w} onChange={(e) => setW(e.target.value)} inputMode="decimal" /></label>
      <button className="btn secondary" onClick={add}>Agregar material</button>
    </div>
  );
}

function Produce({ item, onDone }: { item: Item; onDone: () => void }) {
  const [q, setQ] = useState("10");
  const [date, setDate] = useState(today());
  const [note, setNote] = useState("");
  const [rows, setRows] = useState<ExplosionRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const quantity = parseDecimal(q) ?? 0;

  useEffect(() => {
    if (quantity <= 0) { setRows([]); return; }
    supabase.rpc("explode_bom", { p_product_id: item.id, p_quantity: quantity })
      .then(({ data, error }) => { if (error) setError(error.message); else setRows((data ?? []) as ExplosionRow[]); });
  }, [item.id, quantity]);

  const shortage = rows.some((r) => r.shortage > 0);
  const total = rows.reduce((a, r) => a + r.cost, 0) + Math.round((item.labor_cost + item.overhead_cost) * quantity);

  async function produce() {
    const { error } = await supabase.rpc("produce", { p_product_id: item.id, p_quantity: quantity, p_date: date, p_note: note });
    if (error) setError(error.message); else { setError(null); setNote(""); onDone(); }
  }

  return (
    <div className="card">
      <h2>Producir</h2>
      <div className="form">
        <label>Cantidad ({item.unit})<input value={q} onChange={(e) => setQ(e.target.value)} inputMode="decimal" /></label>
        <label>Fecha<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label>Nota<input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Lote, pedido…" /></label>
        <button className="btn" disabled={quantity <= 0 || shortage || rows.length === 0} onClick={produce}>Producir</button>
      </div>
      <div className="table-wrap" style={{ marginTop: 12 }}><table>
        <thead><tr><th>Material</th><th className="r">Consume</th><th className="r">Disponible</th><th className="r">Costo</th></tr></thead>
        <tbody>{rows.map((r) => (
          <tr key={r.material_id}><td>{r.material_name}</td><td className="r num">{qty(r.required, r.unit)}</td>
            <td className={`r num ${r.shortage > 0 ? "expense" : ""}`}>{qty(r.available)}{r.shortage > 0 && ` (faltan ${qty(r.shortage)})`}</td><td className="r num">{money(r.cost)}</td></tr>
        ))}</tbody>
      </table></div>
      <p>Costo del lote: <strong className="num">{money(total)}</strong>{quantity > 0 && <> · por unidad <strong className="num">{money(Math.round(total / quantity))}</strong></>}</p>
      {shortage && <div className="error">No hay material suficiente para este lote.</div>}
      {error && <div className="error">{error}</div>}
    </div>
  );
}

function Adjust({ item, onDone }: { item: Item; onDone: () => void }) {
  const [q, setQ] = useState("");
  const [inbound, setInbound] = useState(true);
  const [note, setNote] = useState("");
  async function save() {
    const n = parseDecimal(q);
    if (!n || n <= 0) return alert("Cantidad no válida");
    const res = await supabase.from("stock_moves").insert({
      item_id: item.id, type: "ADJUSTMENT", quantity: inbound ? n : -n, unit_cost: item.unit_cost, date: today(), note: note || "Ajuste de inventario",
    });
    if (res.error) alert(res.error.message); else { setQ(""); setNote(""); onDone(); }
  }
  return (
    <div className="card">
      <h2>Ajustar stock</h2>
      <div className="form">
        <label>Tipo<select value={inbound ? "in" : "out"} onChange={(e) => setInbound(e.target.value === "in")}><option value="in">Entrada</option><option value="out">Salida</option></select></label>
        <label>Cantidad<input value={q} onChange={(e) => setQ(e.target.value)} inputMode="decimal" /></label>
        <label>Motivo<input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Conteo físico, merma…" /></label>
        <button className="btn secondary" onClick={save}>Guardar ajuste</button>
      </div>
    </div>
  );
}
