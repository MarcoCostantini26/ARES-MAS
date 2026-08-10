package ares

import com.google.gson.Gson
import jason.asSyntax.*
import jason.environment.Environment
import alice.tuprolog.Prolog
import alice.tuprolog.Theory
import java.io.File
import java.nio.file.Paths

data class ScenarioData(val grid: GridData, val entities: List<EntityData>)
data class GridData(val width: Int, val height: Int)
data class EntityData(val id: String, val type: String, val position: List<Int>)

class AresEnvironment : Environment() {
    
    companion object {
        var gridWidth = 10
        var gridHeight = 10
        var hazardList = listOf<Pair<Int, Int>>()
        lateinit var logFile: File
        var globalTick = 0 

        fun logPlanningEvent(agName: String, plan: List<String>) {
            val ev = """{"tick": $globalTick, "type": "PLANNING", "rover": "$agName", "plan": ${Gson().toJson(plan)}}"""
            logFile.appendText(ev + "\n")
        }
    }

    private lateinit var instanceLogFile: File
    private var tick = 0
    private lateinit var scenario: ScenarioData
    private var isScenarioLoaded = false
    private val prologEngine = Prolog()

    override fun init(args: Array<String>) {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        val dataDir = if (workingDir.endsWith("backend")) File(workingDir, "../data") else File(workingDir, "data")
        
        instanceLogFile = File(dataDir, "events.jsonl")
        instanceLogFile.writeText("") 
        
        logFile = instanceLogFile

        val scenarioFile = File(dataDir, "scenario.json")
        if (scenarioFile.exists()) {
            scenario = Gson().fromJson(scenarioFile.readText(), ScenarioData::class.java)
            isScenarioLoaded = true
            
            gridWidth = scenario.grid.width
            gridHeight = scenario.grid.height
            hazardList = scenario.entities.filter { it.type == "HAZARD" }.map { Pair(it.position[0], it.position[1]) }
            
            println("🪐 Scenario loaded: ${scenario.entities.size} entities found.")
            
            val rulesFile = File(dataDir, "rules.pl")
            if (rulesFile.exists()) {
                prologEngine.setTheory(Theory(rulesFile.readText()))
                for (hazard in hazardList) {
                    prologEngine.addTheory(Theory("hazard(${hazard.first}, ${hazard.second})."))
                }
                println("tuProlog Guardrail Initialized successfully.")
            }
            updatePercepts()
        }
    }

    private fun updatePercepts() {
        if (!isScenarioLoaded) return 
        clearPercepts() 
        for (entity in scenario.entities) {
            val x = entity.position[0]
            val y = entity.position[1]
            when (entity.type) {
                "MINERAL" -> addPercept(Literal.parseLiteral("mineral($x, $y)"))
                "HAZARD" -> addPercept(Literal.parseLiteral("hazard($x, $y, \"sandstorm\")"))
            }
        }
    }

    override fun executeAction(agName: String, action: Structure): Boolean {
        tick++
        globalTick = tick
        
        val functor = action.functor 
        
        if (functor == "move") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()
            
            val query = "safe_to_move($x, $y)."
            val solveInfo = prologEngine.solve(query)
            
            if (solveInfo.isSuccess) {
                val ev = """{"tick": $tick, "type": "MOVE", "rover": "$agName", "to": [$x, $y]}"""
                instanceLogFile.appendText(ev + "\n")
                println("Action executed by $agName: move to [$x, $y]")
            } else {
                val ev = """{"tick": $tick, "type": "VIOLATION", "rover": "$agName", "attempted_to": [$x, $y], "reason": "unsafe_move"}"""
                instanceLogFile.appendText(ev + "\n")
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
            instanceLogFile.appendText(ev + "\n")
        }
        
        updatePercepts() 
        return true 
    }
}