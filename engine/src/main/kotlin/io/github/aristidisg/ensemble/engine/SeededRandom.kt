package io.github.aristidisg.ensemble.engine

/** Deterministic SplitMix64 RNG. The engine never uses global randomness. */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    companion object {
        /** Stable seed from any number of parts (date, scenario, regenerate count, wardrobe version). */
        fun seedOf(vararg parts: Any?): Long {
            var h = -0x340d631b7bdddcdbL
            for (p in parts) {
                for (ch in p.toString()) {
                    h = h xor ch.code.toLong()
                    h *= 0x100000001b3L
                }
                h = h xor 0x1f
                h *= 0x100000001b3L
            }
            return h
        }
    }
}
