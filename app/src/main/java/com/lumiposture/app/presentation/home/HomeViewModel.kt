package com.lumiposture.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.StudySessionSummary
import com.lumiposture.app.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class DailyTrendPoint(
    val dayLabel: String,
    val avgScore: Int,
    val sessionCount: Int,
)

data class HomeUiState(
    val selectedMode: StudyMode = StudyMode.HANDHELD,
    val latestSession: StudySessionSummary? = null,
    val recentSessions: List<StudySessionSummary> = emptyList(),
    val weeklyTrends: List<DailyTrendPoint> = emptyList(),
    val weeklyTotalMinutes: Int = 0,
    val weeklyAverageScore: Int = 0,
    val preferences: UserPreferences = UserPreferences(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    userDataStore: UserCalibrationDataStore,
) : ViewModel() {

    private val selectedModeFlow = MutableStateFlow(StudyMode.HANDHELD)

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.observeAllSessions(),
        userDataStore.preferencesFlow,
        selectedModeFlow,
    ) { sessions, prefs, mode ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6)

        val weeklySessions = sessions.filter { summary ->
            val date = Instant.ofEpochMilli(summary.startTimeMillis).atZone(zone).toLocalDate()
            !date.isBefore(weekStart) && !date.isAfter(today)
        }

        val groupedByDate = weeklySessions.groupBy { summary ->
            Instant.ofEpochMilli(summary.startTimeMillis).atZone(zone).toLocalDate()
        }

        val trends = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            val daySessions = groupedByDate[date].orEmpty()
            val avg = if (daySessions.isNotEmpty()) {
                daySessions.asSequence().map { it.overallScore }.average().roundToInt()
            } else {
                0
            }
            DailyTrendPoint(
                dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("id-ID")),
                avgScore = avg,
                sessionCount = daySessions.size,
            )
        }

        val totalSec = weeklySessions.sumOf { it.durationSeconds }
        val avgWeekly = if (weeklySessions.isNotEmpty()) {
            weeklySessions.asSequence().map { it.overallScore }.average().roundToInt()
        } else {
            0
        }

        HomeUiState(
            selectedMode = mode,
            latestSession = sessions.firstOrNull(),
            recentSessions = sessions.take(3),
            weeklyTrends = trends,
            weeklyTotalMinutes = (totalSec / 60f).roundToInt(),
            weeklyAverageScore = avgWeekly,
            preferences = prefs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(),
    )

    fun selectStudyMode(mode: StudyMode) {
        selectedModeFlow.value = mode
    }
}
