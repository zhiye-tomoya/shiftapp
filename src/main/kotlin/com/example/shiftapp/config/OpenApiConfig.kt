package com.example.shiftapp.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger configuration.
 *
 * This generates interactive API documentation at:
 * http://localhost:8080/swagger-ui/index.html
 *
 * Features:
 * - List of all endpoints with descriptions
 * - Request/response examples
 * - Try-it-out functionality (test endpoints directly from browser!)
 * - JWT authentication support (click "Authorize" button)
 *
 * This is invaluable for:
 * - Frontend developers integrating with your API
 * - Testing endpoints without writing curl commands
 * - Documenting your API for other teams
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            // API metadata
            .info(
                Info()
                    .title("Shift Swap API")
                    .version("1.0")
                    .description("""
                        REST API for managing shift swaps between staff members.

                        **Features:**
                        - User authentication with JWT tokens
                        - Role-based access control (ADMIN vs STAFF)
                        - Shift management (create, submit, approve, reject)
                        - 2-step approval workflow for shift swaps

                        **Authentication:**
                        1. Register or login to get a JWT token
                        2. Click "Authorize" button (top right)
                        3. Enter: Bearer <your-token>
                        4. Now you can test protected endpoints!
                    """.trimIndent())
            )
            // Add security requirement for protected endpoints
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            // Define the security scheme (JWT Bearer token)
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Enter JWT token obtained from /api/auth/login or /api/auth/register")
                    )
            )
    }
}
