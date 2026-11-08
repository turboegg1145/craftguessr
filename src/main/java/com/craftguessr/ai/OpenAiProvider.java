package com.craftguessr.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * OpenAI Chat Completions adapter.
 *
 * <p>Works with api.openai.com, DeepSeek, OpenRouter, SiliconFlow, vLLM and any other
 * {@code /v1/chat/completions} compatible gateway. Images are sent as {@code image_url} data URIs.
 */
public final class OpenAiProvider implements AiProvider {

    @Override
    public String id() {
        return "openai";
    }

    public static JsonObject buildBody(AiRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model());
        body.addProperty("max_tokens", request.maxOutputTokens());
        if (request.temperature() >= 0) {
            body.addProperty("temperature", request.temperature());
        }
        if (request.jsonOnly()) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            body.add("response_format", format);
        }

        JsonArray messages = new JsonArray();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", request.systemPrompt());
            messages.add(system);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        JsonArray content = new JsonArray();
        content.add(AiHttp.textPart(request.userPrompt()));
        for (AiRequest.ImagePart image : request.images()) {
            content.add(AiHttp.imageUrlPart(image.mimeType(), image.base64()));
        }
        user.add("content", content);
        messages.add(user);
        body.add("messages", messages);
        return body;
    }

    @Override
    public AiResponse complete(AiConnection connection, AiRequest request) throws AiException {
        JsonObject body = buildBody(request);
        JsonObject response = AiHttp.postJson(
                connection.baseUrl() + "/v1/chat/completions",
                body,
                connection.timeout(),
                "Authorization", "Bearer " + connection.apiKey());

        JsonObject choice = firstChoice(response);
        if (choice == null) {
            throw new AiException("no choices in response: " + AiHttp.truncate(response.toString(), 400), 200, null);
        }
        String text = extractText(choice);
        JsonObject usage = AiHttp.at(response, "usage");
        int prompt = AiHttp.intAt(usage, "prompt_tokens", 0);
        int completion = AiHttp.intAt(usage, "completion_tokens", 0);
        return new AiResponse(text, prompt, completion, 200, response.toString());
    }

    private static JsonObject firstChoice(JsonObject response) {
        if (response == null) {
            return null;
        }
        var choices = response.get("choices");
        if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().isEmpty()) {
            return null;
        }
        var first = choices.getAsJsonArray().get(0);
        return first.isJsonObject() ? first.getAsJsonObject() : null;
    }

    static String extractText(JsonObject choice) {
        JsonObject message = choice.getAsJsonObject("message");
        if (message == null) {
            return "";
        }
        var content = message.get("content");
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (var part : content.getAsJsonArray()) {
                if (part.isJsonObject()) {
                    var t = part.getAsJsonObject().get("text");
                    if (t != null && t.isJsonPrimitive()) {
                        sb.append(t.getAsString());
                    }
                }
            }
            return sb.toString();
        }
        return content.toString();
    }
}
