package it.smartphonecombo.uecapabilityparser.importer

import it.smartphonecombo.uecapabilityparser.model.Capabilities
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ImportMtkLteTest {
    private val path = "src/test/resources/mtkLte/"

    private fun parse(inputFilename: String, oracleFilename: String) {
        val filePath = "$path/input/$inputFilename"
        val actual = ImportMTKLte.parse(File(filePath).readBytes())
        val expected =
            Json.decodeFromString<Capabilities>(File("$path/oracle/$oracleFilename").readText())
        // Override dynamic properties
        expected.parserVersion = actual.parserVersion
        expected.timestamp = actual.timestamp
        expected.id = actual.id

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun parsePreCaCombInfo() {
        parse("PRE_CA_COMB_INFO.txt", "PRE_CA_COMB_INFO.json")
    }

    @Test
    fun parseUeCaCombInfo() {
        parse("UE_CA_COMB_INFO.txt", "UE_CA_COMB_INFO.json")
    }
}
