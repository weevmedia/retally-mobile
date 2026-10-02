package com.retally.shared.ui.barcode

/**
 * Code 128 encoder (subsets B and C). Produces the module widths a renderer paints as
 * alternating bars and spaces, starting with a bar. Digit-only input uses subset C (two
 * digits per symbol) so a 20-digit receipt code stays compact; anything else uses subset B.
 *
 * Reference: ISO/IEC 15417. Each entry below is the 6 element widths of one symbol.
 */
object Code128 {

    private const val CODE_C = 99
    private const val START_B = 104
    private const val START_C = 105

    private val PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232",
    )
    private const val STOP = "2331112"

    /** Symbol values (start … checksum), exposed for tests. */
    fun symbols(text: String): List<Int> {
        require(text.isNotEmpty()) { "Code 128 needs at least one character" }
        val values = mutableListOf<Int>()
        if (text.all { it.isDigit() }) {
            var rest = text
            if (rest.length % 2 == 1) {
                // Odd digit count: one digit in subset B, then switch to C for the pairs.
                values += START_B
                values += rest.first().code - 32
                values += CODE_C
                rest = rest.drop(1)
            } else {
                values += START_C
            }
            rest.chunked(2).forEach { values += it.toInt() }
        } else {
            values += START_B
            text.forEach { ch ->
                require(ch.code in 32..126) { "Code 128 B supports printable ASCII only" }
                values += ch.code - 32
            }
        }
        val checksum = values.drop(1).foldIndexed(values.first()) { i, acc, v -> acc + v * (i + 1) } % 103
        values += checksum
        return values
    }

    /** Alternating bar/space widths in modules, starting with a bar, including the stop pattern. */
    fun moduleWidths(text: String): List<Int> =
        buildList {
            symbols(text).forEach { v -> PATTERNS[v].forEach { add(it.digitToInt()) } }
            STOP.forEach { add(it.digitToInt()) }
        }
}
