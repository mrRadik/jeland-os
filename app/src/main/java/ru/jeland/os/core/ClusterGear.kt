package ru.jeland.os.core

/** Installed SOP4: RcMTopBottomGearUpdate LUT at 0x87cdd0, KZB integer state records
 * 0x4d12b45..0x4d14d20 (texture names plus actual state IDs, not string-table order).
 * Hashes and independent extracted expected cases live in test resources/cluster-pps.
 * Native 5 is D1, NOT D. Numeric-only and M variants are separate OEM states.
 */
object ClusterGear {
    private val nativeStates=intArrayOf(0,1,2,3,4,8,9,10,11,12,13,14,37,38,22,23,24,25,26,27,28,29,30,22,23,24,25,26,27,28,29,30,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,39,40,41,0,31,32,33,34,35,36,39,40,41,0,31,32,33,34,35,36,39,40,41,6,7)
    private val labels=mapOf(1 to "P", 2 to "R", 3 to "N", 4 to "D", 5 to "S", 6 to "M", 7 to "L", 8 to "D1", 9 to "D2", 10 to "D3", 11 to "D4", 12 to "D5", 13 to "D6", 14 to "D7", 15 to "S1", 16 to "S2", 17 to "S3", 18 to "S4", 19 to "S5", 20 to "S6", 21 to "S7", 22 to "M1", 23 to "M2", 24 to "M3", 25 to "M4", 26 to "M5", 27 to "M6", 28 to "M7", 29 to "M8", 30 to "M9", 31 to "1", 32 to "2", 33 to "3", 34 to "4", 35 to "5", 36 to "6", 37 to "D8", 38 to "D9")
    fun native(raw:Int):String?=nativeStates.getOrNull(raw)?.let(labels::get)
    // Android stock CarInfoUtil verifies only these basic CarInfo /26 values.
    // Do not reuse the extended QNX enumeration on a different Android channel.
    fun carInfo(raw:Int?):String?=when(raw){1->"P";2->"R";3->"N";4->"D";else->null}
}
