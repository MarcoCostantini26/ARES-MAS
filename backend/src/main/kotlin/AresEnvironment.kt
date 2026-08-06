import com.google.gson.Gson
import jason.asSyntax.*
import jason.environment.Environment
import java.io.File
import java.nio.file.Paths

data class ScenarioData(val grid: GridData, val entities: List<EntityData>)
data class GridData(val width: Int, val height: Int)
data class EntityData(val id: String, val type: String, val position: List<Int>)

class AresEnvironment : Environment() {
    
    private lateinit var logFile: File
    private var tick = 0
    private lateinit var scenario: ScenarioData
    private var isScenarioLoaded = false

    override fun init(args: Array<String>) {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        val dataDir = if (workingDir.endsWith("backend")) File(workingDir, "../data") else File(workingDir, "data")
        logFile = File(dataDir, "events.jsonl")
        logFile.writeText("") 

        val scenarioFile = File(dataDir, "scenario.json")
        if (scenarioFile.exists()) {
            scenario = Gson().fromJson(scenarioFile.readText(), ScenarioData::class.java)
            isScenarioLoaded = true
            println("🪐 Scenario loaded: ${scenario.entities.size} entities found.")
            updatePercepts()
        } else {
            println("ERROR: Scenario.json file not found!")
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
        val functor = action.functor 
        
        if (functor == "move") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()
            
            val ev = """{"tick": $tick, "type": "MOVE", "rover": "$agName", "to": [$x, $y]}"""
            logFile.appendText(ev + "\n")
            println("Action performed by $agName: move a [$x, $y]")
        }
        
        if (functor == "log_cnp") {
            val msgType = action.getTerm(0).toString() 
            val content = action.getTerm(1).toString() 
            val sender = action.getTerm(2).toString()
            val receiver = action.getTerm(3).toString()
            
            val ev = """{"tick": $tick, "type": "NEGOTIATION", "msg_type": "$msgType", "content": "$content", "sender": "$sender", "receiver": "$receiver"}"""
            logFile.appendText(ev + "\n")
        }
        
        updatePercepts() 
        return true 
    }
}