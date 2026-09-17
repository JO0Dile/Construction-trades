package il.co.tradesmanager.core.find

import il.co.tradesmanager.core.find.Search.Hit
import il.co.tradesmanager.core.find.Search.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules one search box across the whole app is built on.
 *
 * Most of these are about the three scripts rather than about searching, and
 * every one of them is a way for the box to look like it is working while
 * quietly finding nothing: a name stored with points and typed without them, a
 * number typed with the dash it was stored without, a foreman typing the
 * Arabic-Indic digits his own screen showed him.
 *
 * The marks are written as escapes on purpose. A test for whether a mark is
 * stripped, with the mark pasted in invisibly, is a test that passes for the
 * wrong reason the day somebody's editor tidies the file.
 */
class SearchTest {

    /* ---------------------------------------------------------- the folding */

    @Test
    fun `case and punctuation come out`() {
        assertEquals("po 0042", Search.fold("PO-0042"))
        assertEquals("a b", Search.fold("  a --  b  "))
    }

    @Test
    fun `hebrew points come off`() {
        // יְרוּשָׁלַיִם, pointed, against the same word written plainly.
        val pointed = "\u05D9\u05B0\u05E8\u05D5\u05BC\u05E9\u05C1\u05B8\u05DC\u05B7" +
            "\u05D9\u05B4\u05DD"
        assertEquals(Search.fold("\u05D9\u05E8\u05D5\u05E9\u05DC\u05D9\u05DD"), Search.fold(pointed))
    }

    @Test
    fun `hebrew letters that change shape at the end of a word do not`() {
        // ך ם ן ף ץ  ->  כ מ נ פ צ
        assertEquals(
            "\u05DB\u05DE\u05E0\u05E4\u05E6",
            Search.fold("\u05DA\u05DD\u05DF\u05E3\u05E5"),
        )
    }

    @Test
    fun `a word is found by its middle even when the last letter changed shape`() {
        // Searching לים finds ירושלים, whose mem is the final form.
        val jerusalem = "\u05D9\u05E8\u05D5\u05E9\u05DC\u05D9\u05DD"
        val lim = "\u05DC\u05D9\u05DD"
        assertTrue(Search.score(Search.terms(lim), jerusalem, "") > 0)
    }

    @Test
    fun `arabic harakat come off and the tatweel is not a letter`() {
        val muhammad = "\u0645\u062D\u0645\u062F"
        val pointed = "\u0645\u064F\u062D\u064E\u0645\u064E\u0651\u062F"
        val stretched = "\u0645\u0640\u0640\u0640\u062D\u0645\u062F"
        assertEquals(Search.fold(muhammad), Search.fold(pointed))
        assertEquals(muhammad, Search.fold(stretched))
    }

    @Test
    fun `the several spellings of a letter agree`() {
        // أ إ آ ٱ all fold to ا.
        val alefs = "\u0623\u0625\u0622\u0671"
        assertEquals("\u0627\u0627\u0627\u0627", Search.fold(alefs))
        // ة folds to ه, ى to ي.
        assertEquals("\u0634\u0631\u0643\u0647", Search.fold("\u0634\u0631\u0643\u0629"))
        assertEquals("\u064A", Search.fold("\u0649"))
    }

    @Test
    fun `arabic digits are digits`() {
        assertEquals("42", Search.fold("\u0664\u0662"))
        assertEquals("42", Search.fold("\u06F4\u06F2"))
    }

    @Test
    fun `an invisible direction mark is not a word break`() {
        val plain = "\u05D9\u05E8\u05D5\u05E9\u05DC\u05D9\u05DD"
        val withMark = "\u05D9\u05E8\u05D5\u200F\u05E9\u05DC\u05D9\u05DD"
        assertEquals(Search.fold(plain), Search.fold(withMark))
        val muhammad = "\u0645\u062D\u0645\u062F"
        assertEquals(muhammad, Search.fold("\u0645\u062D\u200C\u0645\u062F"))
    }

    /* ----------------------------------------------------------- the digits */

    @Test
    fun `separators are not part of a number`() {
        assertEquals("03123456", Search.digits("03-123456"))
        assertEquals("03123456", Search.digits("\u0660\u0663-\u0661\u0662\u0663\u0664\u0665\u0666"))
        assertEquals("", Search.digits("no numbers"))
    }

