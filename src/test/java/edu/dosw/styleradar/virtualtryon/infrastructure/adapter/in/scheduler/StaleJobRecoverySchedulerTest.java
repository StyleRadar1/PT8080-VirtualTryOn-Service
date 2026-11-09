package edu.dosw.styleradar.virtualtryon.infrastructure.adapter.in.scheduler;

import edu.dosw.styleradar.virtualtryon.application.service.ProcessTryOnJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleJobRecoverySchedulerTest {

    @Mock
    private ProcessTryOnJobService processTryOnJobService;

    @Test
    @DisplayName("sweepStaleProcessingJobs: Debe invocar el servicio de recuperación con el Duration configurado")
    void shouldInvokeSweepStaleProcessingJobsWithConfiguredDuration() {
        when(processTryOnJobService.sweepStaleProcessingJobs(any(Duration.class))).thenReturn(2);

        StaleJobRecoveryScheduler scheduler = new StaleJobRecoveryScheduler(processTryOnJobService, 15);
        scheduler.sweepStaleProcessingJobs();

        verify(processTryOnJobService).sweepStaleProcessingJobs(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("sweepStaleProcessingJobs: Debe tolerar errores del servicio sin propagar excepción para no tumbar el scheduler")
    void shouldTolerateServiceExceptionsGracefully() {
        when(processTryOnJobService.sweepStaleProcessingJobs(any(Duration.class)))
                .thenThrow(new RuntimeException("Database timeout during stale jobs sweep"));

        StaleJobRecoveryScheduler scheduler = new StaleJobRecoveryScheduler(processTryOnJobService, 10);

        assertThatCode(scheduler::sweepStaleProcessingJobs).doesNotThrowAnyException();
        verify(processTryOnJobService).sweepStaleProcessingJobs(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("Constructor: Debe asegurar un valor mínimo de 1 minuto si se configura 0 o negativo")
    void shouldEnforceMinimumDurationOfOneMinute() {
        when(processTryOnJobService.sweepStaleProcessingJobs(any(Duration.class))).thenReturn(0);

        StaleJobRecoveryScheduler scheduler = new StaleJobRecoveryScheduler(processTryOnJobService, 0);
        scheduler.sweepStaleProcessingJobs();

        verify(processTryOnJobService).sweepStaleProcessingJobs(Duration.ofMinutes(1));
    }
}

