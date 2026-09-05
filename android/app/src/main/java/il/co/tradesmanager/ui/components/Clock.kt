package il.co.tradesmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * The wall clock, as something a screen can recompose against.
 *
 * A permit that runs out at five o'clock has to stop saying "live" at five
 * o'clock, on a phone that has been sitting open on the permit since two.
 * Reading `System.currentTimeMillis()` during composition does not do that:
 * the value is captured once and the screen goes on showing it until something
 * else happens to redraw. So the time is state, and it advances.
 *
 * The ticker only runs while the screen is actually in front of somebody. A
 * phone in a pocket has nothing to redraw, and a loop that keeps waking it up
 * to find that out is a battery cost with no reader.
 */
@Composable
fun rememberNow(intervalMillis: Long = 30_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(initialValue = System.currentTimeMillis(), lifecycleOwner, intervalMillis) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                // Set on resume as well as on each tick: coming back to the app
                // after an hour must not wait out the interval first.
                value = System.currentTimeMillis()
                delay(intervalMillis)
            }
        }
    }
    return now
}
