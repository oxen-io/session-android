import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream

task("ipToCode") {
    val inputFile = File("${projectDir}/geolite2_country_blocks_ipv4.csv")

    val outputDir = "${buildDir}/generated/binary"
    val outputFile = File(outputDir, "geolite2_country_blocks_ipv4.bin").apply { parentFile.mkdirs() }

    outputs.file(outputFile)

    doLast {

        // Ensure the input file exists
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Input file does not exist: ${inputFile.absolutePath}")
        }

        // Create a DataOutputStream to write binary data
        DataOutputStream(FileOutputStream(outputFile)).use { out ->
            inputFile.useLines { lines ->
                var prevCode = -1
                lines.drop(1).forEach { line ->
                    runCatching {
                        val ints = line.split(".", "/", ",")
                        val code = ints[5].toInt().also { if (it == prevCode) return@forEach }
                        val ip = ints.take(4).fold(0) { acc, s -> acc shl 8 or s.toInt() }

                        out.writeInt(ip)
                        out.writeInt(code)

                        prevCode = code
                    }
                }
            }
        }

        println("Processed data written to: ${outputFile.absolutePath}")
    }
}
