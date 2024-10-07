import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream

// Check that the correct number of arguments is provided
if (args.size < 2) {
    throw IllegalArgumentException("Please provide both input and output file paths.")
}

// Get the input and output file paths from the command line arguments
val inputFile = File(args[0])
val outputFile = File(args[1]).apply { parentFile.mkdirs() }

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
