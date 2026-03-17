import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sefira.omer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sefira.omer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.play.services.location)
    implementation(libs.kosherjava.zmanim)
}

// ── Generate one launcher icon PNG per Omer day (1–49) ───────────────────────
// Uses only java.base (CRC32, Deflater, ByteArrayOutputStream) — no java.awt.
// Runs before every build; Gradle skips it when outputs are up-to-date.

/** 5×7 pixel font — 7 rows per glyph, each row is 5 bits (bit 4 = left col) */
val OMER_FONT: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110,0b10001,0b10011,0b10101,0b11001,0b10001,0b01110),
    '1' to intArrayOf(0b00100,0b01100,0b00100,0b00100,0b00100,0b00100,0b01110),
    '2' to intArrayOf(0b01110,0b10001,0b00001,0b00110,0b01000,0b10000,0b11111),
    '3' to intArrayOf(0b11110,0b00001,0b00001,0b01110,0b00001,0b00001,0b11110),
    '4' to intArrayOf(0b00010,0b00110,0b01010,0b10010,0b11111,0b00010,0b00010),
    '5' to intArrayOf(0b11111,0b10000,0b10000,0b11110,0b00001,0b00001,0b11110),
    '6' to intArrayOf(0b01110,0b10000,0b10000,0b11110,0b10001,0b10001,0b01110),
    '7' to intArrayOf(0b11111,0b00001,0b00010,0b00100,0b01000,0b01000,0b01000),
    '8' to intArrayOf(0b01110,0b10001,0b10001,0b01110,0b10001,0b10001,0b01110),
    '9' to intArrayOf(0b01110,0b10001,0b10001,0b01111,0b00001,0b00001,0b01110),
    'O' to intArrayOf(0b01110,0b10001,0b10001,0b10001,0b10001,0b10001,0b01110),
    'M' to intArrayOf(0b10001,0b11011,0b10101,0b10001,0b10001,0b10001,0b10001),
    'E' to intArrayOf(0b11111,0b10000,0b10000,0b11110,0b10000,0b10000,0b11111),
    'R' to intArrayOf(0b11110,0b10001,0b10001,0b11110,0b10100,0b10010,0b10001),
)

fun omerInt4(v: Int): ByteArray =
    byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

fun omerChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
    out.write(omerInt4(data.size))
    val tb = type.toByteArray(Charsets.US_ASCII)
    out.write(tb)
    out.write(data)
    val crc = CRC32().also { it.update(tb); it.update(data) }
    out.write(omerInt4(crc.value.toInt()))
}

fun writeOmerPng(file: File, pixels: IntArray, w: Int, h: Int) {
    val out = ByteArrayOutputStream()
    // PNG signature
    out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    // IHDR
    val ihdr = ByteArrayOutputStream()
    ihdr.write(omerInt4(w)); ihdr.write(omerInt4(h))
    ihdr.write(8); ihdr.write(6) // 8-bit RGBA
    ihdr.write(0); ihdr.write(0); ihdr.write(0)
    omerChunk(out, "IHDR", ihdr.toByteArray())
    // IDAT: raw RGBA rows, then zlib-compressed
    val raw = ByteArrayOutputStream()
    for (y in 0 until h) {
        raw.write(0) // filter: None
        for (x in 0 until w) {
            val p = pixels[y * w + x]
            raw.write((p shr 16) and 0xFF) // R
            raw.write((p shr  8) and 0xFF) // G
            raw.write( p         and 0xFF) // B
            raw.write((p ushr 24) and 0xFF) // A
        }
    }
    val comp = ByteArrayOutputStream()
    DeflaterOutputStream(comp).also { d -> d.write(raw.toByteArray()); d.close() }
    omerChunk(out, "IDAT", comp.toByteArray())
    omerChunk(out, "IEND", byteArrayOf())
    file.writeBytes(out.toByteArray())
}

fun drawOmerGlyph(pixels: IntArray, imgW: Int, imgH: Int,
                  ox: Int, oy: Int, ch: Char, scale: Int, color: Int) {
    val rows = OMER_FONT[ch] ?: return
    for (row in rows.indices) {
        for (col in 0..4) {
            if (rows[row] and (1 shl (4 - col)) != 0) {
                for (sy in 0 until scale) for (sx in 0 until scale) {
                    val px = ox + col * scale + sx
                    val py = oy + row * scale + sy
                    if (px in 0 until imgW && py in 0 until imgH)
                        pixels[py * imgW + px] = color
                }
            }
        }
    }
}

fun makeOmerIconPixels(day: Int): IntArray {
    val W = 192; val H = 192
    val BG    = 0xFF0D2F4A.toInt() // dark teal
    val GOLD  = 0xFFD4AF37.toInt()
    val SGOLD = 0xFFB89644.toInt() // subtitle gold

    val pixels = IntArray(W * H) { BG }

    // Rounded-rect transparent corners (radius 24)
    val R = 24
    for (y in 0 until H) for (x in 0 until W) {
        val cx = x.coerceIn(R, W - R - 1)
        val cy = y.coerceIn(R, H - R - 1)
        val dx = x - cx; val dy = y - cy
        if (dx * dx + dy * dy > R * R) pixels[y * W + x] = 0
    }

    // Number (scale 12 for 1 digit, 8 for 2 digits)
    val numStr  = day.toString()
    val nScale  = if (day < 10) 12 else 8
    val gW      = 5 * nScale; val gH = 7 * nScale; val gap = nScale
    val numW    = numStr.length * gW + (numStr.length - 1) * gap

    // Subtitle "OMER" at scale 3
    val sScale  = 3
    val sGW     = 5 * sScale; val sGH = 7 * sScale; val sGap = sScale
    val subW    = 4 * sGW + 3 * sGap

    val blockH  = gH + nScale * 2 + sGH
    val numY    = (H - blockH) / 2
    val subY    = numY + gH + nScale * 2

    var numX = (W - numW) / 2
    for (ch in numStr) {
        drawOmerGlyph(pixels, W, H, numX, numY, ch, nScale, GOLD)
        numX += gW + gap
    }
    var subX = (W - subW) / 2
    for (ch in "OMER") {
        drawOmerGlyph(pixels, W, H, subX, subY, ch, sScale, SGOLD)
        subX += sGW + sGap
    }
    return pixels
}

tasks.register("generateOmerIcons") {
    val outDir = project.file("src/main/res/drawable-nodpi")
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        for (day in 1..49) {
            val pixels = makeOmerIconPixels(day)
            writeOmerPng(File(outDir, "ic_omer_day_$day.png"), pixels, 192, 192)
        }
        println("generateOmerIcons: wrote 49 icons to $outDir")
    }
}

afterEvaluate {
    tasks.named("preBuild") { dependsOn("generateOmerIcons") }
}
