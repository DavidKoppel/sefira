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
// Runs before every build; skipped automatically when outputs are up-to-date.
tasks.register("generateOmerIcons") {
    val outDir = project.file("src/main/res/drawable-nodpi")
    outputs.dir(outDir)
    doLast {
        System.setProperty("java.awt.headless", "true")
        outDir.mkdirs()
        for (day in 1..49) {
            val size = 192
            val img = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                               java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                               java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            // Rounded-rect background — dark teal matching the app icon
            g.color = java.awt.Color(0x0D, 0x2F, 0x4A)
            g.fillRoundRect(0, 0, size, size, 48, 48)

            // Day number — large gold
            val numStr = day.toString()
            val fontSize = if (day < 10) 96 else 76
            g.color = java.awt.Color(0xD4, 0xAF, 0x37)
            g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, fontSize)
            var fm = g.fontMetrics
            val tx = (size - fm.stringWidth(numStr)) / 2
            val ty = size / 2 + (fm.ascent - fm.descent) / 2 - 12
            g.drawString(numStr, tx, ty)

            // "Omer" subtitle — smaller, muted gold
            g.color = java.awt.Color(0xB8, 0x96, 0x44)
            g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 28)
            fm = g.fontMetrics
            val sx = (size - fm.stringWidth("Omer")) / 2
            g.drawString("Omer", sx, ty + fm.ascent + 6)

            g.dispose()
            val outFile = java.io.File(outDir, "ic_omer_day_$day.png")
            javax.imageio.ImageIO.write(img, "PNG", outFile)
        }
        println("generateOmerIcons: wrote 49 icons to $outDir")
    }
}

afterEvaluate {
    tasks.named("preBuild") { dependsOn("generateOmerIcons") }
}
