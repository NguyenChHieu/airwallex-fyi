package com.airwallexfyi.runtime

import com.airwallexfyi.admin.AdminTokenFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "airwallex-fyi.admin.token=test-admin-token",
        "spring.datasource.url=jdbc:h2:mem:health_controller_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    ],
)
@AutoConfigureMockMvc
class HealthControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {
    @Test
    fun `public health endpoint does not require admin token`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }

    @Test
    fun `admin health endpoint still requires admin token`() {
        mockMvc.perform(get("/admin/health"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            get("/admin/health")
                .header(AdminTokenFilter.ADMIN_TOKEN_HEADER, "test-admin-token"),
        )
            .andExpect(status().isOk)
    }
}
