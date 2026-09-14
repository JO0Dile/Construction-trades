package il.co.tradesmanager.core.find

import il.co.tradesmanager.core.access.Lens

/**
 * Finding one record among all of them, by typing part of it.
 *
 * The app has forty-three destinations and a register behind most of them. A
 * site manager looking for the permit that covers the third-floor riser knows
 * the words "third floor" and does not know, and should not have to know, that
 * permits live under Safety rather than under the job. Until now the only way
 * in was to already be in the right register: there are search boxes on the
 * catalogue, the crew and the gate, and each one searches its own table.
 *
 * Two rules do the work, and both of them are about the three scripts rather
 * than about searching.
 *
 * **Folding.** The existing search boxes compare with SQL `LIKE` over a
 * lowercased column. `LIKE` in SQLite folds case for ASCII and nothing else,
 * which is to say it does not fold Hebrew or Arabic at all, and no amount of
 * SQL will strip a harakat. So the comparison happens here, in Kotlin, over
 * text that has had the marks taken off it: Hebrew points, Arabic harakat,
 * the tatweel — which is a typographic stretch, not a letter — the several
 * accepted spellings of alef, ta marbuta against ha, and the Hebrew letters
 * that change shape at the end of a word. A man whose name is stored with
 * points and typed without them is the same man.
 *
 * The bidirectional marks are dropped rather than turned into spaces. They are
 * invisible, they sit inside words in right-to-left text as a matter of
 * course, and a search that treats one as a word break silently stops finding
 * half the records in the database.
 *
 * **Digits.** Arabic reads with Arabic-Indic digits, which is what `Formats`
 * already puts on the screen — so ٤٢ is what an Arabic-speaking foreman sees
 * and ٤٢ is what he will type. It is folded to 42 here. And a number is
 * matched a second way, against the record's digits with the separators taken
 * out, so an ID typed 03-123456 finds one stored 03123456 and the other way
 * round. Nobody remembers which side of that a number was entered on.
 *
 * Everything in here is pure, which is the point: a search that can only be
 * exercised through a database is a search nobody exercises, and a folding
 * table is exactly the kind of thing that is wrong in one letter for a year.
 *
 * Cost: [score] folds the row it is given every time it is called. That is a
 * few thousand short strings per keystroke on a phone holding one firm's work,
 * which is cheap, and it is the price of being right in three scripts. If it
 * ever stops being cheap the answer is a folded column written at insert time,
 * not a fold that skips the marks.
 */
object Search {

    /**
     * Below this many characters — after folding, so marks do not count — a
     * query is not run at all.
     *
     * One letter matches most of the database, and a list of everything is the
     * same as no list. It is deliberately not higher: Hebrew and Arabic words
     * are short, and a two-letter root is a real thing to type.
     */
    const val SHORTEST_QUERY = 2

    /**
     * How many results are shown.
     *
     * A screen of sixty is already more than anybody reads; the cut exists so
     * that a common word does not build a list of four thousand rows to throw
     * all but the first screen of away.
     */
    const val MOST_RESULTS = 60

    /**
     * Which register a hit came out of, and what a viewer has to be able to
     * read for it to be shown at all.
     *
     * The lenses are copied off the screens themselves rather than decided
     * here — orders and plant are gated on Stuff, permits and snags on
     * Evidence, the crew on People — so search shows exactly what its register
     * shows and nothing else. A search box that quietly widens somebody's
     * access is the easiest way in the world to leak a wage bill, and a set of
     * rules written twice is a set of rules that will disagree.
     *
     * A job carries four because the Projects tab does: a bookkeeper with
     * Money and nothing else can already open the job list, and search that
     * pretended otherwise would look broken to them.
     *
     * The declaration order is also the order ties break in.
     */
    enum class Kind(val lenses: Set<Lens>) {
        JOB(setOf(Lens.PLAN, Lens.STUFF, Lens.EVIDENCE, Lens.MONEY)),
        PERSON(setOf(Lens.PEOPLE)),
        ITEM(setOf(Lens.STUFF)),
        ORDER(setOf(Lens.STUFF)),
        PERMIT(setOf(Lens.EVIDENCE)),
        SNAG(setOf(Lens.EVIDENCE)),
        PLANT(setOf(Lens.STUFF)),
    }

    /**
     * One row, reduced to what a result list needs.
     *
     * [detail] is everything else worth showing and matching on — a client, an
     * address, a supplier, the job it is on — run together. It is matched at a
     * lower weight than [title] and shown under it.
     *
     * [isOpen] is whether the thing is still live: an open snag, a permit that
     * has not been signed back, a job that has not completed. It is not part
     * of how well the text matched. It breaks ties, because somebody searching
     * is nearly always looking for something that is still going on.
     */
    data class Hit(
        val id: String,
        val kind: Kind,
        val title: String,
        val detail: String,
        val isOpen: Boolean,
        val score: Int,
    )

    /**
     * The words of a query, folded. Empty means: do not search.
     *
     * [shortest] is how many characters it takes before anything happens, and
     * it is not the same question in both places this is used. The box that
     * searches the whole app needs two, because one letter matches most of the
     * database and a list of everything is the same as no list. A filter box
     * over a list somebody is already looking at needs one: they can see what
     * it is doing to the rows in front of them, and a single letter narrowing
     * a screen of stock is exactly what it is for.
     *
     * Counted after folding, so a letter with a vowel point under it is one
     * character and not two.
     */
    fun terms(query: String, shortest: Int = SHORTEST_QUERY): List<String> {
        val folded = fold(query)
        if (folded.length < shortest) return emptyList()
        return folded.split(' ').filter { it.isNotEmpty() }.distinct()
    }

