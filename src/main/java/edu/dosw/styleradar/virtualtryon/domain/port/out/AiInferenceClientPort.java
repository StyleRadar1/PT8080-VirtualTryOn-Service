package edu.dosw.styleradar.virtualtryon.domain.port.out;

import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;

import java.util.Optional;

/**
 * Puerto de salida (Driven Port) para la comunicación con el motor de inferencia externa FASHN AI (SR-RF-25).
 */
public interface AiInferenceClientPort {

    Optional<byte[]> generateTryOnRender(String modelImageUrl, String garmentImageUrl, GarmentCategory category);

    JobStatus queryExternalJobStatus(String externalJobId);
}

