import { useState, type FormEvent } from "react";
import { isConfigured, supabase } from "../lib/supabase";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    setBusy(false);
    if (error) setError(/invalid login/i.test(error.message) ? "Correo o contraseña incorrectos." : error.message);
  }

  async function reset() {
    if (!email.trim()) { setError("Escribe tu correo para enviarte el enlace."); return; }
    const { error } = await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo: window.location.origin + "/panel" });
    if (error) setError(error.message); else setNotice("Te enviamos un correo para crear una nueva contraseña.");
  }

  return (
    <div className="center">
      <form className="card login" onSubmit={submit}>
        <div className="row"><img src="/icon.svg" alt="" width={44} height={44} /><h1 style={{ margin: 0 }}>DYD Contable</h1></div>
        <p className="muted" style={{ margin: 0 }}>Ingresa con el correo y la contraseña que te dio el administrador.</p>
        {!isConfigured && <div className="error">Falta configurar Supabase en el archivo .env (ver .env.example).</div>}
        <label>Correo<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required /></label>
        <label>Contraseña<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required /></label>
        {error && <div className="error">{error}</div>}
        {notice && <div className="notice">{notice}</div>}
        <button className="btn" disabled={busy}>{busy ? "Ingresando…" : "Ingresar"}</button>
        <button type="button" className="btn secondary" onClick={reset}>Olvidé mi contraseña</button>
      </form>
    </div>
  );
}
