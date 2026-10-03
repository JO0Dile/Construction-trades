package il.co.tradesmanager.core.access

import il.co.tradesmanager.core.audit.Summaries

/**
 * What a firm publishes about itself, and to whom.
 *
 * A company signing up wants its name and mark on the app the crew opens
 * every morning. It does not necessarily want the owner's mobile number on
 * that same screen, in the hands of forty people, one of whom is leaving next
 * week — and it certainly does not want that decided by whichever developer
 * wrote the screen.
 *
 * So visibility is per field and it is asked, not assumed. The default is not
 * to publish: a firm that skips the question has published nothing, which is
 * the outcome it can live with. A default of "visible" that somebody meant to
 * change and forgot cannot be taken back.
 */
object CompanyProfile {

    /** Who is looking. */
    enum class Audience {
        /** Somebody inside the firm with a management role. Sees everything. */
        INSIDE,

        /**
         * On the tools for this firm, or engaged by it on a job. Sees the
         * name and mark, and whatever else the firm chose to publish.
         */
        WORKFORCE,

        /** Another firm on the same job, or nobody in particular. */
        OUTSIDE,
    }

    /**
     * A field that can be published, and whether it is on by default.
     *
     * Name and logo carry no default flag because they are not optional: a
     * firm with a hidden name is not a firm anybody can work for.
     */
    enum class Field {
        EMAIL,
        PHONE,
        WEBSITE,
        ADDRESS,
        REGISTRATION_NUMBER,
        LICENCE,
        ;

        /**
         * What this field is called in an audit summary.
         *
         * A `when` rather than the enum's own name, so the compiler insists
         * on an answer when a field is added. Building the key by
         * concatenation would have quietly produced one nothing recognises,
         * and an unrecognised key prints as itself — which on a Hebrew screen
         * reads as "field_registration_number".
         */
        val summaryKey: String
            get() = when (this) {
                EMAIL -> Summaries.FIELD_EMAIL
                PHONE -> Summaries.FIELD_PHONE
                WEBSITE -> Summaries.FIELD_WEBSITE
                ADDRESS -> Summaries.FIELD_ADDRESS
                REGISTRATION_NUMBER -> Summaries.FIELD_REGISTRATION
                LICENCE -> Summaries.FIELD_LICENCE
            }
    }

    /**
     * The firm's choices, as stored.
     *
     * Absent means not published. Reading a missing key as "show it" would
     * make every row written before this feature existed suddenly public.
     */
    data class Publication(val visibleToWorkforce: Set<Field> = emptySet())

    fun visibleTo(field: Field, audience: Audience, publication: Publication): Boolean =
        when (audience) {
            Audience.INSIDE -> true
            Audience.WORKFORCE -> field in publication.visibleToWorkforce
            // A registration number is on every invoice the firm issues, so
            // hiding it from another firm on the same job would be theatre.
            // Everything else stays in.
            Audience.OUTSIDE -> field == Field.REGISTRATION_NUMBER
        }

    /**
     * Which audience a viewer falls into.
     *
     * [managementRole] is what decides INSIDE, not employment: a bookkeeper
     * who is not on any site is inside the firm, and a foreman on the tools
     * is not — which is the distinction the owner's mobile number turns on.
     */
    fun audienceOf(
        viewerCompanyId: String?,
        subjectCompanyId: String,
        managementRole: Boolean,
        engagedBySubject: Boolean = false,
    ): Audience = when {
        viewerCompanyId == subjectCompanyId && managementRole -> Audience.INSIDE
        viewerCompanyId == subjectCompanyId || engagedBySubject -> Audience.WORKFORCE
        else -> Audience.OUTSIDE
    }

    /** Roles that see the firm's own private details. */
    fun isManagement(role: Role): Boolean = role == Role.OWNER || role == Role.MANAGER ||
        role == Role.FINANCE || role == Role.HR
}
