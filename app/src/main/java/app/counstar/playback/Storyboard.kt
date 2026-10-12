package app.counstar.playback

/**
 * YouTube's storyboards: grids of tiny frames from across a video, shown above the seek bar
 * while you scrub. The spec looks like
 * `https://i.ytimg.com/sb/ID/storyboard3_L$L/$N.jpg?sqp=...|48#27#100#10#10#0#default#rs$...|160#90#...`:
 * a URL template, then one level per size (width#height#count#columns#rows#interval#name#signature).
 */
class Storyboard(
    private val url: String,
    private val level: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    private val count: Int,
    private val columns: Int,
    private val rows: Int,
    private val intervalMs: Long,
    private val name: String,
    private val signature: String,
) {
    /** Which sheet image, and which cell in it, shows the frame at [positionMs]. */
    class Frame(val sheetUrl: String, val column: Int, val row: Int)

    fun frameAt(positionMs: Long, durationMs: Long): Frame {
        val interval = if (intervalMs > 0) intervalMs else (durationMs / count.coerceAtLeast(1)).coerceAtLeast(1)
        val index = (positionMs / interval).toInt().coerceIn(0, count - 1)
        val perSheet = columns * rows
        val sheet = index / perSheet
        val cell = index % perSheet
        val sheetName = name.replace("\$M", sheet.toString())
        val sheetUrl = url.replace("\$L", level.toString()).replace("\$N", sheetName) + "&sigh=" + signature
        return Frame(sheetUrl, cell % columns, cell / columns)
    }

    val columnsPerSheet get() = columns
    val rowsPerSheet get() = rows

    companion object {
        /** The largest level that's still small enough to load quickly while scrubbing. */
        fun parse(spec: String?): Storyboard? {
            spec ?: return null
            val parts = spec.split('|')
            if (parts.size < 2) return null
            val levels = parts.drop(1).mapIndexedNotNull { i, level ->
                val f = level.split('#')
                if (f.size < 8) return@mapIndexedNotNull null
                runCatching {
                    Storyboard(
                        url = parts[0],
                        level = i,
                        frameWidth = f[0].toInt(),
                        frameHeight = f[1].toInt(),
                        count = f[2].toInt(),
                        columns = f[3].toInt(),
                        rows = f[4].toInt(),
                        intervalMs = f[5].toLong(),
                        name = f[6],
                        signature = f[7],
                    )
                }.getOrNull()
            }
            return levels.filter { it.frameWidth <= 180 }.maxByOrNull { it.frameWidth } ?: levels.firstOrNull()
        }
    }
}
