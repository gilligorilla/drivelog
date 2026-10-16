package com.example.drivelog.data

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Streams CSV lines to disk one at a time. Every line is flushed and fsync'd so that an app
 * crash, battery pull, or kill loses at most the line currently being written.
 *
 * All disk I/O happens on a single background thread, in submission order, so callers (the UI
 * thread, the audio thread) never block on storage.
 */
class CsvLogger(private val onError: (String) -> Unit) {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "csv-writer") }
    private var stream: FileOutputStream? = null

    /** Opens [file] for writing and streams [headerLines] immediately. */
    fun open(file: File, headerLines: List<String>) = submit {
        closeStream()
        stream = FileOutputStream(file, /* append = */ true)
        headerLines.forEach { writeLine(it) }
    }

    fun append(line: String) = submit { writeLine(line) }

    /** Flushes remaining lines and closes the file. */
    fun close() = submit { closeStream() }

    private fun writeLine(line: String) {
        val out = stream ?: return
        out.write((line + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
        out.fd.sync()
    }

    private fun closeStream() {
        val out = stream ?: return
        stream = null
        try {
            out.flush()
            out.fd.sync()
        } finally {
            out.close()
        }
    }

    private fun submit(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                onError("CSV write failed: ${e.message}")
            }
        }
    }

    companion object {
        const val HEADER = "Timestamp,Action,Depth,Stroke_Inches,Blows_Per_Foot,Notes"

        fun row(
            timestamp: String,
            action: String,
            depth: Int,
            strokeInches: Int,
            blowsPerFoot: Int,
            notes: String = "",
        ): String {
            val quotedNotes = "\"" + notes.replace("\"", "\"\"") + "\""
            return "$timestamp,$action,$depth,$strokeInches,$blowsPerFoot,$quotedNotes"
        }

        fun headerLines(
            project: String,
            hammer: String,
            pileDescription: String,
            pileName: String,
        ): List<String> = listOf(
            "Project Name: ${oneLine(project)}",
            "Hammer Description: ${oneLine(hammer)}",
            "Pile Description: ${oneLine(pileDescription)}",
            "Pile Name: ${oneLine(pileName)}",
            HEADER,
        )

        private fun oneLine(s: String) = s.replace('\n', ' ').replace('\r', ' ')
    }
}
