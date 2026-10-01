package com.dyd.contable.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dyd.contable.data.AccountingRepository
import com.dyd.contable.data.Profile
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de la sesión (Supabase Auth) y del perfil del usuario. */
class SessionViewModel(private val repo: AccountingRepository) : ViewModel() {
    val status: StateFlow<SessionStatus> = repo.sessionStatus
    val profile: StateFlow<Profile?> = repo.profile
    val errors: SharedFlow<String> = repo.errors

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun loadProfile() {
        viewModelScope.launch { repo.loadProfile() }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            message = "Escribe tu correo y tu contraseña."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            repo.signIn(email, password)
            busy = false
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            message = "Escribe tu correo para enviarte el enlace."
            return
        }
        viewModelScope.launch {
            if (repo.sendPasswordReset(email)) message = "Te enviamos un correo para crear una nueva contraseña."
        }
    }

    fun changePassword(newPassword: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.changePassword(newPassword)) }
    }

    fun signOut() {
        viewModelScope.launch { repo.signOut() }
    }

    fun refresh() = repo.refresh()
}
