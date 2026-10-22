package ch.hygro.padelscore.health

/** Never interpret the since-boot sensor reading as match steps. */
internal class StepDeltaAccumulator(initialTotal: Long = 0L) {
    var total: Long = initialTotal.coerceAtLeast(0L)
        private set
    private var previous: Long? = null

    fun accept(reading: Long): Long? {
        if (reading < 0L) return null
        val last = previous
        previous = reading
        if (last != null && reading >= last) total += reading - last
        return total
    }
}
