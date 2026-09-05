package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.data.local.dao.BriefingRecord
import il.co.tradesmanager.data.local.dao.EvidenceDao
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.PermitPrecautionEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkAttendeeEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Toolbox talks and permits to work: the Evidence lens.
 *
 * Both of these exist to be produced later — to an inspector, an insurer, or a
 * court — by somebody who was not there. That shapes every decision here. The
 * wording of a precaution is copied onto the permit rather than referenced, so
 * a record cannot change meaning when the app is updated. An attendee's name is
 * stored beside their account id, so the register still reads years after the
 * account was removed. And nothing that would weaken a record is reachable: a
 * permit cannot be issued with a precaution outstanding, because the only way
 * to issue one goes through [issue], which asks
 * [il.co.tradesmanager.core.evidence.Permits.canIssue] first.
 */
class EvidenceRepository(
    private val dao: EvidenceDao,
    private val audit: AuditTrail,
) {

    // ---- Toolbox talks ----

    fun observeTalks(projectId: String? = null): Flow<List<ToolboxTalkEntity>> =
        dao.observeTalks(projectId)

    fun observeTalk(id: String): Flow<ToolboxTalkEntity?> = dao.observeTalk(id)

    fun observeAttendees(talkId: String): Flow<List<ToolboxTalkAttendeeEntity>> =
        dao.observeAttendees(talkId)

    /** Everyone on the books and when they were last briefed. */
    fun observeBriefingRecords(): Flow<List<BriefingRecord>> = dao.observeBriefingRecords()

    suspend fun recordTalk(
        projectId: String?,
        topic: String,
        notes: String?,
        heldAt: Long,
        presenterName: String,
        presenterId: String?,
        actorName: String,
    ): ToolboxTalkEntity {
        val talk = ToolboxTalkEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            topic = topic.trim(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            heldAt = heldAt,
            presenterName = presenterName.trim(),
            presenterId = presenterId,
            signatureStrokes = null,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertTalk(talk)
        audit.record(TALK, talk.id, AuditTrail.Action.CREATE, actorName, talk.topic)
        return talk
    }

    /**
     * Adds somebody to a register.
     *
     * The id is derived from the talk and the person when they have an account,
     * so signing the same person in twice corrects the row rather than making
     * a register that claims a crew of nine when six turned up. A name typed
     * for a visiting subcontractor gets a fresh id, because two different
     * people called Yossi is a real situation and merging them would be worse.
     */
    suspend fun signIn(
        talkId: String,
        accountId: String?,
        name: String,
        signatureStrokes: String?,
        actorName: String,
    ) {
        val attendee = ToolboxTalkAttendeeEntity(
            id = accountId?.let { "$talkId:$it" } ?: UUID.randomUUID().toString(),
            talkId = talkId,
            accountId = accountId,
            name = name.trim(),
            signatureStrokes = signatureStrokes,
            signedAt = System.currentTimeMillis(),
        )
        dao.upsertAttendee(attendee)
        audit.record(TALK, talkId, AuditTrail.Action.SIGN_OFF, actorName, "${attendee.name} attended")
    }

    suspend fun removeAttendee(attendee: ToolboxTalkAttendeeEntity, actorName: String) {
        dao.deleteAttendee(attendee)
        audit.record(TALK, attendee.talkId, AuditTrail.Action.UPDATE, actorName, "Removed ${attendee.name}")
    }

    suspend fun removeTalk(talk: ToolboxTalkEntity, actorName: String) {
        dao.deleteTalk(talk)
        audit.record(TALK, talk.id, AuditTrail.Action.DELETE, actorName, talk.topic)
    }

    /** A briefing nobody attended records nothing. Used to mark the row unfinished. */
    suspend fun isRegisterEmpty(talkId: String): Boolean = dao.attendeeCount(talkId) == 0

    // ---- Permits to work ----

    fun observePermits(projectId: String? = null): Flow<List<PermitEntity>> =
        dao.observePermits(projectId)

    fun observePermit(id: String): Flow<PermitEntity?> = dao.observePermit(id)

    fun observePrecautions(permitId: String): Flow<List<PermitPrecautionEntity>> =
        dao.observePrecautions(permitId)

    /**
     * Raises a permit as a draft, with its precautions already on it.
     *
     * [precautions] arrives as resolved text from the screen that showed it,
     * in the language the issuer was reading. That is the point: the record has
     * to say what the person actually ticked.
     */
    suspend fun raisePermit(
        projectId: String?,
        type: String,
        description: String,
        location: String?,
        issuedToName: String,
        precautions: List<String>,
        actorName: String,
    ): PermitEntity {
        val now = System.currentTimeMillis()
        val permit = PermitEntity(
            id = UUID.randomUUID().toString(),
            reference = "PTW-%03d".format(dao.permitCount() + 1),
            projectId = projectId,
            type = type,
            status = Permits.Status.DRAFT,
            description = description.trim(),
            location = location?.trim()?.takeIf { it.isNotEmpty() },
            issuedByName = null,
            issuedToName = issuedToName.trim(),
            validFrom = null,
            validTo = null,
            issuedAt = null,
            issuerSignature = null,
            closedAt = null,
            closedByName = null,
            closeNotes = null,
            createdBy = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertPermit(permit)
        dao.upsertPrecautions(
            precautions.mapIndexed { index, label ->
                PermitPrecautionEntity(
                    id = UUID.randomUUID().toString(),
                    permitId = permit.id,
                    label = label,
                    checked = false,
                    checkedAt = null,
                    sortOrder = index,
                )
            },
        )
        audit.record(PERMIT, permit.id, AuditTrail.Action.CREATE, actorName, permit.reference)
        return permit
    }

    suspend fun tick(precaution: PermitPrecautionEntity, checked: Boolean) {
        dao.upsertPrecaution(
            precaution.copy(
                checked = checked,
                checkedAt = if (checked) System.currentTimeMillis() else null,
            ),
        )
    }

    /**
     * Signs and hands over the permit. Returns false, changing nothing, if a
     * precaution is outstanding or the window makes no sense.
     *
     * A boolean rather than an exception because the caller is a screen with a
     * disabled button: this is the second line of defence, not the first, and
     * the point is that there is no code path that skips it.
     */
    suspend fun issue(
        permitId: String,
        validFrom: Long,
        validTo: Long,
        issuedByName: String,
        signatureStrokes: String?,
    ): Boolean {
        val permit = dao.permit(permitId) ?: return false
        val precautions = dao.precautions(permitId)
        val allowed = Permits.canIssue(
            status = permit.status,
            precautionsTotal = precautions.size,
            precautionsChecked = precautions.count { it.checked },
            validFrom = validFrom,
            validTo = validTo,
        )
        if (!allowed) return false

        val now = System.currentTimeMillis()
        dao.upsertPermit(
            permit.copy(
                status = Permits.Status.ISSUED,
                validFrom = validFrom,
                validTo = validTo,
                issuedAt = now,
                issuedByName = issuedByName.trim(),
                issuerSignature = signatureStrokes,
                updatedAt = now,
            ),
        )
        audit.record(
            PERMIT, permitId, AuditTrail.Action.SIGN_OFF, issuedByName,
            "${permit.reference} issued to ${permit.issuedToName}",
        )
        return true
    }

    /**
     * Signs the permit back: the work is finished and the area has been made
     * safe. An expired permit can still be closed, and normally has to be —
     * work stopping and the area being checked are two different events.
     */
    suspend fun close(permitId: String, closedByName: String, notes: String?): Boolean {
        val permit = dao.permit(permitId) ?: return false
        if (!Permits.canClose(permit.status)) return false

        val now = System.currentTimeMillis()
        dao.upsertPermit(
            permit.copy(
                status = Permits.Status.CLOSED,
                closedAt = now,
                closedByName = closedByName.trim(),
                closeNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            ),
        )
        audit.record(
            PERMIT, permitId, AuditTrail.Action.SIGN_OFF, closedByName,
            "${permit.reference} signed back",
        )
        return true
    }

    /**
     * Withdraws a permit before its time, because conditions changed. Kept
     * rather than deleted: a permit that was cancelled at eleven because the
     * wind got up is exactly the sort of thing somebody asks about later.
     */
    suspend fun cancel(permit: PermitEntity, actorName: String) {
        dao.upsertPermit(
            permit.copy(status = Permits.Status.CANCELLED, updatedAt = System.currentTimeMillis()),
        )
        audit.record(PERMIT, permit.id, AuditTrail.Action.UPDATE, actorName, "${permit.reference} cancelled")
    }

    suspend fun removePermit(permit: PermitEntity, actorName: String) {
        dao.deletePermit(permit)
        audit.record(PERMIT, permit.id, AuditTrail.Action.DELETE, actorName, permit.reference)
    }

    // ---- Snagging ----

    fun observeSnags(projectId: String? = null): Flow<List<SnagEntity>> =
        dao.observeSnags(projectId)

    fun observeSnag(id: String): Flow<SnagEntity?> = dao.observeSnag(id)

    suspend fun raiseSnag(
        projectId: String,
        title: String,
        location: String?,
        assignedToName: String?,
        tradeId: String?,
        dueOn: Long?,
        blocksHandover: Boolean,
        actorName: String,
    ): SnagEntity {
        val now = System.currentTimeMillis()
        val snag = SnagEntity(
            id = UUID.randomUUID().toString(),
            reference = "SNAG-%03d".format(dao.snagCount() + 1),
            projectId = projectId,
            title = title.trim(),
            location = location?.trim()?.takeIf { it.isNotEmpty() },
            tradeId = tradeId,
            assignedToName = assignedToName?.trim()?.takeIf { it.isNotEmpty() },
            status = Snags.Status.OPEN,
            blocksHandover = blocksHandover,
            raisedByName = actorName,
            raisedAt = now,
            dueOn = dueOn,
            fixedByName = null,
            fixedAt = null,
            verifiedByName = null,
            verifiedAt = null,
            verifyNotes = null,
            updatedAt = now,
        )
        dao.upsertSnag(snag)
        audit.record(SNAG, snag.id, AuditTrail.Action.CREATE, actorName, "${snag.reference} ${snag.title}")
        return snag
    }

    /**
     * Records that somebody says they have put it right.
     *
     * Deliberately not the same call as [verifySnag], and deliberately not
     * able to reach [Snags.Status.CLOSED]. The person who did the work cannot
     * be the person who signs it off, or the list is worth nothing.
     */
    suspend fun markSnagFixed(snagId: String, actorName: String): Boolean {
        val snag = dao.snag(snagId) ?: return false
        if (!Snags.canMarkFixed(snag.status)) return false
        val now = System.currentTimeMillis()
        dao.upsertSnag(
            snag.copy(
                status = Snags.Status.FIXED,
                fixedByName = actorName.trim(),
                fixedAt = now,
                // A snag going round again starts with a clean verification:
                // the last rejection was about work that has now been redone.
                verifiedByName = null,
                verifiedAt = null,
                verifyNotes = null,
                updatedAt = now,
            ),
        )
        audit.record(SNAG, snagId, AuditTrail.Action.UPDATE, actorName, "${snag.reference} claimed fixed")
        return true
    }

    /**
     * Somebody went and looked. [accepted] false sends it back to the trade
     * rather than deleting anything — a snag that failed its check twice is
     * the most useful row on the list.
     */
    suspend fun verifySnag(
        snagId: String,
        accepted: Boolean,
        notes: String?,
        actorName: String,
    ): Boolean {
        val snag = dao.snag(snagId) ?: return false
        if (!Snags.canVerify(snag.status)) return false
        val now = System.currentTimeMillis()
        val status = if (accepted) Snags.Status.CLOSED else Snags.Status.REJECTED
        dao.upsertSnag(
            snag.copy(
                status = status,
                verifiedByName = actorName.trim(),
                verifiedAt = now,
                verifyNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            ),
        )
        audit.record(
            SNAG, snagId, AuditTrail.Action.SIGN_OFF, actorName,
            "${snag.reference} ${status.lowercase()}",
        )
        return true
    }

    suspend fun removeSnag(snag: SnagEntity, actorName: String) {
        dao.deleteSnag(snag)
        audit.record(SNAG, snag.id, AuditTrail.Action.DELETE, actorName, snag.reference)
    }

    private companion object {
        const val TALK = "toolbox_talk"
        const val PERMIT = "permit"
        const val SNAG = "snag"
    }
}
