import { useState } from "react";
import { Link } from "react-router-dom";
import { useLoad } from "../lib/hooks";
import { check, isConfigured, supabase, type CatalogItem } from "../lib/supabase";
import { money } from "../lib/format";

const business = (import.meta.env.VITE_BUSINESS_NAME as string | undefined) || "Pallets DYD";
const whatsapp = (import.meta.env.VITE_WHATSAPP as string | undefined) || "";

/** Catálogo público: lo ve cualquier persona, sin iniciar sesión. */
export default function CatalogPage() {
  const { data, error, loading } = useLoad(async () => {
    if (!isConfigured) return [] as CatalogItem[];
    return check(await supabase.from("catalog").select("*").order("name")) as CatalogItem[];
  }, []);

  return (
    <>
      <header className="hero">
        <div className="topbar">
          <img src="/icon.svg" alt="" width={36} height={36} />
          <strong>{business}</strong>
          <span className="spacer" />
          <Link to="/login" style={{ color: "#fff", opacity: 0.8 }}>Ingresar</Link>
        </div>
        <h1>Pallets de madera para tu operación</h1>
        <p>Fabricamos pallets estándar, reforzados y a la medida. Entrega rápida y precios por volumen.</p>
        {whatsapp && (
          <a className="btn" href={`https://wa.me/${whatsapp}?text=${encodeURIComponent("Hola, quiero cotizar pallets")}`} target="_blank" rel="noreferrer">
            Cotizar por WhatsApp
          </a>
        )}
      </header>
      <section className="catalog">
        {loading && <div className="card">Cargando catálogo…</div>}
        {error && <div className="card error">No se pudo cargar el catálogo: {error}</div>}
        {!loading && !error && (data?.length ?? 0) === 0 && (
          <div className="card">Pronto publicaremos nuestros productos.</div>
        )}
        {data?.map((p) => <ProductCard key={p.id} p={p} />)}
      </section>
      <footer className="footer"><Link to="/privacidad">Privacidad</Link> · © {new Date().getFullYear()} {business}</footer>
    </>
  );
}

function ProductCard({ p }: { p: CatalogItem }) {
  const [units, setUnits] = useState("50");
  const text = `Hola, quiero cotizar ${units || "?"} ${p.unit} de "${p.name}"${p.dimensions ? ` (${p.dimensions})` : ""}.`;
  return (
    <article className="product">
      <div className="img">
        {p.image_url ? <img src={p.image_url} alt={p.name} loading="lazy" /> : <img src="/icon.svg" alt="" style={{ width: 96, height: 96 }} />}
      </div>
      <div className="body">
        <div className="row">
          <h3 style={{ margin: 0 }}>{p.name}</h3>
          <span className="spacer" />
          <span className={`pill ${p.in_stock ? "ok" : "warn"}`}>{p.in_stock ? "Disponible" : "Bajo pedido"}</span>
        </div>
        {p.dimensions && <div className="muted small">Medidas: {p.dimensions}</div>}
        {p.description && <div className="small">{p.description}</div>}
        <div className="spacer" />
        {p.sale_price > 0 && <div className="price">{money(p.sale_price)} <span className="muted small">/ {p.unit}</span></div>}
        {whatsapp && (
          <div className="row">
            <input style={{ width: 90 }} value={units} onChange={(e) => setUnits(e.target.value)} aria-label="Cantidad" inputMode="numeric" />
            <a className="btn" href={`https://wa.me/${whatsapp}?text=${encodeURIComponent(text)}`} target="_blank" rel="noreferrer">Cotizar</a>
          </div>
        )}
      </div>
    </article>
  );
}