    /* ------------------------------------------------------------ the query */

    @Test
    fun `one letter is not a search`() {
        assertEquals(emptyList<String>(), Search.terms("\u05D0"))
        // And a mark is not a second letter.
        assertEquals(emptyList<String>(), Search.terms("\u05D0\u05B6"))
        assertEquals(listOf("\u05D0\u05D1"), Search.terms("\u05D0\u05D1"))
    }

    @Test
    fun `a filter box searches on one letter and the whole-app box does not`() {
        // The box over the whole app needs two: one letter matches most of the
        // database. A filter box over a list somebody is already looking at
        // needs one, because they can see it working on the rows in front of
        // them. InventoryRepository.observe is the caller that asks for one.
        assertEquals(emptyList<String>(), Search.terms("\u05D0"))
        assertEquals(listOf("\u05D0"), Search.terms("\u05D0", shortest = 1))
        // Still counted after folding: a letter with a point under it is one
        // letter, not two.
        assertEquals(listOf("\u05D0"), Search.terms("\u05D0\u05B6", shortest = 1))
    }

    @Test
    fun `words are split and said once`() {
        assertEquals(listOf("hanson", "cement"), Search.terms("Hanson  hanson cement"))
        assertEquals(emptyList<String>(), Search.terms("   "))
    }

    /* ----------------------------------------------------------- the score */

    private val cement = Search.terms("cement")

    @Test
    fun `where the word landed is what the score is made of`() {
        assertEquals(8, Search.score(cement, "Cement order", "acme"))
        assertEquals(6, Search.score(cement, "Cementitious grout", "acme"))
        assertEquals(4, Search.score(cement, "Recemented slab", "acme"))
        assertEquals(3, Search.score(cement, "Order 12", "cement acme"))
        assertEquals(2, Search.score(cement, "Order 12", "cementitious"))
        assertEquals(1, Search.score(cement, "Order 12", "recemented"))
        assertEquals(0, Search.score(cement, "Order 12", "sand"))
    }

    @Test
    fun `every word has to appear somewhere`() {
        val both = Search.terms("hanson cement")
        assertEquals(0, Search.score(both, "Cement order", "acme"))
        assertEquals(11, Search.score(both, "Cement order", "Hanson Ltd"))
        assertEquals(11, Search.score(Search.terms("cement hanson"), "Cement order", "Hanson Ltd"))
    }

    @Test
    fun `a number is found on either side of its separators`() {
        val plain = Search.terms("03123456")
        assertEquals(1, Search.score(plain, "Dani Levi", "03-123456"))
        assertEquals(6, Search.score(Search.terms("03-123456"), "Dani Levi", "03-123456"))
        // Typed in Arabic-Indic, stored in Latin.
        val arabic = Search.terms("\u0660\u0663\u0661\u0662\u0663\u0664\u0665\u0666")
        assertEquals(1, Search.score(arabic, "Dani Levi", "03-123456"))
        assertEquals(0, Search.score(Search.terms("99999999"), "Dani Levi", "03-123456"))
    }

    /* --------------------------------------------------------- the ordering */

    private fun hit(id: String, kind: Kind, title: String, isOpen: Boolean, score: Int) =
        Hit(
            id = id,
            kind = kind,
            title = title,
            detail = "",
            isOpen = isOpen,
            score = score,
        )

    @Test
    fun `the best match first, then the one still open`() {
        val ordered = Search.best(
            listOf(
                hit("a", Kind.JOB, "Cement store", isOpen = false, score = 8),
                hit("b", Kind.SNAG, "Cement store", isOpen = true, score = 8),
                hit("c", Kind.ITEM, "Cement", isOpen = true, score = 12),
                hit("d", Kind.PERMIT, "Nothing", isOpen = true, score = 0),
            ),
        )
        assertEquals(listOf("c", "b", "a"), ordered.map { it.id })
    }

    @Test
    fun `the list is cut rather than built and thrown away`() {
        val many = (0 until 100).map { hit("id$it", Kind.JOB, "Cement", isOpen = true, score = 5) }
        assertEquals(60, Search.best(many, limit = 60).size)
        assertEquals(Search.MOST_RESULTS, Search.best(many).size)
    }
}
