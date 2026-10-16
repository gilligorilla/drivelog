package com.example.drivelog

import com.example.drivelog.data.CsvLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvLoggerTest {
    @Test
    fun rowMatchesSpecLayout() {
        assertEquals(
            "2026-10-09 13:45:01,depth,45,42,32,\"\"",
            CsvLogger.row("2026-10-09 13:45:01", "depth", 45, 42, 32)
        )
        assertEquals(
            "2026-10-09 13:45:22,note,45,42,12,\"Stop for splice\"",
            CsvLogger.row("2026-10-09 13:45:22", "note", 45, 42, 12, "Stop for splice")
        )
    }

    @Test
    fun notesAreQuotedAndEscaped() {
        assertEquals(
            "t,note,1,0,0,\"a, \"\"b\"\"\"",
            CsvLogger.row("t", "note", 1, 0, 0, "a, \"b\"")
        )
    }

    @Test
    fun headerHasFiveLinesInOrder() {
        val h = CsvLogger.headerLines("Bridge 7", "D30-42", "HP14x73\nbattered", "P-12")
        assertEquals(
            listOf(
                "Project Name: Bridge 7",
                "Hammer Description: D30-42",
                "Pile Description: HP14x73 battered",
                "Pile Name: P-12",
                "Timestamp,Action,Depth,Stroke_Inches,Blows_Per_Foot,Notes",
            ),
            h
        )
    }
}
