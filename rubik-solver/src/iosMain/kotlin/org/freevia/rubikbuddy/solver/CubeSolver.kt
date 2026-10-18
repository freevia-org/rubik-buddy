package org.freevia.rubikbuddy.solver

import org.freevia.rubikbuddy.model.CubeState
import org.freevia.rubikbuddy.model.CubeValidator
import org.freevia.rubikbuddy.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import min2phase.min2phase_c_init
import min2phase.min2phase_c_solve
import platform.Foundation.NSRecursiveLock

@OptIn(ExperimentalForeignApi::class)
actual object CubeSolver {
    // The upstream solver has shared tables and the C wrapper owns a shared result buffer.
    private val lock = NSRecursiveLock()
    private var initialized = false

    private inline fun <T> serialized(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }

    actual fun initialize() = serialized {
        if (!initialized) {
            min2phase_c_init()
            initialized = true
        }
    }

    actual suspend fun solve(state: CubeState, timeoutMs: Long): SolveResult = withContext(Dispatchers.Default) {
        val validation = CubeValidator.validate(state)
        if (validation is CubeValidator.ValidationResult.Invalid) return@withContext SolveResult.Error(validation.reason)
        if (state.isSolved()) return@withContext SolveResult.Success(emptyList(), 0)
        withTimeoutOrNull(timeoutMs) {
            val context = currentCoroutineContext()
            context.ensureActive()
            val result = serialized {
                context.ensureActive()
                initialize()
                // Copy before unlocking: the next native call can overwrite the C string.
                min2phase_c_solve(state.toFaceletString(), 21, 1000, 0, 0)?.toKString()
            }
            // Native search is bounded by probes, not preemptible by coroutine cancellation.
            context.ensureActive()
            when {
                result == null -> SolveResult.Error("No result from solver")
                result.startsWith("Error") -> SolveResult.Error(result)
                else -> {
                    val moves = Move.parseSequence(result)
                    if (!state.applyMoves(moves).isSolved()) SolveResult.Error("Solver returned an invalid solution")
                    else SolveResult.Success(moves, moves.size)
                }
            }
        } ?: SolveResult.Error("Solver timed out after ${timeoutMs / 1000}s")
    }
}
