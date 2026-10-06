package com.example.dopaminecut2.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dopaminecut2.auth.AuthRepository
import com.example.dopaminecut2.data.local.OnboardingFlowPolicy
import com.example.dopaminecut2.data.local.OnboardingStage
import com.example.dopaminecut2.data.local.OnboardingStore
import com.example.dopaminecut2.domain.InterventionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class InterventionSetupUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val selection: InterventionMode? = null,
    val editing: Boolean = false,
    val nextStage: OnboardingStage? = null,
    val message: String? = null
)

class InterventionSetupViewModel(
    private val authRepository: AuthRepository,
    private val store: OnboardingStore
) : ViewModel() {
    private val ownerId = authRepository.currentUserId()
    private val mutableState = MutableStateFlow(InterventionSetupUiState())
    val uiState = mutableState.asStateFlow()
    private var draft: InterventionMode? = null

    init { load() }

    fun restoreDraft(userId: String?, mode: InterventionMode?) {
        if (userId != ownerId || mode == null) return
        draft = mode
        if (mutableState.value.loaded) mutableState.value = mutableState.value.copy(selection = mode)
    }

    fun select(mode: InterventionMode) {
        if (!mutableState.value.loaded || mutableState.value.saving) return
        draft = mode
        mutableState.value = mutableState.value.copy(selection = mode, message = null)
    }

    fun load() {
        if (mutableState.value.saving || mutableState.value.loaded) return
        val userId = ownerId ?: run {
            mutableState.value = mutableState.value.copy(loading = false, message = "로그인 정보가 없습니다.")
            return
        }
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        viewModelScope.launch {
            try {
                val progress = store.observeOnboarding(userId).first()
                if (authRepository.currentUserId() == userId) {
                    mutableState.value = InterventionSetupUiState(
                        loading = false, loaded = true,
                        selection = draft ?: OnboardingFlowPolicy.intervention(progress),
                        editing = progress.stage == OnboardingStage.COMPLETE
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (authRepository.currentUserId() == userId) mutableState.value = mutableState.value.copy(
                    loading = false, message = "개입 설정을 불러오지 못했습니다. 다시 시도해 주세요."
                )
            }
        }
    }

    fun save(recordOnly: Boolean = false) {
        val current = mutableState.value
        if (!current.loaded || current.saving) return
        val mode = if (recordOnly) InterventionMode.RECORD_ONLY else current.selection ?: return
        val userId = ownerId ?: return
        if (authRepository.currentUserId() != userId) return
        mutableState.value = current.copy(saving = true, selection = mode, message = null)
        viewModelScope.launch {
            try {
                store.saveInterventionChoice(userId, mode)
                if (authRepository.currentUserId() == userId) mutableState.value = mutableState.value.copy(
                    saving = false,
                    nextStage = if (current.editing) OnboardingStage.COMPLETE else OnboardingStage.MEASUREMENT
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (authRepository.currentUserId() == userId) mutableState.value = mutableState.value.copy(
                    saving = false, message = "개입 방식을 저장하지 못했습니다. 다시 시도해 주세요."
                )
            }
        }
    }
}
