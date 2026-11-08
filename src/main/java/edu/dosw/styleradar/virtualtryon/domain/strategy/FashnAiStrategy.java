package edu.dosw.styleradar.virtualtryon.domain.strategy;

import edu.dosw.styleradar.virtualtryon.domain.exception.AiProviderTimeoutException;
import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.AiInferenceClientPort;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import java.util.Optional;

/**
 * Estrategia concreta (Patrón Strategy) que delega la inferencia generativa de prueba virtual
 * hacia FASHN AI API y almacena el render resultante en Azure Blob Storage con URL SAS sp=r (SR-RF-25).
 */
public class FashnAiStrategy implements AiInferenceStrategy {

    private final AiInferenceClientPort aiInferenceClientPort;
    private final BlobStoragePort blobStoragePort;
    private final MockAiStrategy fallbackStrategy;

    public FashnAiStrategy(
            AiInferenceClientPort aiInferenceClientPort,
            BlobStoragePort blobStoragePort,
            MockAiStrategy fallbackStrategy
    ) {
        this.aiInferenceClientPort = aiInferenceClientPort;
        this.blobStoragePort = blobStoragePort;
        this.fallbackStrategy = fallbackStrategy;
    }

    @Override
    public SasSignedUrl submitTryOn(TryOnJob job) {
        GarmentCategory category = job.getGarmentCategory() != null ? job.getGarmentCategory() : GarmentCategory.TOPS;
        String garmentReferenceUrl = "https://styleradar.dosw.edu/catalog/garments/" + job.getGarmentId().value() + ".png";
        Optional<byte[]> renderedBytes = aiInferenceClientPort.generateTryOnRender(
                job.getPhotoUrl(),
                garmentReferenceUrl,
                category
        );
        if (renderedBytes.isPresent() && renderedBytes.get().length > 0) {
            return blobStoragePort.storeRenderedResult(job.getUserId(), job.getId(), renderedBytes.get());
        }
        return fallbackStrategy.submitTryOn(job);
    }

    @Override
    public JobStatus checkStatus(String externalJobId) {
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new AiProviderTimeoutException("El identificador de ejecución externa en FASHN AI no puede ser vacío");
        }
        return aiInferenceClientPort.queryExternalJobStatus(externalJobId);
    }

    @Override
    public boolean supportsProvider(AiProvider provider) {
        return AiProvider.FASHN_AI == provider;
    }
}

