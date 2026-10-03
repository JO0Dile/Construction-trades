package il.co.tradesmanager.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Closes the app and opens it again, for the one thing that cannot be done
 * while it is running.
 *
 * A staged restore is applied by [StagedRestore.apply], which runs from
 * `DatabaseFactory.create`, which runs from `AppContainer`, which is built in
 * `Application.onCreate`. That last one fires when the **process** starts —
 * not when the activity does.
 *
 * Which is why the old instruction did not work. It said "close the app
 * completely and open it again", and on a modern Android that is not
 * something a person can reliably do: swiping the task away from Recents
 * destroys the activity and routinely leaves the process alive, so
 * `Application.onCreate` never runs a second time, `apply` is never called,
 * and the app comes back up on the database it already had with the restore
 * still sitting there pending. Nothing was broken about the restore. It was
 * never reached.
 *
 * So the app does it itself. An alarm a moment in the future holds the intent
 * that starts it again, and then the process ends — properly, not politely, so
 * that the next launch is a real one.
 *
 * If the alarm never fires the person is left looking at a closed app, which
 * is recoverable: they open it, `Application.onCreate` runs because the
 * process really did die, and the restore applies. That is the worst case, and
 * it is still better than the best case was before.
 */
object Relaunch {

    /** Distinct from anything else this app schedules. */
    private const val REQUEST = 4711

    /**
     * Long enough for the alarm to be registered before the process goes,
     * short enough that the app is back before somebody wonders.
     */
    private const val AFTER_MS = 150L

    fun now(context: Context) {
        val app = context.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return // No launcher activity: better to do nothing than to exit.

        val pending = PendingIntent.getActivity(
            app,
            REQUEST,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // An inexact alarm on purpose: an exact one needs a permission this app
        // has no other reason to hold, and a hundred milliseconds either way is
        // not something anybody can see.
        app.getSystemService(AlarmManager::class.java)
            ?.set(AlarmManager.RTC, System.currentTimeMillis() + AFTER_MS, pending)

        exitProcess(0)
    }
}
