package ru.jeland.os.core

/** Needle-only self-check. Digital speed/RPM always continue to show real readings. */
data class ClusterStartupNeedles(val speed:Float,val rpm:Float)
object ClusterStartupSweep {
    const val DURATION=1600L
    fun sample(elapsed:Long,speed:Float,rpm:Float):ClusterStartupNeedles? {
        if(elapsed !in 0 until DURATION)return null
        fun ease(t:Float):Float {val x=t.coerceIn(0f,1f);return x*x*(3-2*x)}
        val up=ease(elapsed/650f)
        if(elapsed<=800)return ClusterStartupNeedles(220f*up,8000f*up)
        val tail=1-ease((elapsed-800)/800f)
        return ClusterStartupNeedles(speed+(220-speed)*tail,rpm+(8000-rpm)*tail)
    }
}
