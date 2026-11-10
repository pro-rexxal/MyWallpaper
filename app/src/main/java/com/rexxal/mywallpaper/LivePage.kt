package com.rexxal.mywallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView

/** Tab 3: live wallpaper with a fully customizable clock and date. */
class LivePage(private val a: MainActivity) {

    private val c = a
    private val p = Ui.prefs(c)
    private fun dp(v: Int) = Ui.dp(c, v)
    private val cfg = LiveConfig.load(p)
    private lateinit var preview: LivePreviewView

    val view: LinearLayout = build()

    private fun changed() {
        cfg.save(p)
        preview.cfg = cfg.copy()
        if (cfg.bgMode == 0 && preview.photo == null) reloadPhoto()
        preview.invalidate()
    }

    fun refresh() = preview.postInvalidate()

    fun reloadPhoto() {
        Thread {
            val bmp = LiveRenderer.loadPhoto(c, 720)
            a.runOnUiThread {
                preview.photo = bmp
                preview.invalidate()
            }
        }.start()
    }

    private fun build(): LinearLayout {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        preview = LivePreviewView(c).apply {
            aspect = a.screenW.toFloat() / a.screenH
            cfg = this@LivePage.cfg.copy()
        }
        root.addView(preview, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        // Robot buddy
        col.addView(Ui.section(c, "Robot buddy"))
        val bot = Ui.card(c)
        bot.addView(Ui.text(c,
            "Tap him · tap anywhere to send him there · drag to lead him · double-tap for the time. " +
                "Ignore him and he falls asleep. Try it on the preview above!", 12f, Ui.MUTED))
        Ui.toggle(c, bot, "Show robot", cfg.robot) { cfg.robot = it; changed() }
        Ui.slider(c, bot, "Size", 26, cfg.robotSize - 12, { "${it + 12}" }) { cfg.robotSize = it + 12; changed() }
        bot.addView(Ui.label(c, "Color"))
        bot.addView(Ui.colors(c, cfg.robotColor) { cfg.robotColor = it; changed() })
        bot.addView(Ui.pill(c, "💬  Say something nice") {
            preview.robot.say(Robot.THOUGHTS.random(), System.currentTimeMillis(), 4000L)
            preview.invalidate()
        }, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(8) })
        col.addView(bot)

        // Background
        col.addView(Ui.section(c, "Background"))
        val bg = Ui.card(c)
        bg.addView(Ui.choice(c, LiveRenderer.BG_LABELS, cfg.bgMode) {
            cfg.bgMode = it
            changed()
            if (it == 0 && !LiveRenderer.photoFile(c).exists()) a.toast("Pick a photo in Edit, then tap ✨ Use in Live")
        }.view)
        bg.addView(Ui.label(c, "Color 1 (glow / particles)"))
        bg.addView(Ui.colors(c, cfg.color1) { cfg.color1 = it; changed() })
        bg.addView(Ui.label(c, "Color 2 (base)"))
        bg.addView(Ui.colors(c, cfg.color2) { cfg.color2 = it; changed() })
        Ui.slider(c, bg, "Speed", 9, cfg.speed - 1, { "${it + 1}" }) { cfg.speed = it + 1; changed() }
        Ui.slider(c, bg, "Photo dim", 80, cfg.photoDim, { "$it%" }) { cfg.photoDim = it; changed() }
        col.addView(bg)

        // Clock
        col.addView(Ui.section(c, "Clock"))
        val clock = Ui.card(c)
        Ui.toggle(c, clock, "Show time", cfg.showTime) { cfg.showTime = it; changed() }
        Ui.toggle(c, clock, "24-hour", cfg.use24h) { cfg.use24h = it; changed() }
        Ui.toggle(c, clock, "Show seconds", cfg.showSeconds) { cfg.showSeconds = it; changed() }
        Ui.toggle(c, clock, "Show AM/PM", cfg.showAmPm) { cfg.showAmPm = it; changed() }
        clock.addView(Ui.label(c, "Font"))
        clock.addView(Ui.choice(c, LiveRenderer.FONT_LABELS, cfg.font) { cfg.font = it; changed() }.view)
        Ui.slider(c, clock, "Size", 37, cfg.timeSize - 8, { "${it + 8}" }) { cfg.timeSize = it + 8; changed() }
        clock.addView(Ui.label(c, "Text color"))
        clock.addView(Ui.colors(c, cfg.textColor) { cfg.textColor = it; changed() })
        Ui.toggle(c, clock, "Soft shadow", cfg.shadow) { cfg.shadow = it; changed() }
        col.addView(clock)

        // Date
        col.addView(Ui.section(c, "Date"))
        val date = Ui.card(c)
        Ui.toggle(c, date, "Show date", cfg.showDate) { cfg.showDate = it; changed() }
        Ui.toggle(c, date, "Date above time", cfg.dateAbove) { cfg.dateAbove = it; changed() }
        date.addView(Ui.label(c, "Format"))
        date.addView(Ui.choice(c, LiveRenderer.DATE_PATTERNS.indices.map { LiveRenderer.dateExample(it) }, cfg.dateFormat) {
            cfg.dateFormat = it; changed()
        }.view)
        Ui.slider(c, date, "Size", 12, cfg.dateSize - 2, { "${it + 2}" }) { cfg.dateSize = it + 2; changed() }
        col.addView(date)

        // Layout
        col.addView(Ui.section(c, "Position"))
        val pos = Ui.card(c)
        Ui.slider(c, pos, "From top", 85, cfg.posY - 5, { "${it + 5}%" }) { cfg.posY = it + 5; changed() }
        pos.addView(Ui.label(c, "Align"))
        pos.addView(Ui.choice(c, listOf("Left", "Center", "Right"), cfg.align) { cfg.align = it; changed() }.view)
        col.addView(pos)

        col.addView(Ui.text(c,
            "Tip: Realme draws its own clock on the lock screen. To avoid two clocks, use this on the home screen, " +
                "or change the lock screen clock style in Realme's settings.",
            12f, Ui.MUTED).apply { setPadding(dp(4), dp(14), dp(4), dp(8)) })

        root.addView(ScrollView(c).apply { addView(col) }, LinearLayout.LayoutParams(Ui.MATCH, dp(230)))

        root.addView(Ui.button(c, "Set Live Wallpaper", true) { setLive() },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })

        if (cfg.bgMode == 0) reloadPhoto()
        return root
    }

    private fun setLive() {
        cfg.save(p)
        val comp = ComponentName(c, ClockWallpaperService::class.java)
        try {
            a.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp))
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                a.toast("Choose \"MyWallpaper Live\" from the list")
            } catch (e2: Exception) {
                a.toast("This phone doesn't allow live wallpapers")
            }
        }
    }
}
