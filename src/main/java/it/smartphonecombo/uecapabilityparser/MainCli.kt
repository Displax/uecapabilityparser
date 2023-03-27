package it.smartphonecombo.uecapabilityparser

import com.ericsson.mts.asn1.KotlinJsonFormatWriter
import com.ericsson.mts.asn1.converter.ConverterNSG
import com.ericsson.mts.asn1.converter.ConverterOsix
import com.ericsson.mts.asn1.converter.ConverterQcat
import com.ericsson.mts.asn1.converter.ConverterWireshark
import it.smartphonecombo.uecapabilityparser.extension.indexOf
import it.smartphonecombo.uecapabilityparser.importer.Import0xB0CD
import it.smartphonecombo.uecapabilityparser.importer.Import0xB826
import it.smartphonecombo.uecapabilityparser.importer.ImportCapabilities
import it.smartphonecombo.uecapabilityparser.importer.ImportCapabilityInformation
import it.smartphonecombo.uecapabilityparser.importer.ImportLteCarrierPolicy
import it.smartphonecombo.uecapabilityparser.importer.ImportMTKLte
import it.smartphonecombo.uecapabilityparser.importer.ImportNrCapPrune
import it.smartphonecombo.uecapabilityparser.importer.ImportNvItem
import it.smartphonecombo.uecapabilityparser.model.Capabilities
import it.smartphonecombo.uecapabilityparser.model.Rat
import it.smartphonecombo.uecapabilityparser.util.Config
import it.smartphonecombo.uecapabilityparser.util.Utility
import it.smartphonecombo.uecapabilityparser.util.Utility.getAsn1Converter
import it.smartphonecombo.uecapabilityparser.util.Utility.multipleParser
import it.smartphonecombo.uecapabilityparser.util.Utility.outputFile
import it.smartphonecombo.uecapabilityparser.util.Utility.preformatHexData
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException

/**
 * The Class Main.
 *
 * @author handy
 */
