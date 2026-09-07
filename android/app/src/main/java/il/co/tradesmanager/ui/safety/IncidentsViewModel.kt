package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.di.AppContainer
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The site's incident register. */
class IncidentsViewModel(
    private val container: AppContainer,
    private val projectId: String?,
) : ViewModel() {

    val incidents: StateFlow<List<IncidentEntity>> = container.safety.observeIncidents()
        .map { all ->
            Incidents.order(
                items = all,
                occurredAt = { it.occurredAt },
                severity = { Incidents.parse(it.severity) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Records one.
     *
     * No location is captured. An incident is reported by somebody standing
     * where it happened, and taking a coordinate off their phone to prove it
     * is surveillance the job does not need — the same rule the rest of the
     * app follows.
     */
    fun report(severity: Incidents.Severity, description: String) = viewModelScope.launch {
        val text = description.trim()
        if (text.isEmpty()) return@launch
        val actor = container.settings.settings.first().actorName
        val now = System.currentTimeMillis()
        container.safety.reportIncident(
            IncidentEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                severity = severity.name,
                description = text,
                occurredAt = now,
                reportedByName = actor.ifBlank { "unknown" },
                createdAt = now,
            ),
        )
    }
}
