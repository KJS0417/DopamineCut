package com.example.dopaminecut2.auth

import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dopaminecut2.databinding.ActivitySignupBinding
import com.example.dopaminecut2.main.BaseActivity
import kotlinx.coroutines.launch

class SignupActivity : BaseActivity<ActivitySignupBinding>(ActivitySignupBinding::inflate) {

    // LoginActivity와 동일한 AuthViewModel을 사용하여 로직을 처리합니다.
    private val viewModel: AuthViewModel by viewModels()

    override fun initView() {
        // 화면 초기 세팅 (현재는 비워둠)
    }

    override fun setupListeners() {
        // 가입하기 버튼 클릭 이벤트
        binding.btnSignup.setOnClickListener {
            // EditText에 입력된 값들을 가져옴 (trim()으로 양옆 공백 제거)
            val email = binding.etEmail.text.toString().trim()
            val pw = binding.etPassword.text.toString().trim()
            val nickname = binding.etNickname.text.toString().trim()

            // 뷰모델로 데이터 전달 및 회원가입 요청
            viewModel.signup(email, pw, nickname)
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1. 로딩 상태 감시 (프로그레스 바 껐다 켜기)
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.btnSignup.isEnabled = !isLoading // 로딩 중 버튼 클릭 방지
                    }
                }

                // 2. 이벤트(성공/실패 메시지) 감시
                launch {
                    viewModel.uiEvent.collect { eventMessage ->
                        if (eventMessage == "SIGNUP_SUCCESS") {
                            showToast("회원가입 완료! 환영합니다.")
                            // 현재 화면(SignupActivity)을 종료하면 자동으로 이전 화면(LoginActivity)으로 돌아갑니다.
                            finish()
                        } else {
                            // 에러가 났을 경우 토스트 띄우기 (예: "비밀번호는 6자리 이상이어야 합니다.")
                            showToast(eventMessage)
                        }
                    }
                }
            }
        }
    }
}