package com.mailautomation

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_ENTRIES = 300
    private val entries = ArrayDeque<String>()
    private val lock = Any()

    /** Called on the thread that produced the log entry. Switch to UI thread before touching views. */
    @Volatile var listener: (() -> Unit)? = null

    fun d(tag: String, msg: String) = add("D", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val full = if (t != null) "$msg\n  → ${t.javaClass.simpleName}: ${t.message}" else msg
        add("E", tag, full)
        // Log up to 3 levels of cause chain
        if (t != null) {
            var cause = t.cause
            var depth = 1
            while (cause != null && depth <= 3) {
                add("E", tag, "  Ursache $depth: ${cause.javaClass.simpleName}: ${cause.message}")
                cause = cause.cause
                depth++
            }
        }
    }

    private fun add(level: String, tag: String, msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())
        val entry = "$time [$level] $tag: $msg"
        val androidLevel = when (level) { "E" -> Log.ERROR; "W" -> Log.WARN; else -> Log.DEBUG }
        Log.println(androidLevel, tag, msg)
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
        listener?.invoke()
    }

    fun getLog(): String = synchronized(lock) {
        if (entries.isEmpty()) "(Noch keine Einträge)"
        else entries.joinToString("\n")
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        listener?.invoke()
    }
}
