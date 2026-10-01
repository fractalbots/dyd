import { useState, type FormEvent } from "react";
import { useLoad } from "../lib/hooks";
import { check, supabase, type Profile, type Role } from "../lib/supabase";
import { ROLE_HELP, ROLE_LABEL } from "../lib/format";

export default function UsersPage() {
  const users = useLoad(async () => check(await supabase.from("profiles").select("*").order("full_name")) as Profile[], []);

  async function update(p: Profile, patch: Partial<Profile>) {
    const res = await supabase.from("profiles").update(patch).eq("id", p.id);
    if (res.error) alert(res.error.message); else users.reload();
  }

  return (
    <>
      <h1>Usuarios y permisos</h1>
      <div className="card table-wrap"><table>
        <thead><tr><th>Rol</th><th>Qué puede hacer</th></tr></thead>
        <tbody>{Object.entries(ROLE_LABEL).map(([k, v]) => <tr key={k}><td><strong>{v}</strong></td><td className="muted">{ROLE_HELP[k]}</td></tr>)}</tbody>
      </table></div>
      <NewUser onSaved={users.reload} />
      <div className="card table-wrap"><table>
        <thead><tr><th>Nombre</th><th>Rol</th><th>Acceso</th></tr></thead>
        <tbody>{(users.data ?? []).map((u) => (
          <tr key={u.id}>
            <td>{u.full_name || "Sin nombre"}</td>
            <td><select value={u.role} onChange={(e) => update(u, { role: e.target.value as Role })} style={{ maxWidth: 200 }}>
              {Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select></td>
            <td><label className="check"><input type="checkbox" checked={u.active} onChange={(e) => update(u, { active: e.target.checked })} /> Activo</label></td>
          </tr>
        ))}</tbody>
      </table></div>
    </>
  );
}

function NewUser({ onSaved }: { onSaved: () => void }) {
  const [f, setF] = useState({ full_name: "", email: "", password: "", role: "vendedor" });
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (f.password.length < 8) return setError("La contraseña debe tener al menos 8 caracteres.");
    const { data, error } = await supabase.functions.invoke("create-user", { body: f });
    if (error || data?.error) {
      setError(data?.error ?? "No se pudo crear. Verifica que la función create-user esté publicada en Supabase.");
    } else {
      setMsg(`Usuario creado: ${f.email}`); setError(null);
      setF({ full_name: "", email: "", password: "", role: "vendedor" }); onSaved();
    }
  }
  return (
    <form className="card" onSubmit={submit}>
      <h2>Nuevo usuario</h2>
      <div className="form">
        <label>Nombre<input value={f.full_name} onChange={(e) => setF({ ...f, full_name: e.target.value })} /></label>
        <label>Correo<input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} required /></label>
        <label>Contraseña temporal<input type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required /></label>
        <label>Rol<select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })}>
          {Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select></label>
        <button className="btn">Crear usuario</button>
      </div>
      {msg && <div className="notice" style={{ marginTop: 12 }}>{msg}</div>}
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
    </form>
  );
}
