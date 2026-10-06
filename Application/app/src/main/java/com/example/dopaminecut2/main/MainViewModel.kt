package com.example.dopaminecut2.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.local.OnboardingStage
import com.example.dopaminecut2.data.local.OnboardingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MainEntryState {
    data object Loading : MainEntryState
    data object AuthenticationRequired : MainEntryState
    data class Ready(val userId: String, val stage: OnboardingStage) : MainEntryState
    data class Error(val message: String) : MainEntryState
}

class MainViewModel(
    private val authRepository: AuthRepository,
    private val onboardingStore: OnboardingStore
) : ViewModel() {
    private val _entryState = MutableStateFlow<MainEntryState>(MainEntryState.Loading)
    val entryState: StateFlow<MainEntryState> = _entryState.asStateFlow()

    private var progressJob: Job? = null
    private var activeUserId: String? = null

    init {
        viewModelScope.launch {
            authRepository.authState().collect(::onAccountChanged)
        }
    }

    fun refreshEntry() {
        onAccountChanged(authRepository.currentUserId())
    }

    private fun onAccountChanged(userId: String?) {
        if (userId == null) {
            activeUserId = null
            progressJob?.cancel()
            progressJob = null
            _entryState.value = MainEntryState.AuthenticationRequired
            return
        }
        if (activeUserId == userId && progressJob?.isActive == true) return

        activeUserId = userId
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            runCatching {
                onboardingStore.observeOnboarding(userId).collect { progress ->
                    if (authRepository.currentUserId() == userId) {
                        _entryState.value = MainEntryState.Ready(userId, progress.stage)
                    }
                }
            }.onFailure { error ->
                _entryState.value = MainEntryState.Error(
                    error.localizedMessage ?: "초기 설정 상태를 불러오지 못했습니다."
                )
            }
        }
    }
}
