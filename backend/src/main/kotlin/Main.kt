import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Paths
import java.util.Random

fun main() {
    val currentDir = Paths.get("").toAbsolutePath().toString()
    val dataDir = if (currentDir.endsWith("backend")) File(currentDir, "../data") else File(currentDir, "data")
    
    val scenarioFile = File(dataDir, "scenario.json")
    val logFile = File(dataDir, "events.jsonl")

    if (!scenarioFile.exists()) {
        println("Error: scenario.json not found")
        return
    }
    
    val gson = Gson()
    val scenario = gson.fromJson(scenarioFile.readText(), JsonObject::class.java)
    val gridWidth = scenario.getAsJsonObject("grid").get("width").asInt
    val gridHeight = scenario.getAsJsonObject("grid").get("height").asInt
    
    val rovers = scenario.getAsJsonArray("rovers").map { it.asJsonObject.get("id").asString }
    val hazards = scenario.getAsJsonArray("hazards").map { it.asJsonObject.get("type").asString }
    
    println("Scenario upload: Grid ${gridWidth}x${gridHeight}, Rovers: $rovers, Hazards: $hazards")
    
    logFile.writeText("") 
    val random = Random()
    
    println("ARES-MAS Mock Simulator Started...")
    
    for (tick in 1..20) {
        Thread.sleep(1000) 
        
        for (rover in rovers) {
            val x = random.nextInt(gridWidth)
            val y = random.nextInt(gridHeight)
            val ev = """{"tick": $tick, "type": "MOVE", "rover": "$rover", "to": [$x, $y]}"""
            logFile.appendText(ev + "\n")
        }
        
        for (hazard in hazards) {
            val x = random.nextInt(gridWidth)
            val y = random.nextInt(gridHeight)
            val ev = """{"tick": $tick, "type": "HAZARD_MOVE", "hazard": "$hazard", "to": [$x, $y]}"""
            logFile.appendText(ev + "\n")
        }
        
        println("Completed Tick $tick.")
    }
    println("Simulation Ended.")
}