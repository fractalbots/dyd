import type { ReactNode } from "react";
import { Link } from "react-router-dom";

const business = (import.meta.env.VITE_BUSINESS_NAME as string | undefined) || "Pallets DYD";
const email = (import.meta.env.VITE_CONTACT_EMAIL as string | undefined) || "";
const whatsapp = (import.meta.env.VITE_WHATSAPP as string | undefined) || "";
const UPDATED = "28 de septiembre de 2026";

function Contact() {
  return (
    <ul>
      {email && <li>Correo: <a href={`mailto:${email}`}>{email}</a></li>}
      {whatsapp && <li>WhatsApp: <a href={`https://wa.me/${whatsapp}`} target="_blank" rel="noreferrer">+{whatsapp}</a></li>}
      {!email && !whatsapp && <li>Escribe al administrador de tu empresa.</li>}
    </ul>
  );
}

function Legal({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <header className="hero" style={{ padding: "24px 16px 32px" }}>
        <div className="topbar">
          <img src="/icon.svg" alt="" width={36} height={36} />
          <Link to="/" style={{ color: "#fff" }}><strong>{business}</strong></Link>
        </div>
        <h1 style={{ fontSize: "1.8rem" }}>{title}</h1>
      </header>
      <article className="card legal">{children}</article>
      <footer className="footer">
        <Link to="/privacidad">Privacidad</Link> · <Link to="/eliminar-cuenta">Eliminar cuenta</Link> · © {new Date().getFullYear()} {business}
      </footer>
    </>
  );
}

/** Política de privacidad pública (la pide Google Play). */
export function PrivacyPage() {
  return (
    <Legal title="Política de privacidad">
      <p className="muted">Última actualización: {UPDATED}</p>
      <p>Esta política explica qué datos trata la app <strong>DYD Contable</strong> (Android y web) de {business}, para qué los usa y
        qué derechos tienes, conforme a la Ley Orgánica de Protección de Datos Personales del Ecuador.</p>

      <h2>Quién es el responsable</h2>
      <p>{business} es responsable de los datos. Para cualquier consulta:</p>
      <Contact />

      <h2>Para quién es la app</h2>
      <p>DYD Contable es una herramienta interna de contabilidad, inventario y facturación. Solo pueden entrar las personas a las que
        el administrador de la empresa les crea un usuario. No está dirigida a menores de edad.</p>

      <h2>Qué datos tratamos</h2>
      <ul>
        <li><strong>Tu cuenta:</strong> nombre, correo electrónico, contraseña (cifrada) y el rol que te asignó el administrador.</li>
        <li><strong>Datos del negocio que tú registras:</strong> movimientos contables, cuentas, inventario, costos, producción y facturas.</li>
        <li><strong>Clientes y proveedores:</strong> nombre o razón social, cédula o RUC, dirección, teléfono, correo y la información pública
          del catastro del SRI (actividad económica, estado, tipo y régimen del contribuyente).</li>
        <li><strong>Fotos de productos</strong> que decidas subir desde tu galería.</li>
      </ul>
      <p>La app no usa tu ubicación, contactos, micrófono ni cámara, no muestra publicidad y no incluye herramientas de rastreo o analítica.</p>

      <h2>Para qué los usamos</h2>
      <ul>
        <li>Llevar la contabilidad, el inventario y los costos de la empresa.</li>
        <li>Emitir facturas electrónicas y enviarlas al Servicio de Rentas Internas (SRI), como exige la ley.</li>
        <li>Consultar el RUC de un cliente en el SRI para llenar sus datos.</li>
        <li>Controlar quién puede ver o cambiar cada información según su rol.</li>
      </ul>

      <h2>Con quién se comparten</h2>
      <ul>
        <li><strong>Supabase</strong> (proveedor de base de datos en la nube), que guarda la información por cuenta de {business}.</li>
        <li><strong>El SRI</strong>, al emitir facturas electrónicas o consultar un RUC.</li>
        <li>Los demás usuarios de tu empresa, según los permisos de su rol.</li>
      </ul>
      <p>No vendemos ni alquilamos datos personales a nadie.</p>

      <h2>Seguridad</h2>
      <p>Toda la comunicación viaja cifrada (HTTPS). Cada usuario inicia sesión con su propia contraseña y la base de datos solo deja ver
        a cada rol lo que le corresponde. La firma electrónica de la empresa se guarda como secreto del servidor, nunca en el teléfono.</p>

      <h2>Cuánto tiempo los guardamos</h2>
      <p>Los datos de tu usuario se guardan mientras tengas acceso. Los registros contables y las facturas se conservan al menos 7 años,
        porque la normativa tributaria del Ecuador obliga a mantenerlos.</p>

      <h2>Tus derechos</h2>
      <p>Puedes pedir acceso, corrección, eliminación u oposición al uso de tus datos, y la portabilidad de los mismos. Para eliminar tu
        cuenta sigue los pasos de <Link to="/eliminar-cuenta">Eliminar cuenta</Link>. Respondemos en un máximo de 15 días.</p>

      <h2>Cambios</h2>
      <p>Si cambiamos esta política, publicaremos la nueva versión en esta misma página con su fecha.</p>
    </Legal>
  );
}

/** Instrucciones para borrar la cuenta y los datos (Google Play exige un enlace público). */
export function DeleteAccountPage() {
  const subject = encodeURIComponent("Eliminar mi cuenta de DYD Contable");
  const body = encodeURIComponent("Hola, quiero eliminar mi cuenta de DYD Contable.\nCorreo con el que ingreso: ");
  return (
    <Legal title="Eliminar tu cuenta de DYD Contable">
      <p>Puedes pedir que borremos tu usuario de <strong>DYD Contable</strong> ({business}) y los datos personales asociados.</p>

      <h2>Cómo pedirlo</h2>
      <ol>
        <li>Escríbenos desde el correo con el que entras a la app{email ? "" : ", o pídeselo al administrador de tu empresa"}.</li>
        <li>Indica que quieres eliminar tu cuenta.</li>
        <li>Te confirmaremos la eliminación en un máximo de 15 días.</li>
      </ol>
      {email && <p><a className="btn" href={`mailto:${email}?subject=${subject}&body=${body}`}>Pedir eliminación por correo</a></p>}
      <Contact />
      <p className="muted small">El administrador de la empresa también puede desactivar un usuario al instante desde Usuarios y permisos.</p>

      <h2>Qué se borra</h2>
      <ul>
        <li>Tu usuario, correo, nombre, contraseña y rol.</li>
        <li>Tu acceso a la app y a la web.</li>
      </ul>

      <h2>Qué se conserva y por cuánto tiempo</h2>
      <ul>
        <li>Los registros contables, facturas electrónicas y movimientos de inventario de la empresa se guardan <strong>7 años</strong>,
          porque la normativa tributaria del Ecuador obliga a conservarlos. En ellos ya no aparecerá tu nombre como usuario.</li>
        <li>Las facturas autorizadas por el SRI no se pueden borrar del SRI.</li>
      </ul>
    </Legal>
  );
}
