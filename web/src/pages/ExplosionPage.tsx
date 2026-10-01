import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, supabase, type ExplosionRow, type Item } from "../lib/supabase";
import { money, parseDecimal, qty } from "../lib/format";

export default function ExplosionPage() {
  const [params] = useSearchParams();
  const products = useLoad(async () => check(await supabase.from("item_stock").select("*").eq("kind", "PRODUCT").eq("archived", false).order("name")) as Item[], []);
  const [productId, setProductId] = useState(params.get("producto") ?? "");
  const [q, setQ] = useState("100");
  const [rows, setRows] = useState<ExplosionRow[]>([]);
  const quantity = parseDecimal(q) ?? 0;
  const product = products.data?.find((p) => String(p.id) === productId);

  useEffect(() => {
    if (!productId || quantity <= 0) { setRows([]); return; }
    supabase.rpc("explode_bom", { p_product_id: Number(productId), p_quantity: quantity })
      .then(({ data }) => setRows((data ?? []) as ExplosionRow[]));
  }, [productId, quantity]);

  const materials = rows.reduce((a, r) => a + r.cost, 0);
  const labor = Math.round((product?.labor_cost ?? 0) * quantity);
  const overhead = Math.round((product?.overhead_cost ?? 0) * quantity);
  const total = materials + labor + overhead;
  const toBuy = rows.reduce((a, r) => a + Math.round(r.shortage * r.unit_cost), 0);
  const revenue = Math.round((product?.sale_price ?? 0) * quantity);

  return (
    <>
      <h1>Explosión de materiales</h1>
      <p className="muted">Elige un producto y cuántas unidades quieres fabricar: verás cuánto material necesitas, cuánto tienes y cuánto falta comprar.</p>
      <div className="card form">
        <label>Producto<select value={productId} onChange={(e) => setProductId(e.target.value)}>
          <option value="">Elige…</option>{products.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select></label>
        <label>Cantidad a fabricar<input value={q} onChange={(e) => setQ(e.target.value)} inputMode="decimal" /></label>
      </div>
      {product && (
        <div className="card">
          {rows.length === 0 ? <p className="muted">Este producto no tiene lista de materiales.</p> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Material</th><th className="r">Necesita</th><th className="r">Tiene</th><th className="r">Falta</th><th className="r">Costo unit.</th><th className="r">Costo</th></tr></thead>
              <tbody>{rows.map((r) => (
                <tr key={r.material_id}>
                  <td>{r.material_name}</td><td className="r num">{qty(r.required, r.unit)}</td><td className="r num">{qty(r.available)}</td>
                  <td className={`r num ${r.shortage > 0 ? "expense" : "income"}`}>{r.shortage > 0 ? qty(r.shortage) : "—"}</td>
                  <td className="r num">{money(r.unit_cost)}</td><td className="r num">{money(r.cost)}</td>
                </tr>
              ))}</tbody>
            </table></div>
          )}
          <table style={{ maxWidth: 420, marginTop: 16 }}><tbody>
            <tr><td>Materiales</td><td className="r num">{money(materials)}</td></tr>
            <tr><td>Mano de obra</td><td className="r num">{money(labor)}</td></tr>
            <tr><td>Costos indirectos</td><td className="r num">{money(overhead)}</td></tr>
            <tr><td><strong>Costo total</strong></td><td className="r num"><strong>{money(total)}</strong></td></tr>
            {quantity > 0 && <tr><td>Costo por unidad</td><td className="r num">{money(Math.round(total / quantity))}</td></tr>}
            {revenue > 0 && <tr><td>Utilidad esperada</td><td className={`r num ${revenue >= total ? "income" : "expense"}`}>{money(revenue - total)}</td></tr>}
          </tbody></table>
          {rows.length > 0 && (toBuy > 0
            ? <div className="error" style={{ marginTop: 12 }}>Debes comprar material por aprox. {money(toBuy)} para este lote.</div>
            : <div className="notice" style={{ marginTop: 12 }}>Tienes todo el material para este lote.</div>)}
        </div>
      )}
    </>
  );
}
