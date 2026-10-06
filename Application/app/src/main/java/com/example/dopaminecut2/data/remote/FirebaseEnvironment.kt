package com.example.dopaminecut2.data.remote

import com.example.dopaminecut2.BuildConfig

enum class FirebaseEnvironment {
    DEVELOPMENT,
    PRODUCTION;

    companion object {
        val current: FirebaseEnvironment
            get() = when (BuildConfig.FIREBASE_ENVIRONMENT) {
                "production" -> PRODUCTION
                else -> DEVELOPMENT
            }
    }
}
