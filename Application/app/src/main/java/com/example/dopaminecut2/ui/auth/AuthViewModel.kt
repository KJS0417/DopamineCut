package com.example.dopaminecut2.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.auth.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var nextCompletionId = 1L

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.Login -> login(action.email, action.password)
            is AuthAction.Signup -> signup(action.email, action.password, action.nickname)
            is AuthAction.RequestPasswordReset -> requestPasswordReset(action.email)
            AuthAction.ClearMessage -> _uiState.value = _uiState.value.copy(message = null)
        }
    }

    fun consumeCompletion(id: Long) {
        if (_uiState.value.completion?.id == id) {
            _uiState.value = _uiState.value.copy(completion = null)
        }
    }

    private fun login(email: String, password: String) {
        validateCredentials(email, password)?.let { showError(AuthScreen.LOGIN, it); return }
        launchRequest(AuthScreen.LOGIN) {
            authRepository.login(email.trim(), password)
                .onSuccess {
                    _uiState.value = AuthUiState(
                        completion = AuthCompletion.LoggedIn(nextCompletionId++)
                    )
                }
                .onFailure { showError(AuthScreen.LOGIN, authErrorMessage(it)) }
        }
    }

    private fun signup(email: String, password: String, nickname: String) {
        validateCredentials(email, password)?.let { showError(AuthScreen.SIGNUP, it); return }
        if (nickname.isBlank()) {
            showError(AuthScreen.SIGNUP, "닉네임을 입력해 주세요.")
            return
        }
        if (nickname.trim().length > 20) {
            showError(AuthScreen.SIGNUP, "닉네임은 20자 이하로 입력해 주세요.")
            return
        }
        launchRequest(AuthScreen.SIGNUP) {
            authRepository.signup(email.trim(), password, nickname.trim())
                .onSuccess {
                    _uiState.value = AuthUiState(
                        completion = AuthCompletion.SignedUp(nextCompletionId++)
                    )
                }
                .onFailure { showError(AuthScreen.SIGNUP, authErrorMessage(it)) }
        }
    }

    private fun requestPasswordReset(email: String) {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            showError(AuthScreen.LOGIN, "재설정 메일을 받을 이메일을 확인해 주세요.")
            return
        }
        launchRequest(AuthScreen.LOGIN) {
            authRepository.requestPasswordReset(email.trim())
                .onSuccess { showError(AuthScreen.LOGIN, "비밀번호 재설정 메일을 보냈습니다.") }
                .onFailure { showError(AuthScreen.LOGIN, authErrorMessage(it)) }
        }
    }

    private fun launchRequest(screen: AuthScreen, block: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true, activeScreen = screen)
            block()
            if (_uiState.value.isLoading) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun validateCredentials(email: String, password: String): String? = when {
        email.isBlank() || password.isBlank() -> "이메일과 비밀번호를 모두 입력해 주세요."
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "이메일 형식을 확인해 주세요."
        password.length < 6 -> "비밀번호는 6자 이상이어야 합니다."
        else -> null
    }

    private fun showError(screen: AuthScreen, message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            activeScreen = screen,
            message = message
        )
    }

    private fun authErrorMessage(error: Throwable): String = when (error) {
        is FirebaseNetworkException -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "이메일 또는 비밀번호를 확인해 주세요."
        else -> error.localizedMessage ?: "인증 요청을 처리하지 못했습니다."
    }
}
