package com.craftguessr.ai;

/**
 * Prompt templates for the scan director.
 *
 * <p>Design goals: minimal output tokens (a single flat JSON object), strict "reject" behaviour for
 * boring/blocked spots, and an optional witty commentary used by the round reveal.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static final String SYSTEM = String.join("\n",
            "You are the location director of a Minecraft GeoGuessr game.",
            "You receive a 2x2 collage of first-person screenshots (north, east, south, west) taken from one",
            "candidate viewpoint, plus cheap server-side statistics about the spot.",
            "Decide whether the spot is a good guessable location for a human player.",
            "Good spots: recognisable builds, villages, roads, bridges, landmarks, distinctive terrain, signs,",
            "anything a player could reason about from a photo.",
            "Bad spots: solid walls, leaf or stone caves, tiny 1-block holes, endless flat farmland, anything",
            "where over 80% of every view is a single natural material, or where no landmark is visible.",
            "Always answer with a single JSON object and nothing else.");

    /** Token-saving instruction: one flat object, no prose, no markdown. */
    public static String userPrompt(HostContext context, boolean wantCommentary) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Candidate spot statistics:\n");
        sb.append(context.toPromptText());
        sb.append("\nReply with ONE compact JSON object using exactly these keys:\n");
        sb.append("{\"keep\":bool,\"yaw\":number,\"pitch\":number,\"difficulty\":\"EASY|MEDIUM|HARD\",");
        sb.append("\"title\":\"short location name (<=24 chars, player-facing)\"");
        if (wantCommentary) {
            sb.append(",\"commentary\":\"one witty reveal sentence (<=60 chars)\"");
        }
        sb.append("}\n");
        sb.append("Rules:\n");
        sb.append("- \"yaw\" is the best compass heading to face in degrees, Minecraft convention: ");
        sb.append("0=south(+Z), 90=west(-X), 180=north(-Z), 270=east(+X).\n");
        sb.append("- \"pitch\" is degrees, negative looks down: -90 straight down, 0 horizon, +90 straight up.\n");
        sb.append("- \"difficulty\" rates how hard it is to recognise the place: EASY (famous landmark), ");
        sb.append("MEDIUM, HARD (subtle terrain).\n");
        sb.append("- If the collage is unusable set \"keep\":false and still fill the remaining keys with placeholders.\n");
        sb.append("- No markdown, no explanation, no extra keys.\n");
        return sb.toString();
    }

    /** Ask the model to write commentary for an already accepted spot (optional second pass). */
    public static String commentaryPrompt(HostContext context, String title) {
        return "Spot title: " + title + "\n" + context.toPromptText()
                + "\nWrite ONE witty, playful Chinese sentence (<=60 chars) that teases this location for a game reveal. "
                + "Reply with a single JSON object: {\"commentary\":\"...\"}";
    }
}
