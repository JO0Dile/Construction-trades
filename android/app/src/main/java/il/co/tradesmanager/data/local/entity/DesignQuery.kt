package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One question put to a designer, and the answer when it comes.
 *
 * The answer is written once and then left alone: what the engineer said on
 * the day is the thing somebody will need to quote, not a later tidy-up of
 * it. See core.work.Queries for the rules.
 */
@Entity(
    tableName = "design_queries",
    indices = [Index("projectId")],
)
data class DesignQueryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Q-001, numbered per job in the order asked. */
    val reference: String,
    val question: String,
    /** Who it was put to: "the architect", "the structural engineer", a name. */
    val askedOf: String,
    /** The drawing it is about, as numbered on the register, if it is about one. */
    val drawingNumber: String? = null,
    val askedAt: Long,
    val neededBy: Long? = null,
    val askedByName: String,
    val answer: String? = null,
    val answeredAt: Long? = null,
    /** Who typed the answer in, which is rarely the designer who gave it. */
    val answerRecordedByName: String? = null,
)
