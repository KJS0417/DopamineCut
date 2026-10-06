package com.example.dopaminecut2.ui.auth

enum class AuthScreen { LOGIN, SIGNUP }

sealed interface AuthCompletion {
    val id: Long
    data class LoggedIn(override val id: Long) : AuthCompletion
    data class SignedUp(override val id: Long) : AuthCompletion
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val activeScreen: AuthScreen? = null,
    val message: String? = null,
    val completion: AuthCompletion? = null
)

sealed interface AuthAction {
    data class Login(val email: String, val password: String) : AuthAction
    data class Signup(val email: String, val password: String, val nickname: String) : AuthAction
    data class RequestPasswordReset(val email: String) : AuthAction
    data object ClearMessage : AuthAction
}
