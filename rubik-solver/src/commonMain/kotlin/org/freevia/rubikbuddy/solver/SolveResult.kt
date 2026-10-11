package org.freevia.rubikbuddy.solver

import org.freevia.rubikbuddy.model.Move

sealed class SolveResult {
    data class Success(
        val moves: List<Move>,
        val moveCount: Int
    ) : SolveResult()

    data class Error(
        val reason: String
    ) : SolveResult()
}
