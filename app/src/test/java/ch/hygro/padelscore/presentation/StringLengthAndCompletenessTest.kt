package ch.hygro.padelscore.presentation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringLengthAndCompletenessTest {

    private val compactKeyMaxLength = mapOf(
        "reset" to 10,
        "opponent_short" to 6,
        "our_team" to 8,
        "advantage_abbr" to 4,
        "golden_abbr" to 6,
        "star_abbr" to 6,
        "advantage_opponent_short" to 12,
        "advantage_us" to 12,
        "golden_point_short" to 14,
        "star_point_short" to 14,
        "change_sides_short" to 16
    )

    @Test
    fun compactLabelsRespectMaxCharacterBudgetInAllLanguages() {
        val resDir = locateResDir("app")
        val valueDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values")
        }.orEmpty()
        assertTrue("Expected at least 10 values directories", valueDirs.size >= 10)

        val violations = mutableListOf<String>()
        for (dir in valueDirs) {
            val stringsFile = File(dir, "strings.xml")
            if (!stringsFile.exists()) continue
            val entries = parseStringsXml(stringsFile)
            for ((key, maxLen) in compactKeyMaxLength) {
                val value = entries[key] ?: continue
                if (value.length > maxLen) {
                    violations += "${dir.name}/$key = \"$value\" (${value.length} > $maxLen)"
                }
            }
        }
        assertTrue(
            "Compact labels exceed max length budget:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun locateResDir(module: String): File {
        val candidates = listOf(
            File("src/main/res"),
            File("$module/src/main/res")
        )
        return candidates.first { it.isDirectory }
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            map[name] = node.textContent.replace("\\'", "'")
        }
        return map
    }
}
