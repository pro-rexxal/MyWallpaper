package com.rexxal.mywallpaper

/**
 * Animation time with full precision. Today's clock in milliseconds (about 1.7 trillion) is too big
 * for a Float, so animations measured from it move in jumps. Measuring from app start keeps them smooth.
 */
object AnimClock {
    private val T0 = System.currentTimeMillis()
    fun ms(now: Long): Float = (now - T0).toFloat()
    fun sec(now: Long): Float = ((now - T0) / 1000.0).toFloat()
}
