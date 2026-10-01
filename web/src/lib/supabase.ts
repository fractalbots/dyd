import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const isConfigured = Boolean(url && key && url.startsWith("https://"));

export const supabase = createClient(url || "https://configura-supabase.supabase.co", key || "sin-clave");

export type Role = "admin" | "contador" | "vendedor" | "bodega" | "produccion" | "calidad" | "id" | "auditor" | "compras";
export type EntryType = "INCOME" | "EXPENSE" | "TRANSFER";
export type ItemKind = "MATERIAL" | "PRODUCT";

export interface Profile { id: string; full_name: string; role: Role; active: boolean }

/** Mismas reglas que las funciones perm_* de supabase/schema.sql; la base de datos es la que manda. */
export function perms(p: Profile | null) {
  const is = (...roles: Role[]) => Boolean(p?.active && roles.includes(p.role));
  return {
    admin: is("admin"),
    manageAccounting: is("admin", "contador"),
    readAccounting: is("admin", "contador", "auditor"),
    seeEntries: is("admin", "contador", "vendedor", "auditor", "compras"),
    createEntries: is("admin", "contador", "vendedor", "compras"),
    entryTypes: (is("admin", "contador") ? ["INCOME", "EXPENSE", "TRANSFER"] : is("vendedor") ? ["INCOME"] : is("compras") ? ["EXPENSE"] : []) as EntryType[],
    crm: is("admin", "contador", "vendedor", "auditor", "compras"),
    editCrm: is("admin", "contador", "vendedor", "compras"),
    writeContacts: is("admin", "contador"),
    writeSuppliers: is("admin", "contador", "compras"),
    purchase: is("admin", "contador", "compras"),
    invoice: is("admin", "contador", "vendedor"),
    seeInvoices: is("admin", "contador", "vendedor", "auditor"),
    editItems: is("admin", "contador", "id"),
    setPrices: is("admin", "contador"),
    produce: is("admin", "contador", "produccion"),
    adjustStock: is("admin", "contador", "bodega"),
    inspect: is("admin", "calidad"),
  };
}
export interface Account { id: number; name: string; type: string; initial_balance: number; balance: number; archived: boolean }
export interface Category { id: number; name: string; type: EntryType; icon: string; color: number }
export interface Contact { id: number; name: string; type: "CLIENT" | "SUPPLIER" | "BOTH"; tax_id: string; phone: string; email: string; notes: string; id_type: string; address: string;
  actividad_economica: string; sri_estado: string; sri_tipo: string; sri_regimen: string; sri_obligado_contabilidad: string;
  sri_data: Record<string, unknown> | null; sri_checked_at: string | null }
export interface ContactStats { contact_id: number; sales: number; expenses: number; cogs: number; receivable: number; payable: number }
export interface Entry {
  id: number; type: EntryType; amount: number; date: string; account_id: number; to_account_id: number | null;
  category_id: number | null; contact_id: number | null; description: string; reference: string;
  is_paid: boolean; due_date: string | null; tax_amount: number; invoice_id: number | null; is_interest: boolean;
  category?: { name: string } | null; account?: { name: string } | null; to_account?: { name: string } | null; contact?: { name: string } | null;
}
export interface Item {
  id: number; name: string; kind: ItemKind; unit: string; sku: string; unit_cost: number; sale_price: number;
  min_stock: number; labor_cost: number; overhead_cost: number; archived: boolean; is_public: boolean;
  description: string; dimensions: string; image_url: string; iva_code: string; stock: number;
}
export interface BomLine { id: number; product_id: number; material_id: number; quantity: number; waste_percent: number }
export interface StockMove { id: number; item_id: number; type: string; quantity: number; unit_cost: number; unit_price: number; date: string; note: string }
export interface ExplosionRow { material_id: number; material_name: string; unit: string; required: number; available: number; shortage: number; unit_cost: number; cost: number }
export interface Interaction { id: number; contact_id: number; kind: string; date: string; note: string; follow_up: string | null; done: boolean; contact?: { name: string } | null }
export interface CatalogItem { id: number; name: string; sku: string; unit: string; sale_price: number; description: string; dimensions: string; image_url: string; in_stock: boolean }

// ---------- Ecuador / SRI ----------
export interface Company {
  ruc: string; razon_social: string; nombre_comercial: string; dir_matriz: string; dir_establecimiento: string;
  estab: string; pto_emi: string; next_secuencial: number; ambiente: number; obligado_contabilidad: boolean;
  contribuyente_especial: string; agente_retencion: string; regimen: string; email: string; phone: string; late_interest_rate: number;
}
export interface SriMessage { identificador?: string; mensaje?: string; informacionAdicional?: string; tipo?: string }
export interface InvoiceLine { id: number; item_id: number | null; code: string; description: string; quantity: number; unit_price: number; discount: number; iva_code: string; iva_rate: number; subtotal: number; tax: number }
export interface Installment { number: number; due_date: string; capital: number; interest: number; total: number }
export interface Invoice {
  id: number; contact_id: number; estab: string; pto_emi: string; secuencial: number; fecha_emision: string; ambiente: number;
  clave_acceso: string; status: string; buyer_id_type: string; buyer_id: string; buyer_name: string; buyer_address: string; buyer_email: string;
  subtotal: number; discount: number; tax: number; total: number; payment_method: string; term_days: number; installments: number;
  finance_rate: number; note: string; authorization_number: string | null; authorized_at: string | null; sri_messages: SriMessage[];
  lines?: InvoiceLine[]; cuotas?: Installment[];
}
export interface LateReceivable { entry_id: number; contact_id: number | null; description: string; amount: number; due_date: string; days: number; days_overdue: number; interest: number }
export interface IvaMonth { month: string; sales_base: number; sales_tax: number; purchases_base: number; purchases_tax: number; tax_due: number; due_date: string }
export interface SriCharges { months: number; interest: number; fine: number; total: number; missing_rates: boolean }
export interface SriRate { quarter_start: string; monthly_rate: number }

export const invoiceNumber = (i: Pick<Invoice, "estab" | "pto_emi" | "secuencial">) => `${i.estab}-${i.pto_emi}-${String(i.secuencial).padStart(9, "0")}`;

export const ENTRY_SELECT =
  "*, category:categories(name), account:accounts!entries_account_id_fkey(name), to_account:accounts!entries_to_account_id_fkey(name), contact:contacts(name)";

/** Lanza el error de Supabase con un mensaje en español cuando es de permisos. */
export function check<T>(res: { data: T | null; error: { message: string } | null }): T {
  if (res.error) {
    const m = res.error.message;
    throw new Error(/row-level security|permission/i.test(m) ? "No tienes permiso para esta acción." : m);
  }
  return res.data as T;
}
