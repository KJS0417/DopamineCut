package com.example.dopaminecut2.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp

/** Firebase 설정 파일이 없는 개발 환경에서 명확하게 기능을 중단하기 위한 진입점. */
object FirebaseBootstrap {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true

        val googleAppId = context.resources.getIdentifier(
            "google_app_id",
            "string",
            context.packageName
        )
        if (googleAppId == 0) return false

        return FirebaseApp.initializeApp(context) != null
    }
}
