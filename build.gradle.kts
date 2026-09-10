import java.util.zip.ZipFile

plugins {
    idea
    eclipse
    `maven-publish`
    jacoco
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.spongepowered.mixin") version "0.7.+"
}

val minecraftVersion = property("minecraft_version") as String
val forgeVersion = property("forge_version") as String
val kotlinForForgeVersion = property("kotlinforforge_version") as String
val createMavenVersion = property("create_maven_version") as String
val ponderVersion = property("ponder_version") as String
val flywheelVersion = property("flywheel_version") as String
val registrateVersion = property("registrate_version") as String
val chemlibVersion = property("chemlib_version") as String
val chemlibCurseFileId = property("chemlib_curse_file_id") as String
val adpotherVersion = property("adpother_version") as String
val forgeEndertechVersion = property("forgeendertech_version") as String
val pneumaticCraftCurseFileId = property("pneumaticcraft_curse_file_id") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modVersion = property("mod_version") as String
val modAuthors = property("mod_authors") as String
val modDescription = property("mod_description") as String
val modLicense = property("mod_license") as String
val modIssueTrackerUrl = property("mod_issue_tracker_url") as String
val packArtifactCache = providers.environmentVariable("BC_PACKAGE_ARTIFACT_CACHE")
    .orElse("${System.getProperty("user.home")}/.cache/bc/packwiz-downloads")

group = property("mod_group") as String
version = modVersion

base {
    archivesName.set("latent-chemlib")
}

fun deobf(notation: Any): Any =
    requireNotNull(extensions.getByName("fg").withGroovyBuilder { "deobf"(notation) })

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
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
        dirs(
            "${packArtifactCache.get()}/mods",
            "../heat-sync/build/libs"
        )
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Heat Sync owns the pack's thermal transport API.  This is intentionally typed,
    // not an event/reflection bridge, so generated HU cannot silently disappear.
    compileOnly(deobf("local:heat-sync:0.1.0"))
    runtimeOnly(deobf("local:heat-sync:0.1.0"))

    // Latent does not integrate with Create or Kotlin for Forge, but the local
    // Heat Sync runtime used by the repository gate has those mandatory dependencies.
    runtimeOnly("thedarkcolour:kotlinforforge:$kotlinForForgeVersion")
    runtimeOnly(deobf("com.simibubi.create:create-$minecraftVersion:$createMavenVersion:slim"))
    runtimeOnly(deobf("net.createmod.ponder:Ponder-Forge-$minecraftVersion:$ponderVersion"))
    runtimeOnly(deobf("io.github.llamalad7:mixinextras-forge:0.3.6"))
    runtimeOnly(deobf("dev.engine-room.flywheel:flywheel-forge-$minecraftVersion:$flywheelVersion"))
    runtimeOnly(deobf("com.tterrag.registrate:Registrate:$registrateVersion"))
    runtimeOnly(deobf("curse.maven:pneumaticcraft-repressurized-281849:$pneumaticCraftCurseFileId"))

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    implementation(deobf("curse.maven:chemlib-340666:$chemlibCurseFileId"))
    compileOnly(deobf("local:AdPother:1.20.1-$adpotherVersion-build.2294"))
    runtimeOnly(deobf("local:AdPother:1.20.1-$adpotherVersion-build.2294"))
    compileOnly(deobf("local:ForgeEndertech:1.20.1-$forgeEndertechVersion-build.2294"))
    runtimeOnly(deobf("local:ForgeEndertech:1.20.1-$forgeEndertechVersion-build.2294"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.processResources {
    val props = mapOf(
        "minecraftVersion" to minecraftVersion,
        "forgeVersion" to forgeVersion,
        "chemlibVersion" to chemlibVersion,
        "adpotherVersion" to adpotherVersion,
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
    dependsOn(tasks.named("compileJava"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json")) {
        rename { "latent_chemlib.refmap.json" }
    }
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

tasks.named<JavaCompile>("compileJava") {
    // MixinGradle does not declare this annotation-processor sidecar itself.
    // Tracking it prevents an incremental release build from reusing classes
    // after the refmap has disappeared and silently producing an unusable JAR.
    outputs.file(layout.buildDirectory.file("tmp/compileJava/compileJava-refmap.json"))

    // Gradle can execute an invalidated incremental compile without invoking
    // annotation processors when no source changed, leaving the tracked refmap
    // absent. A full Java compile is small here and guarantees the release
    // sidecar is recreated whenever this task has to run.
    options.isIncremental = false
}

val verifyRuntimeJar by tasks.registering {
    group = "verification"
    description = "Rejects a release JAR whose production mixins cannot be remapped."
    dependsOn(stageRuntimeJar)

    val runtimeJar = layout.buildDirectory.file("libs/${base.archivesName.get()}-$version.jar")
    inputs.file(runtimeJar)

    doLast {
        val jarFile = runtimeJar.get().asFile
        ZipFile(jarFile).use { zip ->
            val mixinConfig = zip.getEntry("latent_chemlib.mixins.json")
                ?: throw GradleException("Runtime JAR is missing latent_chemlib.mixins.json: $jarFile")
            val mixinConfigText = zip.getInputStream(mixinConfig).bufferedReader().use { it.readText() }
            check(mixinConfigText.contains("\"refmap\": \"latent_chemlib.refmap.json\"")) {
                "Runtime mixin config does not declare latent_chemlib.refmap.json: $jarFile"
            }

            val refmap = zip.getEntry("latent_chemlib.refmap.json")
                ?: throw GradleException("Runtime JAR is missing latent_chemlib.refmap.json: $jarFile")
            val refmapText = zip.getInputStream(refmap).bufferedReader().use { it.readText() }
            check(refmapText.contains("RadioactiveItemEntityMixin") && refmapText.contains("m_6469_")) {
                "Runtime refmap lacks the production mapping for RadioactiveItemEntityMixin.hurt: $jarFile"
            }
        }
    }
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
                include("com/bettercontent/latentchemlib/data/ChemicalTraits.class")
                include("com/bettercontent/latentchemlib/data/NuclearDecayRule.class")
                include("com/bettercontent/latentchemlib/data/NumericCurve.class")
                include("com/bettercontent/latentchemlib/data/PresetCurve.class")
                include("com/bettercontent/latentchemlib/data/SchedulerProfile.class")
                include("com/bettercontent/latentchemlib/sim/ChemicalState.class")
                include("com/bettercontent/latentchemlib/sim/EmergentMath.class")
                include("com/bettercontent/latentchemlib/sim/SimulationBudget.class")
                include("com/bettercontent/latentchemlib/sim/SimulationBudgetLedger.class")
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
    dependsOn(verifyRuntimeJar)
}
