package il.co.tradesmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.Plans
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * What the firm is on, and how many people that covers.
 *
 * The plan is [Plans.Entitlement] with nothing passed, which is free, and
 * that is not a placeholder standing in for storage that has not been written
 * yet. Nothing has been sold, so nobody is on anything else, and a column
 * recording a plan that nothing can set would be a column that lies. The day
 * a subscription can be checked against something other than the phone it was
 * bought on, this reads it from there.
 *
 * The count of people is real, and it is the number that will matter first:
 * it is what the seats in [Plans.seats] are counted against.
 */
class PlansViewModel(container: AppContainer) : ViewModel() {

    /** Free, for everybody, until a subscription can be verified. */
    val entitlement: Plans.Entitlement = Plans.Entitlement()

    @OptIn(ExperimentalCoroutinesApi::class)
    val onTheBooks: StateFlow<Int> = container.session.state
        .flatMapLatest { state ->
            val companyId = (state as? SessionRepository.State.SignedIn)?.active?.companyId
            container.memberships.observeOnTheBooks(companyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
