package com.lumiposture.app.presentation.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.domain.model.SessionReport
import com.lumiposture.app.domain.usecase.AnalyzeSessionWithAiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val report: SessionReport? = null,
    val isLoading: Boolean = true,
    val isReanalyzing: Boolean = false,
    val analysisErrorMessage: String? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val analyzeSessionUseCase: AnalyzeSessionWithAiUseCase,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: 0L
    private val isReanalyzingFlow = MutableStateFlow(value = false)
    private val errorMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportUiState> = combine(
        sessionRepository.observeSessionReport(sessionId),
        isReanalyzingFlow,
        errorMessageFlow,
    ) { report, reanalyzing, errorMsg ->
        ReportUiState(
            report = report,
            isLoading = report == null,
            isReanalyzing = reanalyzing,
            analysisErrorMessage = errorMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ReportUiState(),
    )

    fun reanalyzeWithGemini() {
        val current = uiState.value.report ?: return
        if (isReanalyzingFlow.value) return

        viewModelScope.launch {
            isReanalyzingFlow.value = true
            errorMessageFlow.value = null
            val aggregate = analyzeSessionUseCase.buildAggregateInput(
                durationSeconds = current.session.durationSeconds,
                studyMode = current.session.studyMode,
                overallScore = current.session.overallScore,
                samples = current.samples,
                avgBrightnessPct = 60,
            )
            val outcome = analyzeSessionUseCase.analyzeWithOutcome(aggregate, current.samples)
            sessionRepository.updateSessionInsight(current.session.id, outcome.insight)
            errorMessageFlow.value = if (outcome.insight.isGeneratedByCloudAi) {
                null
            } else {
                outcome.fallbackReason
            }
            isReanalyzingFlow.value = false
        }
    }
}
