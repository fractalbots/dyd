package com.dyd.contable.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dyd.contable.data.Profile

@Composable
fun MoreScreen(
    profile: Profile?,
    onPending: () -> Unit,
    onAccounts: () -> Unit,
    onContacts: () -> Unit,
    onCategories: () -> Unit,
    onUsers: () -> Unit,
    onInvoices: () -> Unit,
    onCompany: () -> Unit,
    onInterests: () -> Unit,
    onChangePassword: (String, (Boolean) -> Unit) -> Unit,
    onSignOut: () -> Unit,
) {
    var changingPassword by remember { mutableStateOf(false) }
    val accounting = profile?.canManageAccounting == true

    com.dyd.contable.ui.components.ScreenScaffold(title = "Más opciones") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                leadingContent = { com.dyd.contable.ui.components.IconBadge(Icons.Filled.AccountCircle, MaterialTheme.colorScheme.primary, size = 48.dp) },
                headlineContent = { Text(profile?.fullName?.ifBlank { null } ?: "Mi cuenta") },
                supportingContent = { Text(profile?.let { roleLabel(it.role) } ?: "Cargando…") },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (profile?.canReadCrm == true) {
                MoreItem(Icons.Filled.People, "Clientes y proveedores", "Rentabilidad, seguimientos y deudas", onContacts)
            }
            if (profile?.canSeeInvoices == true) {
                MoreItem(Icons.AutoMirrored.Filled.ReceiptLong, "Facturación electrónica", "Emite facturas y envíalas al SRI", onInvoices)
            }
            if (profile?.canReadAccounting == true) {
                MoreItem(Icons.Filled.Percent, "Intereses e impuestos", "Mora de clientes, IVA mensual e intereses del SRI", onInterests)
            }
            if (accounting) {
                MoreItem(Icons.Filled.Business, "Empresa y SRI", "RUC, establecimiento, ambiente y tasa de mora", onCompany)
                MoreItem(Icons.Filled.Schedule, "Pendientes", "Cuentas por cobrar y por pagar", onPending)
                MoreItem(Icons.Filled.AccountBalance, "Cuentas", "Caja, bancos y tarjetas con su saldo", onAccounts)
                MoreItem(Icons.Filled.Category, "Categorías", "Organiza tus ingresos y gastos", onCategories)
            }
            if (profile?.isAdmin == true) {
                MoreItem(Icons.Filled.AdminPanelSettings, "Usuarios y permisos", "Crea accesos y asigna roles", onUsers)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MoreItem(Icons.Filled.Key, "Cambiar contraseña", null) { changingPassword = true }
            MoreItem(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión", null, onSignOut)
            val web = com.dyd.contable.BuildConfig.WEB_URL
            if (web.isNotBlank()) {
                val uri = LocalUriHandler.current
                MoreItem(Icons.Filled.Policy, "Política de privacidad", null) { uri.openUri("$web/privacidad") }
                MoreItem(Icons.Filled.PersonRemove, "Eliminar mi cuenta", "Cómo borrar tu usuario y tus datos") { uri.openUri("$web/eliminar-cuenta") }
            }
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("DYD Contable 2.0") },
                supportingContent = { Text("Tus datos están en la nube (Supabase) y se comparten con tu equipo y la web.") },
            )
        }
    }

    if (changingPassword) {
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { changingPassword = false },
            title = { Text("Cambiar contraseña") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(password, { password = it }, label = { Text("Nueva contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Repítela") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        password.length < 8 -> error = "Mínimo 8 caracteres"
                        password != confirm -> error = "Las contraseñas no coinciden"
                        else -> onChangePassword(password) { ok -> if (ok) changingPassword = false else error = "No se pudo cambiar" }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { changingPassword = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).fillMaxWidth(),
        leadingContent = { com.dyd.contable.ui.components.IconBadge(icon, MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
    )
}
