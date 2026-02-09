package org.ssm.flightradar.service.timeout

import kotlinx.coroutines.withTimeoutOrNull

object TimeoutRunner {
    suspend fun <T> run(timeoutMs: Long, block: suspend () -> T): T? {
        return withTimeoutOrNull(timeoutMs) { block() }
    }
}
