package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.rest.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("BUG-031: Preserva X-Correlation-Id y X-Request-Id entrantes en MDC y headers de respuesta")
    void shouldPreserveIncomingCorrelationAndRequestIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/try-on/quotas");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-abc-123");
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "req-xyz-999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcCorr = new AtomicReference<>();
        AtomicReference<String> mdcReq = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcCorr.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
            mdcReq.set(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertThat(mdcCorr.get()).isEqualTo("corr-abc-123");
        assertThat(mdcReq.get()).isEqualTo("req-xyz-999");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-xyz-999");
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("BUG-031: Genera X-Correlation-Id y X-Request-Id si faltan y limpia MDC al finalizar")
    void shouldGenerateCorrelationIdsWhenMissingAndUseRequestIdFallback() throws Exception {
        MockHttpServletRequest noHeadersReq = new MockHttpServletRequest("GET", "/api/v1/try-on/quotas");
        MockHttpServletResponse noHeadersRes = new MockHttpServletResponse();

        filter.doFilter(noHeadersReq, noHeadersRes, (req, res) -> {
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNotBlank();
            assertThat(MDC.get(CorrelationIdFilter.REQUEST_ID_MDC_KEY))
                    .isEqualTo(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
        });

        assertThat(noHeadersRes.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isNotBlank();
        assertThat(noHeadersRes.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(noHeadersRes.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        MockHttpServletRequest onlyReqId = new MockHttpServletRequest("GET", "/api/v1/try-on/quotas");
        onlyReqId.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "req-fallback-77");
        MockHttpServletResponse onlyReqRes = new MockHttpServletResponse();
        filter.doFilter(onlyReqId, onlyReqRes, (req, res) -> {
            assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("req-fallback-77");
        });
        assertThat(onlyReqRes.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("req-fallback-77");
        assertThat(onlyReqRes.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER)).isEqualTo("req-fallback-77");
    }
}
