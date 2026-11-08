package edu.dosw.styleradar.virtualtryon.domain.strategy;

import edu.dosw.styleradar.virtualtryon.domain.model.AiProvider;
import edu.dosw.styleradar.virtualtryon.domain.model.GarmentCategory;
import edu.dosw.styleradar.virtualtryon.domain.model.JobStatus;
import edu.dosw.styleradar.virtualtryon.domain.model.TryOnJob;
import edu.dosw.styleradar.virtualtryon.domain.port.out.BlobStoragePort;
import edu.dosw.styleradar.virtualtryon.domain.valueobject.SasSignedUrl;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Estrategia concreta (Patrón Strategy) de composición gráfica local con Graphics2D y AlphaComposite
 * (migrada de ComposicionProbador de StyleRadar-Backend) que genera el render PNG superponiendo la
 * prenda en la región anatómica correspondiente (torso o piernas) y lo almacena en Azure Blob Storage (SR-RF-25).
 */
public class MockAiStrategy implements AiInferenceStrategy {

    static final int MAX_SIDE_PX = 1600;
    static final double RELATIVE_GARMENT_WIDTH = 0.55;
    static final double TORSO_VERTICAL_OFFSET = 0.22;
    static final double LEGS_VERTICAL_OFFSET = 0.50;
    static final float GARMENT_OPACITY = 0.90f;

    private final BlobStoragePort blobStoragePort;

    public MockAiStrategy(BlobStoragePort blobStoragePort) {
        this.blobStoragePort = blobStoragePort;
    }

    @Override
    public SasSignedUrl submitTryOn(TryOnJob job) {
        BufferedImage baseSilhouette = createDefaultSilhouetteCanvas(400, 600);
        BufferedImage garmentOverlay = createDefaultGarmentOverlay(220, 260, job.getGarmentCategory());
        byte[] renderedPng = composeOverlay(baseSilhouette, garmentOverlay, job.getGarmentCategory());
        return blobStoragePort.storeRenderedResult(job.getUserId(), job.getId(), renderedPng);
    }

    @Override
    public JobStatus checkStatus(String externalJobId) {
        return JobStatus.COMPLETED;
    }

    @Override
    public boolean supportsProvider(AiProvider provider) {
        return AiProvider.MOCK_LOCAL == provider;
    }

    public BufferedImage decodeImage(byte[] imageBytes) {
        try {
            return imageBytes == null ? null : ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException ex) {
            return null;
        }
    }

    public byte[] composeOverlay(BufferedImage userPhoto, BufferedImage garmentImage, GarmentCategory category) {
        BufferedImage boundedBase = boundDimensions(userPhoto);
        int garmentWidth = (int) Math.round(boundedBase.getWidth() * RELATIVE_GARMENT_WIDTH);
        int garmentHeight = Math.max(
                1,
                (int) Math.round((double) garmentImage.getHeight() * garmentWidth / garmentImage.getWidth())
        );
        int x = (boundedBase.getWidth() - garmentWidth) / 2;
        double verticalRatio = (category == GarmentCategory.BOTTOMS) ? LEGS_VERTICAL_OFFSET : TORSO_VERTICAL_OFFSET;
        int y = (int) Math.round(boundedBase.getHeight() * verticalRatio);

        BufferedImage output = new BufferedImage(
                boundedBase.getWidth(),
                boundedBase.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = output.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(boundedBase, 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, GARMENT_OPACITY));
            g.drawImage(garmentImage, x, y, garmentWidth, garmentHeight, null);
        } finally {
            g.dispose();
        }
        return encodePng(output);
    }

    static BufferedImage boundDimensions(BufferedImage photo) {
        int maxSide = Math.max(photo.getWidth(), photo.getHeight());
        if (maxSide <= MAX_SIDE_PX) {
            return photo;
        }
        double scale = (double) MAX_SIDE_PX / maxSide;
        int scaledWidth = (int) Math.round(photo.getWidth() * scale);
        int scaledHeight = (int) Math.round(photo.getHeight() * scale);
        BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(photo, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            g.dispose();
        }
        return resized;
    }

    private static BufferedImage createDefaultSilhouetteCanvas(int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(new Color(245, 245, 248));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(55, 65, 85));
            g.fillOval(width / 2 - 35, 45, 70, 75);
            g.fillRoundRect(width / 2 - 75, 130, 150, 220, 28, 28);
            g.fillRect(width / 2 - 55, 350, 48, 195);
            g.fillRect(width / 2 + 7, 350, 48, 195);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static BufferedImage createDefaultGarmentOverlay(int width, int height, GarmentCategory category) {
        BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = overlay.createGraphics();
        try {
            Color garmentColor = switch (category != null ? category : GarmentCategory.TOPS) {
                case BOTTOMS -> new Color(35, 75, 140, 230);
                case ONE_PIECES -> new Color(155, 45, 90, 230);
                default -> new Color(40, 110, 165, 230);
            };
            g.setColor(garmentColor);
            g.fillRoundRect(10, 10, width - 20, height - 20, 24, 24);
        } finally {
            g.dispose();
        }
        return overlay;
    }

    private static byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible codificar el resultado del probador virtual en PNG", ex);
        }
    }
}

