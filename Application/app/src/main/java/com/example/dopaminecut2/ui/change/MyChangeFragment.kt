package com.example.dopaminecut2.ui.change

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dopaminecut2.R
import com.example.dopaminecut2.databinding.FragmentMyChangeBinding
import com.example.dopaminecut2.ui.common.ContentState
import com.example.dopaminecut2.ui.navigation.AppDestination
import com.example.dopaminecut2.ui.navigation.AppNavigator
import com.example.dopaminecut2.ui.navigation.GoalCandidateSource
import kotlinx.coroutines.launch

class MyChangeFragment : Fragment() {
    private var _binding: FragmentMyChangeBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val viewModel: MyChangeViewModel by viewModels {
        com.example.dopaminecut2.di.ViewModelFactory { MyChangeViewModel(
            (requireActivity().application as com.example.dopaminecut2.DopamineCutApplication).requireDependencies()) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentMyChangeBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val navigator = AppNavigator(findNavController())
        binding.btnBack.setOnClickListener { navigator.back() }
        binding.btnNextGoal.setOnClickListener {
            navigator.navigate(AppDestination.GoalCandidates(GoalCandidateSource.NEXT_PLAN))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvState.text = when (val content = state.content) {
                        is ContentState.Data -> "${content.value.resultLabel}\n\n${content.value.baselineLabel}"
                        is ContentState.Empty -> content.message
                        is ContentState.Error -> content.message
                        is ContentState.Unavailable -> content.reason
                        ContentState.Loading -> ""
                    }
                    binding.btnNextGoal.isEnabled = state.content is ContentState.Data
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
    override fun onResume() { super.onResume(); viewModel.onAction(MyChangeAction.Refresh) }
}
