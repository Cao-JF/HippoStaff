plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.retrooper:packetevents-spigot:2.9.5")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.13")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("io.lettuce:lettuce-core:6.5.5.RELEASE")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith(".jar") }
                .map { zipTree(it) }
        })
        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
        )
    }
}