internal object MainCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options()
        val help = Option("h", "help", false, "Print this help message.")
        options.addOption(help)
        val inputFile = Option("i", "input", true, "Main capability file.")
        inputFile.isRequired = true
        options.addOption(inputFile)
        val inputFileNR = Option("inputNR", true, "NR UE Capability file.")
        options.addOption(inputFileNR)
        val inputFileENDC = Option("inputENDC", true, "ENDC UE Capability file.")
        options.addOption(inputFileENDC)
        val defaultInputIsNR =
            Option("nr", "defaultNR", false, "Main capability input is NR (otherwise LTE).")
        options.addOption(defaultInputIsNR)
        val multiple0xB826 =
            Option(
                "multi",
                "multiple0xB826",
                false,
                "Use this option if input contains several 0xB826 hexdumps separated by blank lines and optionally prefixed with \"Payload :\"."
            )
        options.addOption(multiple0xB826)
        val type =
            Option(
                "t",
                "type",
                true,
                "Type of capability.\nValid values are:\nH (UE Capability Hex Dump)\nW (Wireshark UE Capability Information)\nN (NSG UE Capability Information)\nC (Carrier policy)\nCNR (NR Cap Prune)\nE (28874 nvitem binary, decompressed)\nQ (QCAT 0xB0CD)\nQNR (0xB826 hexdump)\nM (MEDIATEK CA_COMB_INFO)\nO (OSIX UE Capability Information)\nQC (QCAT UE Capability Information)."
            )
        type.isRequired = true
        options.addOption(type)
        val csv =
            Option(
                "c",
                "csv",
                true,
                "Output a csv, if no file specified the csv will be output to standard output.\nSome parsers output multiple CSVs, in these cases \"-LTE\", \"-NR\", \"-EN-DC\", \"-NR-DC\" will be added before the extension."
            )
        csv.setOptionalArg(true)
        options.addOption(csv)
        val uelog =
            Option(
                "l",
                "uelog",
                true,
                "Output the uelog, if no file specified the uelog will be output to standard output."
            )
        uelog.setOptionalArg(true)
        options.addOption(uelog)
        val debug = Option("d", "debug", false, "Print debug info.")
        options.addOption(debug)

        val parser: CommandLineParser = DefaultParser()
        val formatter = HelpFormatter()
        val cmd: CommandLine
        try {
            cmd = parser.parse(options, args)
            val flags = Config
            if (cmd.hasOption("help")) {
                formatter.printHelp("ueCapabilityParser", options)
                return
            }
            if (cmd.hasOption("debug")) {
                flags["debug"] = "true"
            }
            val typeLog = cmd.getOptionValue("type")
            val comboList = parsing(cmd, typeLog)

            if (cmd.hasOption("csv")) {
                val fileName: String? = cmd.getOptionValue("csv")
                if (
                    typeLog == "W" ||
                        typeLog == "N" ||
                        typeLog == "H" ||
                        typeLog == "QNR" ||
                        typeLog == "CNR" ||
                        typeLog == "QC" ||
                        typeLog == "O"
                ) {
                    val lteCombos = comboList.lteCombos
                    if (!lteCombos.isNullOrEmpty()) {
                        outputFile(
                            Utility.toCsv(lteCombos),
                            fileName?.let { Utility.appendBeforeExtension(it, "-LTE") }
                        )
                    }
                    val nrCombos = comboList.nrCombos
                    if (!nrCombos.isNullOrEmpty()) {
                        outputFile(
                            Utility.toCsv(nrCombos),
                            fileName?.let { Utility.appendBeforeExtension(it, "-NR") }
                        )
                    }
                    val enDcCombos = comboList.enDcCombos
                    if (!enDcCombos.isNullOrEmpty()) {
                        outputFile(
                            Utility.toCsv(enDcCombos),
                            fileName?.let { Utility.appendBeforeExtension(it, "-EN-DC") }
                        )
                    }
                    val nrDcCombos = comboList.nrDcCombos
                    if (!nrDcCombos.isNullOrEmpty()) {
                        outputFile(
                            Utility.toCsv(nrDcCombos),
                            fileName?.let { Utility.appendBeforeExtension(it, "-NR-DC") }
                        )
                    }
                } else {
                    outputFile(Utility.toCsv(comboList), fileName)
                }
            }
        } catch (e: ParseException) {
            val helpArgs = args.contains("-h") || args.contains("--help")
            if (!helpArgs) {
                System.err.println(e.localizedMessage)
            }
            formatter.printHelp("ueCapabilityParser", options)
            if (!helpArgs) {
                exitProcess(1)
            }
        }
    }

    private fun parsing(cmd: CommandLine, typeLog: String): Capabilities {
        try {
            val filePath = cmd.getOptionValue("input")
            val file = File(filePath)
            val imports: ImportCapabilities?
            when (typeLog) {
                "E" -> imports = ImportNvItem
                "C" -> imports = ImportLteCarrierPolicy
                "CNR" -> imports = ImportNrCapPrune
                "Q" -> imports = Import0xB0CD
                "M" -> imports = ImportMTKLte
                "QNR" -> imports = Import0xB826
                "W",
                "N",
                "O",
                "QC",
                "H" -> return ueCapabilityHandling(cmd, typeLog)
                else -> {
                    System.err.println(
                        "Only type W (wireshark), N (NSG), H (Hex Dump), Osix (OSIX UE Capability Informationn), " +
                            "QC (QCAT UE Capability Information), C (Carrier policy), CNR (NR Cap Prune), E (28874 nvitem), " +
                            "Q (0xB0CD text), M (CA_COMB_INFO), QNR (0xB826 hexdump) are supported!"
                    )
                    exitProcess(1)
                }
            }

            if (cmd.hasOption("uelog")) {
                val outputFile = cmd.getOptionValue("uelog")
                outputFile(file.reader().use(InputStreamReader::readText), outputFile)
            }

            return if (typeLog == "QNR") {
                multipleParser(
                    file.reader().use(InputStreamReader::readText),
                    cmd.hasOption("multi"),
                    imports
                )
            } else {
                file.inputStream().use { imports.parse(it) }
            }
        } catch (e: Exception) {
            System.err.println("Error")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private fun ueCapabilityHandling(cmd: CommandLine, typeLog: String): Capabilities {
        var input: String
        var inputNR = ""
        var inputENDC = ""
        input = Utility.readFile(cmd.getOptionValue("input"), StandardCharsets.UTF_8)
        if (typeLog == "H" || typeLog == "N" || typeLog == "W") {
            if (cmd.hasOption("inputNR")) {
                inputNR = Utility.readFile(cmd.getOptionValue("inputNR"), StandardCharsets.UTF_8)
            }
            if (cmd.hasOption("inputENDC")) {
                inputENDC =
                    Utility.readFile(cmd.getOptionValue("inputENDC"), StandardCharsets.UTF_8)
            }
            if (typeLog == "H") {
                input = preformatHexData(input)
                inputNR = preformatHexData(inputNR)
                inputENDC = preformatHexData(inputENDC)
            } else {
                input += inputENDC + inputNR
            }
        }
        val imports = ImportCapabilityInformation
        val formatWriter = KotlinJsonFormatWriter()
        val ratContainerMap = mutableMapOf<String, JsonElement>()
        lateinit var eutraIdentifier: Regex
        lateinit var nrIdentifier: Regex
        lateinit var mrdcIdentifier: Regex

        val converter =
            when (typeLog) {
                "W" -> {
                    eutraIdentifier = "${Rat.EUTRA.ratCapabilityIdentifier}\\s".toRegex()
                    nrIdentifier = "${Rat.NR.ratCapabilityIdentifier}\\s".toRegex()
                    mrdcIdentifier = "${Rat.EUTRA_NR.ratCapabilityIdentifier}\\s".toRegex()
                    ConverterWireshark()
                }
                "N" -> {
                    eutraIdentifier = "rat-Type : ${Rat.EUTRA}\\s".toRegex()
                    nrIdentifier = "rat-Type : ${Rat.NR}\\s".toRegex()
                    mrdcIdentifier = "rat-Type : ${Rat.EUTRA_NR}\\s".toRegex()
                    ConverterNSG()
                }
                "O" -> {
                    eutraIdentifier = "${Rat.EUTRA.ratCapabilityIdentifier}\\s".toRegex()
                    nrIdentifier = "${Rat.NR.ratCapabilityIdentifier}\\s".toRegex()
                    mrdcIdentifier = "${Rat.EUTRA_NR.ratCapabilityIdentifier}\\s".toRegex()
                    ConverterOsix()
                }
                "QC" -> {
                    eutraIdentifier = "value ${Rat.EUTRA.ratCapabilityIdentifier} ::=\\s".toRegex()
                    nrIdentifier = "value ${Rat.NR.ratCapabilityIdentifier} ::=\\s".toRegex()
                    mrdcIdentifier =
                        "value ${Rat.EUTRA_NR.ratCapabilityIdentifier} ::=\\s".toRegex()
                    ConverterQcat()
                }
                "H" -> null
                else -> null
            }

        if (typeLog == "H") {
            val defaultRat = if (cmd.hasOption("defaultNR")) Rat.NR else Rat.EUTRA
            ratContainerMap += Utility.getUeCapabilityJsonFromHex(defaultRat, input)
            if (inputNR.isNotBlank()) {
                ratContainerMap += Utility.getUeCapabilityJsonFromHex(Rat.NR, inputNR)
            }
            if (inputENDC.isNotBlank()) {
                ratContainerMap += Utility.getUeCapabilityJsonFromHex(Rat.EUTRA_NR, inputENDC)
            }
        } else {
            val list =
                listOf(
                        Rat.EUTRA to input.indexOf(eutraIdentifier),
                        Rat.EUTRA_NR to input.indexOf(mrdcIdentifier),
                        Rat.NR to input.indexOf(nrIdentifier)
                    )
                    .filter { it.second != -1 }
                    .sortedBy { it.second }
            var eutra = ""
            var eutraNr = ""
            var nr = ""
            for (i in list.indices) {
                val (rat, start) = list[i]
                val end = list.getOrNull(i + 1)?.second ?: input.length
                when (rat) {
                    Rat.EUTRA ->
                        eutra = input.substring(start + eutraIdentifier.toString().length - 1, end)
                    Rat.EUTRA_NR ->
                        eutraNr = input.substring(start + mrdcIdentifier.toString().length - 1, end)
                    Rat.NR -> nr = input.substring(start + nrIdentifier.toString().length - 1, end)
                    else -> {}
                }
            }
            if (eutra.isNotBlank()) {
                getAsn1Converter(Rat.EUTRA, converter!!)
                    .convert(
                        Rat.EUTRA.ratCapabilityIdentifier,
                        eutra.byteInputStream(),
                        formatWriter
                    )
                formatWriter.jsonNode?.let { ratContainerMap.put(Rat.EUTRA.toString(), it) }
            }
            if (eutraNr.isNotBlank() || nr.isNotBlank()) {
                val nrConverter = getAsn1Converter(Rat.NR, converter!!)
                if (eutraNr.isNotBlank()) {
                    nrConverter.convert(
                        Rat.EUTRA_NR.ratCapabilityIdentifier,
                        eutraNr.byteInputStream(),
                        formatWriter
                    )
                    formatWriter.jsonNode?.let { ratContainerMap.put(Rat.EUTRA_NR.toString(), it) }
                }
                if (nr.isNotBlank()) {
                    nrConverter.convert(
                        Rat.NR.ratCapabilityIdentifier,
                        nr.byteInputStream(),
                        formatWriter
                    )
                    formatWriter.jsonNode?.let { ratContainerMap.put(Rat.NR.toString(), it) }
                }
            }
        }

        val jsonOutput = JsonObject(ratContainerMap)

        if (cmd.hasOption("uelog")) {
            val outputFile = cmd.getOptionValue("uelog")
            outputFile(jsonOutput.toString(), outputFile)
        }

        val jsonEutra = jsonOutput.getOrDefault(Rat.EUTRA.toString(), null) as? JsonObject
        val jsonEutraNr = jsonOutput.getOrDefault(Rat.EUTRA_NR.toString(), null) as? JsonObject
        val jsonNr = jsonOutput.getOrDefault(Rat.NR.toString(), null) as? JsonObject

        return imports.parse(jsonEutra, jsonEutraNr, jsonNr)
    }
}
