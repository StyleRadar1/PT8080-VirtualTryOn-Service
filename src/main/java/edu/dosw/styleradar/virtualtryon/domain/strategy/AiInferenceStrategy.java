package edu.dosw.styleradar.virtualtryon.domain.strategy;

import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;

/**
 * Interfaz del patrón GoF Strategy para el motor de inferencia de prueba virtual (SR-RF-25).
 * Permite intercambiar en caliente entre el proveedor SaaS externo (FASHN AI) y la estrategia
 * de composición gráfica local (MockAiStrategy) sin acoplar el dominio.
 */
public interface AiInferenceStrategy {

    SasSignedUrl submitTryOn(TryOnJob job);

    JobStatus checkStatus(String externalJobId);

    boolean supportsProvider(AiProvider provider);
}

