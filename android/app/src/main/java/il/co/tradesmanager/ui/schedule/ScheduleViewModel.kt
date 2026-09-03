package il.co.tradesmanager.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.core.time.TimeOfDay
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val blocks: StateFlow<List<TaskBlockEntity>> = _date
        .flatMapLatest { container.schedule.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openTimeEntry: StateFlow<TimeEntryEntity?> = container.schedule.observeOpenTimeEntry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftDay(days: Long) { _date.value = _date.value.plusDays(days) }

    fun addBlock(title: String, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val actor = container.settings.settings.first().actorName
        val (start, end) = TimeOfDay.sanitiseRange(startMinute, endMinute)
        container.schedule.save(
            TaskBlockEntity(
                id = UUID.randomUUID().toString(),
                epochDay = _date.value.toEpochDay(),
                startMinute = start,
                endMinute = end,
                title = title,
                createdAt = 0,
                updatedAt = 0,
            ),
            actorName = actor,
        )
    }

    fun setDone(id: String, done: Boolean) = viewModelScope.launch {
        container.schedule.setDone(id, done, container.settings.settings.first().actorName)
    }

    fun delete(id: String) = viewModelScope.launch {
        container.schedule.delete(id, container.settings.settings.first().actorName)
    }

    fun copyToTomorrow() = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.schedule.copyDay(blocks.value, _date.value.plusDays(1), actor)
    }

    /**
     * Check-in without location: the GPS stamp is added by the screen only
     * once the user has granted the permission, so a refused permission costs
     * the stamp and nothing else.
     */
    fun toggleCheckIn(latitude: Double?, longitude: Double?) = viewModelScope.launch {
        val open = openTimeEntry.value
        val actor = container.settings.settings.first().actorName
        if (open == null) {
            container.schedule.checkIn(actor.ifBlank { "worker" }, null, latitude, longitude)
        } else {
            container.schedule.checkOut(open)
        }
    }
}
