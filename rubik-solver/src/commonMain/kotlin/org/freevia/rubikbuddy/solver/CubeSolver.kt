package org.freevia.rubikbuddy.solver

import org.freevia.rubikbuddy.model.CubeState

expect object CubeSolver {
    fun initialize()
    suspend fun solve(state: CubeState, timeoutMs: Long = 5_000L): SolveResult
}
