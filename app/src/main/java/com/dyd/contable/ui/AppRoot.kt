package com.dyd.contable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyd.contable.ui.auth.LoginScreen
import com.dyd.contable.ui.auth.SessionViewModel
import com.dyd.contable.ui.navigation.DydNavHost
import io.github.jan.supabase.auth.status.SessionStatus

/** Decide qué mostrar según la sesión: carga, inicio de sesión o la app. */
@Composable
fun AppRoot(session: SessionViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val status by session.status.collectAsStateWithLifecycle()
    when (status) {
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> {
            val profile by session.profile.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { session.loadProfile() }
            // Se espera el perfil para abrir la app con las pantallas de su rol.
            if (profile != null) {
                DydNavHost(session = session)
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    TextButton(onClick = session::loadProfile) { Text("Reintentar") }
                    TextButton(onClick = session::signOut) { Text("Cerrar sesión") }
                }
            }
        }
        is SessionStatus.NotAuthenticated -> LoginScreen(session)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
