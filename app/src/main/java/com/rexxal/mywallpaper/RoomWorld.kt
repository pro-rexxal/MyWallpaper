package com.rexxal.mywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The robot's futuristic room: window to the outside world, gadgets, drone buddy,
 * daily routine, real phone charging, and touch play. Positions are 0..1 of the screen.
 */
class RoomWorld {

    companion object {
        const val GROUND = 0.875f
        const val BACK = 0.70f
        const val WX0 = 0.1f; const val WY0 = 0.2f; const val WX1 = 0.9f; const val WY1 = 0.49f
        const val SOCKET_X = 0.335f; const val SOCKET_Y = 0.665f
        val SPOT = mapOf("desk" to .22f, "drink" to .36f, "telescope" to .42f, "window" to .5f,
            "speaker" to .57f, "arcade" to .76f, "plant" to .8f, "pod" to .12f)
        val NEON_NAMES = listOf("Cyan + Violet", "Pink + Blue", "Mint + Teal", "Sunset", "Gold + White")
        private val NEON = listOf(
            intArrayOf(0xFF3EE8FF.toInt(), 0xFF9B6BFF.toInt()), intArrayOf(0xFFFF5CC8.toInt(), 0xFF4D7CFF.toInt()),
            intArrayOf(0xFF4DFFB8.toInt(), 0xFF19B3C7.toInt()), intArrayOf(0xFFFF8A3D.toInt(), 0xFFFF3D6E.toInt()),
            intArrayOf(0xFFFFD166.toInt(), 0xFFF2F4FF.toInt()))
        val WEATHERS = listOf("Clear", "Cloudy", "Rain", "Storm", "Snow", "Fog")
        val SCENES = listOf("Morning", "Noon", "Sunset", "Night", "Rain", "Storm", "Snow", "Fog")
        val DREAMS = listOf("Meteor shower", "Aurora", "UFO", "Rainbow", "Blossoms", "Rocket launch", "Sky whale", "Eclipse")
        private val DREAM_LINES = mapOf("Meteor shower" to "Shooting stars!! 🌠", "Aurora" to "Whoa… the sky is dancing 🌌",
            "UFO" to "Is that… a UFO?! 🛸", "Rainbow" to "A rainbow! 🌈", "Blossoms" to "So pretty… 🌸",
            "Rocket launch" to "3… 2… 1… 🚀", "Sky whale" to "A whale in the sky?! 🐋", "Eclipse" to "An eclipse! 🌑")
        val THOUGHTS = Robot.THOUGHTS
        private val GIGGLES = listOf("Hehe! 😄", "Boop!", "That tickles!", "Wheee!", "Hi there! 👋", "Beep beep!")
        private val CONFETTI = intArrayOf(0xFFFF5A5F.toInt(), 0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF4CC9F0.toInt(), 0xFF7C5CFF.toInt(), 0xFFF72585.toInt())
        private const val P_HEART = 0; private const val P_NOTE = 1; private const val P_SPARK = 2; private const val P_CONFETTI = 3; private const val P_DUST = 4
    }

    // ---------------- settings (set every frame from LiveConfig) ----------------
    var mode = 0          // 0 real, 1 dream, 2 fixed
    var weather = "Clear"
    var scene = "Night"
    var neon = NEON[0]
    var lightsOn = true
    var hourOverride: Float? = null
    /** How far (0..1 of the height) the whole room is moved up. */
    var shift = 0f

    // ---------------- app icons (from the icon map) ----------------
    var colliders: List<IconMap.Box> = emptyList()
    /** Current home page position from the launcher (0 = first page, 1.5 = halfway to the third). */
    var pageOffset = 0f
    private var prevOffset = 0f
    private var sizeN = .18f
    private var aspect = .45f
    private var lastBump = 0L
    private class EB(val i: Int, val x0: Float, val x1: Float, val y0: Float, val y1: Float)

    private fun effBoxes(): List<EB> {
        val out = ArrayList<EB>(colliders.size)
        colliders.forEachIndexed { i, c ->
            val dx = if (c.page < 0) 0f else c.page - pageOffset
            val x0 = c.x + dx; val x1 = x0 + c.w
            if (x1 >= -.05f && x0 <= 1.05f) out += EB(i, x0, x1, c.y + shift, c.y + c.h + shift)
        }
        return out
    }

    private fun jumpTo(bx: EB, now: Long) {
        val b = bot; val g = 4.2f; val dh = max(.02f, b.y - bx.y0) + .05f
        val vy0 = -kotlin.math.sqrt(2 * g * dh); val t = -vy0 / g + kotlin.math.sqrt(2 * .05f / g); val tx = (bx.x0 + bx.x1) / 2
        b.air = true; b.vy = vy0; b.vx = (tx - b.x) / t; b.jumping = true; b.standOn = -1; b.squashStart = now; b.facing = if (tx > b.x) 1f else -1f
    }

    // ---------------- robot ----------------
    inner class Bot {
        var x = .5f; var y = GROUND; var vy = 0f; var vx = 0f; var vxs = 0f
        var air = false; var carried = false; var facing = 1f; var tx = .5f
        var lookX = 0f; var lookY = 0f; var lookTx = 0f; var lookTy = 0f; var nextLook = 0L
        var blinkStart = -1_000_000L; var nextBlink = 0L
        var sleeping = false; var energy = .8f; var mood = .7f; var lastInteract = System.currentTimeMillis()
        var action = "idle"; var goal: String? = null; var actionUntil = 0L; var nextAction = System.currentTimeMillis() + 3000
        var speech: String? = null; var speechUntil = 0L
        var happyUntil = 0L; var waveUntil = 0L; var surpriseUntil = 0L; var squashStart = -1_000_000L
        var standOn = -1; var jumping = false
        var petting = false; var spinStart = -1_000_000L; var nextThought = System.currentTimeMillis() + 20000; var sleepStart = 0L; var lean = 0f
    }
    val bot = Bot()

