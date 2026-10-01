@file:OptIn(ExperimentalMaterial3Api::class)

package com.dyd.contable.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.AppRole
import com.dyd.contable.data.Profile
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import com.dyd.contable.ui.components.SelectorField
import com.dyd.contable.ui.components.StatusPill
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UsersViewModel(private val repo: AccountingRepository) : ViewModel() {
    val users: StateFlow<List<Profile>> = repo.profiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val me: StateFlow<Profile?> = repo.profile

    fun update(p: Profile) { viewModelScope.launch { repo.updateProfile(p) } }

    fun create(email: String, password: String, name: String, role: AppRole, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.createUser(email, password, name, role)) }
    }
}

fun roleLabel(r: AppRole) = when (r) {
    AppRole.admin -> "Administrador"
    AppRole.contador -> "Contador"
    AppRole.vendedor -> "Vendedor"
    AppRole.bodega -> "Bodega"
    AppRole.produccion -> "Producción"
    AppRole.calidad -> "Calidad"
    AppRole.id -> "I+D"
    AppRole.auditor -> "Auditor"
    AppRole.compras -> "Compras"
}

fun roleDescription(r: AppRole) = when (r) {
    AppRole.admin -> "Todo, incluidos usuarios y permisos."
    AppRole.contador -> "Contabilidad, inventario y producción."
    AppRole.vendedor -> "Clientes, seguimientos, inventario (lectura) y sus propias ventas."
    AppRole.bodega -> "Inventario: entradas, salidas y ajustes de stock. Sin dinero."
    AppRole.produccion -> "Registra órdenes de producción y consulta la explosión de materiales."
    AppRole.calidad -> "Aprueba o rechaza los lotes producidos."
    AppRole.id -> "Diseña productos: fichas, lista de materiales, costos y fotos del catálogo."
    AppRole.auditor -> "Consulta todo (contabilidad, reportes, inventario, clientes) sin modificar nada."
    AppRole.compras -> "Registra compras a proveedores, crea proveedores y consulta el inventario."
}

@Composable
fun UsersScreen(onBack: () -> Unit, viewModel: UsersViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = "Usuarios",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.PersonAdd, contentDescription = "Nuevo usuario") }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                Text(
                    "Cada persona entra con su correo y contraseña. El rol define qué puede ver y hacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(users, key = { it.id }) { u ->
                ListItem(
                    modifier = Modifier.clickable(enabled = u.id != me?.id) { editing = u },
                    leadingContent = { IconBadge(Icons.Filled.AdminPanelSettings, MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(u.fullName.ifBlank { "Sin nombre" } + if (u.id == me?.id) " (tú)" else "") },
                    supportingContent = { Text(roleLabel(u.role)) },
                    trailingContent = { if (!u.active) StatusPill("Inactivo", MaterialTheme.colorScheme.error) },
                )
            }
        }
    }

    editing?.let { u ->
        var role by remember(u.id) { mutableStateOf(u.role) }
        var active by remember(u.id) { mutableStateOf(u.active) }
        var name by remember(u.id) { mutableStateOf(u.fullName) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Editar usuario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SelectorField("Rol", role, AppRole.entries, ::roleLabel, { if (it != null) role = it })
                    Text(roleDescription(role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Acceso activo", modifier = Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.update(u.copy(fullName = name.trim(), role = role, active = active)); editing = null }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }

    if (creating) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(AppRole.vendedor) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nuevo usuario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Correo") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Contraseña temporal") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = { Text("Mínimo 8 caracteres. Pídele que la cambie al entrar.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SelectorField("Rol", role, AppRole.entries, ::roleLabel, { if (it != null) role = it })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        name.isBlank() || !email.contains("@") -> error = "Escribe el nombre y un correo válido"
                        password.length < 8 -> error = "La contraseña debe tener al menos 8 caracteres"
                        else -> viewModel.create(email, password, name, role) { ok ->
                            if (ok) {
                                creating = false
                                scope.launch { snackbar.showSnackbar("Usuario creado: ${email.trim()}") }
                            } else {
                                error = "No se pudo crear. Revisa que la función create-user esté publicada en Supabase."
                            }
                        }
                    }
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}
