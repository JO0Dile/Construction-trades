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
        val pointed = "יְרוּשָׁלַ" +
            "יִם"
        assertEquals(Search.fold("ירושלים"), Search.fold(pointed))
    }

    @Test
    fun `hebrew letters that change shape at the end of a word do not`() {
        // ך ם ן ף ץ  ->  כ מ נ פ צ
        assertEquals(
            "כמנפצ",
            Search.fold("ךםןףץ"),
        )
    }

    @Test
    fun `a word is found by its middle even when the last letter changed shape`() {
        // Searching לים finds ירושלים, whose mem is the final form.
        val jerusalem = "ירושלים"
        val lim = "לים"
        assertTrue(Search.score(Search.terms(lim), jerusalem, "") > 0)
    }

    @Test
    fun `arabic harakat come off and the tatweel is not a letter`() {
        val muhammad = "محمد"
        val pointed = "مُحَمَّد"
        val stretched = "مـــحمد"
        assertEquals(Search.fold(muhammad), Search.fold(pointed))
        assertEquals(muhammad, Search.fold(stretched))
    }

    @Test
    fun `the several spellings of a letter agree`() {
        // أ إ آ ٱ all fold to ا.
        val alefs = "أإآٱ"
        assertEquals("اااا", Search.fold(alefs))
        // ة folds to ه, ى to ي.
        assertEquals("شركه", Search.fold("شركة"))
        assertEquals("ي", Search.fold("ى"))
    }

    @Test
    fun `arabic digits are digits`() {
        assertEquals("42", Search.fold("٤٢"))
        assertEquals("42", Search.fold("۴۲"))
    }

    @Test
    fun `an invisible direction mark is not a word break`() {
        val plain = "ירושלים"
        val withMark = "ירו‏שלים"
        assertEquals(Search.fold(plain), Search.fold(withMark))
        val muhammad = "محمد"
        assertEquals(muhammad, Search.fold("مح‌مد"))
    }

    /* ----------------------------------------------------------- the digits */

    @Test
    fun `separators are not part of a number`() {
        assertEquals("03123456", Search.digits("03-123456"))
        assertEquals("03123456", Search.digits("٠٣-١٢٣٤٥٦"))
        assertEquals("", Search.digits("no numbers"))
    }

    /* ------------------------------------------------------------ the query */

    @Test
    fun `one letter is not a search`() {
        assertEquals(emptyList<String>(), Search.terms("א"))
        // And a mark is not a second letter.
        assertEquals(emptyList<String>(), Search.terms("אֶ"))
        assertEquals(listOf("אב"), Search.terms("אב"))
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
        val arabic = Search.terms("٠٣١٢٣٤٥٦")
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
