package pl.watchme.logging

import org.slf4j.LoggerFactory

class CrashLogger(
    private val log: (String, Throwable) -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        log("FATAL uncaught exception in thread ${thread.name}", error)
        previous?.uncaughtException(thread, error)
    }

    companion object {
        fun install() {
            val logger = LoggerFactory.getLogger("Crash")
            Thread.setDefaultUncaughtExceptionHandler(
                CrashLogger(
                    log = { message, error -> logger.error(message, error) },
                    previous = Thread.getDefaultUncaughtExceptionHandler(),
                ),
            )
        }
    }
}
