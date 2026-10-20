package com.example.networkmonitor

import android.content.Context
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object LogStore {
    private const val FILE_NAME = "network_monitor.csv"
    private const val HEADER = "timestamp,target,address,success,latency_ms,network,error"

    @Synchronized
    fun append(
        context: Context,
        target: String,
        address: String,
        success: Boolean,
        latencyMs: Long?,
        network: String,
        error: String?
    ) {
        val file = file(context)
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            file.writeText("$HEADER\n", Charsets.UTF_8)
        }

        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val row = listOf(
            timestamp,
            target,
            address,
            success.toString(),
            latencyMs?.toString().orEmpty(),
            network,
            error.orEmpty()
        ).joinToString(",") { csvEscape(it) }

        file.appendText("$row\n", Charsets.UTF_8)
    }

    @Synchronized
    fun ensureFile(context: Context): File {
        val file = file(context)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("$HEADER\n", Charsets.UTF_8)
        }
        return file
    }

    @Synchronized
    fun latest(context: Context, count: Int = 30): String {
        val file = ensureFile(context)
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.size <= 1) return "Henüz ölçüm yok."

        return lines.drop(1).takeLast(count).asReversed().joinToString("\n") { line ->
            val parts = parseCsvLine(line)
            if (parts.size < 7) return@joinToString line
            val status = if (parts[3] == "true") "BAŞARILI" else "BAŞARISIZ"
            val latency = parts[4].ifBlank { "-" }
            "${parts[0]}  ${parts[1]} (${parts[2]})  $status  ${latency} ms  ${parts[5]}" +
                if (parts[6].isBlank()) "" else "  ${parts[6]}"
        }
    }

    private fun file(context: Context): File = File(context.filesDir, "logs/$FILE_NAME")

    private fun csvEscape(value: String): String {
        val normalized = value.replace("\r", " ").replace("\n", " ")
        return if (normalized.any { it == ',' || it == '"' }) {
            "\"${normalized.replace("\"", "\"\"")}\""
        } else normalized
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }
}
