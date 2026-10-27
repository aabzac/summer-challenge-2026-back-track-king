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
    description = "List named versions, local references and candidates; -Pbot=<id> for details"
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
    args = listOf(prop("submitVersion", "current"), prop("submitOut", "build/submit"))
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

tasks.register<JavaExec>("verifySubmission") {
    group = "game"
    description = "Read-only exact comparison of an exported submission with current local source/configuration"
    mainClass.set("btk.submit.ArtifactCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("submitVersion", "current"), prop("submitOut", "build/submit"))
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
    description = "Validate and render CG replays into replays/rendered/cg/{arena,boss}/"
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

tasks.register<JavaExec>("cgLeaderStudy") {
    group = "cg"
    description = "Export one named player's sabotage/construction decisions as feature tables through the core"
    mainClass.set("btk.tools.CgLeaderStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg-indrill"), prop("player", "Indrill"), prop("out", "build/analysis/leader-study"))
}

tasks.register<JavaExec>("cgCellStudy") {
    group = "cg"
    description = "Per-cell construction study of one named player: routes at placement time and what each track did later"
    mainClass.set("btk.tools.CgCellStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg-indrill"), prop("player", "Indrill"), prop("out", "build/analysis/leader-study"))
}

tasks.register<JavaExec>("cgAnalyze") {
    group = "cg"
    description = "Analyze validated arena games for one agent, including same-turn counterfactual diagnostics"
    mainClass.set("btk.tools.CgPlayerStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg"), prop("agent", ""), prop("out", ""))
}

tasks.register<JavaExec>("cgDisrupt") {
    group = "cg"
    description = "Known-state sabotage valuation vs timed search on validated replay prefixes"
    mainClass.set("btk.tools.CgDisruptStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("targets", "replays/cg/903970829.json@0@43+46,replays/cg/903956941.json@1@19+28"),
        prop("out", "build/analysis/disrupt-diagnostic.json"))
}

tasks.register<JavaExec>("rolloutCheck") {
    group = "game"
    description = "Serial work-cost and legality audit of the independent rollout policy on saved states"
    mainClass.set("btk.tools.RolloutCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "build/analysis/reply-study-v1/capture"),
        prop("out", "build/analysis/rollout-v1/cost.json"))
}

tasks.register<JavaExec>("searchExperimentStudy") {
    group = "game"
    description = "Fixed search-experiment mechanism/cost study or core-derived cached boss exclusions"
    mainClass.set("btk.tools.SearchExperimentStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("mode", "study"), prop("input", "build/analysis/reply-study-v1/capture"),
        prop("out", "build/analysis/strategy-search-v1/diagnostic.json"))
}

tasks.register<JavaExec>("cgTiming") {
    group = "cg"
    description = "Offline search-phase timing on validated replay states; not a process deadline check"
    mainClass.set("btk.tools.CgTimingStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("paths", "replays/cg/903509641.json,replays/cg/903508669.json"),
        prop("params", ""), prop("out", "build/analysis/search-timing.json"))
}

tasks.register<JavaExec>("networkCellStudy") {
    group = "cg"
    description = "Audit single-cell network roots on the same 133 saved boss states"
    mainClass.set("btk.tools.NetworkCellStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("input", "docs/evidence/spread-construction-v1-diagnostic.json"),
        prop("bot", "current?USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK=1,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_PROTECTED_OPENING=0"),
        prop("out", "build/analysis/network-single-cell-v1/diagnostic.json"),
        prop("base", "current?USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_PROTECTED_OPENING=0"),
        prop("opportunities", "docs/evidence/network-control-single-cell-v1.json"))
}

tasks.register<JavaExec>("rolloutFollowupStudy") {
    group = "cg"
    description = "Shared-state diagnostics for independent rollout follow-ups (not strength)"
    mainClass.set("btk.tools.RolloutFollowupStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("input", "build/analysis/boss-v06-losses-v2/cohorts.json"),
        prop("bot", "current?USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_ROLLOUT_DIVERSE_REPLIES=1,USE_PROTECTED_OPENING=0"), prop("out", "build/analysis/rollout-followup-study.json"),
        prop("baseline", "current?USE_ROLLOUT_QUICK_ROUTE=0,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_PROTECTED_OPENING=0"))
}

tasks.register<JavaExec>("quickRouteStudy") {
    group = "cg"
    description = "Quick-income route coverage and recorded-state diagnostics (not strength)"
    mainClass.set("btk.tools.QuickRouteStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("input", "build/analysis/boss-v06-losses-v2/cohorts.json"),
        prop("bot", "current?USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_PROTECTED_OPENING=0"), prop("out", "build/analysis/quick-route-study.json"))
}

tasks.register<JavaExec>("bossIntentStudy") {
    group = "cg"
    description = "Boss replay study: announced intents, target holding, pair ranks, route-model membership (docs/boss-proxy-v1.md)"
    mainClass.set("btk.tools.BossIntentStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg/boss"), prop("player", "Boss 4"), prop("out", "build/analysis/boss-intent"))
}

tasks.register<JavaExec>("bossAgreementStudy") {
    group = "cg"
    description = "Replay a bot in the boss seat over the boss corpus and measure decision agreement"
    mainClass.set("btk.tools.BossAgreementStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg/boss"), prop("player", "Boss 4"), prop("bot", "boss-v1?NOISE=0"), prop("maxGames", ""))
}

tasks.register<JavaExec>("bossTargetStudy") {
    group = "cg"
    description = "One row per boss disrupt: the boss's region vs a local boss model's, with the opponent's tracks, income and instability (docs/proxy-agreement-v1.md)"
    mainClass.set("btk.tools.BossTargetStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg/boss"), prop("player", "Boss 4"), prop("bot", "neural-v6"), prop("out", "build/analysis/boss-target/targets.csv"), prop("maxGames", ""))
}

tasks.register<JavaExec>("bossChooserTiming") {
    group = "game"
    description = "Wall time of the real-turn BossSabotage chooser on v09-vs-v09 positions; run with -Pmaps=1 in separate JVMs for the cold (CG-like) figure"
    mainClass.set("btk.tools.BossChooserTiming")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("maps", "12"), prop("seed", "777000"))
}

tasks.register<JavaExec>("proxyBehaviorStudy") {
    group = "game"
    description = "Local paired games with a boss-like spec in the boss seat; writes BossIntentStudy tables for fingerprint comparison"
    mainClass.set("btk.tools.ProxyBehaviorStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("boss", "boss-v1"), prop("opp", "v09"), prop("games", "200"), prop("seed", "910000"),
        prop("out", "build/analysis/proxy-behavior"), prop("threads", "2"))
}

/** Run the merged Player.java as an external process against an in-process bot (protocol + timing check). */
tasks.register<JavaExec>("procCheck") {
    group = "game"
    description = "Play build/submit/Player.java via stdin/stdout against a local bot"
    mainClass.set("btk.runner.ProcessCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", "v01"), prop("games", "3"), prop("map", ""), prop("graceMs", "0"), prop("out", ""),
        prop("processDiagnostics", "false"), prop("submitOut", ""))
}
