package com.example.dopaminecut2.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.example.dopaminecut2.DopamineCutApplication
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.ActivityAuthBinding
import com.example.dopaminecut2.di.ViewModelFactory
import com.example.dopaminecut2.main.MainActivity
import com.example.dopaminecut2.ui.auth.AuthCompletion
import com.example.dopaminecut2.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private var repository: AuthRepository? = null
    private var viewModel: AuthViewModel? = null

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelFactory {
            AuthViewModel(requireNotNull(repository) { "Firebase 인증 기능이 준비되지 않았습니다." })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (application as DopamineCutApplication)
            .dependenciesOrNull()
            ?.authRepository
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnRetryFirebase.setOnClickListener { initializeAuthentication() }
        initializeAuthentication()
    }

    private fun initializeAuthentication() {
        val dependencies = (application as DopamineCutApplication).dependenciesOrNull()
        if (dependencies == null) {
            binding.authUnavailableGroup.visibility = View.VISIBLE
            return
        }
        repository = dependencies.authRepository
        binding.authUnavailableGroup.visibility = View.GONE
        if (dependencies.authRepository.currentUserId() != null) {
            openMain()
            return
        }
        if (supportFragmentManager.findFragmentById(R.id.auth_nav_host) == null) {
            val navHost = NavHostFragment.create(R.navigation.auth_nav_graph)
            supportFragmentManager.beginTransaction()
                .replace(R.id.auth_nav_host, navHost)
                .setPrimaryNavigationFragment(navHost)
                .commitNow()
        }
        if (viewModel == null) {
            viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
            observeAuthentication()
        }
    }

    private fun observeAuthentication() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel?.uiState?.collect { state ->
                    val completion = state.completion ?: return@collect
                    when (completion) {
                        is AuthCompletion.LoggedIn,
                        is AuthCompletion.SignedUp -> openMain()
                    }
                    viewModel?.consumeCompletion(completion.id)
                }
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            if (intent.getStringExtra("habit_owner") == repository?.currentUserId()) {
                putExtra("habit_owner", intent.getStringExtra("habit_owner"))
                putExtra("habit_destination", intent.getStringExtra("habit_destination"))
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