    /**
     * How well a row matches, or 0 for not at all.
     *
     * Every word has to appear somewhere. Somebody typing "hanson cement"
     * wants the row that has both, and a row with only "cement" on it is the
     * reason they are typing two words. The words may land in different
     * fields and in either order — that is how a person describes a record
     * they half-remember.
     *
     * Where each word lands is what the number is made of: a whole word in
     * the title beats the start of a word in the title beats the middle of
     * one, and all three beat the same three in the detail. So a job called
     * "Ramat Gan" sorts above one whose client happens to live there.
     */
    fun score(terms: List<String>, title: String, detail: String): Int {
        if (terms.isEmpty()) return 0
        val foldedTitle = " " + fold(title) + " "
        val foldedDetail = " " + fold(detail) + " "
        val titleDigits = digits(title)
        val detailDigits = digits(detail)

        var total = 0
        for (term in terms) {
            val here = termScore(term, foldedTitle, foldedDetail, titleDigits, detailDigits)
            if (here == 0) return 0
            total += here
        }
        return total
    }

    /**
     * The hits worth showing, best first.
     *
     * Ties break on still-open, then on the title, then on the register and
     * the id — so the same query on the same data gives the same list every
     * time. A result order that shuffles is a result order nobody trusts.
     */
    fun best(hits: List<Hit>, limit: Int = MOST_RESULTS): List<Hit> =
        hits.asSequence()
            .filter { it.score > 0 }
            .sortedWith(
                compareByDescending<Hit> { it.score }
                    .thenByDescending { it.isOpen }
                    .thenBy { fold(it.title) }
                    .thenBy { it.kind.ordinal }
                    .thenBy { it.id },
            )
            .take(limit)
            .toList()

    /**
     * Text reduced to what two people typing the same thing have in common:
     * lower case, no marks, one spelling of each letter, ASCII digits, and a
     * single space wherever anything else was.
     */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (raw in text) {
            val c = mapped(raw) ?: continue
            if (c == ' ') {
                if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
            } else {
                out.append(c)
            }
        }
        return out.toString().trim()
    }

    /**
     * Every digit in the text, in order, with everything else thrown away.
     *
     * This is what makes 03-123456 and 03123456 the same number. It is
     * deliberately not clever about what kind of number it is: an ID, an order
     * reference and a phone number are all just the digits somebody remembers.
     */
    fun digits(text: String): String {
        val out = StringBuilder()
        for (raw in text) {
            val c = mapped(raw)
            if (c != null && c in '0'..'9') out.append(c)
        }
        return out.toString()
    }

    private const val WHOLE_WORD_IN_TITLE = 8
    private const val STARTS_A_TITLE_WORD = 6
    private const val INSIDE_THE_TITLE = 4
    private const val WHOLE_WORD_IN_DETAIL = 3
    private const val STARTS_A_DETAIL_WORD = 2
    private const val INSIDE_THE_DETAIL = 1

    private fun termScore(
        term: String,
        title: String,
        detail: String,
        titleDigits: String,
        detailDigits: String,
    ): Int = when {
        title.contains(" $term ") -> WHOLE_WORD_IN_TITLE
        title.contains(" $term") -> STARTS_A_TITLE_WORD
        title.contains(term) -> INSIDE_THE_TITLE
        // Only reached when the digits were split up by something — a dash in
        // a reference, a space in a phone number — which is why it scores as
        // being inside rather than as a word.
        term.isAllDigits() && titleDigits.contains(term) -> INSIDE_THE_TITLE
        detail.contains(" $term ") -> WHOLE_WORD_IN_DETAIL
        detail.contains(" $term") -> STARTS_A_DETAIL_WORD
        detail.contains(term) -> INSIDE_THE_DETAIL
        term.isAllDigits() && detailDigits.contains(term) -> INSIDE_THE_DETAIL
        else -> 0
    }

    private fun String.isAllDigits(): Boolean = isNotEmpty() && all { it in '0'..'9' }

    private fun mapped(c: Char): Char? = when (c) {
        // Invisible. Dropped rather than made into a space: the direction
        // marks sit inside words in Hebrew and Arabic as a matter of course,
        // and treating one as a word break loses the word.
        '\u200B', '\u200C', '\u200D', '\u200E', '\u200F', '\u2066', '\u2067',
        '\u2068', '\u2069', '\uFEFF',
        -> null

        // Hebrew points and cantillation. Written or not written, it is the
        // same word; most text on a site has none of them and a name copied
        // off a document has all of them.
        in '\u0591'..'\u05BD', '\u05BF', '\u05C1', '\u05C2', '\u05C4', '\u05C5', '\u05C7',
        -> null

        // Hebrew letters that change shape at the end of a word. Searching
        // "לים" has to find "ירושלים", and the mem in it is the final one.
        '\u05DA' -> '\u05DB'
        '\u05DD' -> '\u05DE'
        '\u05DF' -> '\u05E0'
        '\u05E3' -> '\u05E4'
        '\u05E5' -> '\u05E6'

        // Arabic harakat, the superscript alef, the Quranic marks — and the
        // tatweel, which stretches a word for the look of it and means
        // nothing.
        '\u0640', in '\u064B'..'\u065F', '\u0670', in '\u06D6'..'\u06ED',
        -> null

        // The letters Arabic is written more than one way. Hamza on alef is
        // routinely left off; ta marbuta and ha are typed for each other; alef
        // maqsura and ya are the same key to most people.
        '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627'
        '\u0624' -> '\u0648'
        '\u0626', '\u0649' -> '\u064A'
        '\u0629' -> '\u0647'

        // Arabic-Indic and Eastern Arabic-Indic digits, which is what an
        // Arabic-locale screen shows and therefore what gets typed back in.
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')

        else -> if (c.isLetterOrDigit()) c.lowercaseChar() else ' '
    }
}
