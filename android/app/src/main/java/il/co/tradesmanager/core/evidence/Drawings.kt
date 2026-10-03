package il.co.tradesmanager.core.evidence

/**
 * Which revision of each drawing is the one to build from.
 *
 * The expensive mistake on a site is rarely a misread drawing. It is a
 * correctly read drawing that stopped being right a fortnight ago: the
 * architect issued revision C, the office has it, and the steel fixer is
 * working off the revision B that has been folded in his back pocket since
 * the start. The register exists to make "which one is current" a question
 * with one answer on every phone on the job.
 *
 * The newest received is current, and receiving it supersedes the one before.
 * Revisions are not ordered by their letters: firms letter them differently
 * (A, B, C; P1, P2 then C1; 0, 1, 2), and a register that decided D comes
 * after C would be right until the day a firm restarted at A for
 * construction issue. What arrived last is what the office sent last.
 */
object Drawings {

    enum class Refusal {
        NO_NUMBER,
        NO_REVISION,

        /** That revision of that drawing is already the current one. */
        ALREADY_CURRENT,
    }

    /** One sheet as the rules see it. */
    data class Sheet(
        val id: String,
        val number: String,
        val revision: String,
        val receivedAt: Long,
        val supersededAt: Long?,
    ) {
        val isCurrent: Boolean get() = supersededAt == null
    }

    /**
     * The number drawings are matched on. "A-101", "a 101" and "A101" are
     * the same sheet written three ways by three people; a register that
     * treated them as three drawings would show three current revisions of
     * one wall.
     */
    fun key(number: String): String =
        number.trim().uppercase().filter { it.isLetterOrDigit() }

    fun sameRevision(a: String, b: String): Boolean =
        a.trim().uppercase().filter { it.isLetterOrDigit() } == b.trim().uppercase().filter { it.isLetterOrDigit() }

    fun refusal(number: String, revision: String, current: Sheet?): Refusal? = when {
        key(number).isEmpty() -> Refusal.NO_NUMBER
        revision.isBlank() -> Refusal.NO_REVISION
        current != null && sameRevision(current.revision, revision) -> Refusal.ALREADY_CURRENT
        else -> null
    }

    /** The current sheet of [number], if any. */
    fun currentOf(sheets: List<Sheet>, number: String): Sheet? {
        val wanted = key(number)
        return sheets.filter { it.isCurrent && key(it.number) == wanted }.maxByOrNull { it.receivedAt }
    }

    /**
     * The current sheet of every drawing, in drawing-number order with the
     * numbers inside compared as numbers, so A-2 comes before A-10.
     */
    fun current(sheets: List<Sheet>): List<Sheet> = sheets
        .filter { it.isCurrent }
        .groupBy { key(it.number) }
        .map { (_, same) -> same.maxBy { it.receivedAt } }
        .sortedWith { a, b -> naturalCompare(key(a.number), key(b.number)) }

    /** Every earlier revision of one drawing, newest first. */
    fun history(sheets: List<Sheet>, number: String): List<Sheet> {
        val wanted = key(number)
        return sheets.filter { !it.isCurrent && key(it.number) == wanted }.sortedByDescending { it.receivedAt }
    }

    /** Text with runs of digits compared as numbers: "A2" < "A10". */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numberA = a.substring(startA, i).trimStart('0')
                val numberB = b.substring(startB, j).trimStart('0')
                if (numberA.length != numberB.length) return numberA.length - numberB.length
                val byDigits = numberA.compareTo(numberB)
                if (byDigits != 0) return byDigits
            } else {
                if (a[i] != b[j]) return a[i].compareTo(b[j])
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
