package com.shivam.glassicons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.xmlpull.v1.XmlPullParser

data class PackIcon(val name: String, val label: String, val category: String)

data class MissingApp(val label: String, val component: ComponentName)

data class Wallpaper(val id: String, val light: String, val dark: String)

/** Reads the pack's own `drawable.xml`, `appfilter.xml` and bundled preview wallpapers. */
object IconCatalog {

    fun icons(context: Context): List<PackIcon> {
        val res = context.resources
        val names = res.getStringArray(R.array.icon_names)
        val labels = res.getStringArray(R.array.icon_labels)
        val labelOf = names.zip(labels).toMap()
        val out = ArrayList<PackIcon>()
        val parser = res.getXml(R.xml.drawable)
        var category = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "category" -> category = parser.getAttributeValue(null, "title") ?: ""
                    "item" -> parser.getAttributeValue(null, "drawable")?.let { out += PackIcon(it, labelOf[it] ?: it, category) }
                }
            }
            event = parser.next()
        }
        parser.close()
        return out
    }

    /** Components and packages covered by appfilter.xml. */
    fun covered(context: Context): Pair<Set<String>, Set<String>> {
        val comps = HashSet<String>()
        val pkgs = HashSet<String>()
        val parser = context.resources.getXml(R.xml.appfilter)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val c = parser.getAttributeValue(null, "component") ?: ""
                val inner = c.substringAfter("{", "").substringBefore("}", "")
                if (inner.contains('/')) {
                    comps += inner
                    pkgs += inner.substringBefore('/')
                }
            }
            event = parser.next()
        }
        parser.close()
        return comps to pkgs
    }

    /** Installed launchable apps that have no icon in this pack yet. */
    fun missing(context: Context): List<MissingApp> {
        val pm = context.packageManager
        val (comps, pkgs) = covered(context)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(pm).toString() }
            .filter { (cn, _) -> cn.packageName != context.packageName }
            .filter { (cn, _) -> "${cn.packageName}/${cn.className}" !in comps && cn.packageName !in pkgs }
            .map { (cn, label) -> MissingApp(label, cn) }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** appfilter lines a contributor can paste into tools/icons/generate_icons.py. */
    fun requestText(apps: List<MissingApp>): String = buildString {
        appendLine("Glass Icons — icon requests (${apps.size})")
        appendLine()
        apps.forEach { a ->
            appendLine("<!-- ${a.label} -->")
            appendLine("<item component=\"ComponentInfo{${a.component.packageName}/${a.component.className}}\" drawable=\"\" />")
        }
    }

    fun wallpapers(context: Context): List<Wallpaper> {
        val files = context.assets.list("wallpapers")?.toSet() ?: emptySet()
        return files.filter { it.endsWith("_light.webp") }
            .map { it.removeSuffix("_light.webp") }
            .filter { "${it}_dark.webp" in files }
            .sorted()
            .map { Wallpaper(it, "wallpapers/${it}_light.webp", "wallpapers/${it}_dark.webp") }
    }

    fun loadAsset(context: Context, path: String): Bitmap? =
        try { context.assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    @SuppressLint("DiscouragedApi")
    fun drawableId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)
}
