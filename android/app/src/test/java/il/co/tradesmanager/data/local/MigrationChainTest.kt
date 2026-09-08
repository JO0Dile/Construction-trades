package il.co.tradesmanager.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration chain has to reach the version the code declares.
 *
 * Room only finds out otherwise on a phone that already holds data: it opens
 * the file, sees an older version, looks for a path forward and — finding a
 * gap — throws. A fresh install never notices, so this is precisely the bug
 * that passes every check on a developer's machine and breaks for every
 * existing user at once. Three releases went out that way. It is cheap to
 * assert here instead.
 *
 * What this cannot check is whether a migration produces the columns its
 * entities describe; that needs Room's own validation against an exported
 * schema. It checks the shape of the chain, which is the half that has
 * actually gone wrong.
 */
class MigrationChainTest {

    @Test
    fun chainIsContiguousFromOne() {
        val steps = Migrations.ALL.sortedBy { it.startVersion }
        var at = 1
        steps.forEach { step ->
            assertEquals(
                "migration ${step.startVersion}->${step.endVersion} does not follow version $at",
                at,
                step.startVersion,
            )
            assertEquals(
                "migration from ${step.startVersion} must go up exactly one version",
                at + 1,
                step.endVersion,
            )
            at = step.endVersion
        }
        assertEquals("the chain must end at the declared version", DATABASE_VERSION, at)
    }

    @Test
    fun everyStepIsRegisteredOnce() {
        val seen = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals("a migration is registered twice", seen.size, seen.toSet().size)
    }

    @Test
    fun declaredVersionIsAtLeastOne() {
        assertTrue("a database version below 1 is not a version", DATABASE_VERSION >= 1)
    }
}
