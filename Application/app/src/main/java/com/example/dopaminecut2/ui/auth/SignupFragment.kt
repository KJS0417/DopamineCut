package com.example.dopaminecut2.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dopaminecut2.databinding.FragmentSignupBinding
import com.example.dopaminecut2.ui.navigation.AppDestination
import com.example.dopaminecut2.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

class SignupFragment : Fragment() {
    private var _binding: FragmentSignupBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSignupBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navigator = AppNavigator(findNavController())
        binding.btnSignup.setOnClickListener {
            viewModel.onAction(
                AuthAction.Signup(
                    binding.etEmail.text?.toString().orEmpty(),
                    binding.etPassword.text?.toString().orEmpty(),
                    binding.etNickname.text?.toString().orEmpty()
                )
            )
        }
        binding.btnGoToLogin.setOnClickListener { navigator.navigate(AppDestination.Login) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val loadingHere = state.isLoading && state.activeScreen == AuthScreen.SIGNUP
                    binding.progressBar.visibility = if (loadingHere) View.VISIBLE else View.GONE
                    binding.btnSignup.isEnabled = !state.isLoading
                    binding.tvAuthMessage.text = state.message.orEmpty()
                    binding.tvAuthMessage.visibility = if (
                        state.activeScreen == AuthScreen.SIGNUP && state.message != null
                    ) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
