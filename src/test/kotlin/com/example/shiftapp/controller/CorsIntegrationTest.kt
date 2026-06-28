package com.example.shiftapp.controller

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

/**
 * Verifies CORS is configured so a browser-based front-end on an allowed origin
 * can call the API with credentials (the refresh-token cookie).
 *
 * The allowed origin is overridden here to a Vercel-style host to prove the
 * env-driven `app.cors.allowed-origins` property is honored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = ["app.cors.allowed-origins=https://my-app.vercel.app,https://my-app-*.vercel.app"],
)
class CorsIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `preflight from an allowed Vercel origin is accepted with credentials`() {
        mockMvc.options("/api/shifts") {
            header(HttpHeaders.ORIGIN, "https://my-app.vercel.app")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        }.andExpect {
            status { isOk() }
            header {
                string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://my-app.vercel.app")
                string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            }
        }
    }

    @Test
    fun `preflight from a Vercel preview origin matches the wildcard pattern`() {
        mockMvc.options("/api/shifts") {
            header(HttpHeaders.ORIGIN, "https://my-app-git-feature.vercel.app")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        }.andExpect {
            status { isOk() }
            header {
                string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    "https://my-app-git-feature.vercel.app",
                )
            }
        }
    }

    @Test
    fun `preflight from a disallowed origin is rejected`() {
        mockMvc.options("/api/shifts") {
            header(HttpHeaders.ORIGIN, "https://evil.example.com")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `allowed methods are advertised on preflight`() {
        mockMvc.options("/api/shifts") {
            header(HttpHeaders.ORIGIN, "https://my-app.vercel.app")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        }.andExpect {
            status { isOk() }
            header {
                string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST"))
            }
        }
    }
}
