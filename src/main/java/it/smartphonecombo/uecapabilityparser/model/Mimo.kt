package it.smartphonecombo.uecapabilityparser.model

import it.smartphonecombo.uecapabilityparser.extension.indexOfMin
import it.smartphonecombo.uecapabilityparser.util.WeakConcurrentHashMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Mimo : Comparable<Mimo> {
    fun toCompactStr(): String

    fun average(): Double

    override fun compareTo(other: Mimo): Int = average().compareTo(other.average())

    companion object {
        private val cacheInt = WeakConcurrentHashMap<Int, Mimo>()
        private val cacheIntArray = WeakConcurrentHashMap<List<Int>, Mimo>()
        private val cacheQcIndex = WeakConcurrentHashMap<Int, Mimo>()

        fun from(int: Int): Mimo {
            val cachedResult = cacheInt[int]
            if (cachedResult != null) {
                return cachedResult
            }

            val result =
                if (int == 0) {
                    EmptyMimo
                } else if (int > 10) {
                    from(int.toString().map(Char::digitToInt))
                } else {
                    SingleMimo(int)
                }

            cacheInt[int] = result
            return result
        }

        fun from(list: List<Int>): Mimo {
            val cachedResult = cacheIntArray[list]
            if (cachedResult != null) {
                return cachedResult
            }

            val result =
                if (list.isEmpty()) {
                    EmptyMimo
                } else if (list.size == 1 || list.distinct().count() == 1) {
                    from(list.first())
                } else {
                    MixedMimo(list.sortedDescending())
                }

            cacheIntArray[list] = result
            return result
        }

        /**
         * Return mimo from Qualcomm diag index.
         *
         * The sequence generator is guessed, so it can be wrong or incomplete.
         */
        fun fromQcIndex(index: Int): Mimo {
            val cachedResult = cacheQcIndex[index]
            if (cachedResult != null) {
                return cachedResult
            }

            /*
                Some examples:
                0 -> 0
                1 -> 1
                2 -> 2
                3 -> 4
                4 -> 1_1
                5 -> 2_1
                6 -> 2_2
                7 -> 4_2
                8 -> 4_4
                9 -> 1_1_1
                10 -> 2_1_1
                ...
                72 -> 2_2_2_2_2_2_2_2
            */
            var result = intArrayOf(0)
            for (i in 1..index) {
                val indexOfMin = result.indexOfMin()
                when (result[indexOfMin]) {
                    4 -> result = IntArray(result.size + 1) { 1 }
                    2 -> result[indexOfMin] += 2
                    else -> result[indexOfMin] += 1
                }
            }

            val resultMimo = from(result.toList())
            cacheQcIndex[index] = resultMimo

            return resultMimo
        }
    }
}

@Serializable
@SerialName("empty")
object EmptyMimo : Mimo {
    override fun toCompactStr(): String = ""

    override fun toString(): String = ""

    override fun average(): Double = 0.0
}

@Serializable
@SerialName("single")
data class SingleMimo(@SerialName("value") private val mimo: Int) : Mimo {
    override fun toCompactStr(): String = mimo.toString()

    override fun toString(): String = mimo.toString()

    override fun average(): Double = mimo.toDouble()
}

@Serializable
@SerialName("mixed")
data class MixedMimo(@SerialName("value") private val mimoList: List<Int>) : Mimo {
    override fun toCompactStr(): String = mimoList.joinToString("")

    override fun toString(): String = mimoList.joinToString(", ")

    override fun average(): Double = mimoList.average()
}

fun Int.toMimo(): Mimo = Mimo.from(this)

/**
 * Return:
 * - 1 if [string] contains "one"
 * - 2 if [string] contains "two"
 * - 4 if [string] contains "four"
 * - 8 if [string] contains "eight"
 * - 0 otherwise
 */
internal fun Int.Companion.fromLiteral(string: String?): Int {
    return when {
        string == null -> 0
        "one" in string -> 1
        "two" in string -> 2
        "four" in string -> 4
        "eight" in string -> 8
        else -> 0
    }
}
