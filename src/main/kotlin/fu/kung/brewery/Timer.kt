package fu.kung.brewery

import io.kweb.shoebox.Shoebox
import io.kweb.state.KVar
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class TimerStore(dir: Path) {
    init {
        if (Files.notExists(dir)) {
            Files.createDirectory(dir)
        }
    }

    data class Timer(
        val uid: String,
        val listUid: String,
        val desc: String,
        val duration: Int,
        val endTime: Instant,
        var displayText: KVar<String>
    )

    val timers = Shoebox<Timer>(dir.resolve("timers"))

    private val timersList = timers.view("timersList", Timer::listUid)

    fun timersList(listUid: String) = timersList.orderedSet(listUid, compareBy(Timer::duration))
}


