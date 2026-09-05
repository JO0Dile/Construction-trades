package il.co.tradesmanager.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import il.co.tradesmanager.data.local.dao.PhotoDao
import il.co.tradesmanager.data.local.entity.PhotoEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Photographs: site plans, progress shots, and pictures of the user's own
 * stock.
 *
 * Every image is copied into the app's own storage rather than referenced
 * where it was found. A gallery Uri is a temporary grant that dies with the
 * process, and a site photo that stops loading a week later is worse than no
 * photo at all — this is a record, and records have to survive.
 */
class PhotoRepository(
    private val context: Context,
    private val dao: PhotoDao,
    private val audit: AuditTrail,
) {

    /**
     * Owner types. A project's plan and its progress photos differ only by
     * this string, which is why marking a photo as the plan needs no schema
     * change — just a different label on the same row.
     */
    object Owner {
        const val PROJECT_PLAN = "project.plan"
        const val PROJECT_PHOTO = "project.photo"
        const val INVENTORY_ITEM = "inventory_item"
        const val INCIDENT = "incident"

        /**
         * A snag's two pictures: the one that raised it, and the one that says
         * it was put right. Two owner types on the same table rather than two
         * columns on the snag, so a defect can carry three photos of an
         * awkward corner without a schema that only allowed for one.
         */
        const val SNAG_RAISED = "snag.raised"
        const val SNAG_FIXED = "snag.fixed"

        val projectAny = listOf(PROJECT_PLAN, PROJECT_PHOTO)
    }

    private val photoDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun observeProjectImages(projectId: String): Flow<List<PhotoEntity>> =
        dao.observeForAny(projectId, Owner.projectAny)

    fun observeFor(ownerType: String, ownerId: String): Flow<List<PhotoEntity>> =
        dao.observeFor(ownerType, ownerId)

    fun observeForOwners(ownerType: String, ownerIds: List<String>): Flow<List<PhotoEntity>> =
        dao.observeForOwners(ownerType, ownerIds)

    /**
     * A cover image per project: the site plan if there is one, otherwise the
     * newest photo. The plan is the picture a person recognises a job by —
     * that is what they were looking at when they walked the site.
     */
    fun observeProjectCovers(): Flow<Map<String, String>> =
        dao.observeAllOfTypes(Owner.projectAny).map { photos ->
            val covers = mutableMapOf<String, String>()
            // Newest first, so a plain photo only fills a gap.
            photos.forEach { covers.putIfAbsent(it.ownerId, it.uri) }
            photos.filter { it.ownerType == Owner.PROJECT_PLAN }
                .forEach { covers[it.ownerId] = it.uri }
            covers
        }

    /** Newest photo per item, for stock thumbnails. */
    fun observeItemThumbnails(): Flow<Map<String, String>> =
        dao.observeAllOfType(Owner.INVENTORY_ITEM).map { photos ->
            // Already ordered newest first, so the first per owner wins.
            photos.associate { it.ownerId to it.uri }
        }

    /**
     * A destination for the camera to write into. The file is created first
     * and shared through the FileProvider, because a camera app cannot write
     * to another app's private directory directly.
     */
    fun newCameraTarget(): Pair<String, Uri> {
        val id = UUID.randomUUID().toString()
        val file = File(photoDir, "$id.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return id to uri
    }

    /** Records a photo the camera has just written to [id]'s file. */
    suspend fun recordCameraPhoto(
        id: String,
        ownerType: String,
        ownerId: String,
        actorName: String,
        note: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): PhotoEntity? = withContext(Dispatchers.IO) {
        val file = File(photoDir, "$id.jpg")
        // A cancelled camera leaves the empty placeholder behind; tidy it up
        // rather than recording a row that renders as a broken image.
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@withContext null
        }
        store(id, file, ownerType, ownerId, actorName, note, latitude, longitude)
    }

    /** Copies a gallery pick into app storage and records it. */
    suspend fun importPhoto(
        source: Uri,
        ownerType: String,
        ownerId: String,
        actorName: String,
        note: String? = null,
    ): PhotoEntity? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(photoDir, "$id.jpg")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)

        if (!copied || file.length() == 0L) {
            file.delete()
            return@withContext null
        }
        store(id, file, ownerType, ownerId, actorName, note, null, null)
    }

    private suspend fun store(
        id: String,
        file: File,
        ownerType: String,
        ownerId: String,
        actorName: String,
        note: String?,
        latitude: Double?,
        longitude: Double?,
    ): PhotoEntity {
        val photo = PhotoEntity(
            id = id,
            ownerType = ownerType,
            ownerId = ownerId,
            uri = file.toURI().toString(),
            capturedAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            note = note,
        )
        dao.upsert(photo)
        audit.record("photo", id, AuditTrail.Action.CREATE, actorName, "$ownerType $ownerId")
        return photo
    }

    /**
     * Promotes a photo to be the project's plan, demoting whichever one held
     * that role — a job has one plan, and two would just be confusing on a
     * site where people are looking for *the* drawing.
     */
    suspend fun markAsPlan(photo: PhotoEntity, currentPlan: PhotoEntity?, actorName: String) {
        currentPlan?.takeIf { it.id != photo.id }?.let {
            dao.setOwnerType(it.id, Owner.PROJECT_PHOTO)
        }
        dao.setOwnerType(photo.id, Owner.PROJECT_PLAN)
        audit.record("photo", photo.id, AuditTrail.Action.UPDATE, actorName, "Marked as site plan")
    }

    suspend fun delete(photo: PhotoEntity, actorName: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(java.net.URI(photo.uri)).delete() }
        }
        dao.delete(photo.id)
        audit.record("photo", photo.id, AuditTrail.Action.DELETE, actorName, "Photo removed")
    }
}
