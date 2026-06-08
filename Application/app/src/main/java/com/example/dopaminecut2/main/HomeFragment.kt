package com.example.dopaminecut2.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dopaminecut2.data.model.AppUsage
import com.example.dopaminecut2.data.model.DopamineLog
import com.example.dopaminecut2.databinding.FragmentHomeBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    // binding 객체
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // viewModel 객체 (Activity와 공유)
    private val viewModel: MainViewModel by activityViewModels()

    // 화면 생성 시 UI 초기화
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 뷰 생성 직후 UI 세팅 및 데이터 감시 호출
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 팝업 버튼 클릭 이벤트 연결
        binding.btnOpenTargetSetting.setOnClickListener {
            // TargetSettingDialog 팝업 띄우기
            val dialog = TargetSettingDialog()
            dialog.show(childFragmentManager, "TargetSettingDialog")
        }

        observeDashboardData()
    }

    // 데이터 감시하여 점수 갱신 및 차트 함수 호출
    private fun observeDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 도파민 로그 관찰 (파이 차트 그리기)
                launch {
                    viewModel.dopamineLogs.collect { logs ->
                        if (logs.isNotEmpty()) {
                            binding.pieChartCategory.post {
                                initPieChart(logs)
                            }
                        }
                    }
                }

                // daily_statistics 데이터 감시/관찰 (가로 막대 차트)
                launch {
                    viewModel.dailyStats.collect { stats ->
                        if (stats != null && stats.appUsage.isNotEmpty()) {
                            binding.barChartAppRanking.post {
                                initHorizontalBarChart(stats.appUsage)
                            }
                        }
                    }
                }
            }
        }
    }

    // 카테고리 비율 렌더링 (Pie Chart)
    private fun initPieChart(logs: List<DopamineLog>) {
        val categoryCountMap = logs.groupingBy { it.category }.eachCount()

        // 파이 차트에 들어갈 엔트리 만들기
        val entries = ArrayList<PieEntry>()
        for ((category, count) in categoryCountMap) {
            // 알 수 없는 카테고리의 이름 변경
            val labelName = if (category == "UNKNOWN") "기타" else category
            entries.add(PieEntry(count.toFloat(), labelName))
        }

        // 차트 색상 및 글씨 크기 세팅
        val dataSet = PieDataSet(entries, "")

        // color 리스트
        val pieColors = listOf(
            "#FF9999".toColorInt(), // 빨강
            "#FFCC99".toColorInt(), // 주황
            "#FFD54F".toColorInt(), // 노랑 (가독성 추가를 위한 개나리색)
            "#99FF99".toColorInt(), // 초록
            "#99CCFF".toColorInt(), // 파랑
            "#9999FF".toColorInt(), // 남색
            "#CC99FF".toColorInt(), // 보라
            "#FF99CC".toColorInt(), // 핑크
            "#B3B3B3".toColorInt()  // 회색 (기타 등)
        )

        dataSet.colors = pieColors
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = android.graphics.Color.WHITE // 흰색 글씨

        // 차트에 데이터 적용/그리기
        val data = PieData(dataSet)

        data.setValueFormatter(object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}회"
            }
        })

        binding.pieChartCategory.data = data
        binding.pieChartCategory.description.isEnabled = false // 보기 싫은 설명 텍스트 숨기기
        binding.pieChartCategory.setEntryLabelColor(android.graphics.Color.BLACK) // 검정색 라벨
        binding.pieChartCategory.animateY(1000) // 1초를 두고 나타나는 애니메이션
        binding.pieChartCategory.invalidate() // 화면 갱신
    }

    // 앱 사용 시간 랭킹 렌더링 (Horizontal Bar)
    private fun initHorizontalBarChart(appUsage: Map<String, AppUsage>) {
        // 사용 시간이 많은 순서대로 내림차순 정렬 (runTimeSec)
        val sortedUsage = appUsage.entries.sortedByDescending { it.value.runTimeSec }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>() // Y축에 들어갈 앱 이름들 (가로 차트라 세로축에 넣음)

        // 데이터를 막대그래프 규격으로 변환
        sortedUsage.forEachIndexed { index, entry ->
            val platformName = entry.key
            // 초를 분 단위로 변환 (시각적 보완)
            val minutesUsed = entry.value.runTimeSec / 60f

            // X좌표는 인덱스, Y좌표는 분(min)
            entries.add(BarEntry(index.toFloat(), minutesUsed))
            labels.add(platformName.replaceFirstChar { it.uppercase() }) // youtube -> Youtube (대문자)
        }

        // 막대그래프 디자인
        val dataSet = BarDataSet(entries, "사용 시간 (분)")
        dataSet.colors = ColorTemplate.PASTEL_COLORS.toList()
        dataSet.valueTextSize = 12f

        val data = BarData(dataSet)
        binding.barChartAppRanking.data = data

        // X축(앱 이름)을 숫자가 아닌 실제 앱 이름(labels)으로 바꿔주기
        val xAxis = binding.barChartAppRanking.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.setDrawGridLines(false) // 배경 격자무늬 제거
        xAxis.granularity = 1f // 레이블 겹치지않게

        binding.barChartAppRanking.isDoubleTapToZoomEnabled = false // 더블 클릭 확대 방지용
        binding.barChartAppRanking.setScaleEnabled(false) // 두 손가락으로..? 줌인 방지용

        // 기타 차트 디자인 정리
        binding.barChartAppRanking.description.isEnabled = false
        binding.barChartAppRanking.axisRight.isEnabled = false // 오른쪽 숫자 제거

        binding.barChartAppRanking.extraLeftOffset = 30f // 왼쪽 여백

        binding.barChartAppRanking.animateY(1000) // 애니메이션 효과
        binding.barChartAppRanking.invalidate()

    }

    // 화면 꺼질 때 메모리 누수 방지용
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}