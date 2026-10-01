// Edge Function "create-user": permite a un administrador crear usuarios desde la app o la web.
// La clave service_role solo existe aquí, en el servidor de Supabase; nunca en la app.
import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  // 1. ¿Quién llama? Debe ser un administrador activo.
  const caller = createClient(url, anonKey, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: userData, error: userError } = await caller.auth.getUser();
  if (userError || !userData.user) return json({ error: "Sesión no válida" }, 401);

  const admin = createClient(url, serviceKey);
  const { data: profile } = await admin
    .from("profiles").select("role, active").eq("id", userData.user.id).single();
  if (!profile || profile.role !== "admin" || !profile.active) {
    return json({ error: "Solo un administrador puede crear usuarios" }, 403);
  }

  // 2. Validar datos.
  const { email, password, full_name, role } = await req.json().catch(() => ({}));
  if (typeof email !== "string" || !email.includes("@")) return json({ error: "Correo no válido" }, 400);
  if (typeof password !== "string" || password.length < 8) return json({ error: "La contraseña debe tener al menos 8 caracteres" }, 400);
  if (!["admin", "contador", "vendedor", "bodega", "produccion", "calidad", "id", "auditor", "compras"].includes(role)) return json({ error: "Rol no válido" }, 400);

  // 3. Crear el usuario (ya confirmado) y fijar su rol.
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: full_name ?? "" },
  });
  if (createError || !created.user) return json({ error: createError?.message ?? "No se pudo crear" }, 400);

  const { error: roleError } = await admin
    .from("profiles").update({ role, full_name: full_name ?? "" }).eq("id", created.user.id);
  if (roleError) return json({ error: roleError.message }, 400);

  return json({ id: created.user.id, email });
});
