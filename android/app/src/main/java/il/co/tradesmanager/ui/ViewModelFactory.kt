package il.co.tradesmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import il.co.tradesmanager.di.AppContainer

/**
 * Builds view models from the container without an injection framework.
 * Every screen's view model takes the container and picks what it needs.
 */
class ViewModelFactory(
    private val container: AppContainer,
    private val create: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
}
