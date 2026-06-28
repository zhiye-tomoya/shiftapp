package com.example.shiftapp.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

/**
 * Logs which datasource the app actually resolved at startup.
 *
 * This is a deployment aid: on Railway/Neon a "Connection refused" almost
 * always means the container fell back to the localhost default because the
 * SPRING_DATASOURCE_URL environment variable wasn't injected. Printing the
 * resolved host (never the password) makes that obvious in the deploy logs.
 */
@Configuration
class DatasourceStartupLogger(
    @Value("\${spring.datasource.url}") private val datasourceUrl: String,
    @Value("\${spring.datasource.username:}") private val datasourceUsername: String,
) {
    private val log = LoggerFactory.getLogger(DatasourceStartupLogger::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun logResolvedDatasource() {
        log.info("[startup] Resolved spring.datasource.url = {}", sanitize(datasourceUrl))
        log.info("[startup] Resolved spring.datasource.username = {}", datasourceUsername)
        if (datasourceUrl.contains("localhost") || datasourceUrl.contains("127.0.0.1")) {
            log.warn(
                "[startup] Datasource is pointing at LOCALHOST. If this is a deployed " +
                    "environment, the SPRING_DATASOURCE_URL env var is NOT being injected " +
                    "— check the Railway service Variables and redeploy."
            )
        }
    }

    /** Strip any embedded credentials from a JDBC URL before logging. */
    private fun sanitize(url: String): String =
        url.replace(Regex("://[^@/]+@"), "://****@")
}
