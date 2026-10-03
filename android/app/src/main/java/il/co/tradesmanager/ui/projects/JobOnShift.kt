package il.co.tradesmanager.ui.projects

import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.first

/**
 * The job somebody on this phone is checked in to right now, if it is one of
 * the active company's: where a new talk, permit or incident most likely
 * belongs.
 *
 * Offered, never imposed. Talks, permits and incidents used to start on no
 * job at all, and whatever was not moved off it was counted on no job's
 * weekly report. A record on the wrong job is one tap to change; a record on
 * no job is invisible to every count that goes job by job.
 */
suspend fun jobOnShift(container: AppContainer): String? {
    val onShift = container.schedule.observeOpenTimeEntry().first()?.projectId ?: return null
    return onShift.takeIf { id -> container.projects.observeProjects().first().any { it.id == id } }
}
