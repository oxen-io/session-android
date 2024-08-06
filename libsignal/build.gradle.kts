import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

val ipToCountryFilename = "geolite2_country_blocks_ipv4"
val ipToCountryCsvFilename = "$ipToCountryFilename.csv"
val ipToCountryDatFilename = "$ipToCountryFilename.dat"

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.google.devtools.ksp")
}

android {
    val androidCompileSdkVersion: Int by project
    val androidMinimumSdkVersion: Int by project

    compileSdkVersion(androidCompileSdkVersion)

    defaultConfig {
        minSdkVersion(androidMinimumSdkVersion)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        named("main") {
            resources.srcDir("$buildDir/generated/assets")
        }
    }
}

tasks.register("processCsvToBinary") {
    doLast {
        processCsv()
    }
}

// Ensure the task runs before the build
tasks.named("preBuild") {
    dependsOn("processCsvToBinary")
}

dependencies {
    val daggerHiltVersion: String by project
    val jetpackHiltVersion: String by project
    val protobufVersion: String by project
    val jacksonDatabindVersion: String by project
    val curve25519Version: String by project
    val okhttpVersion: String by project
    val kotlinVersion: String by project
    val coroutinesVersion: String by project
    val kovenantVersion: String by project
    val junitVersion: String by project

    implementation("androidx.annotation:annotation:1.5.0")
    implementation("com.google.dagger:hilt-android:$daggerHiltVersion")
    ksp("com.google.dagger:hilt-compiler:$daggerHiltVersion")
    ksp("androidx.hilt:hilt-compiler:$jetpackHiltVersion")
    implementation("com.opencsv:opencsv:4.6")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")
    implementation("com.github.oxen-io.session-android-curve-25519:curve25519-java:$curve25519Version")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    implementation("nl.komponents.kovenant:kovenant:$kovenantVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.assertj:assertj-core:3.11.1")
    testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.0.0")
}

fun processCsv(
    src: String,
    dst: String,
    filters: List<Pair<String, (String) -> Boolean>> = emptyList(),
    writers: List<Pair<String, OutputStream.(String) -> Unit>>,
) {
    // Open the source CSV file
    val inputStream = Files.newInputStream(Paths.get(src))
    val reader = BufferedReader(InputStreamReader(inputStream))

    reader.use {
        val indices = it.readLine().split(",")
            .mapIndexed { i, header -> Pair(header, i) }
            .toMap()

        reader.lines().asSequence().drop(1).forEach { line ->
            // Assuming CSV values are comma-separated and we only care about integers
            line.split(',').let { values ->
                fun getValue(header: String) = values[indices[header]!!]
                // Apply each writer function
                if (filters.all { (header, filter) -> filter(getValue(header)) }) {
                    writers.forEach { (header, writeFunction) ->
                        Paths.get(dst)
                            .apply { Files.createDirectories(parent) }
                            .toFile()
                            .outputStream()
                            .use {
                                runCatching {
                                    it.writeFunction(getValue(header))
                                }
                            }
                    }
                }
            }
        }
    }
}

fun String.ipToInt() =
    takeWhile { it != '/' }.split('.').fold(0L) { acc, octet -> acc shl 8 or octet.toLong() }
        .toInt()

fun processCsv() {
    processCsv(
        src = "$projectDir/csv/$ipToCountryCsvFilename",
        dst = "$buildDir/generated/assets/turtle/$ipToCountryDatFilename",
        writers = listOf(
            "network" to { write(it.ipToInt()) },
            "registered_country_geoname_id" to { write(it.toInt()) }
        )
    )
}
