package com.example.dopaminecut2.ui.goal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.dopaminecut2.DopamineCutApplication
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.FragmentGoalCandidatesBinding
import com.example.dopaminecut2.di.ViewModelFactory
import com.example.dopaminecut2.ui.navigation.AppDestination
import com.example.dopaminecut2.ui.navigation.AppNavigator
import com.example.dopaminecut2.ui.navigation.GoalCandidateSource
import com.example.dopaminecut2.ui.navigation.GoalEditorMode

class GoalCandidatesFragment : Fragment() {
    private var _binding: FragmentGoalCandidatesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: GoalViewModel by viewModels {
        val dependencies = (requireActivity().application as DopamineCutApplication).requireDependencies()
        ViewModelFactory {
            GoalViewModel(
                dependencies.authRepository,
                dependencies.onboardingStore,
                dependencies.goalStore
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentGoalCandidatesBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCandidateHabit.visibility = View.GONE
        binding.btnCandidateSession.visibility = View.GONE
        val navigator = AppNavigator(findNavController())
        val source = runCatching {
            GoalCandidateSource.valueOf(arguments?.getString(AppNavigator.ARG_SOURCE).orEmpty())
        }.getOrDefault(GoalCandidateSource.PERSONALIZED)
        binding.tvCandidateContext.text = getString(
            when (source) {
                GoalCandidateSource.INITIAL -> R.string.initial_candidates_notice
                GoalCandidateSource.PERSONALIZED -> R.string.personalized_candidates_unavailable
                GoalCandidateSource.NEXT_PLAN -> R.string.next_candidates_unavailable
            }
        )
        binding.tvCandidateContext.text = "숏폼 시청 시간·영상 수·앱 사용시간을 복수 선택할 수 있어요.\n유효 3일 이상 기록이 있으면 줄이기(15%) · 더 줄이기(30%) · 도전(50%)을 제안해요. 추천을 선택해도 ‘목표 저장’을 눌러야 시작됩니다."
        binding.btnCandidateHabit.setOnClickListener {
            navigator.navigate(AppDestination.GoalEditor(GoalEditorMode.ACCEPT_CANDIDATE, "habit"))
        }
        binding.btnCandidateSession.setOnClickListener {
            navigator.navigate(AppDestination.GoalEditor(GoalEditorMode.ACCEPT_CANDIDATE, "session"))
        }
        binding.btnDirectGoal.setOnClickListener {
            navigator.navigate(AppDestination.GoalEditor(GoalEditorMode.CREATE))
        }
        binding.btnMeasureFirst.setOnClickListener {
            if (source == GoalCandidateSource.INITIAL) {
                viewModel.onAction(GoalAction.ContinueWithoutGoal)
            } else {
                navigator.back()
            }
        }
        binding.btnBack.setOnClickListener { navigator.back() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
