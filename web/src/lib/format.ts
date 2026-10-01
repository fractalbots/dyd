// Ecuador: dólares con formato es-EC. VITE_CURRENCY solo existe por compatibilidad.
const currency = (import.meta.env.VITE_CURRENCY as string | undefined) || "USD";
export const locale = "es-EC";

const moneyFmt = new Intl.NumberFormat(locale, { style: "currency", currency, maximumFractionDigits: 2 });
const qtyFmt = new Intl.NumberFormat(locale, { maximumFractionDigits: 3 });

/** Los montos viajan en centavos. */
export const money = (cents: number | null | undefined) => moneyFmt.format((cents ?? 0) / 100);
export const qty = (n: number | null | undefined, unit?: string) => qtyFmt.format(Number(n ?? 0)) + (unit ? ` ${unit}` : "");
export const toCents = (text: string): number | null => {
  const n = parseDecimal(text);
  return n == null ? null : Math.round(n * 100);
};
export const fromCents = (cents: number) => (cents ? String(cents / 100) : "");

/** Acepta "1.234,56", "1,234.56", "1500" o "0,25". */
export function parseDecimal(input: string): number | null {
  let s = input.replace(/[^\d.,-]/g, "");
  if (!s || s === "-") return null;
  const lastDot = s.lastIndexOf("."), lastComma = s.lastIndexOf(",");
  if (lastDot >= 0 && lastComma >= 0) {
    const dec = lastDot > lastComma ? "." : ",";
    s = s.split(dec === "." ? "," : ".").join("").replace(dec, ".");
  } else if (lastDot >= 0 || lastComma >= 0) {
    const sep = lastDot >= 0 ? "." : ",";
    const parts = s.split(sep);
    const decimals = parts[parts.length - 1].length;
    const intPart = parts[0].replace("-", "");
    s = parts.length === 2 && (decimals <= 2 || intPart === "" || intPart === "0") ? parts.join(".") : parts.join("");
  }
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

export const today = () => new Date().toISOString().slice(0, 10);
export const monthRange = (ym: string) => {
  const [y, m] = ym.split("-").map(Number);
  const last = new Date(y, m, 0).getDate();
  return { from: `${ym}-01`, to: `${ym}-${String(last).padStart(2, "0")}` };
};
export const currentMonth = () => today().slice(0, 7);
export const fmtDate = (iso: string | null | undefined) =>
  iso ? new Date(iso + "T12:00:00").toLocaleDateString(locale, { day: "numeric", month: "short", year: "numeric" }) : "";

export const TYPE_LABEL: Record<string, string> = { INCOME: "Ingreso", EXPENSE: "Gasto", TRANSFER: "Transferencia" };
export const MOVE_LABEL: Record<string, string> = {
  PURCHASE: "Compra", SALE: "Venta", PRODUCTION_IN: "Producción", PRODUCTION_OUT: "Consumo", ADJUSTMENT: "Ajuste",
};
export const ROLE_LABEL: Record<string, string> = {
  admin: "Administrador", contador: "Contador", vendedor: "Vendedor", bodega: "Bodega",
  produccion: "Producción", calidad: "Calidad", id: "I+D", auditor: "Auditor", compras: "Compras",
};
export const ROLE_HELP: Record<string, string> = {
  admin: "Todo, incluidos usuarios y permisos.",
  contador: "Contabilidad, precios de venta, clientes con RUC, facturación, impuestos, inventario y producción.",
  vendedor: "Seguimientos, facturas a clientes ya creados e inventario (lectura).",
  bodega: "Entradas, salidas y ajustes de stock. Sin dinero.",
  produccion: "Registra órdenes de producción y consulta la explosión.",
  calidad: "Aprueba o rechaza los lotes producidos.",
  id: "Fichas de producto, lista de materiales, costos y fotos del catálogo. El precio de venta lo pone contabilidad.",
  auditor: "Consulta todo sin modificar nada.",
  compras: "Proveedores, compras de materiales (con IVA) y entradas a bodega.",
};
export const QUALITY_LABEL: Record<string, string> = { PENDIENTE: "Pendiente", APROBADO: "Aprobado", RECHAZADO: "Rechazado" };
export const KIND_LABEL: Record<string, string> = { CALL: "Llamada", VISIT: "Visita", QUOTE: "Cotización", MESSAGE: "Mensaje", OTHER: "Otro" };

// ---------- Ecuador / SRI ----------
export const ID_TYPES: Record<string, string> = {
  "04": "RUC", "05": "Cédula", "06": "Pasaporte", "07": "Consumidor final", "08": "Identificación del exterior",
};
export const CONSUMIDOR_FINAL = "9999999999999";
export const IVA_CODES: Record<string, string> = { "4": "IVA 15 %", "5": "IVA 5 %", "0": "IVA 0 %", "6": "No objeto de IVA", "7": "Exento de IVA" };
export const ivaRate = (code: string) => (code === "4" ? 15 : code === "5" ? 5 : 0);
export const PAYMENT_METHODS: Record<string, string> = {
  "01": "Efectivo (sin sistema financiero)", "20": "Transferencia u otros con sistema financiero", "16": "Tarjeta de débito",
  "19": "Tarjeta de crédito", "17": "Dinero electrónico", "15": "Compensación de deudas", "21": "Endoso de títulos",
};
export const REGIMES: Record<string, string> = { GENERAL: "Régimen general", RIMPE_EMPRENDEDOR: "RIMPE Emprendedor", RIMPE_NEGOCIO_POPULAR: "RIMPE Negocio popular" };
export const INVOICE_STATUS: Record<string, string> = {
  PENDIENTE: "Por enviar", RECIBIDA: "En proceso SRI", AUTORIZADA: "Autorizada",
  DEVUELTA: "Devuelta", NO_AUTORIZADA: "No autorizada", ANULADA: "Anulada",
};
export const statusPill = (s: string) => (s === "AUTORIZADA" ? "ok" : s === "DEVUELTA" || s === "NO_AUTORIZADA" ? "bad" : s === "ANULADA" ? "" : "warn");

/** Mismas reglas que ec_valid_cedula en supabase/schema.sql. */
export function isValidCedula(id: string) {
  if (!/^\d{10}$/.test(id)) return false;
  const prov = Number(id.slice(0, 2));
  if (!((prov >= 1 && prov <= 24) || prov === 30) || Number(id[2]) >= 6) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) { let d = Number(id[i]) * (i % 2 === 0 ? 2 : 1); if (d > 9) d -= 9; sum += d; }
  return (10 - (sum % 10)) % 10 === Number(id[9]);
}
export function isValidRuc(id: string) {
  if (!/^\d{13}$/.test(id)) return false;
  const prov = Number(id.slice(0, 2));
  if (!((prov >= 1 && prov <= 24) || prov === 30)) return false;
  const t = Number(id[2]);
  if (t <= 5) return isValidCedula(id.slice(0, 10)) && id.slice(10) !== "000";
  if (t === 6) return id.slice(9) !== "0000";
  if (t === 9) return id.slice(10) !== "000";
  return false;
}
export function guessIdType(id: string) {
  if (id === CONSUMIDOR_FINAL) return "07";
  if (/^\d{13}$/.test(id)) return "04";
  if (/^\d{10}$/.test(id)) return "05";
  return "06";
}
/** Mensaje de error de la identificación, o null si es válida. */
export function idError(type: string, id: string): string | null {
  const v = id.trim();
  if (!type) return "Elige el tipo de identificación.";
  if (!v) return "Escribe el número de identificación.";
  const ok = type === "04" ? isValidRuc(v) : type === "05" ? isValidCedula(v) : type === "07" ? v === CONSUMIDOR_FINAL : v.length >= 3 && v.length <= 20;
  if (ok) return null;
  if (type === "04") return "RUC no válido: son 13 dígitos (cédula + 001 para personas).";
  if (type === "05") return "Cédula no válida: revisa los 10 dígitos.";
  if (type === "07") return "Consumidor final usa 9999999999999.";
  return "Identificación no válida.";
}
