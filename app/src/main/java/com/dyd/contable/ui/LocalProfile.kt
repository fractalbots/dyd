package com.dyd.contable.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.dyd.contable.data.Profile

/** Perfil del usuario con sesión, disponible en cualquier pantalla para mostrar u ocultar acciones. */
val LocalProfile = staticCompositionLocalOf<Profile?> { null }
