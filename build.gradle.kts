plugins {
    java
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

application {
    mainClass.set("btk.runner.LocalRunner")
}

fun prop(name: String, default: String): String =
    providers.gradleProperty(name).getOrElse(default)

/** One local game, writes replays/local/game_<ts>_<seed>.json. -Pbot0 -Pbot1 -Pseed -Pmap */
tasks.named<JavaExec>("run") {
    args = listOf(prop("bot0", "current"), prop("bot1", "v01"), prop("seed", ""), prop("map", ""))
}

/** In-process arena. -Pbot1 -Pbot2 -Pgames -Pmaps (dir with CG replays to take maps from) -Pthreads */
tasks.register<JavaExec>("bench") {
    group = "game"
    description = "Benchmark two bots in-process (parallel, side swap, Wilson CI)"
    mainClass.set("btk.runner.BenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot1", "current"), prop("bot2", "v01"), prop("games", "200"),
        prop("maps", ""), prop("threads", ""))
}

/** Merge core + bot.current into build/submit/Player.java and compile-check it. */
tasks.register<JavaExec>("submit") {
    group = "game"
    description = "Merge bot classes into a single Player.java for CodinGame"
    mainClass.set("btk.submit.FileMerger")
    classpath = sourceSets["main"].runtimeClasspath
}

/** Download CG replays. -Ptop=N (top N players) | -Pagent=ID | -Pplayer=pseudo ; -Plimit=games per agent */
tasks.register<JavaExec>("cgFetch") {
    group = "cg"
    description = "Download CodinGame replays into replays/cg/"
    mainClass.set("btk.tools.CgFetch")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("top", ""), prop("agent", ""), prop("player", ""), prop("limit", "10"))
}

/** Replay both players' stdout from CG replays through the local referee and diff scores/events. */
tasks.register<JavaExec>("cgValidate") {
    group = "cg"
    description = "Validate local engine against downloaded CG replays"
    mainClass.set("btk.tools.CgValidate")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("show", ""))
}

tasks.register<JavaExec>("cgStudy") {
    group = "cg"
    description = "Statistics on what top players do in downloaded replays; -Pbot=<spec> measures move agreement"
    mainClass.set("btk.tools.CgPolicyStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("bot", ""))
}

/** Run the merged Player.java as an external process against an in-process bot (protocol + timing check). */
tasks.register<JavaExec>("procCheck") {
    group = "game"
    description = "Play build/submit/Player.java via stdin/stdout against a local bot"
    mainClass.set("btk.runner.ProcessCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", "v01"), prop("games", "3"))
}
