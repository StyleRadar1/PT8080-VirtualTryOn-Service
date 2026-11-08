package edu.dosw.styleradar.virtualtryon.domain.model;

import edu.dosw.styleradar.virtualtryon.domain.valueobject.GarmentId;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Patrón GoF Builder para la construcción segura y validada del agregado raíz TryOnJob,
 * garantizando estado inicial QUEUED, photoPurged = false y marcas de tiempo en UTC (SR-RF-25).
 */
public class TryOnJobBuilder {

    private UUID id;
    private UserId userId;
    private GarmentId garmentId;
    private String photoUrl;
    private GarmentCategory garmentCategory = GarmentCategory.TOPS;
    private AiProvider aiProvider = AiProvider.FASHN_AI;

    public TryOnJobBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public TryOnJobBuilder withUserId(UserId userId) {
        this.userId = userId;
        return this;
    }

    public TryOnJobBuilder withGarmentId(GarmentId garmentId) {
        this.garmentId = garmentId;
        return this;
    }

    public TryOnJobBuilder withPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
        return this;
    }

    public TryOnJobBuilder withCategory(GarmentCategory category) {
        this.garmentCategory = category;
        return this;
    }

    public TryOnJobBuilder withProvider(AiProvider provider) {
        this.aiProvider = provider;
        return this;
    }

    public TryOnJob build() {
        Objects.requireNonNull(userId, "No se puede construir un TryOnJob sin UserId");
        Objects.requireNonNull(garmentId, "No se puede construir un TryOnJob sin GarmentId");
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new IllegalArgumentException("No se puede construir un TryOnJob sin photoUrl");
        }
        UUID resolvedId = id != null ? id : UUID.randomUUID();
        GarmentCategory resolvedCategory = garmentCategory != null ? garmentCategory : GarmentCategory.TOPS;
        AiProvider resolvedProvider = aiProvider != null ? aiProvider : AiProvider.FASHN_AI;
        Instant now = Instant.now();
        return new TryOnJob(
                resolvedId,
                userId,
                garmentId,
                photoUrl.trim(),
                resolvedCategory,
                JobStatus.QUEUED,
                null,
                resolvedProvider,
                null,
                null,
                false,
                now,
                now
        );
    }
}

