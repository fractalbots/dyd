// Edge Function "sri-factura": firma una factura con la firma electrónica de la empresa, la envía al SRI
// y consulta su autorización. La firma (.p12) y su clave viven solo como secretos de Supabase:
//   supabase secrets set SRI_P12_BASE64="$(base64 -w0 firma.p12)" SRI_P12_PASSWORD='...'
import { createClient } from "npm:@supabase/supabase-js@2";
import { buildInvoiceXml, checkAuthorization, loadP12, sendToSri, signXml, type SriMessage } from "../_shared/sri.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const caller = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: userData } = await caller.auth.getUser();
  if (!userData?.user) return json({ error: "Sesión no válida" }, 401);

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: profile } = await admin.from("profiles").select("role, active").eq("id", userData.user.id).single();
  if (!profile?.active || !["admin", "contador", "vendedor"].includes(profile.role)) {
    return json({ error: "No tienes permiso para enviar facturas al SRI" }, 403);
  }

  const { invoice_id } = await req.json().catch(() => ({}));
  // Se lee con los permisos de quien llama: un vendedor solo puede enviar sus propias facturas.
  const { data: inv } = await caller.from("invoices").select("*, lines:invoice_lines(*)").eq("id", invoice_id).single();
  if (!inv) return json({ error: "Factura no encontrada" }, 404);
  if (inv.status === "AUTORIZADA") return json({ status: inv.status, authorization_number: inv.authorization_number, messages: [] });
  if (inv.status === "ANULADA") return json({ error: "La factura está anulada" }, 400);

  const { data: company } = await admin.from("company").select("*").eq("id", 1).single();
  const ambiente = inv.ambiente as 1 | 2;
  const save = (patch: Record<string, unknown>) => admin.from("invoices").update(patch).eq("id", inv.id);
  let messages: SriMessage[] = [];

  try {
    // 1. Firmar y enviar (si ya fue RECIBIDA solo se consulta la autorización).
    if (inv.status !== "RECIBIDA") {
      const p12 = Deno.env.get("SRI_P12_BASE64");
      const password = Deno.env.get("SRI_P12_PASSWORD");
      if (!p12 || !password) {
        return json({ error: "Falta configurar la firma electrónica (secretos SRI_P12_BASE64 y SRI_P12_PASSWORD)" }, 400);
      }
      const lines = [...inv.lines].sort((a: { id: number }, b: { id: number }) => a.id - b.id);
      const signed = signXml(buildInvoiceXml(company, { ...inv, lines }), loadP12(p12, password));
      await save({ xml: signed });

      const rec = await sendToSri(ambiente, signed);
      messages = rec.mensajes;
      // 43/45: la clave ya fue recibida antes; se sigue con la autorización.
      const alreadyReceived = rec.mensajes.some((m) => ["43", "45"].includes(m.identificador));
      if (rec.estado !== "RECIBIDA" && !alreadyReceived) {
        await save({ status: "DEVUELTA", sri_messages: messages });
        return json({ status: "DEVUELTA", messages });
      }
      await save({ status: "RECIBIDA", sri_messages: messages });
      await sleep(2500);
    }

    // 2. Autorización (el SRI puede tardar unos segundos).
    for (let attempt = 0; attempt < 4; attempt++) {
      const auth = await checkAuthorization(ambiente, inv.clave_acceso);
      messages = auth.mensajes;
      if (auth.estado === "AUTORIZADO") {
        await save({
          status: "AUTORIZADA", authorization_number: auth.numeroAutorizacion || inv.clave_acceso,
          authorized_at: auth.fechaAutorizacion ? new Date(auth.fechaAutorizacion).toISOString() : new Date().toISOString(),
          sri_messages: messages,
        });
        return json({ status: "AUTORIZADA", authorization_number: auth.numeroAutorizacion, messages });
      }
      if (auth.estado === "NO AUTORIZADO") {
        await save({ status: "NO_AUTORIZADA", sri_messages: messages });
        return json({ status: "NO_AUTORIZADA", messages });
      }
      await sleep(3000);
    }
    return json({ status: "RECIBIDA", messages, pending: true });
  } catch (e) {
    return json({ error: `No se pudo completar el envío al SRI: ${(e as Error).message}` }, 502);
  }
});
