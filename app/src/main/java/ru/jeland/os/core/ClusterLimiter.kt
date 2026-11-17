package ru.jeland.os.core

/** RcMAdasCruiseLimitUpdate 0x184568, getEMS2 0x1758a8, native SOP4 hash in test fixture.
 * Warning ABI bits 68/77/78 select ASL states; EMS2 is a005 bytes 48..49.
 * 13-bit target in 1/16 km/h, 0x1fff invalid, OEM display clamp 150 km/h.
 */
data class ClusterLimiter(val state:Int,val targetKmh:Int?,val flashing:Boolean) {
    companion object {
        fun decode(warnings:List<Int>?,auxiliary:List<Int>?):ClusterLimiter? {
            if(warnings?.size!=49||warnings.any {it !in 0..255})return null
            fun bit(i:Int)=warnings[i/8] and (1 shl(i%8))!=0
            val state=when {bit(68)->3;bit(77)->4;bit(78)->1;else->return null}
            val packed=auxiliary?.takeIf {it.size==52&&it.all {b->b in 0..255}}
                ?.let {(it[48] shl 5) or (it[49] ushr 3)}
            val speed=packed?.takeIf {it!=0x1fff}?.ushr(4)?.coerceAtMost(150)?.takeIf {it>0}
            return ClusterLimiter(state,speed,state==4||bit(80))
        }
    }
}