    var charging = false; private var plugAnim = -1_000_000L; private var unplugAnim = -1_000_000L
    private var fullSaid = false; private var lowSaid = false
    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, var kicks: Int = 0)
    private var ball: Ball? = null
    private class Part(val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, var rot: Float, val hue: Int)
    private val parts = mutableListOf<Part>()
    private var dx = .6f; private var dy = .5f; private var dvx = 0f; private var dvy = 0f; private var droneLoop = -1_000_000L
    var plant = 2; private var music = false; private var musicUntil = 0L; private var arcadeScore = 0; private var steamUntil = 0L
    private class Cloud(var x: Float, val y: Float, val s: Float, val v: Float)
    private val clouds = List(5) { Cloud(Random.nextFloat(), .05f + Random.nextFloat() * .35f, .5f + Random.nextFloat() * .8f, .006f + Random.nextFloat() * .008f) }
    private class Car(var x: Float, val y: Float, val v: Float)
    private val cars = List(3) { i -> Car(Random.nextFloat(), .35f + i * .12f, (if (i % 2 == 1) -1 else 1) * (.04f + Random.nextFloat() * .05f)) }
    private class Drop(var x: Float, var y: Float, val v: Float, val r: Float)
    private val drops = List(40) { Drop(Random.nextFloat(), Random.nextFloat(), .02f + Random.nextFloat() * .06f, .4f + Random.nextFloat()) }
    private class Building(val x: Float, val w: Float, val h: Float, val ant: Boolean, val lights: BooleanArray)
    private val city: List<Building>
    private val starX = FloatArray(70); private val starY = FloatArray(70); private val starA = FloatArray(70); private val starR = FloatArray(70)
    private class Event(val name: String, val start: Long, val dur: Long, val seed: Float)
    private var event: Event? = null
    private var nextEvent = System.currentTimeMillis() + 20000
    private var flash = 0f; private var nextBolt = System.currentTimeMillis() + 6000
    private var wind = 0f; private var lastUpdate = 0L
    private val taps = ArrayDeque<Long>()

    init {
        val r = java.util.Random(99)
        city = List(14) { i -> Building(i / 14f, .05f + r.nextFloat() * .04f, .2f + r.nextFloat() * .45f, r.nextFloat() < .3f, BooleanArray(30) { r.nextFloat() < .45f }) }
        val s = java.util.Random(11)
        for (k in 0 until 70) { starX[k] = s.nextFloat(); starY[k] = s.nextFloat(); starA[k] = s.nextFloat(); starR[k] = s.nextFloat() }
    }

    fun setNeon(i: Int) { neon = NEON[i.coerceIn(0, NEON.size - 1)] }

    private var lastCfgLights: Boolean? = null
    /** Copies the user's Live settings into the room each frame. */
    fun applyCfg(cfg: LiveConfig) {
        mode = cfg.winMode
        weather = if (cfg.weatherSel <= 0) cfg.liveWeather else WEATHERS[(cfg.weatherSel - 1).coerceIn(0, WEATHERS.size - 1)]
        scene = SCENES[cfg.scene.coerceIn(0, SCENES.size - 1)]
        setNeon(cfg.neonTheme)
        shift = cfg.roomShift / 100f
        if (lastCfgLights != cfg.lightsOn) { lightsOn = cfg.lightsOn; lastCfgLights = cfg.lightsOn }
    }

    fun hour(now: Long): Float {
        hourOverride?.let { return it }
        if (mode == 2) return when (scene) { "Morning" -> 7.5f; "Noon" -> 12.5f; "Sunset" -> 18.7f; else -> 23f }
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
    }
    fun isNight(now: Long): Boolean { val h = hour(now); return h >= 19.5f || h < 6.2f }
    private fun activeWeather(): String = when (mode) {
        0 -> weather
        2 -> if (scene in listOf("Rain", "Storm", "Snow", "Fog")) scene else "Clear"
        else -> "Clear"
    }

    fun say(t: String?, now: Long, ms: Long = 3500) { if (t.isNullOrEmpty()) return; bot.speech = t; bot.speechUntil = now + ms }
    private fun burst(kind: Int, x: Float, y: Float, n: Int) {
        repeat(n) { parts += Part(kind, x, y, (Random.nextFloat() - .5f) * .25f, -.15f - Random.nextFloat() * .35f, 0f, 1.2f + Random.nextFloat() * .8f, Random.nextFloat() * 6f, Random.nextInt(6)) }
        if (parts.size > 160) parts.subList(0, parts.size - 160).clear()
    }
    fun wake(now: Long, msg: String? = null): Boolean {
        val b = bot; if (!b.sleeping) return false
        b.sleeping = false; b.squashStart = now; b.action = "idle"; b.goal = null
        say(msg ?: listOf("Yawn… I'm up! 🥱", "Huh? Oh, hi! 👋", "Five more minutes… ok fine 😅").random(), now)
        return true
    }
    private fun touched(now: Long) { bot.lastInteract = now; bot.mood = (bot.mood + .05f).coerceIn(0f, 1f) }
    private fun goTo(goal: String, now: Long, msg: String? = null) { bot.goal = goal; bot.tx = SPOT[goal] ?: .5f; bot.action = "walking"; bot.actionUntil = now + 20000; if (msg != null) say(msg, now, 2200) }
    private fun setAction(a: String, now: Long, ms: Long) { bot.action = a; bot.actionUntil = now + ms }

    // ---------------- charging ----------------
    fun setPower(level: Float, isCharging: Boolean, now: Long) {
        val b = bot; b.energy = level.coerceIn(0f, 1f)
        if (isCharging && !charging) { charging = true; plugAnim = now; burst(P_SPARK, SOCKET_X, SOCKET_Y, 8); wake(now, ""); b.happyUntil = now + 1800; say(listOf("Thanks for the charge! ⚡", "Ahh, power! 🔌", "Charging up! ⚡").random(), now, 2500) }
        if (!isCharging && charging) { charging = false; unplugAnim = now; burst(P_SPARK, b.x, b.y - .08f, 8); say(if (b.energy >= .99f) "All charged! 💯" else "Unplugged! 🔌", now, 2000) }
        if (isCharging && level >= .999f && !fullSaid) { fullSaid = true; say("Fully charged! 💯", now, 2500); b.happyUntil = now + 1500 }
        if (level < .99f) fullSaid = false
        if (!isCharging && level < .15f && !lowSaid) { lowSaid = true; say("Battery low… plug me in? 🪫", now, 3500) }
        if (level > .2f) lowSaid = false
    }

    // ---------------- gestures ----------------
    fun botHit(nx: Float, ny: Float, size: Float, aspect: Float): Boolean { val sy = size * aspect; return abs(nx - bot.x) < size * .6f && ny < bot.y + sy * .05f && ny > bot.y - sy * 1.5f }
    private fun near(nx: Float, ny: Float, x0: Float, x1: Float, y0: Float, y1: Float) = nx in x0..x1 && ny in y0..y1

    fun tap(nx: Float, ny: Float, now: Long, size: Float, aspect: Float, room: Boolean) {
        val b = bot; touched(now)
        while (taps.isNotEmpty() && now - taps.first() > 650) taps.removeFirst()
        taps.addLast(now)
        if (wake(now)) return
        if (taps.size >= 3) { taps.clear(); party(now); return }
        if (taps.size == 2) { say("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now)) + " ⏰", now, 4000); b.happyUntil = now + 1500; b.squashStart = now; lookAt(nx, ny); return }
        if (botHit(nx, ny, size, aspect)) { b.squashStart = now; b.happyUntil = now + 1200; burst(P_HEART, b.x, b.y - .1f, 3); if (Random.nextFloat() < .4f) say(GIGGLES.random(), now, 1600); return }
        if (hypot(nx - dx, (ny - dy) / aspect * .5f) < .06f) { droneLoop = now; say("Zip did a loop! 🛸", now, 1600); return }
        if (room) {
            if (near(nx, ny, .22f, .3f, .56f, .64f)) { lightsOn = !lightsOn; lightsChanged = true; say(if (lightsOn) "Lights on 💡" else "Cozy mode 🌙", now, 1500); return }
            if (near(nx, ny, .03f, .21f, .56f, .7f)) { goTo("desk", now, "Let me check something 💻"); return }
            if (near(nx, ny, .31f, .41f, .53f, .67f)) { goTo("drink", now, "Energy drink time! 🧃"); return }
            if (near(nx, ny, .41f, .52f, .5f, .7f)) { goTo("telescope", now, "Let's look outside 🔭"); return }
            if (near(nx, ny, .53f, .68f, .58f, .71f)) { music = !music; musicUntil = now + 60000; if (music) goTo("speaker", now, "Music! 🎶") else say("Music off", now, 1200); return }
            if (near(nx, ny, .75f, .97f, .5f, .71f)) { goTo("arcade", now, "Game time! 🕹"); return }
            if (near(nx, ny, .82f, .98f, .74f, .9f)) { goTo("plant", now, "I'll water it! 🌱"); return }
            if (near(nx, ny, 0f, .24f, .74f, .92f)) { goTo("pod", now, "Nap time? 😴"); return }
            if (near(nx, ny, WX0, WX1, WY0, WY1)) { burst(P_SPARK, nx, ny, 8); goTo("window", now, if (isNight(now)) "Look at the stars ✨" else "Nice view! 🏙"); return }
        }
        if (ny > BACK + .04f) { ball = Ball(nx, ny - .2f, (Random.nextFloat() - .5f) * .2f, 0f); say(listOf("Ball! ⚽", "Ooh, a ball!", "Let's play!").random(), now, 1500); b.goal = null; b.action = "idle"; lookAt(nx, ny); return }
        lookAt(nx, ny)
    }
    /** True when the robot changed the room lights by touch, so the app can save it. */
    var lightsChanged = false
    fun longPress(nx: Float, ny: Float, now: Long, size: Float, aspect: Float) { if (botHit(nx, ny, size, aspect)) { wake(now, "Mmm… pets 🥰"); bot.petting = true; touched(now) } }
    fun pickUp(nx: Float, ny: Float, now: Long) { val b = bot; wake(now, ""); b.carried = true; b.air = true; b.petting = false; b.goal = null; b.action = "idle"; b.x = nx; b.y = ny + .05f; touched(now); say(listOf("Whoaaa! 😵", "Put me down! 😆", "I can fly!").random(), now, 1800) }
    fun dragTo(nx: Float, ny: Float) { val b = bot; if (!b.carried) return; b.vx = (nx - b.x) * 20; b.vy = (ny + .05f - b.y) * 20; b.x = nx.coerceIn(.06f, .94f); b.y = (ny + .05f).coerceIn(.15f, GROUND); b.facing = if (b.vx > 0) 1f else -1f }
    fun release() { val b = bot; if (b.carried) { b.carried = false; b.air = b.y < GROUND; b.vy = b.vy.coerceIn(-1.5f, 1.5f); b.vx = b.vx.coerceIn(-.8f, .8f) }; b.petting = false }
    fun swipeUp(now: Long) { val b = bot; touched(now); if (wake(now)) return; if (!b.air) { b.air = true; b.vy = -1.6f; b.spinStart = now; say(listOf("Hup! 🤸", "Backflip!", "Yahoo!").random(), now, 1400) } }
    fun swipeSide(dir: Float, now: Long) { wind = dir * 1.2f; touched(now); dvx += dir * .6f; say("Whoosh! 🌬", now, 1200) }
    private fun lookAt(nx: Float, ny: Float) { val b = bot; b.lookTx = ((nx - b.x) * 4).coerceIn(-1f, 1f); b.lookTy = ((ny - (b.y - .1f)) * 4).coerceIn(-1f, 1f); b.nextLook = System.currentTimeMillis() + 2500 }

    fun party(now: Long) { wake(now, ""); music = true; musicUntil = now + 15000; setAction("dance", now, 7000); burst(P_CONFETTI, .5f, .3f, 50); say("Dance party! 🎉", now, 2500) }
    fun fuel(now: Long) { wake(now, ""); goTo("drink", now, "Energy drink! 🧃") }
    fun throwBall(now: Long) { wake(now, ""); val left = Random.nextBoolean(); ball = Ball(if (left) .1f else .9f, .75f, if (left) .5f else -.5f, -.4f) }
    fun unlock(now: Long) {
        val b = bot; wake(now, ""); b.waveUntil = now + 1600; val h = hour(now)
        say(when { h >= 5 && h < 12 -> "Good morning! ☀️"; h >= 12 && h < 17 -> "Good afternoon! 🌤"; h >= 17 && h < 21 -> "Good evening! 🌆"; else -> "Up late? Sleep soon 🌙" }, now)
    }
    /** Screen just turned on (usually on the lock screen). */
    fun onScreenOn(now: Long) {
        val b = bot; wake(now, ""); b.waveUntil = now + 2000; b.squashStart = now; val h = hour(now)
        say(when { h >= 5 && h < 12 -> listOf("Good morning! ☀️", "Rise and shine! 🌅").random(); h >= 12 && h < 17 -> listOf("Hey there! 👋", "Hi again! 😄").random()
            h >= 17 && h < 22 -> listOf("Good evening! 🌆", "Hey! 👋").random(); else -> listOf("It's late… sleep soon 😴", "Can't sleep? 🌙").random() }, now, 3000)
    }
    /** Phone unlocked. */
    fun onUnlock(now: Long) { val b = bot; if (!b.sleeping) { b.happyUntil = now + 1200; say(listOf("Welcome back! 😊", "Yay, you're here! 🎉", "Let's go! 🚀").random(), now, 2200) } }
    /** A notification arrived. */
    fun onNotification(app: String, now: Long) {
        val b = bot; wake(now, ""); b.surpriseUntil = now + 1200; b.squashStart = now
        if (!b.air && !b.carried) { b.air = true; b.vy = -1.1f }
        burst(P_SPARK, b.x, b.y - .15f, 6); say(if (app.isBlank()) "New notification! 📩" else "📩 $app", now, 3000)
    }

    /** Phone unlocked / wallpaper shown again: greet if you've been away a while. */
    fun onReturn(now: Long) { if (now - bot.lastInteract > 60_000L) { bot.lastInteract = now; unlock(now) } }

    fun nap(now: Long) { wake(now, ""); goTo("pod", now, "Nap time 😴") }
    fun startEvent(name: String?, now: Long) {
        val n = name ?: DREAMS.random()
        event = Event(n, now, if (n == "Eclipse") 16000 else 12000, Random.nextFloat() * 1000)
        val b = bot; if (b.sleeping && Random.nextBoolean()) wake(now, "")
        if (!b.sleeping && !b.carried) { goTo("window", now); b.surpriseUntil = now + 1500; say(DREAM_LINES[n], now, 3000) }
    }

    // ---------------- simulation ----------------
    fun update(now: Long, sleepAfter: Long, room: Boolean) {
        val dt = if (lastUpdate == 0L) 0f else (now - lastUpdate).coerceIn(0L, 100L) / 1000f
        lastUpdate = now
        val b = bot; val night = isNight(now); val wx = activeWeather()
        val pageVel = if (dt > 0f) (pageOffset - prevOffset) / dt else 0f
        prevOffset = pageOffset
        if (abs(pageVel) > .8f && !b.sleeping && !b.carried) {
            b.lean = (b.lean - pageVel * 6).coerceIn(-25f, 25f)
            if (abs(pageVel) > 2.2f && now - lastBump > 1500) { lastBump = now; b.surpriseUntil = now + 900; say(listOf("Whoa! 😵‍💫", "Wheee!", "Slow down! 😆").random(), now, 1300) }
        }
        val boxes = if (room) effBoxes() else emptyList()
        val halfW = sizeN * .42f; val botH = sizeN * aspect * 1.25f
        wind *= .25f.pow(dt)
        clouds.forEach { c -> c.x += (c.v * (if (wx == "Storm") 3 else 1) + wind * .08f) * dt; if (c.x > 1.3f) c.x = -.3f; if (c.x < -.3f) c.x = 1.3f }
        cars.forEach { c -> c.x += c.v * dt; if (c.x > 1.1f) c.x = -.1f; if (c.x < -.1f) c.x = 1.1f }
        drops.forEach { d -> d.y += d.v * dt * (if (wx == "Storm") 1.8f else 1f); if (d.y > 1) { d.y = 0f; d.x = Random.nextFloat() } }
        if (music && now > musicUntil) music = false
        if (room && wx == "Storm" && now > nextBolt) {
            flash = 1f; nextBolt = now + 5000 + Random.nextLong(9000)
            if (!b.sleeping && !b.carried) { b.surpriseUntil = now + 1200; b.squashStart = now; if (Random.nextBoolean()) say(listOf("Eek! ⚡", "Thunder! 😨", "Whoa, loud!").random(), now, 1500) }
        }
        flash = max(0f, flash - dt * 2.2f)
        event?.let { if (now > it.start + it.dur) event = null }
        if (room && mode == 1 && event == null && now > nextEvent) { startEvent(null, now); nextEvent = now + 35000 + Random.nextLong(40000) }

        // drone follows the robot
        val tgx = (b.x + if (b.facing > 0) -.12f else .12f).coerceIn(.08f, .92f)
        val tgy = if (b.carried) b.y - .25f else if (b.sleeping) .62f else b.y - .24f
        val tm = AnimClock.ms(now)
        dvx += ((tgx + sin(tm / 1300f) * .05f) - dx) * dt * 2.5f; dvy += ((tgy + sin(tm / 700f) * .015f) - dy) * dt * 2.5f
        dvx *= .15f.pow(dt); dvy *= .15f.pow(dt); dx += dvx * dt; dy += dvy * dt
        b.mood = (b.mood - dt * .002f).coerceIn(0f, 1f)

        if (b.carried) { } else if (b.air) {
            val py = b.y
            b.vy += 4.2f * dt; b.y += b.vy * dt; b.x = (b.x + b.vx * dt).coerceIn(.06f, .94f); if (!b.jumping) b.vx *= .3f.pow(dt)
            if (b.vy > 0) boxes.firstOrNull { py <= it.y0 + .002f && b.y >= it.y0 && b.x >= it.x0 - .01f && b.x <= it.x1 + .01f }?.let { land ->
                b.y = land.y0; b.vy = 0f; b.vx = 0f; b.air = false; b.jumping = false; b.standOn = land.i; b.squashStart = now; b.tx = b.x; b.vxs = 0f
                b.nextAction = max(b.nextAction, now + 8000 + Random.nextLong(7000))
            }
            if (b.air && b.y >= GROUND) {
                b.jumping = false; b.standOn = -1; b.y = GROUND; val hard = b.vy > 1.4f; b.vy = 0f; b.vx = 0f; b.air = false; b.squashStart = now; b.tx = b.x; if (hard) { say(listOf("Oof! 😵", "Ouch!", "Nailed it! 😎").random(), now, 1500); burst(P_DUST, b.x, GROUND, 8) } }
        }
        ball?.let { bl ->
            bl.vy += 2.6f * dt; bl.x += bl.vx * dt; bl.y += bl.vy * dt
            if (bl.y > GROUND - .012f) { bl.y = GROUND - .012f; bl.vy *= -.55f; bl.vx *= .9f; if (abs(bl.vy) < .05f) bl.vy = 0f }
            if (bl.x < .03f || bl.x > .97f) { bl.vx *= -.8f; bl.x = bl.x.coerceIn(.03f, .97f) }
            bl.vx *= .6f.pow(dt)
            if (!b.sleeping && !b.carried && !b.air) {
                b.tx = bl.x - (if (bl.x > b.x) .05f else -.05f); lookAt(bl.x, bl.y)
                if (abs(bl.x - b.x) < .07f && bl.y > GROUND - .06f) {
                    bl.vx = (if (bl.x > b.x) 1f else -1f) * (.6f + Random.nextFloat() * .6f); bl.vy = -.8f - Random.nextFloat() * .6f
                    b.squashStart = now; b.happyUntil = now + 800; burst(P_SPARK, bl.x, bl.y, 5)
                    bl.kicks++; if (bl.kicks >= 6) { say("Good game! 🏆", now, 2000); ball = null; b.mood = (b.mood + .2f).coerceIn(0f, 1f) }
                }
            }
        }
        if (b.sleeping || b.carried) { updateFace(now, dt, false); updateParts(dt); return }

        // standing on an icon: fall if it slid away (page swipe) or he walked off the edge
        if (b.standOn >= 0 && !b.air) {
            val on = boxes.firstOrNull { it.i == b.standOn }
            if (on == null || b.x < on.x0 - .01f || b.x > on.x1 + .01f) { b.air = true; b.vy = 0f; b.vx = b.vxs; b.standOn = -1; if (Random.nextBoolean()) say(listOf("Waaah! 😱", "Whoops!", "Uh oh…").random(), now, 1200) }
            else b.y = on.y0
        }
        if (b.standOn >= 0 && b.action == "idle" && now < b.nextAction) b.goal = null
        if (b.standOn >= 0 && b.goal != null && !b.air) { b.air = true; b.vy = -.6f; b.vx = kotlin.math.sign(b.tx - b.x) * .35f; b.standOn = -1 }
        val tired = b.energy < .15f && !charging
        if (!b.air) {
            val ddx = b.tx - b.x; val maxV = (if (ball != null) .34f else .2f) * (if (tired) .5f else 1f)
            val want = if (abs(ddx) > .004f) (ddx * 6).coerceIn(-maxV, maxV) else 0f
            b.vxs += (want - b.vxs) * min(1f, dt * 6); b.x += b.vxs * dt; if (abs(b.vxs) > .01f) b.facing = if (b.vxs > 0) 1f else -1f
        }
        // bump into icons in the way
        // only icons at leg/body height, while walking toward them (icons above his head don't count)
        if (!b.air && b.standOn < 0 && abs(b.vxs) > .01f) for (x in boxes) {
            if (x.y0 < b.y - .006f && x.y0 > b.y - botH * .6f && b.x + halfW > x.x0 && b.x - halfW < x.x1 &&
                (if (b.vxs > 0) x.x0 > b.x - halfW * .5f else x.x1 < b.x + halfW * .5f)) {
                val left = abs(b.x - x.x0) < abs(b.x - x.x1); val v = b.vxs
                b.x = if (left) x.x0 - halfW else x.x1 + halfW; b.vxs = -v * .3f; b.tx = b.x
                if (b.y - x.y0 < .3f && Random.nextFloat() < .15f && abs(v) > .03f) { jumpTo(x, now); say("Hup!", now, 900) }
                else if (now - lastBump > 1200 && abs(v) > .03f) { lastBump = now; b.squashStart = now; say(listOf("Oof! 🤕", "Bonk!", "Hey, an icon!").random(), now, 1200) }
                break
            }
        }
        b.lean += ((b.vxs / .2f) * 6 - b.lean) * min(1f, dt * 5)
        val arrived = !b.air && abs(b.tx - b.x) < .006f && abs(b.vxs) < .03f
        if (b.action == "walking" && arrived && b.goal != null) {
            val g = b.goal; b.goal = null
            when (g) {
                "pod" -> { b.sleeping = true; b.sleepStart = now; b.action = "idle"; updateParts(dt); return }
                "desk" -> { b.facing = -1f; setAction("desk", now, 7000) }
                "drink" -> { b.facing = -1f; setAction("drink", now, 3500); steamUntil = now + 5000 }
                "telescope" -> { b.facing = 1f; setAction("telescope", now, 6000) }
                "window" -> { b.facing = 1f; setAction("window", now, if (event != null) 8000 else 4500) }
                "speaker" -> { music = true; musicUntil = now + 40000; setAction("dance", now, 6000) }
                "arcade" -> { b.facing = 1f; setAction("arcade", now, 7000) }
                "plant" -> { b.facing = 1f; setAction("watering", now, 3200) }
            }
        }
        if (b.action == "walking" && now > b.actionUntil) { b.action = "idle"; b.goal = null }
        if (b.action != "idle" && b.action != "walking" && now > b.actionUntil) {
            val a = b.action; b.action = "idle"; b.mood = (b.mood + .1f).coerceIn(0f, 1f)
            when (a) {
                "watering" -> { plant = min(5, plant + 1); burst(P_SPARK, .9f, GROUND - .08f, 10); say(if (plant >= 5) "It bloomed! 🌸" else "Grow, little plant! 🌱", now, 2000) }
                "arcade" -> { val win = Random.nextFloat() < .6f; if (win) arcadeScore++; say(if (win) "New high score! 🏆" else "Aww, so close! 😤", now, 2000); if (win) { b.happyUntil = now + 1500; burst(P_CONFETTI, .86f, .55f, 15) } }
                "drink" -> { say("Refreshing! ⚡", now, 1600); b.happyUntil = now + 1200 }
                "telescope" -> if (night) say(listOf("I saw Saturn! 🪐", "So many stars ✨", "A satellite! 🛰").random(), now, 2200)
                "desk" -> say(listOf("Saved my work 💾", "Coding is fun! 👨‍💻", "Checked the news 📰").random(), now, 2000)
            }
        }
        if (b.action == "idle" && ball == null && now > b.nextAction) {
            val h = hour(now); val r = Random.nextFloat()
            when {
                !room -> { if (r < .6f) b.tx = (b.x + (Random.nextFloat() - .5f) * .5f).coerceIn(.18f, .82f) else if (r < .8f) setAction("dance", now, 3500) else b.waveUntil = now + 1400 }
                tired -> goTo("pod", now, "So tired… 🪫")
                b.standOn >= 0 && r < .35f -> { b.air = true; b.vy = -.7f; b.vx = (if (Random.nextBoolean()) -1 else 1) * .3f; b.standOn = -1; if (Random.nextFloat() < .3f) say("Wheee!", now, 1000) }
                boxes.isNotEmpty() && b.standOn < 0 && Random.nextFloat() < .2f && boxes.any { it.i != b.standOn && it.y0 < b.y - .01f && b.y - it.y0 < .3f && abs((it.x0 + it.x1) / 2 - b.x) < .4f } -> {
                    val t = boxes.filter { it.i != b.standOn && it.y0 < b.y - .01f && b.y - it.y0 < .3f && abs((it.x0 + it.x1) / 2 - b.x) < .4f }.random()
                    jumpTo(t, now); if (Random.nextFloat() < .4f) say(listOf("Climbing! 🧗", "Up we go!", "Nice seat up there").random(), now, 1500)
                }
                (h >= 23 || h < 6) && r < .5f -> goTo("pod", now, "Bedtime… 🌙")
                h >= 6 && h < 10 && r < .35f -> goTo("drink", now, "Morning drink! ☀️")
                night && r < .3f -> goTo("telescope", now)
                h >= 17 && h < 23 && r < .25f -> goTo("speaker", now, "Evening tunes 🎶")
                else -> when (listOf("desk", "arcade", "plant", "window", "wander", "wander", "dance").random()) {
                    "wander" -> b.tx = (b.x + (Random.nextFloat() - .5f) * .5f).coerceIn(.18f, .82f)
                    "dance" -> setAction("dance", now, 3500)
                    "plant" -> if (plant >= 5) b.waveUntil = now + 1400 else goTo("plant", now)
                    "desk" -> goTo("desk", now)
                    "arcade" -> goTo("arcade", now)
                    else -> goTo("window", now)
                }
            }
            b.nextAction = now + 5000 + Random.nextLong(7000)
        }
        if (music && Random.nextFloat() < dt * 1.5f) burst(P_NOTE, .6f + (Random.nextFloat() - .5f) * .05f, .6f, 1)
        if (b.action == "dance" && Random.nextFloat() < dt * 2) burst(P_NOTE, b.x, b.y - .15f, 1)
        if (b.petting && Random.nextFloat() < dt * 6) { burst(P_HEART, b.x, b.y - .12f, 1); b.mood = (b.mood + dt * .2f).coerceIn(0f, 1f) }
        if (now > b.nextThought) { if (now > b.speechUntil) say(THOUGHTS.random(), now, 4500); b.nextThought = now + 35000 + Random.nextLong(30000) }
        if (now - b.lastInteract > sleepAfter && now > b.speechUntil && b.action == "idle" && ball == null) { b.sleeping = true; b.sleepStart = now; b.speech = null }
        updateFace(now, dt, true); updateParts(dt)
    }

    private fun updateFace(now: Long, dt: Float, awake: Boolean) {
        val b = bot
        if (awake) {
            if (now > b.nextLook) { b.lookTx = Random.nextFloat() * 2 - 1; b.lookTy = Random.nextFloat() * 1.2f - .6f; b.nextLook = now + 1500 + Random.nextLong(2500) }
            if (b.action == "window" || b.action == "telescope") { b.lookTy = -1f; b.lookTx = if (b.action == "telescope") .8f else sin(AnimClock.ms(now) / 900f) * .5f }
            if (b.action == "desk" || b.action == "drink") { b.lookTx = -1f; b.lookTy = -.3f }
            if (b.action == "arcade") { b.lookTx = 1f; b.lookTy = -.4f }
            if (now > b.nextBlink) { b.blinkStart = now; b.nextBlink = now + 2200 + Random.nextLong(3500) }
        }
        val k = min(1f, dt * 8); b.lookX += (b.lookTx - b.lookX) * k; b.lookY += (b.lookTy - b.lookY) * k
        if (b.speech != null && now > b.speechUntil) b.speech = null
    }

    private fun updateParts(dt: Float) {
        parts.forEach { p -> p.life += dt; p.x += p.vx * dt * (if (p.kind == P_CONFETTI) 1.6f else 1f); p.vy += (when (p.kind) { P_CONFETTI -> .6f; P_DUST -> .4f; else -> -.05f }) * dt; p.y += p.vy * dt * .4f; p.rot += dt * 4 }
        parts.removeAll { it.life >= it.max }
    }

    // ================= DRAWING =================
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val path = Path()

    private fun a(col: Int, alpha: Float) = (col and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun mix(c1: Int, c2: Int, t: Float): Int { val k = t.coerceIn(0f, 1f); return Color.rgb((Color.red(c1) + (Color.red(c2) - Color.red(c1)) * k).toInt(), (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * k).toInt(), (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * k).toInt()) }
    private fun darker(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
    private fun rr(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, paint: Paint = p) { r.set(x0, y0, x1, y1); c.drawRoundRect(r, max(0f, rad), max(0f, rad), paint) }
    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, paint: Paint = p) { r.set(cx - rx, cy - ry, cx + rx, cy + ry); c.drawOval(r, paint) }
    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, col: Int) { p.shader = null; p.color = col; c.drawCircle(x, y, max(0f, rad), p) }
    private fun glow(c: Canvas, x: Float, y: Float, rad: Float, col: Int, al: Float) { if (rad <= 0f) return; p.shader = RadialGradient(x, y, rad, a(col, al), a(col, 0f), Shader.TileMode.CLAMP); c.drawCircle(x, y, rad, p); p.shader = null }
    private fun vgrad(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, top: Int, bot: Int) { p.shader = LinearGradient(0f, y0, 0f, y1, top, bot, Shader.TileMode.CLAMP); c.drawRect(x0, y0, x1, y1, p); p.shader = null }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) { tp.textSize = size; tp.color = col; tp.isFakeBoldText = bold; tp.textAlign = align; c.drawText(s, x, y, tp) }

    private fun skyColors(hr: Float): IntArray {
        val keys = arrayOf(
            floatArrayOf(0f) to intArrayOf(0xFF060918.toInt(), 0xFF141A3A.toInt()), floatArrayOf(5f) to intArrayOf(0xFF1B1F4A.toInt(), 0xFF5A3D6E.toInt()),
            floatArrayOf(6.5f) to intArrayOf(0xFFF08A5D.toInt(), 0xFFFFD29D.toInt()), floatArrayOf(9f) to intArrayOf(0xFF3F8FD8.toInt(), 0xFFB5DDFF.toInt()),
            floatArrayOf(16f) to intArrayOf(0xFF4384D0.toInt(), 0xFFCBE5FF.toInt()), floatArrayOf(18.5f) to intArrayOf(0xFFE8705A.toInt(), 0xFFFFBE85.toInt()),
            floatArrayOf(20f) to intArrayOf(0xFF2A2350.toInt(), 0xFF6A4A7A.toInt()), floatArrayOf(22f) to intArrayOf(0xFF090C24.toInt(), 0xFF1B2048.toInt()),
            floatArrayOf(24f) to intArrayOf(0xFF060918.toInt(), 0xFF141A3A.toInt()))
        var i = 0; while (i < keys.size - 2 && hr >= keys[i + 1].first[0]) i++
        val (h0, c0) = keys[i]; val (h1, c1) = keys[i + 1]
        val t = ((hr - h0[0]) / (h1[0] - h0[0])).coerceIn(0f, 1f)
        return intArrayOf(mix(c0[0], c1[0], t), mix(c0[1], c1[1], t))
    }

    private fun drawWindowView(c: Canvas, X: Float, Y: Float, W: Float, H: Float, now: Long) {
        val hr = hour(now); val night = isNight(now); val wx = activeWeather(); val tm = AnimClock.ms(now)
        val sky = skyColors(hr); var top = sky[0]; var bot = sky[1]
        when (wx) {
            "Cloudy", "Rain", "Fog" -> { top = mix(top, 0xFF5A6070.toInt(), .45f); bot = mix(bot, 0xFF7A808F.toInt(), .45f) }
            "Storm" -> { top = mix(top, 0xFF1C2030.toInt(), .65f); bot = mix(bot, 0xFF3A4050.toInt(), .6f) }
            "Snow" -> { top = mix(top, 0xFF8A93A8.toInt(), .35f); bot = mix(bot, 0xFFC9D0DC.toInt(), .4f) }
        }
        val ev = event; val ep = if (ev != null) ((now - ev.start).toFloat() / ev.dur).coerceIn(0f, 1f) else 0f
        c.save(); c.clipRect(X, Y, X + W, Y + H)
        vgrad(c, X, Y, X + W, Y + H, top, bot)
        if (ev?.name == "Aurora") {
            val al = sin(ep * PI.toFloat())
            for (k in 0..2) {
                path.reset()
                for (i in 0..30) { val x = X + W * i / 30; val y = Y + H * (.2f + k * .08f) + sin(i / 30f * 8 + tm / 900f + k) * H * .05f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                for (i in 30 downTo 0) { val x = X + W * i / 30; val y = Y + H * (.42f + k * .08f) + sin(i / 30f * 8 + tm / 900f + k) * H * .05f; path.lineTo(x, y) }
                path.close()
                p.shader = LinearGradient(0f, Y, 0f, Y + H * .6f, intArrayOf(a(0xFF50FFB4.toInt(), 0f), a(0xFF50FFB4.toInt(), .35f * al), a(0xFFA05AFF.toInt(), .25f * al)), null, Shader.TileMode.CLAMP)
                c.drawPath(path, p); p.shader = null
            }
        }
        if (night && wx != "Storm" && wx != "Fog") {
            for (k in 0 until 70) { val tw = .5f + .5f * sin(tm / 600f + k); circle(c, X + starX[k] * W, Y + starY[k] * H * .7f, W * (.002f + starR[k] * .004f), a(Color.WHITE, (.25f + starA[k] * .6f) * tw * (if (wx == "Clear") 1f else .4f))) }
        }
        val dayP = ((hr - 6) / 13f).coerceIn(0f, 1f); val nP = (((if (hr < 6) hr + 24 else hr) - 19) / 11f).coerceIn(0f, 1f)
        val eclipse = ev?.name == "Eclipse"
        if (!night && wx != "Storm") {
            val sx = X + W * (.1f + .8f * dayP); val sy = Y + H * (.85f - .7f * sin(dayP * PI.toFloat())); val sr = W * .06f
            glow(c, sx, sy, W * .35f, 0xFFFFF0C8.toInt(), if (wx == "Clear") .6f else .25f)
            circle(c, sx, sy, sr, 0xFFFFF4D6.toInt())
            if (eclipse) circle(c, sx - sr * 2.2f + sr * 4.4f * ep, sy, sr * 1.02f, 0xFF141A2A.toInt())
        } else if (night) {
            val mx = X + W * (.1f + .8f * nP); val my = Y + H * (.8f - .6f * sin(nP * PI.toFloat())); val mr = W * .045f
            glow(c, mx, my, W * .22f, 0xFFDCE6FF.toInt(), .3f)
            circle(c, mx, my, mr, 0xFFF1F0E6.toInt())
            circle(c, mx + (if (eclipse) mr * (2 - 4 * ep) else mr * .45f), my - mr * .2f, mr * .9f, if (eclipse) 0xFF1A0F10.toInt() else top)
        }
        if (ev?.name == "Rainbow") {
            val al = sin(ep * PI.toFloat()) * .55f
            intArrayOf(0xFFFF5A5F.toInt(), 0xFFFF9F1C.toInt(), 0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF4CC9F0.toInt(), 0xFF7C5CFF.toInt()).forEachIndexed { i, col ->
                sp.color = a(col, al); sp.strokeWidth = W * .018f; val rad = W * (.62f - i * .018f)
                r.set(X + W * .5f - rad, Y + H * 1.25f - rad, X + W * .5f + rad, Y + H * 1.25f + rad); c.drawArc(r, 180f, 180f, false, sp)
            }
        }
        if (ev?.name == "Meteor shower") {
            for (k in 0 until 8) {
                val pp = (((now - ev.start) / 1400f) + k * .37f) % 1f; val sx = X + W * ((k * .29f + ev.seed) % 1f) + W * .3f * pp; val sy = Y + H * (.05f + .4f * pp)
                sp.color = a(Color.WHITE, 1 - pp); sp.strokeWidth = W * .005f; c.drawLine(sx, sy, sx - W * .09f, sy - H * .07f, sp)
            }
        }
        val cloudy = wx == "Cloudy" || wx == "Rain" || wx == "Storm" || wx == "Snow"
        clouds.take(if (cloudy) 5 else 2).forEach { cl ->
            val cx = X + cl.x * W; val cy = Y + cl.y * H; val s = W * .16f * cl.s
            p.color = when { night -> a(0xFF96A0C8.toInt(), if (cloudy) .35f else .15f); wx == "Storm" -> a(0xFF505564.toInt(), .9f); cloudy -> a(0xFFE1E6F0.toInt(), .9f); else -> a(Color.WHITE, .85f) }
            c.drawCircle(cx, cy, s * .5f, p); c.drawCircle(cx + s * .45f, cy + s * .08f, s * .4f, p); c.drawCircle(cx - s * .45f, cy + s * .1f, s * .35f, p); c.drawCircle(cx + s * .1f, cy - s * .25f, s * .38f, p)
        }
        if (ev?.name == "Sky whale") {
            val wx2 = X + W * (-.3f + 1.6f * ep); val wy = Y + H * (.35f + sin(ep * 6) * .05f); val s = W * .18f
            p.color = a(0xFF78AAFF.toInt(), .85f); oval(c, wx2, wy, s, s * .35f)
            path.reset(); path.moveTo(wx2 - s * .9f, wy); path.lineTo(wx2 - s * 1.35f, wy - s * .3f + sin(tm / 300f) * s * .1f); path.lineTo(wx2 - s * 1.3f, wy + s * .25f); path.close(); c.drawPath(path, p)
            circle(c, wx2 + s * .6f, wy - s * .05f, s * .05f, Color.WHITE)
        }
        if (ev?.name == "UFO") {
            val ux = X + W * (if (ep < .5f) -.1f + ep * 1.2f else .5f + sin((ep - .5f) * 20) * .05f + (if (ep > .8f) (ep - .8f) * 5 else 0f))
            val uy = Y + H * (.35f + sin(tm / 300f) * .03f - (if (ep > .8f) (ep - .8f) * 3 else 0f)); val s = W * .09f
            if (ep > .4f && ep < .8f) { p.shader = LinearGradient(0f, uy, 0f, Y + H, a(0xFF78FFC8.toInt(), .5f), a(0xFF78FFC8.toInt(), 0f), Shader.TileMode.CLAMP); path.reset(); path.moveTo(ux - s * .3f, uy); path.lineTo(ux + s * .3f, uy); path.lineTo(ux + s * .8f, Y + H); path.lineTo(ux - s * .8f, Y + H); path.close(); c.drawPath(path, p); p.shader = null }
            p.color = 0xFF9FB4D8.toInt(); oval(c, ux, uy, s, s * .25f)
            p.color = a(0xFF96FFDC.toInt(), .8f); r.set(ux - s * .4f, uy - s * .46f, ux + s * .4f, uy + s * .1f); c.drawArc(r, 180f, 180f, true, p)
            for (k in 0 until 5) circle(c, ux - s * .7f + k * s * .35f, uy + s * .05f, s * .06f, if ((floor(tm / 150f).toInt() + k) % 2 == 1) 0xFFFFD166.toInt() else 0xFFFF5A5F.toInt())
        }
        if (ev?.name == "Rocket launch") {
            val pp = easeInOut(((ep - .15f) / .85f).coerceIn(0f, 1f)); val rx = X + W * .72f; val ry = Y + H * (1.05f - pp * 1.4f); val s = W * .035f
            sp.color = a(Color.WHITE, .5f); sp.strokeWidth = s * .8f; c.drawLine(rx, Y + H, rx, ry + s * 2, sp)
            glow(c, rx, ry + s * 2.2f, s * 1.6f, 0xFFFFDC78.toInt(), .95f)
            p.color = 0xFFEEF1F8.toInt(); path.reset(); path.moveTo(rx, ry - s * 1.6f); path.quadTo(rx + s * .7f, ry - s * .6f, rx + s * .5f, ry + s * 1.6f); path.lineTo(rx - s * .5f, ry + s * 1.6f); path.quadTo(rx - s * .7f, ry - s * .6f, rx, ry - s * 1.6f); c.drawPath(path, p)
        }
        // futuristic city
        val lit = night || hr < 7 || hr > 18
        city.forEachIndexed { i, bd ->
            val bx = X + W * bd.x; val bw = W * bd.w; val bh = H * bd.h * .6f; val by = Y + H - bh
            p.color = mix(if (night) 0xFF141A33.toInt() else 0xFF5B6B8C.toInt(), top, .25f); rr(c, bx, by, bx + bw, Y + H + 2, W * .008f)
            if (bd.ant) { sp.color = p.color; sp.strokeWidth = W * .003f; c.drawLine(bx + bw / 2, by, bx + bw / 2, by - H * .06f, sp); circle(c, bx + bw / 2, by - H * .06f, W * .005f, if ((floor(tm / 600f).toInt() + i) % 2 == 1) 0xFFFF5A5F.toInt() else a(0xFFFF5A5F.toInt(), .3f)) }
            if (lit) for (k in bd.lights.indices) {
                if (!bd.lights[k]) continue
                val col = k % 3; val row = k / 3; val lx = bx + bw * (.18f + col * .28f); val ly = by + H * .02f + row * H * .035f
                if (ly > Y + H - H * .02f) break
                p.color = if (k % 7 != 0) a(0xFFFFD68C.toInt(), .85f) else a(neon[0], .9f); c.drawRect(lx, ly, lx + bw * .16f, ly + H * .015f, p)
            }
        }
        cars.forEach { cr -> val cx = X + W * cr.x; val cy = Y + H * cr.y; val col = neon[if (cr.v > 0) 0 else 1]
            p.color = if (lit) a(col, .95f) else 0xFF2A3040.toInt(); rr(c, cx - W * .02f, cy - H * .008f, cx + W * .02f, cy + H * .008f, W * .01f)
            if (lit) glow(c, cx, cy, W * .05f, col, .35f) }
        if (ev?.name == "Blossoms") for (k in 0 until 26) { val pp = ((tm / 6000f) + k * .137f) % 1f; val px = X + W * ((k * .318f + pp * .3f + sin(tm / 800f + k) * .03f) % 1f); val py = Y + H * pp; p.color = a(0xFFFFAACD.toInt(), .9f); oval(c, px, py, W * .008f, W * .005f) }
        if (wx == "Snow") for (k in 0 until 60) { val pp = ((tm / 9000f) + k * .0731f) % 1f; val px = X + W * ((k * .413f + sin(tm / 1200f + k) * .02f + 1f) % 1f); circle(c, px, Y + H * pp, W * (.003f + (k % 3) * .0015f), a(Color.WHITE, .9f)) }
        if (wx == "Rain" || wx == "Storm") { sp.color = a(0xFFC8D7FF.toInt(), .45f); sp.strokeWidth = W * .002f
            for (k in 0 until 70) { val pp = ((tm / (if (wx == "Storm") 380f else 600f)) + k * .0379f) % 1f; val px = X + W * ((k * .271f) % 1f); val py = Y + H * pp; c.drawLine(px, py, px - W * .01f, py + H * .06f, sp) } }
        if (wx == "Fog") repeat(3) { k -> vgrad(c, X, Y + H * (.3f + k * .2f), X + W, Y + H, a(0xFFDCE1EB.toInt(), 0f), a(0xFFDCE1EB.toInt(), .55f)) }
        if (flash > 0f) { p.color = a(0xFFE6EBFF.toInt(), flash * .8f); c.drawRect(X, Y, X + W, Y + H, p)
            if (flash > .7f) { sp.color = Color.WHITE; sp.strokeWidth = W * .006f; path.reset(); var lx = X + W * .6f; var ly = Y; path.moveTo(lx, ly); repeat(6) { lx += (Random.nextFloat() - .5f) * W * .12f; ly += H * .12f; path.lineTo(lx, ly) }; c.drawPath(path, sp) } }
        c.restore()
        if (wx == "Rain" || wx == "Storm") drops.forEach { d -> val ddx = X + W * d.x; val ddy = Y + H * d.y; val rad = W * .007f * d.r; p.color = a(0xFFDCEBFF.toInt(), .35f); oval(c, ddx, ddy, rad * .8f, rad); sp.color = a(0xFFDCEBFF.toInt(), .18f); sp.strokeWidth = rad * .6f; c.drawLine(ddx, ddy - rad, ddx, ddy - rad * 5, sp) }
    }

    private fun easeInOut(t: Float) = if (t < .5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

    private fun drawRoom(c: Canvas, w: Float, h: Float, now: Long) {
        val n1 = neon[0]; val n2 = neon[1]; val hr = hour(now); val night = isNight(now); val tm = AnimClock.ms(now)
        vgrad(c, 0f, 0f, w, h * BACK, 0xFF151827.toInt(), 0xFF1D2134.toInt())
        sp.color = a(Color.WHITE, .035f); sp.strokeWidth = 1f; for (k in 1 until 6) c.drawLine(w * k / 6, h * .05f, w * k / 6, h * BACK, sp)
        // soft light from the ceiling, no hard bar under the status bar
        val strip = if (music) mix(n1, n2, (sin(tm / 250f) + 1) / 2) else n1
        vgrad(c, 0f, 0f, w, h * .18f, a(strip, if (lightsOn) .2f else .08f), a(strip, 0f))
        val X = w * WX0; val Y = h * WY0; val W = w * (WX1 - WX0); val H = h * (WY1 - WY0)
        p.color = 0xFF0B0D16.toInt(); rr(c, X - w * .02f, Y - w * .02f, X + W + w * .02f, Y + H + w * .02f, w * .04f)
        c.save(); path.reset(); r.set(X, Y, X + W, Y + H); path.addRoundRect(r, w * .025f, w * .025f, Path.Direction.CW); c.clipPath(path)
        drawWindowView(c, X, Y, W, H, now)
        p.shader = LinearGradient(X, Y, X + W, Y + H, intArrayOf(a(Color.WHITE, .08f), a(Color.WHITE, 0f), a(Color.WHITE, .04f), a(Color.WHITE, 0f)), floatArrayOf(0f, .4f, .6f, 1f), Shader.TileMode.CLAMP); c.drawRect(X, Y, X + W, Y + H, p); p.shader = null
        c.restore()
        sp.color = 0xFF2A2F45.toInt(); sp.strokeWidth = w * .008f; c.drawLine(X + W / 2, Y, X + W / 2, Y + H, sp)
        sp.color = a(n2, .7f); sp.strokeWidth = w * .004f; r.set(X - w * .02f, Y - w * .02f, X + W + w * .02f, Y + H + w * .02f); c.drawRoundRect(r, w * .04f, w * .04f, sp)
        p.color = 0xFF262A3D.toInt(); rr(c, X - w * .04f, Y + H + w * .012f, X + W + w * .04f, Y + H + w * .012f + h * .012f, w * .01f)
        vgrad(c, 0f, h * BACK, w, h, 0xFF1A1D2C.toInt(), 0xFF0D0F18.toInt())
        sp.color = a(n1, .08f); sp.strokeWidth = 1f; val vx = w * .5f; val vy = h * .45f
        for (k in -6..16) { val x = w * k / 10f; c.drawLine(vx + (x - vx) * ((BACK * h - vy) / (h - vy)), h * BACK, x, h, sp) }
        for (k in 1 until 8) { val y = h * BACK + (h * (1 - BACK)) * (k / 8f).pow(1.6f); c.drawLine(0f, y, w, y, sp) }
        p.color = a(n1, .6f); c.drawRect(0f, h * BACK - 1.5f, w, h * BACK, p)
        val sky = skyColors(hr)[0]; val beam = if (night) .08f else .16f
        p.shader = LinearGradient(0f, h * BACK, 0f, h, a(if (night) 0xFF9FB2FF.toInt() else mix(sky, 0xFFFFF4D6.toInt(), .6f), beam), a(Color.WHITE, 0f), Shader.TileMode.CLAMP)
        path.reset(); path.moveTo(X + W * .05f, h * BACK); path.lineTo(X + W * .95f, h * BACK); path.lineTo(X + W * 1.15f, h); path.lineTo(X - W * .15f, h); path.close(); c.drawPath(path, p); p.shader = null
        if (!night && activeWeather() == "Clear") for (k in 0 until 14) { val pp = ((tm / 14000f) + k * .071f) % 1f; circle(c, X + W * ((k * .37f) % 1f), h * (BACK + .03f) + h * .2f * pp, w * .003f, a(0xFFFFF0C8.toInt(), .35f * sin(pp * PI.toFloat()))) }
        if (flash > 0f) { p.color = a(0xFFC8D2FF.toInt(), flash * .25f); c.drawRect(0f, 0f, w, h, p) }
    }

    private fun drawGadgets(c: Canvas, w: Float, h: Float, now: Long) {
        val n1 = neon[0]; val n2 = neon[1]; val b = bot; val tm = AnimClock.ms(now)
        // desk + holo monitor
        p.color = 0xFF262B40.toInt(); rr(c, w * .03f, h * .645f, w * .27f, h * .655f, w * .01f)
        p.color = 0xFF1D2132.toInt(); c.drawRect(w * .05f, h * .655f, w * .07f, h * BACK, p); c.drawRect(w * .23f, h * .655f, w * .25f, h * BACK, p)
        p.color = 0xFF0D1020.toInt(); rr(c, w * .06f, h * .565f, w * .2f, h * .635f, w * .01f)
        p.color = a(n1, .12f); rr(c, w * .065f, h * .57f, w * .195f, h * .63f, w * .008f)
        text(c, SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now)), w * .075f, h * .585f, w * .022f, a(n1, .85f), true, Paint.Align.LEFT)
        val typing = b.action == "desk"
        for (k in 0 until 5) { val len = .03f + ((k * 37 + floor(tm / (if (typing) 180f else 2500f)).toInt()) % 7) * .012f; p.color = a(if (k % 2 == 1) n2 else n1, .55f); c.drawRect(w * .075f, h * (.595f + k * .007f), w * (.075f + len), h * (.598f + k * .007f), p) }
        p.color = 0xFF3A3F55.toInt(); c.drawRect(w * .125f, h * .635f, w * .135f, h * .645f, p)
        p.color = 0xFF30354A.toInt(); rr(c, w * .09f, h * .639f, w * .17f, h * .644f, w * .004f)
        // lamp
        sp.color = 0xFF3A3F55.toInt(); sp.strokeWidth = w * .008f; path.reset(); path.moveTo(w * .255f, h * .644f); path.lineTo(w * .25f, h * .6f); path.lineTo(w * .27f, h * .585f); c.drawPath(path, sp)
        p.color = 0xFF3A3F55.toInt(); path.reset(); path.moveTo(w * .255f, h * .576f); path.lineTo(w * .29f, h * .582f); path.lineTo(w * .28f, h * .596f); path.close(); c.drawPath(path, p)
        if (lightsOn) glow(c, w * .28f, h * .6f, w * .12f, 0xFFFFDC96.toInt(), .35f)
        // fuel machine
        p.color = 0xFF22273A.toInt(); rr(c, w * .31f, h * .535f, w * .4f, h * .66f, w * .012f)
        p.color = 0xFF0D1020.toInt(); rr(c, w * .32f, h * .548f, w * .39f, h * .585f, w * .006f)
        text(c, "⚡ FUEL", w * .355f, h * .571f, w * .02f, a(n2, .8f), true)
        p.color = 0xFF0D1020.toInt(); rr(c, w * .33f, h * .6f, w * .38f, h * .645f, w * .006f)
        if (b.action == "drink" || now < steamUntil) { p.color = a(n1, .9f); rr(c, w * .345f, h * .62f, w * .365f, h * .643f, w * .004f)
            for (k in 0..2) { val pp = ((tm / 900f) + k / 3f) % 1f; circle(c, w * (.355f + sin(pp * 6 + k) * .008f), h * (.615f - pp * .04f), w * .006f * (1 + pp), a(Color.WHITE, .3f * (1 - pp))) } }
        // socket
        p.color = 0xFF2A2F45.toInt(); rr(c, w * (SOCKET_X - .018f), h * (SOCKET_Y - .012f), w * (SOCKET_X + .018f), h * (SOCKET_Y + .012f), w * .006f)
        val pin = if (charging) a(n1, .9f) else 0xFF11131D.toInt(); circle(c, w * SOCKET_X - w * .006f, h * SOCKET_Y, w * .003f, pin); circle(c, w * SOCKET_X + w * .006f, h * SOCKET_Y, w * .003f, pin)
        // telescope
        val tx = w * .47f; val ty = h * BACK; sp.color = 0xFF3A3F55.toInt(); sp.strokeWidth = w * .007f
        c.drawLine(tx, ty - h * .05f, tx - w * .03f, ty, sp); c.drawLine(tx, ty - h * .05f, tx + w * .03f, ty, sp); c.drawLine(tx, ty - h * .05f, tx, ty, sp)
        c.save(); c.translate(tx, ty - h * .055f); c.rotate(Math.toDegrees((-.6 + (if (b.action == "telescope") sin(tm / 1500f) * .08f else 0f)).toDouble()).toFloat())
        p.color = 0xFFCFD5E6.toInt(); rr(c, -w * .02f, -w * .012f, w * .06f, w * .012f, w * .006f)
        p.color = 0xFF3A3F55.toInt(); rr(c, w * .05f, -w * .016f, w * .065f, w * .016f, w * .004f)
        p.color = a(n1, .8f); rr(c, -w * .026f, -w * .006f, -w * .018f, w * .006f, w * .002f); c.restore()
        // shelf
        p.color = 0xFF262B40.toInt(); rr(c, w * .6f, h * .545f, w * .88f, h * .551f, w * .003f)
        circle(c, w * .62f, h * .536f, w * .012f, 0xFFFF5A5F.toInt())
        p.color = 0xFFFFD166.toInt(); rr(c, w * .662f, h * .522f, w * .678f, h * .545f, w * .003f)
        p.color = n1; path.reset(); path.moveTo(w * .72f, h * .52f); path.lineTo(w * .734f, h * .545f); path.lineTo(w * .706f, h * .545f); path.close(); c.drawPath(path, p)
        circle(c, w * .79f, h * .538f, w * .009f, 0xFF06D6A0.toInt())
        text(c, "🏆", w * .84f, h * .544f, w * .03f, Color.WHITE)
        // speaker
        val pulse = if (music) (sin(tm / 120f) + 1) / 2 else 0f
        p.color = 0xFF1A1D2C.toInt(); rr(c, w * .545f, h * .6f, w * .665f, h * BACK, w * .012f)
        for ((yy, rad) in listOf(.625f to .018f, .672f to .024f)) { circle(c, w * .605f, h * yy, w * rad * (1 + pulse * .12f), 0xFF0B0D16.toInt()); sp.color = a(n2, .6f + pulse * .4f); sp.strokeWidth = w * .003f; c.drawCircle(w * .605f, h * yy, w * rad * (1 + pulse * .12f), sp) }
        if (music) for (k in 0 until 5) { val bh = (sin(tm / 110f + k * 1.7f) + 1) / 2; p.color = a(if (k % 2 == 1) n1 else n2, .85f); rr(c, w * (.555f + k * .022f), h * (.596f - bh * .025f), w * (.567f + k * .022f), h * .598f, w * .003f) }
        // arcade
        p.color = 0xFF1F2338.toInt(); path.reset(); path.moveTo(w * .76f, h * BACK); path.lineTo(w * .76f, h * .53f); path.lineTo(w * .79f, h * .505f); path.lineTo(w * .95f, h * .505f); path.lineTo(w * .96f, h * .53f); path.lineTo(w * .96f, h * BACK); path.close(); c.drawPath(path, p)
        p.color = a(n2, .85f); rr(c, w * .775f, h * .512f, w * .945f, h * .526f, w * .004f)
        text(c, "ROBO-ARCADE", w * .86f, h * .523f, w * .018f, Color.WHITE, true)
        p.color = 0xFF05060C.toInt(); rr(c, w * .785f, h * .535f, w * .935f, h * .6f, w * .008f)
        c.save(); c.clipRect(w * .79f, h * .539f, w * .93f, h * .596f)
        if (b.action == "arcade") { for (k in 0 until 6) { p.color = if (k % 2 == 1) n1 else 0xFFFFD166.toInt(); val ax = w * (.8f + ((tm / 400f + k * .17f) % 1f) * .12f); val ay = h * (.545f + (k % 3) * .015f); c.drawRect(ax, ay, ax + w * .012f, ay + h * .008f, p) }
            p.color = 0xFFFF5A5F.toInt(); val px = w * (.8f + (sin(tm / 300f) + 1) * .055f); c.drawRect(px, h * .585f, px + w * .02f, h * .591f, p) }
        else { val col = a(n1, .5f + .5f * sin(tm / 400f)); text(c, "INSERT COIN", w * .86f, h * .57f, w * .016f, col, true); text(c, "HI " + arcadeScore.toString().padStart(3, '0'), w * .86f, h * .588f, w * .016f, col, true) }
        c.restore()
        p.color = 0xFF2A2F48.toInt(); rr(c, w * .775f, h * .605f, w * .945f, h * .625f, w * .004f)
        circle(c, w * .81f, h * .615f, w * .008f, 0xFFFF5A5F.toInt()); circle(c, w * .9f, h * .615f, w * .006f, 0xFFFFD166.toInt()); circle(c, w * .925f, h * .615f, w * .006f, 0xFFFFD166.toInt())
        // charging pod
        val px = w * .12f; val py = h * GROUND; val ps = w * .13f; val inPod = b.sleeping && abs(b.x - SPOT.getValue("pod")) < .03f
        p.color = 0xFF1F2338.toInt(); oval(c, px, py, ps * .95f, ps * .2f)
        p.color = a(n1, if (inPod) .55f + .3f * sin(tm / 600f) else .3f); oval(c, px, py - ps * .03f, ps * .75f, ps * .12f)
        sp.color = a(n1, .35f); sp.strokeWidth = w * .004f; oval(c, px, py - ps * .03f, ps * .85f, ps * .16f, sp)
        // smart plant
        val qx = w * .9f; val qy = h * GROUND; val qs = w * .1f
        p.color = 0xFFD8DEEC.toInt(); path.reset(); path.moveTo(qx - qs * .3f, qy - qs * .42f); path.lineTo(qx + qs * .3f, qy - qs * .42f); path.lineTo(qx + qs * .22f, qy); path.lineTo(qx - qs * .22f, qy); path.close(); c.drawPath(path, p)
        p.color = a(n1, .85f); c.drawRect(qx - qs * .26f, qy - qs * .22f, qx + qs * .26f, qy - qs * .185f, p)
        val stem = qs * (.3f + plant * .2f); val sway = sin(tm / 900f) * qs * .06f + wind * qs * .1f
        sp.color = 0xFF4DBF6A.toInt(); sp.strokeWidth = qs * .06f; path.reset(); path.moveTo(qx, qy - qs * .42f); path.quadTo(qx + sway * .5f, qy - qs * .42f - stem * .5f, qx + sway, qy - qs * .42f - stem); c.drawPath(path, sp)
        p.color = 0xFF5FD47C.toInt()
        for (k in 0 until min(plant + 1, 4)) { val ly = qy - qs * .42f - stem * (.3f + k * .18f); val side = if (k % 2 == 1) 1f else -1f
            c.save(); c.rotate(Math.toDegrees(side * .5).toFloat(), qx + sway * (.3f + k * .15f) + side * qs * .13f, ly); oval(c, qx + sway * (.3f + k * .15f) + side * qs * .13f, ly, qs * .14f, qs * .06f); c.restore() }
        val fx = qx + sway; val fy = qy - qs * .42f - stem
        if (plant >= 5) { for (k in 0 until 6) { val an = k / 6f * 6.28f + tm / 3000f; circle(c, fx + cos(an) * qs * .12f, fy + sin(an) * qs * .12f, qs * .09f, 0xFFFF8FC8.toInt()) }; circle(c, fx, fy, qs * .08f, 0xFFFFD166.toInt()) }
        else if (plant >= 3) { p.color = 0xFFFF8FC8.toInt(); oval(c, fx, fy, qs * .07f, qs * .1f) }
        if (b.action == "watering") for (k in 0 until 5) { val pp = ((tm / 400f) + k / 5f) % 1f; circle(c, qx - qs * .15f + k * qs * .06f, qy - qs * 1.1f + pp * qs * .6f, qs * .03f, 0xFF7FD3FF.toInt()) }
        // ball
        ball?.let { bl -> val bx = bl.x * w; val by = bl.y * h; val br = w * .028f; p.color = a(Color.BLACK, .3f); oval(c, bx, GROUND * h + w * .005f, br * .9f, br * .25f)
            circle(c, bx, by, br, n2); sp.color = Color.WHITE; sp.strokeWidth = br * .22f; r.set(bx - br * .6f, by - br * .6f, bx + br * .6f, by + br * .6f); c.drawArc(r, Math.toDegrees((bl.x * 30).toDouble()).toFloat(), 137f, false, sp) }
    }

    private fun drawDarkness(c: Canvas, w: Float, h: Float, now: Long): Float {
        val night = isNight(now); val wx = activeWeather()
        var dark = if (night) (if (lightsOn) .28f else .62f) else when (wx) { "Storm" -> .3f; "Clear" -> 0f; else -> .12f }
        if (!lightsOn && !night) dark += .12f
        if (dark > 0f) { p.color = a(0xFF040510.toInt(), dark); c.drawRect(0f, 0f, w, h, p) }
        if (dark > .2f) for ((x, y, rad, col) in listOf(Quad(.135f, .6f, .1f, neon[0]), Quad(.86f, .565f, .11f, neon[1]), Quad(.355f, .57f, .05f, neon[1]), Quad(.12f, GROUND, .12f, neon[0])))
            glow(c, w * x, h * y, w * rad, col, .25f * dark)
        return dark
    }
    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Int)

    private fun drawCable(c: Canvas, w: Float, h: Float, now: Long, s: Float) {
        val b = bot; val sinceUn = (now - unplugAnim) / 500f
        if (!charging && !(sinceUn in 0f..1f)) return
        val n1 = neon[0]; val sx = w * SOCKET_X; val sy = h * SOCKET_Y
        var ex = b.x * w - b.facing * s * .36f; var ey = b.y * h - s * .45f
        if (!charging) { val pp = easeInOut(sinceUn); ex += (sx - ex) * pp * .3f; ey += (h * GROUND - ey) * pp }
        val plugIn = ((now - plugAnim) / 500f).coerceIn(0f, 1f); if (charging && plugIn < 1f) { ex = sx + (ex - sx) * easeInOut(plugIn); ey = sy + (ey - sy) * easeInOut(plugIn) }
        val mx = (sx + ex) / 2; val my = max(sy, ey) + h * .07f + abs(ex - sx) * .08f
        path.reset(); path.moveTo(sx, sy); path.quadTo(mx, my, ex, ey)
        sp.color = 0xFF0A0B12.toInt(); sp.strokeWidth = w * .011f; c.drawPath(path, sp)
        sp.color = 0xFF2C3147.toInt(); sp.strokeWidth = w * .007f; c.drawPath(path, sp)
        if (charging) for (k in 0 until 4) { val t = ((AnimClock.ms(now) / 900f) + k / 4f) % 1f; val x = (1 - t) * (1 - t) * sx + 2 * (1 - t) * t * mx + t * t * ex; val y = (1 - t) * (1 - t) * sy + 2 * (1 - t) * t * my + t * t * ey; glow(c, x, y, w * .02f, n1, .95f) }
        p.color = 0xFF3A3F55.toInt(); rr(c, ex - w * .012f, ey - w * .01f, ex + w * .012f, ey + w * .01f, w * .005f)
    }

    private fun drawDrone(c: Canvas, w: Float, h: Float, now: Long) {
        val n1 = neon[0]; val n2 = neon[1]; val lp = (now - droneLoop) / 900f; var x = dx * w; var y = dy * h; val tm = AnimClock.ms(now)
        if (lp in 0f..1f) { x += sin(lp * PI.toFloat() * 2) * w * .06f; y -= (1 - cos(lp * PI.toFloat() * 2)) * w * .05f }
        val s = w * .045f; val tilt = (dvx * 2).coerceIn(-.4f, .4f)
        glow(c, x, y + s * .9f, s * 1.2f, n1, .3f)
        c.save(); c.translate(x, y); c.rotate(Math.toDegrees(tilt.toDouble()).toFloat())
        p.color = 0xFF3A3F55.toInt(); c.drawRect(-s * .9f, -s * .35f, s * .9f, -s * .25f, p)
        val spin = abs(sin(tm / 30f)); p.color = a(0xFFC8D2F0.toInt(), .5f); oval(c, -s * .85f, -s * .4f, s * .35f * spin + s * .05f, s * .05f); oval(c, s * .85f, -s * .4f, s * .35f * spin + s * .05f, s * .05f)
        p.color = 0xFFE8EBF5.toInt(); oval(c, 0f, 0f, s * .55f, s * .42f)
        p.color = 0xFF15171F.toInt(); oval(c, 0f, s * .02f, s * .36f, s * .24f)
        circle(c, ((bot.x - dx) * 6).coerceIn(-1f, 1f) * s * .12f, s * .02f, s * .11f, n1)
        circle(c, 0f, -s * .42f, s * .06f, if (floor(tm / 500f).toInt() % 2 == 1) n2 else 0xFFFF5A5F.toInt())
        c.restore()
    }

    private fun drawParts(c: Canvas, w: Float, h: Float) {
        parts.forEach { pt ->
            val al = 1 - pt.life / pt.max; val x = pt.x * w; val y = pt.y * h; val s = w * .03f
            c.save(); c.translate(x, y)
            when (pt.kind) {
                P_HEART -> text(c, "❤", 0f, 0f, s * 1.2f, a(0xFFFF5C8A.toInt(), al))
                P_NOTE -> text(c, if (pt.hue % 2 == 1) "♪" else "♫", 0f, 0f, s * 1.1f, a(neon[pt.hue % 2], al), true)
                P_SPARK -> { c.rotate(Math.toDegrees(pt.rot.toDouble()).toFloat()); p.color = a(0xFFFFE68A.toInt(), al); path.reset()
                    for (k in 0 until 4) { c.rotate(90f); path.reset(); path.moveTo(0f, 0f); path.lineTo(s * .12f, s * .12f); path.lineTo(0f, s * .5f); path.lineTo(-s * .12f, s * .12f); path.close(); c.drawPath(path, p) } }
                P_CONFETTI -> { c.rotate(Math.toDegrees(pt.rot.toDouble()).toFloat()); p.color = a(CONFETTI[pt.hue], al); c.drawRect(-s * .15f, -s * .08f, s * .15f, s * .08f, p) }
                else -> circle(c, 0f, 0f, s * .25f * (1 + pt.life), a(0xFFC8B48C.toInt(), .7f * al))
            }
            c.restore()
        }
    }

    private fun drawBot(c: Canvas, w: Float, h: Float, now: Long, bodyIn: Int, sizePct: Int, dark: Float) {
        val b = bot; val s = w * sizePct / 100f; val n1 = neon[0]; val tm = AnimClock.ms(now)
        val body = mix(bodyIn, 0xFF0A0B18.toInt(), dark * .55f); val shade = darker(body, .8f)
        val walking = !b.sleeping && !b.air && !b.carried && abs(b.vxs) > .02f
        val happy = now < b.happyUntil || b.petting; val waving = now < b.waveUntil; val dancing = b.action == "dance" && !b.sleeping
        val surprised = now < b.surpriseUntil; val tired = b.energy < .15f && !charging && !b.sleeping
        val sq = (now - b.squashStart) / 350f; val squash = if (sq in 0f..1f) sin(sq * PI.toFloat()) * .18f else 0f; val breathe = sin(tm / 700f) * .015f
        val bob = when { b.sleeping -> 0f; dancing -> abs(sin(tm / 180f)) * -s * .12f; walking -> abs(sin(tm / 140f)) * -s * .04f; else -> 0f }
        val inPod = b.sleeping && abs(b.x - SPOT.getValue("pod")) < .03f
        val sitting = b.standOn >= 0 && !b.air && !walking && !b.sleeping && b.action != "dance"
        val footY = b.y * h + bob - (if (inPod) s * .04f else 0f); val cx = b.x * w
        val eye = if (tired) 0xFFFFB703.toInt() else if (charging) mix(n1, 0xFF06D6A0.toInt(), .5f) else n1
        val lift = if (b.standOn >= 0) 0f else ((GROUND - b.y) * 4).coerceIn(0f, 1f); p.color = a(Color.BLACK, .35f * (1 - lift * .7f)); oval(c, cx, (if (b.standOn >= 0) b.y else GROUND) * h + s * .02f, s * .42f * (1 - lift * .4f), s * .07f)
        c.save(); c.translate(cx, footY)
        var tilt = when { walking -> b.lean + sin(tm / 140f) * 2; dancing -> sin(tm / 180f) * 14; b.carried -> sin(tm / 150f) * 12; tired -> -4f; else -> 0f }
        val spin = (now - b.spinStart) / 700f; if (spin in 0f..1f) tilt += easeInOut(spin) * 360 * b.facing
        c.rotate(tilt); c.scale(1 + squash - breathe * .5f, 1 - squash + breathe)
        if (sitting) c.translate(0f, s * .1f)
        val hipY = -s * .2f
        sp.color = shade; sp.strokeWidth = s * .09f
        for (side in floatArrayOf(-1f, 1f)) {
            var lx = side * s * .16f; var ly = 0f
            if (walking) { val ph = sin(tm / 140f + (if (side > 0) PI.toFloat() else 0f)); lx += ph * s * .08f; ly = -max(0f, ph) * s * .06f }
            if (b.carried || b.air) { lx += sin(tm / 90f + side) * s * .05f; ly = -s * .02f }
            if (b.sleeping) { lx = side * s * .22f; ly = -s * .02f }
            if (sitting) { lx = side * s * .26f; ly = -s * .005f - max(0f, sin(tm / 600f + side * 1.7f)) * s * .025f }
            c.drawLine(side * s * .14f, hipY, lx, ly - s * .03f, sp); p.color = shade; oval(c, lx + side * s * .02f, ly - s * .02f, s * .09f, s * .045f)
        }
        val bodyTop = hipY - s * .46f
        fun arm(side: Float, ang: Float): FloatArray { val an = Math.toRadians((ang + 15).toDouble()).toFloat(); val L = s * .28f; val ax = side * s * .38f; val ay = bodyTop + s * .12f; val hx = ax + side * sin(an) * L; val hy = ay + cos(an) * L
            sp.color = shade; sp.strokeWidth = s * .08f; c.drawLine(ax, ay, hx, hy, sp); circle(c, hx, hy, s * .06f, shade); return floatArrayOf(hx, hy) }
        val fwd = b.facing
        val aBack = when { walking -> -sin(tm / 140f) * 25; dancing -> 120 + sin(tm / 180f) * 40; happy -> 70f; b.carried -> 150f; b.sleeping -> -10f; surprised -> 100f; tired -> -12f; else -> sin(tm / 700f) * 6 }
        val aFront = when { waving -> 140 + sin(tm / 90f) * 25; walking -> sin(tm / 140f) * 25; dancing -> 120 - sin(tm / 180f) * 40; happy -> 70f; b.carried -> 150f; surprised -> 100f
            b.action == "watering" -> 80f; b.action == "desk" -> 70 + sin(tm / 80f) * 12; b.action == "arcade" -> 60 + sin(tm / 70f) * 15; b.action == "drink" -> 120f
            b.action == "window" -> if (event != null) 110 + sin(tm / 200f) * 10 else 20f; b.sleeping -> -10f; tired -> -12f; else -> sin(tm / 700f) * 6 }
        arm(-fwd, aBack); val hand = arm(fwd, aFront)
        if (b.action == "watering") { p.color = n1; rr(c, min(hand[0] - s * .02f, hand[0] + s * .16f * fwd), hand[1] - s * .08f, max(hand[0] - s * .02f, hand[0] + s * .16f * fwd), hand[1] + s * .05f, s * .03f) }
        if (b.action == "drink") { p.color = n1; rr(c, hand[0] - s * .05f, hand[1] - s * .14f, hand[0] + s * .05f, hand[1] + s * .02f, s * .02f) }
        p.color = shade; rr(c, -s * .38f, bodyTop, s * .38f, hipY + s * .02f, s * .16f)
        p.color = 0xFF0B0D16.toInt(); rr(c, -s * .2f, bodyTop + s * .12f, s * .2f, bodyTop + s * .32f, s * .05f)
        val bars = max(1, kotlin.math.ceil(b.energy * 5).toInt())
        for (k in 0 until 5) { p.color = if (k < bars) (if (b.energy < .15f) 0xFFFF5A5F.toInt() else if (charging) a(0xFF06D6A0.toInt(), .55f + .45f * sin(tm / 200f - k)) else 0xFF06D6A0.toInt()) else 0xFF22263A.toInt()
            rr(c, -s * .17f + k * s * .068f, bodyTop + s * .15f, -s * .17f + k * s * .068f + s * .055f, bodyTop + s * .29f, s * .015f) }
        if (charging) text(c, "⚡", 0f, bodyTop + s * .27f, s * .14f, 0xFFFFD166.toInt(), true)
        val headTop = bodyTop - s * .6f; val sway = sin(tm / 500f) * s * .04f + (if (b.carried) sin(tm / 100f) * s * .06f else 0f) + (if (walking) -b.lean * s * .006f else 0f)
        sp.color = shade; sp.strokeWidth = s * .04f; path.reset(); path.moveTo(0f, headTop + s * .02f); path.quadTo(sway * .3f, headTop - s * .1f, sway, headTop - s * .2f); c.drawPath(path, sp)
        val ballCol = when { charging -> 0xFF06D6A0.toInt(); happy -> 0xFFFF5C8A.toInt(); surprised -> 0xFFFFD166.toInt(); else -> 0xFFFF5A5F.toInt() }
        glow(c, sway, headTop - s * .24f, s * .18f, ballCol, if (b.sleeping) .35f else (180 + 75 * sin(tm / 250f)) / 255f); circle(c, sway, headTop - s * .24f, s * .06f, ballCol)
        p.color = body; rr(c, -s * .5f, headTop, s * .5f, bodyTop + s * .05f, s * .24f)
        p.color = a(Color.WHITE, .3f); rr(c, -s * .38f, headTop + s * .04f, -s * .12f, headTop + s * .075f, s * .02f)
        p.color = shade; rr(c, -s * .58f, headTop + s * .24f, -s * .46f, headTop + s * .44f, s * .05f); rr(c, s * .46f, headTop + s * .24f, s * .58f, headTop + s * .44f, s * .05f)
        p.color = a(n1, .8f); c.drawRect(-s * .56f, headTop + s * .32f, -s * .52f, headTop + s * .36f, p); c.drawRect(s * .52f, headTop + s * .32f, s * .56f, headTop + s * .36f, p)
        p.color = 0xFF0B0D16.toInt(); rr(c, -s * .4f, headTop + s * .1f, s * .4f, headTop + s * .56f, s * .17f)
        glow(c, 0f, headTop + s * .33f, s * .4f, eye, .14f)
        val ex = b.lookX * s * .07f; val ey = b.lookY * s * .04f; val eyeY = headTop + s * .31f + ey
        val bl = (now - b.blinkStart) / 160f; val blink = if (bl in 0f..1f) max(.1f, abs(1 - bl * 2)) else 1f
        sp.color = eye; sp.strokeWidth = s * .05f
        for (side in floatArrayOf(-1f, 1f)) {
            val px = side * s * .17f + ex
            when {
                b.sleeping -> { path.reset(); path.moveTo(px - s * .07f, eyeY + s * .02f); path.quadTo(px, eyeY + s * .06f, px + s * .07f, eyeY + s * .02f); c.drawPath(path, sp) }
                b.petting -> text(c, "❤", px, eyeY + s * .06f, s * .17f, 0xFFFF5C8A.toInt())
                b.carried -> { c.drawLine(px - s * .06f, eyeY - s * .05f, px + s * .06f, eyeY + s * .05f, sp); c.drawLine(px + s * .06f, eyeY - s * .05f, px - s * .06f, eyeY + s * .05f, sp) }
                surprised -> { circle(c, px, eyeY, s * .085f, eye); circle(c, px, eyeY, s * .035f, 0xFF0B0D16.toInt()) }
                happy || dancing -> { path.reset(); path.moveTo(px - s * .07f, eyeY + s * .03f); path.quadTo(px, eyeY - s * .08f, px + s * .07f, eyeY + s * .03f); c.drawPath(path, sp) }
                (b.action == "telescope" || b.action == "window") && isNight(now) -> text(c, "✦", px, eyeY + s * .05f, s * .16f, eye)
                b.action == "desk" || b.action == "arcade" -> { p.color = eye; rr(c, px - s * .07f, eyeY - s * .03f, px + s * .07f, eyeY + s * .03f, s * .02f) }
                else -> { val eh = s * .1f * blink * (if (tired) .5f else 1f); p.color = eye; rr(c, px - s * .06f, eyeY - eh, px + s * .06f, eyeY + eh, min(s * .05f, eh))
                    if (blink > .6f && !tired) circle(c, px + s * .025f, eyeY - s * .04f, s * .018f, a(Color.WHITE, .85f)) }
            }
        }
        circle(c, -s * .29f + ex, headTop + s * .46f, s * .04f, 0x73FF6EC7); circle(c, s * .29f + ex, headTop + s * .46f, s * .04f, 0x73FF6EC7)
        val my = headTop + s * .47f; sp.strokeWidth = s * .03f; sp.color = eye
        when {
            b.sleeping -> circle(c, ex, my, s * .022f, eye)
            b.carried || surprised -> { p.color = eye; oval(c, ex, my + s * .01f, s * .035f, s * .045f) }
            tired -> { path.reset(); path.moveTo(-s * .05f + ex, my + s * .02f); path.quadTo(ex, my - s * .015f, s * .05f + ex, my + s * .02f); c.drawPath(path, sp) }
            else -> { val open = if (happy || b.speech != null || dancing) s * .07f else s * .035f; path.reset(); path.moveTo(-s * .06f + ex, my); path.quadTo(ex, my + open, s * .06f + ex, my); c.drawPath(path, sp) }
        }
        c.restore()
        val headY = footY + (bodyTop - s * .6f)
        if (b.sleeping) for (k in 0..2) { val t = (((now - b.sleepStart) / 2400f) + k / 3f) % 1f; text(c, "z", cx + s * (.35f + t * .35f), headY - s * (.1f + t * .6f), s * (.14f + t * .16f), a(Color.WHITE, .85f * (1 - t)), true) }
        b.speech?.let { sp2 ->
            tp.textSize = s * .2f; tp.isFakeBoldText = true; val tw = tp.measureText(sp2); val pad = s * .12f; val bw = tw + pad * 2; val bh = s * .2f + pad * 1.4f
            val bx = (cx - bw / 2).coerceIn(w * .03f, max(w * .03f, w * .97f - bw)); val by = headY - s * .42f - bh
            p.color = a(Color.WHITE, .96f); rr(c, bx, by, bx + bw, by + bh, bh / 2)
            val tail = cx.coerceIn(bx + bh / 2, max(bx + bh / 2, bx + bw - bh / 2)); path.reset(); path.moveTo(tail - s * .07f, by + bh - 1); path.lineTo(tail, by + bh + s * .1f); path.lineTo(tail + s * .07f, by + bh - 1); path.close(); c.drawPath(path, p)
            text(c, sp2, bx + bw / 2, by + bh / 2 + s * .07f, s * .2f, 0xFF15171F.toInt(), true)
        }
    }

    /** Draws the room (when [room]) and returns a function that draws the robot, drone and particles on top. */
    fun draw(c: Canvas, w: Float, h: Float, now: Long, cfg: LiveConfig, room: Boolean): () -> Unit {
        sizeN = cfg.robotSize / 100f; aspect = w / h
        update(now, cfg.sleepSec * 1000L, room)
        var dark = 0f
        val off = if (room) shift * h else 0f
        if (room) {
            vgrad(c, 0f, h - off - 2f, w, h, 0xFF0D0F18.toInt(), 0xFF07080E.toInt())   // floor continues below
            c.save(); c.translate(0f, -off)
            drawRoom(c, w, h, now); drawGadgets(c, w, h, now)
            c.restore()
            dark = drawDarkness(c, w, h, now)
        }
        return {
            c.save(); c.translate(0f, -off)
            val s = w * cfg.robotSize / 100f
            if (room) drawCable(c, w, h, now, s)
            if (cfg.robot) drawBot(c, w, h, now, cfg.robotColor, cfg.robotSize, dark)
            if (room) drawDrone(c, w, h, now)
            drawParts(c, w, h)
            c.restore()
        }
    }

    fun status(): String {
        val b = bot
        val doing = when {
            b.carried -> "flying"; b.sleeping -> "sleeping"; ball != null -> "playing ball"; b.petting -> "being petted"
            else -> when (b.action) { "idle" -> "chilling"; "walking" -> "walking to ${b.goal ?: "somewhere"}"; "dance" -> "dancing"; "desk" -> "at his desk"
                "arcade" -> "playing arcade"; "drink" -> "having fuel"; "telescope" -> "using the telescope"; "window" -> "looking outside"; "watering" -> "watering the plant"; else -> b.action }
        }
        return "Battery ${(b.energy * 100).toInt()}%${if (charging) " ⚡" else ""}  ·  Mood ${if (b.mood > .7f) "😄" else if (b.mood > .4f) "🙂" else "😐"}  ·  $doing"
    }
}
