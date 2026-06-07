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
import com.example.dopaminecut2.databinding.FragmentHomeBinding
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

                // 도파민 로그 관찰 (파이 차트용)
                launch {
                    viewModel.dopamineLogs.collect { logs ->
                        if (logs.isNotEmpty()) {
                            initPieChart(logs)
                        }
                    }
                }

                // daily_statistics 데이터 감시/관찰 (Bar 차트용)
                launch {
                    viewModel.dailyStats.collect { stats ->
                        if (stats != null) {
                            initHorizontalBarChart(stats.appUsage)
                        }
                    }
                }
            }
        }
    }

    // 카테고리 비율 렌더링 (Pie Chart)
    private fun initPieChart(logs: List<com.example.dopaminecut2.data.model.DopamineLog>) {
        // TODO: MPAndroidChart 라이브러리 사용,
        // logs 데이터를 파이 차트용 데이터로 변환해 넣는 코드 작성.
    }

    // 앱 사용 시간 랭킹 렌더링 (Horizontal Bar)
    private fun initHorizontalBarChart(appUsage: Map<String, com.example.dopaminecut2.data.model.AppUsage>) {
        // TODO: MPAndroidChart 라이브러리 사용,
        // appUsage 데이터를 가로 막대 차트용 데이터로 변환해 넣는 코드 작성.
    }

    // 화면 꺼질 때 메모리 누수 방지용
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}