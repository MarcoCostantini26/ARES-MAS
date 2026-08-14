package ares

import com.google.gson.Gson
import jason.asSyntax.*
import jason.environment.Environment
import alice.tuprolog.Prolog
import alice.tuprolog.Theory
import java.io.File
import java.nio.file.Paths
import java.util.Timer
import java.util.TimerTask

data class ScenarioData(val grid: GridData, val entities: List<EntityData>)
data class GridData(val width: Int, val height: Int)
data class EntityData(val id: String, val type: String, val position: List<Int>)

class AresEnvironment : Environment() {

    data class MineralState(val id: String, val x: Int, val y: Int, var claimed: Boolean = false)

    companion object {
        var gridWidth = 10
        var gridHeight = 10
        var hazardList = listOf<Pair<Int, Int>>()
        var mineralStates = mutableListOf<MineralState>()
        var batteryLevels = mutableMapOf<String, Int>()
        lateinit var logFile: File
        private var globalTick = 0
        private val resourceLock = Any()

        @Synchronized
        fun nextTick(): Int {
            globalTick++
            return globalTick
        }

        fun logPlanningEvent(agName: String, plan: List<String>) {
            val t = nextTick()
            val ev = """{"tick": $t, "type": "PLANNING", "rover": "$agName", "plan": ${Gson().toJson(plan)}}"""
            logFile.appendText(ev + "\n")
        }
    }

    private lateinit var dataDir: File
    private lateinit var scenario: ScenarioData
    private var isScenarioLoaded = false
    private val prologEngine = Prolog()
    private val engineLock = Any()

    private var patrolIndex = 0
    private val patrolRoute = listOf(Pair(5, 5), Pair(6, 5), Pair(6, 6), Pair(5, 6))
    private var patrolMovesDone = 0
    private val maxPatrolMoves = 8
    private lateinit var hazardTimer: Timer

    override fun init(args: Array<String>) {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        dataDir = if (workingDir.endsWith("backend")) File(workingDir, "../data") else File(workingDir, "data")
        logFile = File(dataDir, "events.jsonl")
        logFile.writeText("")

        val scenarioFile = File(dataDir, "scenario.json")
        if (scenarioFile.exists()) {
            scenario = Gson().fromJson(scenarioFile.readText(), ScenarioData::class.java)
            isScenarioLoaded = true

            gridWidth = scenario.grid.width
            gridHeight = scenario.grid.height
            hazardList = scenario.entities.filter { it.type == "HAZARD" }.map { Pair(it.position[0], it.position[1]) }
            mineralStates = scenario.entities.filter { it.type == "MINERAL" }
                .map { MineralState(it.id, it.position[0], it.position[1]) }
                .toMutableList()

            batteryLevels["harv1"] = 21
            batteryLevels["harv2"] = 100

            println("🪐 Scenario loaded: ${scenario.entities.size} entities found.")

            val rulesFile = File(dataDir, "rules.pl")
            if (rulesFile.exists()) {
                rebuildPrologTheory()
                println("tuProlog Guardrail Initialized successfully.")
            } else {
                println("ERROR: rules.pl file not found!")
            }

            updatePercepts()
            startHazardPatrol()
        } else {
            println("ERROR: scenario.json file not found!")
        }
    }

    private fun rebuildPrologTheory() {
        val rulesFile = File(dataDir, "rules.pl")
        prologEngine.setTheory(Theory(rulesFile.readText()))
        for ((hx, hy) in hazardList) {
            prologEngine.addTheory(Theory("hazard($hx, $hy)."))
        }
    }

