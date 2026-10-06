package com.example.dopaminecut2.ui.common

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Data<T>(val value: T, val isSyncPending: Boolean = false) : ContentState<T>
    data class Empty(val message: String) : ContentState<Nothing>
    data class Unavailable(val reason: String) : ContentState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true) : ContentState<Nothing>
}
