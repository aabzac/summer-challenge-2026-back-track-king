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
    args = listOf(prop("bot0", "current"), prop("bot1", "v01"), prop("seed", ""), prop("map", ""), prop("timing", "cg"))
}

/** Paired-map arena. -Pbot1 -Pbot2 -Pgames (even) -Pmaps -Pthreads -Pseed -Pout (JSON report) */
tasks.register<JavaExec>("bench") {
    group = "game"
    description = "Benchmark two bots on paired maps with frozen reference parameters and a JSON report"
    mainClass.set("btk.runner.BenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot1", "current"), prop("bot2", "v01"), prop("games", ""),
        prop("maps", ""), prop("threads", ""), prop("seed", ""), prop("out", ""), prop("timing", "cg"),
        prop("pool", ""), prop("excludeReports", ""))
}

tasks.register<JavaExec>("benchSuite") {
    group = "game"
    description = "Sequential matched opponent panel; separate reports by opponent and map height"
    mainClass.set("btk.runner.BenchmarkSuite")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", "current"), prop("pool", "bench/pools/development-v1.json"),
        prop("opponents", "bench/opponents-v1.json"), prop("threads", "2"),
        prop("out", "build/bench/suite-" + System.currentTimeMillis()), prop("excludeReports", ""))
}

tasks.register<JavaExec>("botCatalog") {
    group = "game"
    description = "List named versions, local references and pinned experiments; -Pbot=<id> for details"
    mainClass.set("btk.tools.BotCatalogTool")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", ""))
}

/** Merge core + bot.current into build/submit/Player.java and compile-check it. */
tasks.register<JavaExec>("submit") {
    group = "game"
    description = "Merge bot classes into a single Player.java for CodinGame"
    mainClass.set("btk.submit.FileMerger")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("submitVersion", "current"))
}

/** Download CG replays. -Ptop=N | -Pagent=ID | -Pplayer=pseudo | -Pgame=ID ; -Plimit=games per agent */
tasks.register<JavaExec>("cgFetch") {
    group = "cg"
    description = "Download CodinGame replays into replays/cg/"
    mainClass.set("btk.tools.CgFetch")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("top", ""), prop("agent", ""), prop("player", ""), prop("limit", "10"),
        prop("game", ""))
}

/** Replay both players' stdout from CG replays through the local referee and diff scores/events. */
tasks.register<JavaExec>("cgValidate") {
    group = "cg"
    description = "Validate local engine against downloaded CG replays"
    mainClass.set("btk.tools.CgValidate")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"))
}

/** Validate and convert canonical CG replay(s) to the visualizer schema. Requires -Ppath. */
tasks.register<JavaExec>("cgRender") {
    group = "cg"
    description = "Validate and render CG replays into replays/rendered/cg/{arena,bot}/"
    mainClass.set("btk.tools.CgRender")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", ""), prop("out", "replays/rendered/cg"))
}

/** Regenerate maps from refereeInput seeds and compare every cell/town with replay viewer data. */
tasks.register<JavaExec>("cgMapValidate") {
    group = "cg"
    description = "Validate the authentic map generator against downloaded CG replay seeds"
    mainClass.set("btk.tools.CgMapValidate")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"))
}

tasks.register<JavaExec>("cgStudy") {
    group = "cg"
    description = "Statistics on what top players do in downloaded replays; -Pbot=<spec> measures move agreement"
    mainClass.set("btk.tools.CgPolicyStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("bot", ""), prop("timing", "cg"))
}

tasks.register<JavaExec>("cgAnalyze") {
    group = "cg"
    description = "Analyze validated arena games for one agent, including same-turn counterfactual diagnostics"
    mainClass.set("btk.tools.CgPlayerStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("agent", ""), prop("out", ""))
}

tasks.register<JavaExec>("cgForecast") {
    group = "cg"
    description = "Offline adaptive sabotage forecast; chronological observations, opponent-group holdout"
    mainClass.set("btk.tools.CgForecastStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("agent", ""), prop("out", ""), prop("params", ""))
}

tasks.register<JavaExec>("cgDisrupt") {
    group = "cg"
    description = "Known-state sabotage valuation vs timed search on validated replay prefixes"
    mainClass.set("btk.tools.CgDisruptStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("targets", "replays/cg/903970829.json@0@43+46,replays/cg/903956941.json@1@19+28"),
        prop("out", "build/analysis/disrupt-diagnostic.json"))
}

tasks.register<JavaExec>("cgTiming") {
    group = "cg"
    description = "Offline search-phase timing on validated replay states; not a process deadline check"
    mainClass.set("btk.tools.CgTimingStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("paths", "replays/cg/903509641.json,replays/cg/903508669.json"),
        prop("params", ""), prop("out", "build/analysis/search-timing.json"))
}

/** Run the merged Player.java as an external process against an in-process bot (protocol + timing check). */
tasks.register<JavaExec>("procCheck") {
    group = "game"
    description = "Play build/submit/Player.java via stdin/stdout against a local bot"
    mainClass.set("btk.runner.ProcessCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", "v01"), prop("games", "3"), prop("map", ""), prop("graceMs", "0"), prop("out", ""),
        prop("processDiagnostics", "false"))
}
