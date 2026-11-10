package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Where the app icons sit on each home page (a grid you line up with your launcher), plus the dock. */
class IconMap {
    var cols = 4
    var rows = 6
    var top = 14        // % from top: centre of the first row
    var bottom = 72     // % from top: centre of the last row
    var side = 4        // % margin left and right
    var size = 62       // icon size, % of a cell's width
    var dock = true
    var dockY = 90      // % from top: centre of the dock row
    var pages = 3
    var enabled = true
    val cells: MutableList<MutableSet<Int>> = MutableList(5) { mutableSetOf() }
    val dockCells = mutableSetOf<Int>()

    class Box(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float)

    fun cellCenter(col: Int, row: Int): Pair<Float, Float> {
        val usable = 1f - 2 * side / 100f
        val x = side / 100f + (col + 0.5f) * usable / cols
        val y = if (rows <= 1) top / 100f else top / 100f + row * (bottom - top) / 100f / (rows - 1)
        return x to y
    }

    fun dockCenter(col: Int): Pair<Float, Float> {
        val usable = 1f - 2 * side / 100f
        return (side / 100f + (col + 0.5f) * usable / cols) to dockY / 100f
    }

    /** Icon box size in screen fractions; [aspect] = width / height of the screen. */
    fun iconSize(aspect: Float): Pair<Float, Float> {
        val w = (1f - 2 * side / 100f) / cols * size / 100f
        return w to w * aspect
    }

    fun boxes(aspect: Float): List<Box> {
        if (!enabled) return emptyList()
        val (w, h) = iconSize(aspect)
        val out = mutableListOf<Box>()
        for (p in 0 until pages) for (i in cells[p]) {
            val (cx, cy) = cellCenter(i % cols, i / cols)
            if (i / cols < rows) out += Box(p, cx - w / 2, cy - h / 2, w, h)
        }
        if (dock) for (i in dockCells) { if (i < cols) { val (cx, cy) = dockCenter(i); out += Box(-1, cx - w / 2, cy - h / 2, w, h) } }
        return out
    }

    fun save(p: SharedPreferences) {
        val o = JSONObject().put("cols", cols).put("rows", rows).put("top", top).put("bottom", bottom).put("side", side)
            .put("size", size).put("dock", dock).put("dockY", dockY).put("pages", pages).put("enabled", enabled)
            .put("cells", JSONArray(cells.map { JSONArray(it.toList()) })).put("dockCells", JSONArray(dockCells.toList()))
        p.edit().putString(KEY, o.toString()).apply()
    }

    companion object {
        const val KEY = "icon_map"
        fun bgFile(c: Context, page: Int) = File(c.filesDir, "iconmap_page_$page.jpg")

        fun load(p: SharedPreferences): IconMap {
            val m = IconMap()
            val s = p.getString(KEY, null) ?: return m
            try {
                val o = JSONObject(s)
                m.cols = o.optInt("cols", 4); m.rows = o.optInt("rows", 6); m.top = o.optInt("top", 14); m.bottom = o.optInt("bottom", 72)
                m.side = o.optInt("side", 4); m.size = o.optInt("size", 62); m.dock = o.optBoolean("dock", true); m.dockY = o.optInt("dockY", 90)
                m.pages = o.optInt("pages", 3).coerceIn(1, 5); m.enabled = o.optBoolean("enabled", true)
                o.optJSONArray("cells")?.let { arr -> for (i in 0 until minOf(arr.length(), 5)) { val a = arr.getJSONArray(i); for (k in 0 until a.length()) m.cells[i] += a.getInt(k) } }
                o.optJSONArray("dockCells")?.let { a -> for (k in 0 until a.length()) m.dockCells += a.getInt(k) }
            } catch (e: Exception) { }
            return m
        }
    }
}
