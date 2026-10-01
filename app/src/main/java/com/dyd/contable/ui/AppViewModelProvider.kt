package com.dyd.contable.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dyd.contable.DydApp
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.ui.auth.SessionViewModel
import com.dyd.contable.ui.crm.ContactDetailViewModel
import com.dyd.contable.ui.dashboard.DashboardViewModel
import com.dyd.contable.ui.entries.EntriesViewModel
import com.dyd.contable.ui.entries.EntryEditorViewModel
import com.dyd.contable.ui.inventory.ExplosionViewModel
import com.dyd.contable.ui.invoices.InvoiceDetailViewModel
import com.dyd.contable.ui.invoices.InvoiceEditorViewModel
import com.dyd.contable.ui.invoices.InvoicesViewModel
import com.dyd.contable.ui.inventory.InventoryViewModel
import com.dyd.contable.ui.inventory.ItemDetailViewModel
import com.dyd.contable.ui.more.AccountsViewModel
import com.dyd.contable.ui.more.CategoriesViewModel
import com.dyd.contable.ui.more.CompanyViewModel
import com.dyd.contable.ui.more.ContactsViewModel
import com.dyd.contable.ui.more.UsersViewModel
import com.dyd.contable.ui.pending.PendingViewModel
import com.dyd.contable.ui.reports.ReportsViewModel
import com.dyd.contable.ui.taxes.InterestsViewModel

/** Fábrica única de ViewModels (inyección de dependencias manual y sencilla). */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SessionViewModel(repository()) }
        initializer { DashboardViewModel(repository()) }
        initializer { EntriesViewModel(repository()) }
        initializer { EntryEditorViewModel(createSavedStateHandle(), repository()) }
        initializer { PendingViewModel(repository()) }
        initializer { ReportsViewModel(repository()) }
        initializer { AccountsViewModel(repository()) }
        initializer { ContactsViewModel(repository()) }
        initializer { CategoriesViewModel(repository()) }
        initializer { InventoryViewModel(repository()) }
        initializer { ItemDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { ExplosionViewModel(createSavedStateHandle(), repository()) }
        initializer { ContactDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { UsersViewModel(repository()) }
        initializer { InvoicesViewModel(repository()) }
        initializer { InvoiceEditorViewModel(repository()) }
        initializer { InvoiceDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { CompanyViewModel(repository()) }
        initializer { InterestsViewModel(repository()) }
    }
}

private fun CreationExtras.repository(): AccountingRepository =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DydApp).repository
