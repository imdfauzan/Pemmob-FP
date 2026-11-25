package com.lumiposture.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.domain.model.StudySessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class HistoryUiState(
    val sessions: List<StudySessionSummary> = emptyList(),
    val totalMinutesAllTime: Int = 0,
    val averageScoreAllTime: Int = 0,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = sessionRepository.observeAllSessions()
        .map { list ->
            val totalMin = (list.sumOf { it.durationSeconds } / 60f).roundToInt()
            val avgScore = if (list.isNotEmpty()) {
                list.asSequence().map { it.overallScore }.average().roundToInt()
            } else {
                0
            }
            HistoryUiState(
                sessions = list,
                totalMinutesAllTime = totalMin,
                averageScoreAllTime = avgScore,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HistoryUiState(),
        )

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
        }
    }
}
