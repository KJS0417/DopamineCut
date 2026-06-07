package com.example.dopaminecut2.main

import androidx.fragment.app.Fragment
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.ActivityMainBinding
import com.example.dopaminecut2.settings.SettingsFragment

class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    override fun initView() {
        if (supportFragmentManager.findFragmentById(R.id.main_frame_layout) == null) {
            replaceFragment(HomeFragment())
        }

        // 임시
        binding.tvUserNickname.text = "안녕하세요, (user)님!"
    }

    override fun setupListeners() {
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_stats -> {
                    replaceFragment(StatsFragment())
                    true
                }
                R.id.nav_community -> {
                    // 나중에 교체
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    override fun observeViewModel() {}

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frame_layout, fragment)
            .commit()
    }
}