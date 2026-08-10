package strips

import ares.AresEnvironment
import jason.asSemantics.DefaultInternalAction
import jason.asSemantics.TransitionSystem
import jason.asSemantics.Unifier
import jason.asSyntax.*
import java.util.LinkedList

data class StripsAction(val name: Literal, val pre: Set<String>, val add: Set<String>, val del: Set<String>)

class plan_return : DefaultInternalAction() {

    override fun execute(ts: TransitionSystem, un: Unifier, args: Array<Term>): Boolean {
        val cx = (args[0] as NumberTerm).solve().toInt()
        val cy = (args[1] as NumberTerm).solve().toInt()
        val bx = (args[2] as NumberTerm).solve().toInt()
        val by = (args[3] as NumberTerm).solve().toInt()

        ts.logger.info("STRIPS Engine activated for route [$cx, $cy] to [$bx, $by]")

        val planList = computeStripsPlan(cx, cy, bx, by)

        return if (planList != null) {
            un.unifies(args[4], planList)
        } else {
            ts.logger.severe("STRIPS Failed: No safe path found!")
            false
        }
    }

    private fun computeStripsPlan(startX: Int, startY: Int, goalX: Int, goalY: Int): ListTerm? {
        val initialState = setOf("at($startX,$startY)")
        val goalState = "at($goalX,$goalY)"

        val queue = LinkedList<Pair<Set<String>, List<Literal>>>()
        val visited = mutableSetOf<Set<String>>()

        queue.add(Pair(initialState, emptyList()))
        visited.add(initialState)

        val width = AresEnvironment.gridWidth
        val height = AresEnvironment.gridHeight
        val hazards = AresEnvironment.hazardList

        while (queue.isNotEmpty()) {
            val (currentState, currentPlan) = queue.removeFirst()

            if (currentState.contains(goalState)) {
                val jasonList = ASSyntax.createList()
                currentPlan.forEach { jasonList.add(it) }
                return jasonList
            }

            val currentAt = currentState.first { it.startsWith("at(") }
            val parts = currentAt.removePrefix("at(").removeSuffix(")").split(",")
            val cx = parts[0].toInt()
            val cy = parts[1].toInt()

            val possibleMoves = listOf(Pair(cx, cy - 1), Pair(cx, cy + 1), Pair(cx - 1, cy), Pair(cx + 1, cy))

            for ((nx, ny) in possibleMoves) {
                if (nx in 0 until width && ny in 0 until height && !hazards.contains(Pair(nx, ny))) {
                    
                    val moveLiteral = ASSyntax.createLiteral("move", ASSyntax.createNumber(nx.toDouble()), ASSyntax.createNumber(ny.toDouble()))
                    
                    val action = StripsAction(
                        name = moveLiteral,
                        pre = setOf("at($cx,$cy)"),
                        add = setOf("at($nx,$ny)"),
                        del = setOf("at($cx,$cy)")
                    )

                    if (currentState.containsAll(action.pre)) {
                        val nextState = currentState.minus(action.del).plus(action.add)
                        if (!visited.contains(nextState)) {
                            visited.add(nextState)
                            queue.add(Pair(nextState, currentPlan + action.name))
                        }
                    }
                }
            }
        }
        return null 
    }
}