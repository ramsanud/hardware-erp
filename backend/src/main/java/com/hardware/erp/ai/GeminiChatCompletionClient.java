package com.hardware.erp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hardware.erp.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Talks to Google's Gemini API (Generative Language API, generateContent)
 * directly over java.net.http - the free-tier provider (Google AI Studio
 * issues a key with no card required), selected by default via
 * app.ai.provider. Mirrors AnthropicChatCompletionClient's structure and
 * tool-use round-trip exactly; only the wire protocol differs (Gemini calls
 * the assistant role "model" not "assistant", and represents tool calls as
 * functionCall/functionResponse parts rather than Anthropic's tool_use
 * content blocks).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiChatCompletionClient implements ChatCompletionClient {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_TOOL_ROUNDS = 5;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.gemini-api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini-model:gemini-2.0-flash}")
    private String model;

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String chat(String systemPrompt, List<AiChatMessage> history, String userMessage, List<AiTool> availableTools) {
        Map<String, AiTool> toolsByName = availableTools.stream()
                .collect(java.util.stream.Collectors.toMap(AiTool::name, t -> t));

        ArrayNode contents = objectMapper.createArrayNode();
        for (AiChatMessage turn : history) {
            contents.add(textTurn("assistant".equals(turn.role()) ? "model" : "user", turn.content()));
        }
        contents.add(textTurn("user", userMessage));

        ArrayNode toolDeclarations = buildToolDeclarations(availableTools);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode response;
            try {
                response = callApi(systemPrompt, contents, toolDeclarations);
            } catch (Exception e) {
                log.error("Gemini API call failed", e);
                return "Sorry, I couldn't reach the AI service right now. Please try again in a moment.";
            }

            JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
            List<JsonNode> functionCalls = new java.util.ArrayList<>();
            for (JsonNode part : parts) {
                if (part.has("functionCall")) functionCalls.add(part.path("functionCall"));
            }

            if (functionCalls.isEmpty()) {
                return extractText(parts);
            }

            // The model's own turn (including its functionCall parts) must be replayed back verbatim.
            ObjectNode modelTurn = objectMapper.createObjectNode();
            modelTurn.put("role", "model");
            modelTurn.set("parts", parts);
            contents.add(modelTurn);

            ArrayNode functionResponseParts = objectMapper.createArrayNode();
            for (JsonNode call : functionCalls) {
                String toolName = call.path("name").asText();
                Map<String, String> args = new java.util.HashMap<>();
                call.path("args").fields().forEachRemaining(entry -> args.put(entry.getKey(), entry.getValue().asText()));

                AiTool tool = toolsByName.get(toolName);
                String result = tool != null ? safeExecute(tool, args) : "Unknown tool: " + toolName;

                ObjectNode functionResponse = objectMapper.createObjectNode();
                ObjectNode responseBody = objectMapper.createObjectNode();
                responseBody.put("result", result);
                functionResponse.put("name", toolName);
                functionResponse.set("response", responseBody);
                ObjectNode part = objectMapper.createObjectNode();
                part.set("functionResponse", functionResponse);
                functionResponseParts.add(part);
            }
            ObjectNode userTurn = objectMapper.createObjectNode();
            userTurn.put("role", "user");
            userTurn.set("parts", functionResponseParts);
            contents.add(userTurn);
        }

        return "I looked into that but couldn't finish within the allowed number of steps. Try asking a more specific question.";
    }

    private String safeExecute(AiTool tool, Map<String, String> args) {
        try {
            return tool.execute(args);
        } catch (Exception e) {
            log.error("AI tool '{}' failed", tool.name(), e);
            return "That lookup failed. Do not retry the same tool call again for this question.";
        }
    }

    private JsonNode callApi(String systemPrompt, ArrayNode contents, ArrayNode toolDeclarations) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode systemParts = objectMapper.createArrayNode();
        ObjectNode systemPart = objectMapper.createObjectNode();
        systemPart.put("text", systemPrompt);
        systemParts.add(systemPart);
        systemInstruction.set("parts", systemParts);
        body.set("system_instruction", systemInstruction);
        body.set("contents", contents);
        if (!toolDeclarations.isEmpty()) {
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode toolEntry = objectMapper.createObjectNode();
            toolEntry.set("functionDeclarations", toolDeclarations);
            tools.add(toolEntry);
            body.set("tools", tools);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + model + ":generateContent?key=" + apiKey))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode parsed = objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Gemini API returned " + response.statusCode() + ": "
                    + parsed.path("error").path("message").asText(response.body()));
        }
        return parsed;
    }

    private ArrayNode buildToolDeclarations(List<AiTool> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiTool tool : tools) {
            ObjectNode declaration = objectMapper.createObjectNode();
            declaration.put("name", tool.name());
            declaration.put("description", tool.description());

            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "OBJECT");
            ObjectNode properties = objectMapper.createObjectNode();
            ArrayNode required = objectMapper.createArrayNode();
            tool.parameters().forEach((paramName, paramDescription) -> {
                ObjectNode property = objectMapper.createObjectNode();
                property.put("type", "STRING");
                property.put("description", paramDescription);
                properties.set(paramName, property);
                required.add(paramName);
            });
            schema.set("properties", properties);
            schema.set("required", required);
            declaration.set("parameters", schema);

            array.add(declaration);
        }
        return array;
    }

    private ObjectNode textTurn(String role, String text) {
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", role);
        ArrayNode parts = objectMapper.createArrayNode();
        ObjectNode part = objectMapper.createObjectNode();
        part.put("text", text);
        parts.add(part);
        turn.set("parts", parts);
        return turn;
    }

    private String extractText(JsonNode parts) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(part.path("text").asText());
            }
        }
        return sb.length() == 0 ? "I don't have an answer for that." : sb.toString();
    }
}
