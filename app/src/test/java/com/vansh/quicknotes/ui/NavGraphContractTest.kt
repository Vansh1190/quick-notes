package com.vansh.quicknotes.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the crash that shipped in v1.0.0/v1.0.1: a route constant existed (and was used as a
 * start destination or navigate target) but no `composable(...)` was registered for it, so
 * NavHost threw before any UI appeared. Parsing the nav source keeps this cheap and dependency-free.
 */
class NavGraphContractTest {

    private val navSource: File by lazy {
        val relative = "src/main/java/com/vansh/quicknotes/ui/QuickNotesNavHost.kt"
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull(File::exists)
            ?: error("QuickNotesNavHost.kt not found from ${File("").absolutePath}")
    }

    private val routeConstants: Map<String, String> by lazy {
        val objectBody = Regex("object Routes \\{([\\s\\S]*?)\\n}")
            .find(navSource.readText())
            ?.groupValues?.get(1)
            ?: error("object Routes not found")
        Regex("""const val (\w+)\s*=\s*"([^"]+)"""")
            .findAll(objectBody)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `every declared route constant is registered in the nav graph`() {
        val source = navSource.readText()
        val registered = Regex("""composable\(\s*(?:route\s*=\s*)?Routes\.(\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

        // *_ID constants are path values (e.g. NEW_NOTE_ID), not destinations.
        val missing = routeConstants.keys.filter { !it.endsWith("_ID") } - registered
        assertEquals("registered=$registered", emptySet<String>(), missing.toSet())
    }

    @Test
    fun `list detail pattern declares the argument its url segment requires`() {
        val pattern = routeConstants.getValue("LIST_DETAIL")
        val segment = Regex("""\{(\w+)}""").find(pattern)?.groupValues?.get(1)
        assertTrue("LIST_DETAIL pattern is $pattern", segment != null && sourceRegistersArgument(segment))
    }

    private fun sourceRegistersArgument(name: String): Boolean =
        Regex("""navArgument\("$name"\)""").containsMatchIn(navSource.readText())
}
