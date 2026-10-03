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

/** Candidate-set datasets for the neural imitation policy. -Ppath/-Pplayer comma lists, -Pholdout, -Pnegatives */
tasks.register<JavaExec>("cgNeuralExport") {
    group = "cg"
    description = "Export DISRUPT/PLACE decisions of named players as NeuralFeatures candidate rows (training data)"
    mainClass.set("btk.tools.CgNeuralExport")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg-indrill"), prop("player", "Indrill"), prop("out", "build/analysis/neural-data"),
        prop("holdout", "40"), prop("negatives", "40"), prop("seed", "20260920"))
}

/** Terminal-outcome decisions from the retained imitation policy, with its original feature space. */
tasks.register<JavaExec>("neuralV2Collect") {
    group = "game"
    description = "Collect local outcome fine-tuning decisions for neural-v2 against frozen opponents"
    mainClass.set("btk.tools.NeuralV2Collect")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("games", "64"), prop("seed", "2200000"), prop("weights", "build/analysis/neural-v2/weights-0.json"),
        prop("out", "build/analysis/neural-v2/round-1"), prop("temp", "1.0"))
}

/** Register existing corpus maps or score fixed probe rows; creates no games. */
tasks.register<JavaExec>("neuralV2Verify") {
    group = "game"
    description = "Write-once neural-v2 corpus exclusions or generated-weight numerical parity"
    mainClass.set("btk.tools.NeuralV2Verify")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("mode", "maps"), prop("path", "replays/cg-indrill,replays/cg/boss,replays/cg/903510176.json"),
        prop("weights", "build/analysis/neural-v2/weights-0.json"), prop("out", "build/bench/neural-v2-corpus-excluded.json"))
}

/** Per-head exploration follow-up; retained V2 collection remains unchanged. */
tasks.register<JavaExec>("neuralV4Collect") {
    group = "game"
    description = "Collect neural-v4 decisions with sabotage T=1 and construction T=0.5"
    mainClass.set("btk.tools.NeuralV4Collect")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("games", "192"), prop("seed", "3100000"), prop("weights", "build/analysis/neural-v4/weights-0.json"),
        prop("out", "build/analysis/neural-v4/round-1"), prop("sabotageTemp", "1.0"), prop("buildTemp", "0.5"))
}

/** Register existing maps or compare fixed probe rows with the new generated weights. */
tasks.register<JavaExec>("neuralV4Verify") {
    group = "game"
    description = "Write-once neural-v4 corpus exclusions or generated-weight numerical parity"
    mainClass.set("btk.tools.NeuralV4Verify")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("mode", "maps"), prop("path", "replays/cg-indrill,replays/cg/boss,replays/cg/903510176.json"),
        prop("weights", "build/analysis/neural-v4/weights-0.json"), prop("out", "build/bench/neural-v4-corpus-excluded.json"))
}

/** Local terminal-outcome data for the independent Indrill-style action-value model. */
tasks.register<JavaExec>("indrillCollect") {
    group = "game"
    description = "Collect local sequential action-value training rows; no arena or boss API calls"
    mainClass.set("btk.tools.IndrillCollect")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("games", "48"), prop("seed", "2000000"), prop("weights", "build/analysis/indrill-q/weights-0.bin"),
        prop("out", "build/analysis/indrill-q/round-1.bin.gz"), prop("epsilon", "0.25"), prop("opponent", "mixed"))
}

/** Float32 cross-language model parity and fixed training-map exclusions. */
tasks.register<JavaExec>("indrillV2Collect") {
    group = "game"
    description = "Collect v2 learned-state-value Q episodes, alternating frozen opponent seats"
    mainClass.set("btk.tools.IndrillV2Collect")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("games", "128"), prop("seed", "2100000"), prop("weights", "build/analysis/indrill-q-v2/weights-0.bin"),
        prop("out", "build/analysis/indrill-q-v2/round-1.bin.gz"), prop("epsilon", "0.30"), prop("opponent", "mixed"))
}

tasks.register<JavaExec>("indrillV2Probe") {
    group = "game"
    description = "Register v2's 960 training maps and export value/advantage numerical parity observations"
    mainClass.set("btk.tools.IndrillV2Probe")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("weights", "build/analysis/indrill-q-v2/weights-6.bin"), prop("out", "build/analysis/indrill-q-v2/probe"))
}

tasks.register<JavaExec>("indrillProbe") {
    group = "game"
    description = "Register training-map exclusions and export fixed float32 inference parity observations"
    mainClass.set("btk.tools.IndrillProbe")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("weights", "build/indrill/round4.bin"), prop("out", "build/analysis/indrill-q/parity"))
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

tasks.register<JavaExec>("replyStudy") {
    group = "game"
    description = "Capture a fixed local sample, then audit narrow reply prediction on shared states (tools only)"
    mainClass.set("btk.tools.ReplyStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("mode", "analyze"), prop("path", "build/analysis/reply-study-v1/capture"),
        prop("out", "build/analysis/reply-study-v1/report.json"))
}

tasks.register<JavaExec>("jointReplyStudy") {
    group = "game"
    description = "Audit joint first-build/own-sabotage values on the retained replyStudy sample (tools only)"
    mainClass.set("btk.tools.JointReplyStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "build/analysis/reply-study-v1/capture"),
        prop("prior", "build/analysis/reply-study-v1/report.json"),
        prop("out", "build/analysis/reply-study-v1/joint-report.json"))
}

tasks.register<JavaExec>("jointActionCheck") {
    group = "game"
    description = "Serial 36-ms cost check for the joint-action candidate on saved states"
    mainClass.set("btk.tools.JointActionCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "build/analysis/reply-study-v1/capture"),
        prop("out", "build/analysis/joint-action-v1/cost.json"))
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

tasks.register<JavaExec>("cgBossStudy") {
    group = "cg"
    description = "Inspect observed boss defense on an explicit validated replay sample"
    mainClass.set("btk.tools.CgBossDefenseStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("paths", ""), prop("out", "build/analysis/boss-defense-" + System.currentTimeMillis() + ".json"))
}

tasks.register<JavaExec>("cgBossLossStudy") {
    group = "cg"
    description = "Validated descriptive comparison of explicitly counted chronological boss cohorts"
    mainClass.set("btk.tools.CgBossLossStudy")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("path", "replays/cg/boss"), prop("before", "16"), prop("total", "30"),
        prop("out", "build/analysis/boss-v06-losses-v2/cohorts.json"))
}

tasks.register<JavaExec>("cgRolloutBossProbe") {
    group = "cg"
    description = "Known-state frozen rollout diagnostics on a previously recorded boss cohort"
    mainClass.set("btk.tools.CgRolloutBossProbe")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("input", "build/analysis/boss-v06-losses-v2/cohorts.json"),
        prop("out", "build/analysis/boss-v06-losses-v2/rollout-probe.json"))
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

/** Run the merged Player.java as an external process against an in-process bot (protocol + timing check). */
tasks.register<JavaExec>("procCheck") {
    group = "game"
    description = "Play build/submit/Player.java via stdin/stdout against a local bot"
    mainClass.set("btk.runner.ProcessCheck")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(prop("bot", "v01"), prop("games", "3"), prop("map", ""), prop("graceMs", "0"), prop("out", ""),
        prop("processDiagnostics", "false"), prop("submitOut", ""))
}
