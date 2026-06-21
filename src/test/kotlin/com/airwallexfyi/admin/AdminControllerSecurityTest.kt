package com.airwallexfyi.admin

import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    properties = [
        "airwallex-fyi.admin.token=test-admin-token",
        "spring.datasource.url=jdbc:h2:mem:admin_security_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
@AutoConfigureMockMvc
class AdminControllerSecurityTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @Test
    fun `missing admin token is rejected`() {
        mockMvc.perform(get("/admin/security-test"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `wrong admin token is rejected`() {
        mockMvc.perform(
            get("/admin/security-test")
                .header(AdminTokenFilter.ADMIN_TOKEN_HEADER, "wrong-token"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `configured admin token is accepted`() {
        mockMvc.perform(
            get("/admin/security-test")
                .header(AdminTokenFilter.ADMIN_TOKEN_HEADER, "test-admin-token"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `manual summarize endpoint requires admin token`() {
        mockMvc.perform(post("/admin/posts/${UUID.randomUUID()}/summarize"))
            .andExpect(status().isUnauthorized)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SecurityRouteConfig {
        @Bean
        fun securityProbeController(): SecurityProbeController = SecurityProbeController()
    }

    @RestController
    class SecurityProbeController {
        @GetMapping("/admin/security-test")
        fun probe(): Map<String, String> = mapOf("status" to "ok")
    }
}
