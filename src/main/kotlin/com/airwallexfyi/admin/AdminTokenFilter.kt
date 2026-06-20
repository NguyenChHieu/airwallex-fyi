package com.airwallexfyi.admin

import com.airwallexfyi.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AdminTokenFilter(
    private val appProperties: AppProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return path != "/admin" && !path.startsWith("/admin/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val providedToken = request.getHeader(ADMIN_TOKEN_HEADER)
        val expectedToken = appProperties.admin.token

        if (providedToken == null || !tokensMatch(providedToken, expectedToken)) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = "application/json"
            response.writer.write("""{"error":"unauthorized"}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun tokensMatch(providedToken: String, expectedToken: String): Boolean {
        if (expectedToken.isBlank()) {
            return false
        }

        return MessageDigest.isEqual(
            providedToken.toByteArray(StandardCharsets.UTF_8),
            expectedToken.toByteArray(StandardCharsets.UTF_8),
        )
    }

    companion object {
        const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
    }
}