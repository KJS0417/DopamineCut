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
import com.example.dopaminecut2.databinding.FragmentLoginBinding
import com.example.dopaminecut2.ui.navigation.AppDestination
import com.example.dopaminecut2.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLoginBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navigator = AppNavigator(findNavController())
        binding.btnLogin.setOnClickListener {
            viewModel.onAction(
                AuthAction.Login(
                    binding.etEmail.text?.toString().orEmpty(),
                    binding.etPassword.text?.toString().orEmpty()
                )
            )
        }
        binding.btnResetPassword.setOnClickListener {
            viewModel.onAction(
                AuthAction.RequestPasswordReset(binding.etEmail.text?.toString().orEmpty())
            )
        }
        binding.btnGoToSignup.setOnClickListener { navigator.navigate(AppDestination.Signup) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val loadingHere = state.isLoading && state.activeScreen == AuthScreen.LOGIN
                    binding.progressBar.visibility = if (loadingHere) View.VISIBLE else View.GONE
                    binding.btnLogin.isEnabled = !state.isLoading
                    binding.btnResetPassword.isEnabled = !state.isLoading
                    binding.tvAuthMessage.text = state.message.orEmpty()
                    binding.tvAuthMessage.visibility = if (
                        state.activeScreen == AuthScreen.LOGIN && state.message != null
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
