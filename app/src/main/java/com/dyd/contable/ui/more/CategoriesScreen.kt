@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dyd.contable.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Category
import com.dyd.contable.data.EntryType
import com.dyd.contable.ui.AppViewModelProvider
import com.dyd.contable.ui.components.CategoryIcons
import com.dyd.contable.ui.components.ColorPicker
import com.dyd.contable.ui.components.ConfirmDialog
import com.dyd.contable.ui.components.IconBadge
import com.dyd.contable.ui.components.ScreenScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: AccountingRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(category: Category) {
        viewModelScope.launch { repo.saveCategory(category) }
    }

    fun delete(category: Category) {
        viewModelScope.launch { repo.deleteCategory(category) }
    }
}

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val type = if (tab == 0) EntryType.INCOME else EntryType.EXPENSE

    ScreenScaffold(
        title = "Categorías",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", type = type, color = CategoryIcons.palette.first()) }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva categoría")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ingresos") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gastos") })
                }
            }
            items(categories.filter { it.type == type }, key = { it.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable { editing = c },
                    leadingContent = { IconBadge(CategoryIcons.of(c.icon), Color(c.color)) },
                    headlineContent = { Text(c.name) },
                )
            }
        }
    }

    editing?.let { category ->
        CategoryDialog(
            initial = category,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = if (category.id != 0L) ({ deleting = category; editing = null }) else null,
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "¿Eliminar \"${category.name}\"?",
            message = "Los movimientos de esta categoría quedarán como \"Sin categoría\".",
            confirmLabel = "Eliminar",
            onConfirm = { viewModel.delete(category) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Nueva categoría" else "Editar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text("Nombre") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Ícono", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(color).copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, contentDescription = key, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPicker(CategoryIcons.palette, color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = true
                else onSave(initial.copy(name = name.trim(), icon = icon, color = color))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