    private fun startHazardPatrol() {
        hazardTimer = Timer(true)
        hazardTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                moveHazard()
            }
        }, 3000, 4000)
    }

    private fun moveHazard() {
        synchronized(engineLock) {
            if (patrolMovesDone >= maxPatrolMoves) {
                hazardTimer.cancel()
                println("Sandstorm patrol completed ($maxPatrolMoves moves) — hazard now static.")
                return
            }

            patrolIndex = (patrolIndex + 1) % patrolRoute.size
            val newPos = patrolRoute[patrolIndex]
            hazardList = listOf(newPos)
            rebuildPrologTheory()
            patrolMovesDone++

            val t = nextTick()
            val ev = """{"tick": $t, "type": "HAZARD_MOVE", "hazard_id": "sandstorm1", "to": [${newPos.first}, ${newPos.second}]}"""
            logFile.appendText(ev + "\n")
            println("Sandstorm moved to [${newPos.first}, ${newPos.second}] ($patrolMovesDone/$maxPatrolMoves)")

            updatePercepts()
        }
    }

    private fun updatePercepts() {
        if (!isScenarioLoaded) return
        clearPercepts()

        for (m in mineralStates) {
            if (!m.claimed) {
                addPercept(Literal.parseLiteral("mineral(${m.x}, ${m.y})"))
            }
        }
        for ((x, y) in hazardList) {
            addPercept(Literal.parseLiteral("hazard($x, $y, \"sandstorm\")"))
        }
    }

    override fun executeAction(agName: String, action: Structure): Boolean {
        val tick = Companion.nextTick()
        val functor = action.functor

        if (functor == "move") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()

            val isSafe = synchronized(engineLock) {
                prologEngine.solve("safe_to_move($x, $y).").isSuccess
            }

            if (isSafe) {
                val ev = """{"tick": $tick, "type": "MOVE", "rover": "$agName", "to": [$x, $y], "battery": ${batteryLevels[agName]}}"""
                logFile.appendText(ev + "\n")

                synchronized(resourceLock) {
                    val current = batteryLevels.getOrDefault(agName, 100)
                    batteryLevels[agName] = (current - 2).coerceAtLeast(0)
                }

                println("Action executed by $agName: move to [$x, $y] (Battery: ${batteryLevels[agName]})")
            } else {
                val ev = """{"tick": $tick, "type": "VIOLATION", "rover": "$agName", "attempted_to": [$x, $y], "action_type": "MOVE", "reason": "unsafe_move", "rule_evaluated": "safe_to_move($x, $y) :- \\+ hazard($x, $y).", "hazard_matched": "hazard($x, $y)"}"""
                logFile.appendText(ev + "\n")
                println("GUARDRAIL ACTIVE: Movement of $agName to [$x, $y] blocked by tuProlog!")
                return false
            }
        }

        if (functor == "log_cnp") {
            val msgType = action.getTerm(0).toString()
            val content = action.getTerm(1).toString()
            val sender = action.getTerm(2).toString()
            val receiver = action.getTerm(3).toString()

            val ev = """{"tick": $tick, "type": "NEGOTIATION", "msg_type": "$msgType", "content": "$content", "sender": "$sender", "receiver": "$receiver"}"""
            logFile.appendText(ev + "\n")
        }

        if (functor == "claim_mineral") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()

            val success = synchronized(resourceLock) {
                val m = mineralStates.find { it.x == x && it.y == y && !it.claimed }
                if (m != null) { m.claimed = true; true } else false
            }

            val ev = """{"tick": $tick, "type": "CLAIM", "rover": "$agName", "at": [$x, $y], "success": $success}"""
            logFile.appendText(ev + "\n")
            println(if (success) "$agName claimed mineral at [$x, $y]" else "$agName's claim on [$x, $y] rejected (already taken)")

            updatePercepts()
            return success
        }

        if (functor == "extract") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()
            val batteryLevel = batteryLevels.getOrDefault(agName, 100)

            val isSafe = synchronized(engineLock) {
                val rulesFile = File(dataDir, "rules.pl")
                val tempEngine = Prolog()
                tempEngine.setTheory(Theory(rulesFile.readText()))
                for ((hx, hy) in hazardList) {
                    tempEngine.addTheory(Theory("hazard($hx, $hy)."))
                }
                tempEngine.addTheory(Theory("is_harvester($agName)."))
                tempEngine.addTheory(Theory("battery_level($agName, $batteryLevel)."))
                tempEngine.solve("safe_to_extract($agName, $x, $y).").isSuccess
            }

            if (!isSafe) {
                val ev = """{"tick": $tick, "type": "VIOLATION", "rover": "$agName", "attempted_to": [$x, $y], "action_type": "EXTRACT", "reason": "Battery level $batteryLevel is below the 20-unit safe threshold"}"""
                logFile.appendText(ev + "\n")
                println("GUARDRAIL ACTIVE: Extraction by $agName at [$x, $y] blocked — battery $batteryLevel < 20")
                return false
            }

            synchronized(resourceLock) {
                mineralStates.removeAll { it.x == x && it.y == y }
            }

            val ev = """{"tick": $tick, "type": "EXTRACT", "rover": "$agName", "at": [$x, $y]}"""
            logFile.appendText(ev + "\n")
            println("$agName extracted the mineral at [$x, $y]")

            updatePercepts()
            return true
        }

        if (functor == "mission_complete") {
            synchronized(engineLock) {
                if (::hazardTimer.isInitialized) {
                    hazardTimer.cancel()
                }
            }
            val ev = """{"tick": $tick, "type": "MISSION_COMPLETE", "rover": "$agName"}"""
            logFile.appendText(ev + "\n")
            println("Mission complete signal from $agName — sandstorm patrol halted.")
        }

        updatePercepts()
        return true
    }
}