package com.craftguessr.ai;

import java.util.List;

/**
 * Protocol-agnostic request for a single multimodal "judge this Minecraft screenshot" call.
 *
 * <p>One request carries exactly one image (the 2x2 collage produced by the ghost camera) plus the
 * textual host context gathered by the chunk scanner. Implementations translate this into the wire
 * format of their respective API.
 */
public record AiRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        List<ImagePart> images,
        int maxOutputTokens,
        double temperature,
        boolean jsonOnly
) {
    public AiRequest {
        images = List.copyOf(images);
    }

    /** One base64 encoded image. */
    public record ImagePart(String mimeType, String base64) {
        public static ImagePart jpeg(byte[] bytes) {
            return new ImagePart("image/jpeg", java.util.Base64.getEncoder().encodeToString(bytes));
        }

        public static ImagePart png(byte[] bytes) {
            return new ImagePart("image/png", java.util.Base64.getEncoder().encodeToString(bytes));
        }

        public String dataUri() {
            return "data:" + mimeType + ";base64," + base64;
        }
    }
}
