import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    implementation("com.zaxxer:HikariCP:6.2.1") {
        // Paper levert zelf de SLF4J API.
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // Paper 26.2 levert zelf een sqlite-jdbc driver op de runtime-classpath
    // (bevestigd via smoketest: parent-classloader wint). Daarom bundelen we
    // hem NIET in de shaded jar - dat zou een tweede, ongebruikte 5+ MB aan
    // native binaries meesturen die door de classloader-precedence toch nooit
    // gebruikt worden. compileOnly zorgt dat `org.sqlite.JDBC` beschikbaar is
    // tijdens compilatie; testImplementation zorgt dat de JUnit-tests een
    // driver hebben.
    compileOnly("org.xerial:sqlite-jdbc:3.50.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.50.1.0")

    // Loggingimplementatie voor tests buiten Paper.
    testImplementation("org.slf4j:slf4j-simple:2.0.16")

    // Adventure + MiniMessage voor tests van pure MiniMessage-templates. Paper
    // levert dit in productie; hier testen we los.
    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("net.kyori:adventure-text-minimessage:5.2.0")

    // Paper API voor config-tests die MemoryConfiguration nodig hebben.
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    processResources {
        filteringCharset = "UTF-8"

        val props = mapOf(
            "version" to project.version.toString(),
            "description" to (project.description ?: "Dominium")
        )

        inputs.properties(props)

        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")

        relocate(
            "com.zaxxer.hikari",
            "dev.ankiesmp.dominium.libs.hikari"
        )

        mergeServiceFiles()
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}