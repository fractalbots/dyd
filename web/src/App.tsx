import { Navigate, NavLink, Outlet, Route, Routes } from "react-router-dom";
import { useSession } from "./lib/hooks";
import { perms, supabase, type Profile } from "./lib/supabase";
import { ROLE_LABEL } from "./lib/format";
import CatalogPage from "./pages/CatalogPage";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import EntriesPage from "./pages/EntriesPage";
import InventoryPage from "./pages/InventoryPage";
import ItemPage from "./pages/ItemPage";
import ExplosionPage from "./pages/ExplosionPage";
import ContactsPage from "./pages/ContactsPage";
import ContactPage from "./pages/ContactPage";
import UsersPage from "./pages/UsersPage";
import InvoicesPage from "./pages/InvoicesPage";
import InvoiceNewPage from "./pages/InvoiceNewPage";
import InvoicePage from "./pages/InvoicePage";
import CompanyPage from "./pages/CompanyPage";
import TaxesPage from "./pages/TaxesPage";
import { DeleteAccountPage, PrivacyPage } from "./pages/LegalPages";

export default function App() {
  const { session, profile } = useSession();
  return (
    <Routes>
      <Route path="/" element={<CatalogPage />} />
      <Route path="/privacidad" element={<PrivacyPage />} />
      <Route path="/eliminar-cuenta" element={<DeleteAccountPage />} />
      <Route path="/login" element={session ? <Navigate to="/panel" replace /> : <LoginPage />} />
      <Route
        path="/panel"
        element={session === undefined ? null : session ? <Shell profile={profile} /> : <Navigate to="/login" replace />}
      >
        <Route index element={!profile ? null : perms(profile).seeEntries ? <DashboardPage profile={profile} /> : <Navigate to="/panel/inventario" replace />} />
        <Route path="movimientos" element={<EntriesPage profile={profile} />} />
        <Route path="inventario" element={<InventoryPage profile={profile} />} />
        <Route path="inventario/:id" element={<ItemPage profile={profile} />} />
        <Route path="explosion" element={<ExplosionPage />} />
        <Route path="clientes" element={<ContactsPage profile={profile} />} />
        <Route path="clientes/:id" element={<ContactPage profile={profile} />} />
        <Route path="facturas" element={!profile ? null : perms(profile).seeInvoices ? <InvoicesPage profile={profile} /> : <Navigate to="/panel" replace />} />
        <Route path="facturas/nueva" element={!profile ? null : perms(profile).invoice ? <InvoiceNewPage /> : <Navigate to="/panel" replace />} />
        <Route path="facturas/:id" element={!profile ? null : perms(profile).seeInvoices ? <InvoicePage profile={profile} /> : <Navigate to="/panel" replace />} />
        <Route path="impuestos" element={!profile ? null : perms(profile).readAccounting ? <TaxesPage profile={profile} /> : <Navigate to="/panel" replace />} />
        <Route path="empresa" element={!profile ? null : perms(profile).readAccounting ? <CompanyPage profile={profile} /> : <Navigate to="/panel" replace />} />
        <Route path="usuarios" element={!profile ? null : perms(profile).admin ? <UsersPage /> : <Navigate to="/panel" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function Shell({ profile }: { profile: Profile | null }) {
  const can = perms(profile);
  return (
    <div className="shell">
      <nav className="side">
        <div className="brand"><img src="/icon.svg" alt="" /> DYD Contable</div>
        {can.seeEntries && <NavLink to="/panel" end>Resumen</NavLink>}
        {can.seeEntries && <NavLink to="/panel/movimientos">Movimientos</NavLink>}
        <NavLink to="/panel/inventario">Inventario y catálogo</NavLink>
        <NavLink to="/panel/explosion">Explosión de materiales</NavLink>
        {can.seeInvoices && <NavLink to="/panel/facturas">Facturación SRI</NavLink>}
        {can.crm && <NavLink to="/panel/clientes">Clientes y proveedores</NavLink>}
        {can.readAccounting && <NavLink to="/panel/impuestos">Intereses e impuestos</NavLink>}
        {can.readAccounting && <NavLink to="/panel/empresa">Empresa y SRI</NavLink>}
        {can.admin && <NavLink to="/panel/usuarios">Usuarios</NavLink>}
        <NavLink to="/" target="_blank">Ver catálogo ↗</NavLink>
        <div className="foot">
          <div><strong>{profile?.full_name || "…"}</strong></div>
          <div className="muted">{profile ? ROLE_LABEL[profile.role] : ""}</div>
          <button className="btn secondary small" style={{ marginTop: 8 }} onClick={() => supabase.auth.signOut()}>Cerrar sesión</button>
        </div>
      </nav>
      <main className="main"><Outlet /></main>
    </div>
  );
}
