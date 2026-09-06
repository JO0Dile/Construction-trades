package il.co.tradesmanager.ui.company

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.CompanyProfile
import il.co.tradesmanager.data.local.entity.CompanyEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The signed-in firm's own profile, and what of it the crew is shown. */
class CompanyProfileViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    val company: StateFlow<CompanyEntity?> = session
        .map { (it as? SessionRepository.State.SignedIn)?.company }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Who the signed-in person is to their own firm.
     *
     * Only management edits the profile, and only management sees the fields
     * that were not published — which is the same distinction, so it is asked
     * once here rather than twice on the screen.
     */
    val audience: StateFlow<CompanyProfile.Audience> = session
        .map { state ->
            val signedIn = state as? SessionRepository.State.SignedIn
                ?: return@map CompanyProfile.Audience.OUTSIDE
            CompanyProfile.audienceOf(
                viewerCompanyId = signedIn.active?.companyId,
                subjectCompanyId = signedIn.company?.id.orEmpty(),
                managementRole = CompanyProfile.isManagement(signedIn.role),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CompanyProfile.Audience.OUTSIDE,
        )

    fun save(
        name: String,
        registrationNumber: String,
        email: String,
        phone: String,
        website: String,
        addressLine: String,
        licenceNumber: String,
        classification: String,
        published: Set<CompanyProfile.Field>,
    ) = viewModelScope.launch {
        val current = company.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.accounts.updateCompanyProfile(
            company = current,
            name = name,
            registrationNumber = registrationNumber,
            logoUri = current.logoUri,
            email = email,
            phone = phone,
            website = website,
            addressLine = addressLine,
            contractorLicenceNumber = licenceNumber,
            contractorClassification = classification,
            licenceExpiresOn = current.licenceExpiresOn,
            published = published,
            actorName = actor,
        )
    }

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    /** A mark taken with the camera — a photograph of the sign on the van. */
    fun captureLogo(photoId: String) = viewModelScope.launch {
        val current = company.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        val stored = container.photos.recordCameraPhoto(photoId, LOGO_OWNER, current.id, actor)
        // Null when the camera was cancelled. Leaving the existing mark alone
        // is the right answer; blanking it because somebody backed out is not.
        if (stored != null) pointAtLogo(current, stored.uri, actor)
    }

    /** A mark chosen from the gallery. */
    fun setLogo(source: Uri) = viewModelScope.launch {
        val current = company.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        val stored = container.photos.importPhoto(source, LOGO_OWNER, current.id, actor)
        if (stored != null) pointAtLogo(current, stored.uri, actor)
    }

    /**
     * Saves the new mark without touching anything else.
     *
     * Everything else is passed straight back from the row rather than from
     * the screen, because choosing a logo must not quietly commit whatever
     * half-typed phone number happened to be in the field at the time.
     */
    private suspend fun pointAtLogo(current: CompanyEntity, uri: String, actor: String) {
        container.accounts.updateCompanyProfile(
            company = current,
            name = current.name,
            registrationNumber = current.registrationNumber,
            logoUri = uri,
            email = current.email,
            phone = current.phone,
            website = current.website,
            addressLine = current.addressLine,
            contractorLicenceNumber = current.contractorLicenceNumber,
            contractorClassification = current.contractorClassification,
            licenceExpiresOn = current.licenceExpiresOn,
            published = publishedOf(current),
            actorName = actor,
        )
    }

    companion object {
        const val LOGO_OWNER = "company.logo"

        /**
         * What the firm chose to publish, read back off the row.
         *
         * An unrecognised name is dropped rather than guessed at. A field this
         * version of the app does not know about must not become a field it
         * publishes.
         */
        fun publishedOf(company: CompanyEntity?): Set<CompanyProfile.Field> =
            company?.publishedToWorkforce
                ?.split(',')
                ?.mapNotNull { name ->
                    CompanyProfile.Field.entries.firstOrNull { it.name == name.trim() }
                }
                ?.toSet()
                .orEmpty()
    }
}
