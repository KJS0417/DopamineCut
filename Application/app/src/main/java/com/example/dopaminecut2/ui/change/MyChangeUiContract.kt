package com.example.dopaminecut2.ui.change

import com.example.dopaminecut2.ui.common.ContentState

data class ChangeSummaryUi(
    val baselineLabel: String,
    val resultLabel: String,
    val changePercent: Int
)

data class MyChangeUiState(
    val content: ContentState<ChangeSummaryUi> = ContentState.Unavailable(
        "목표 기간과 비교 기준을 연결한 뒤 내 변화 결과를 제공할 예정입니다."
    )
)

sealed interface MyChangeAction {
    data object Refresh : MyChangeAction
}
