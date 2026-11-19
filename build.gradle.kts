import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin debe coincidir con fabric-language-kotlin (1.14.2+kotlin.2.4.21)
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("net.fabricmc.fabric-loom") version "1.17.+"
}

val modId: String by project
val modName: String by project
val modVersion: String by project
val mavenGroup: String by project
val minecraftVersion: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
val fabricKotlinVersion: String by project
val permissionsApiVersion: String by project
val sguiVersion: String by project

base.archivesName.set(modId)
// La versión lleva la de Minecraft: 0.4.2+26.2, 0.5.0+26.3...
version = "$modVersion+$minecraftVersion"
group = mavenGroup

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.nucleoid.xyz/") // SGUI (menús de servidor)
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // Permisos (LuckPerms): se incluye dentro del .jar
    implementation("me.lucko:fabric-permissions-api:$permissionsApiVersion")
    include("me.lucko:fabric-permissions-api:$permissionsApiVersion")

    // Menús tipo cofre que funcionan con clientes vanilla: se incluye dentro del .jar
    implementation("eu.pb4:sgui:$sguiVersion")
    include("eu.pb4:sgui:$sguiVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        inputs.property("fabricKotlin", fabricKotlinVersion)
        inputs.property("minecraft", minecraftVersion)
        filesMatching("fabric.mod.json") {
            expand(
                mapOf(
                    "version" to project.version,
                    "fabricKotlin" to fabricKotlinVersion,
                    "minecraft" to minecraftVersion,
                ),
            )
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        from("LICENSE")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}
