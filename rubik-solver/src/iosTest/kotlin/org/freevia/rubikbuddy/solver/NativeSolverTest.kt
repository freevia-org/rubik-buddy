package org.freevia.rubikbuddy.solver

import kotlinx.coroutines.*
import org.freevia.rubikbuddy.model.*
import kotlin.test.*

class NativeSolverTest {
    @Test fun solvesScrambledCube() = runBlocking {
        for (scramble in listOf("R U F", "R U R' U' F2 D L2 B U2")) {
            val cube = CubeState.solved().applyMoves(Move.parseSequence(scramble))
            val result = CubeSolver.solve(cube)
            assertIs<SolveResult.Success>(result)
            assertTrue(cube.applyMoves(result.moves).isSolved())
        }
    }
    @Test fun simultaneousSolvesKeepTheirOwnResult() = runBlocking {
        listOf("R U F", "L D B", "F2 R2 U2").map { scramble ->
            async(Dispatchers.Default) {
                CubeSolver.initialize()
                val cube = CubeState.solved().applyMoves(Move.parseSequence(scramble))
                val result = CubeSolver.solve(cube)
                assertIs<SolveResult.Success>(result)
                assertTrue(cube.applyMoves(result.moves).isSolved())
            }
        }.awaitAll()
        Unit
    }
    @Test fun rejectsUnscannedAndInvalidCubes() = runBlocking {
        assertIs<SolveResult.Error>(CubeSolver.solve(CubeState.fromFacelets(IntArray(54) { -1 })))
        assertIs<SolveResult.Error>(CubeSolver.solve(CubeState.fromFacelets(IntArray(54) { 0 })))
        assertEquals(0, (CubeSolver.solve(CubeState.solved()) as SolveResult.Success).moveCount)
    }
}
