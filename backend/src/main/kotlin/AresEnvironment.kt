import jason.asSyntax.*
import jason.environment.Environment
import java.io.File
import java.nio.file.Paths

class AresEnvironment : Environment() {
    
    private lateinit var logFile: File
    private var tick = 0

    override fun init(args: Array<String>) {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        println("🪐 ARES-MAS Environment Initialized")
        
        val dataDir = if (workingDir.endsWith("backend")) File(workingDir, "../data") else File(workingDir, "data")
        
        logFile = File(dataDir, "events.jsonl")
        logFile.writeText("")
    }

    override fun executeAction(agName: String, action: Structure): Boolean {
        tick++
        val functor = action.functor
        
        if (functor == "move") {
            val x = (action.getTerm(0) as NumberTerm).solve().toInt()
            val y = (action.getTerm(1) as NumberTerm).solve().toInt()
            
            val ev = """{"tick": $tick, "type": "MOVE", "rover": "$agName", "to": [$x, $y]}"""
            logFile.appendText(ev + "\n")
            println("Azione eseguita da $agName: move a [$x, $y]")
        }
        
        return true 
    }
}