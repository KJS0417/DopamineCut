package com.example.dopaminecut2.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dopaminecut2.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {

    // binding
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    // viewModel (HomeFragment와 같은 데이터 공유)
    private val viewModel: MainViewModel by activityViewModels()

    // 화면 생성 시 UI 초기화
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 뷰 생성 직후 초기 세팅 및 데이터 감시 호출
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeStatisticsData()
    }

    // 누적 데이터 수신 완료 시 하위 차트 및 UI 렌더링 함수 순차적 호출
    private fun observeStatisticsData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // DailyStatistics 관찰 (달력, 바 차트, 도넛 차트용)
                launch {
                    viewModel.dailyStats.collect { stats ->
                        if (stats != null) {
                            initStreakUI(stats)
                            initBarChart(stats)
                            initDoughnutChart(stats)
                        }
                    }
                }

                // 도파민 로그 관찰 (시간대 분석 라인 차트용)
                launch {
                    viewModel.dopamineLogs.collect { logs ->
                        if (logs.isNotEmpty()) {
                            initLineChart(logs)
                        }
                    }
                }
            }
        }
    }

    // 달성일 확인 캘린더 아이콘 표기 (Streak)
    private fun initStreakUI(stats: com.example.dopaminecut2.data.model.DailyStatistics) {
        // TODO: daily_score를 판별, 목표 달성 여부를 달력에 표시하는 로직
    }

    // 주간 숏폼 시청 추이 렌더링 (Bar Chart)
    private fun initBarChart(stats: com.example.dopaminecut2.data.model.DailyStatistics) {
        // TODO: app_usage의 shortform_count 합산을 구해서 BarEntry 데이터 변환
    }

    // 취약 시간대 렌더링 (Line Chart)
    private fun initLineChart(logs: List<com.example.dopaminecut2.data.model.DopamineLog>) {
        // TODO: created_at 시간대별로 감점을 묶어서 LineEntry 데이터 변환
    }

    // 앱 사용 목적 분석 렌더링 (Doughnut Chart)
    private fun initDoughnutChart(stats: com.example.dopaminecut2.data.model.DailyStatistics) {
        // TODO: run_time_sec vs shortform_time_sec 비율을 PieEntry로 변환

        // MPAndroidChart 설정 (가운데를 뚫어서 도넛 모양으로 만듦)
        binding.doughnutChartUsage.isDrawHoleEnabled = true
        binding.doughnutChartUsage.holeRadius = 50f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}