import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    eclipse
    `maven-publish`
    jacoco
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.spongepowered.mixin") version "0.7.+"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val kotlinForForgeVersion = property("kotlinforforge_version") as String
val createReleaseVersion = property("create_release_version") as String
val createMavenVersion = property("create_maven_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val chemlibVersion = property("chemlib_version") as String
val chemlibCurseFileId = property("chemlib_curse_file_id") as String
val heatSyncVersion = property("heatsync_version") as String
val emiVersion = property("emi_version") as String
val emiCurseFileId = property("emi_curse_file_id") as String
val jeiVersion = property("jei_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String
val modIssueTrackerUrl = property("mod_issue_tracker_url") as String

group = property("mod_group") as String
version = modVersion

base {
    archivesName.set(modId)
}

fun deobf(notation: Any): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources = true

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "info")
            property("forge.enabledGameTestNamespaces", "$modId,minecraft")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", file("build/createSrgToMcp/output.srg").absolutePath)

            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("client")

        create("server") {
            arg("--nogui")
        }

        create("gameTestServer")

        create("data") {
            args(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
            )
        }
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://www.cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
    maven("https://maven.blamejared.com")
    flatDir {
        dirs("../heat-sync/build/libs")
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    implementation("thedarkcolour:kotlinforforge:$kotlinForForgeVersion")

    implementation(deobf("com.simibubi.create:create-$minecraftVersion:$createMavenVersion:slim"))
    implementation(deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    implementation(deobf("io.github.llamalad7:mixinextras-forge:0.3.6"))
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly(deobf("dev.engine-room.flywheel:flywheel-forge-api-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion"))
    implementation(deobf("com.tterrag.registrate:Registrate:$registrateVersion"))

    implementation(deobf("curse.maven:chemlib-340666:$chemlibCurseFileId"))
    compileOnly(deobf("com.gerald:heatsync:$heatSyncVersion"))
    runtimeOnly(deobf("com.gerald:heatsync:$heatSyncVersion"))
    compileOnly(deobf("curse.maven:emi-580555:$emiCurseFileId"))
    runtimeOnly(deobf("curse.maven:emi-580555:$emiCurseFileId"))
    compileOnly(deobf("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion"))
    compileOnly(deobf("mezz.jei:jei-$minecraftVersion-forge-api:$jeiVersion"))
    runtimeOnly(deobf("mezz.jei:jei-$minecraftVersion-forge:$jeiVersion"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "kotlinForForgeVersion" to kotlinForForgeVersion,
        "createReleaseVersion" to createReleaseVersion,
        "heatSyncVersion" to heatSyncVersion,
        "chemlibVersion" to chemlibVersion,
        "emiVersion" to emiVersion,
        "jeiVersion" to jeiVersion,
        "modId" to modId,
        "modName" to modName,
        "modVersion" to modVersion,
        "modAuthors" to modAuthors,
        "modDescription" to modDescription,
        "modIssueTrackerUrl" to modIssueTrackerUrl,
        "modLicense" to modLicense
    )

    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

mixin {
    add(sourceSets.main.get(), "latent_chemlib.refmap.json")
    config("latent_chemlib.mixins.json")
}

val syncGameTestStructures by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("gameteststructures"))
    into(layout.projectDirectory.dir("run/gameteststructures"))
}

tasks.matching { it.name == "prepareRunGameTestServer" }.configureEach {
    dependsOn(syncGameTestStructures)
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.asFileTree.matching {
                include("com/gerald/latentchemlib/data/ChemicalTraits.class")
                include("com/gerald/latentchemlib/data/MachineProfile.class")
                include("com/gerald/latentchemlib/data/NuclearDecayRule.class")
                include("com/gerald/latentchemlib/data/NumericCurve.class")
                include("com/gerald/latentchemlib/data/PresetCurve.class")
                include("com/gerald/latentchemlib/data/SchedulerProfile.class")
                include("com/gerald/latentchemlib/sim/ChemicalState.class")
                include("com/gerald/latentchemlib/sim/ChamberPacingSimulator.class")
                include("com/gerald/latentchemlib/sim/MachineTransfer.class")
                include("com/gerald/latentchemlib/sim/ReactionRuleSelector.class")
                include("com/gerald/latentchemlib/sim/EmergentMath.class")
                include("com/gerald/latentchemlib/sim/SimulationBudget.class")
                include("com/gerald/latentchemlib/sim/SimulationBudgetLedger.class")
            }
        )
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs the fast deterministic verification lane."
    dependsOn(tasks.named("check"))
}

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane, including headless Forge GameTests."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(tasks.named("headlessGameTest"))
}
