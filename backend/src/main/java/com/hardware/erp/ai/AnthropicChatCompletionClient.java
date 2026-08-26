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
 * Talks to Anthropic's Messages API directly over java.net.http - no SDK
 * dependency added for one endpoint. Handles the tool-use round trip
 * internally: the caller gets back a plain final answer, never sees the
 * wire protocol.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
public class AnthropicChatCompletionClient implements ChatCompletionClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_TOKENS = 1024;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.anthropic-api-key:}")
    private String apiKey;

    @Value("${app.ai.anthropic-model:claude-sonnet-5}")
    private String model;

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String chat(String systemPrompt, List<AiChatMessage> history, String userMessage, List<AiTool> availableTools) {
        Map<String, AiTool> toolsByName = availableTools.stream()
                .collect(java.util.stream.Collectors.toMap(AiTool::name, t -> t));

        ArrayNode messages = objectMapper.createArrayNode();
        for (AiChatMessage turn : history) {
            messages.add(textMessage(turn.role(), turn.content()));
        }
        messages.add(textMessage("user", userMessage));

        ArrayNode toolDefinitions = buildToolDefinitions(availableTools);

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode response;
            try {
                response = callApi(systemPrompt, messages, toolDefinitions);
            } catch (Exception e) {
                log.error("Anthropic API call failed", e);
                return "Sorry, I couldn't reach the AI service right now. Please try again in a moment.";
            }

            JsonNode content = response.path("content");
            String stopReason = response.path("stop_reason").asText("");

            if (!"tool_use".equals(stopReason)) {
                return extractText(content);
            }

            // The assistant's own turn (including its tool_use blocks) must be replayed back verbatim.
            ObjectNode assistantTurn = objectMapper.createObjectNode();
            assistantTurn.put("role", "assistant");
            assistantTurn.set("content", content);
            messages.add(assistantTurn);

            ArrayNode toolResults = objectMapper.createArrayNode();
            for (JsonNode block : content) {
                if (!"tool_use".equals(block.path("type").asText())) {
                    continue;
                }
                String toolUseId = block.path("id").asText();
                String toolName = block.path("name").asText();
                Map<String, String> args = new java.util.HashMap<>();
                block.path("input").fields().forEachRemaining(entry -> args.put(entry.getKey(), entry.getValue().asText()));

                AiTool tool = toolsByName.get(toolName);
                String result = tool != null
                        ? safeExecute(tool, args)
                        : "Unknown tool: " + toolName;

                ObjectNode toolResult = objectMapper.createObjectNode();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", toolUseId);
                toolResult.put("content", result);
                toolResults.add(toolResult);
            }
            ObjectNode userTurn = objectMapper.createObjectNode();
            userTurn.put("role", "user");
            userTurn.set("content", toolResults);
            messages.add(userTurn);
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

    private JsonNode callApi(String systemPrompt, ArrayNode messages, ArrayNode tools) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.set("messages", messages);
        if (!tools.isEmpty()) {
            body.set("tools", tools);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode parsed = objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Anthropic API returned " + response.statusCode() + ": " + parsed.path("error").path("message").asText(response.body()));
        }
        return parsed;
    }

    private ArrayNode buildToolDefinitions(List<AiTool> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiTool tool : tools) {
            ObjectNode definition = objectMapper.createObjectNode();
            definition.put("name", tool.name());
            definition.put("description", tool.description());

            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = objectMapper.createObjectNode();
            ArrayNode required = objectMapper.createArrayNode();
            tool.parameters().forEach((paramName, paramDescription) -> {
                ObjectNode property = objectMapper.createObjectNode();
                property.put("type", "string");
                property.put("description", paramDescription);
                properties.set(paramName, property);
                required.add(paramName);
            });
            schema.set("properties", properties);
            schema.set("required", required);
            definition.set("input_schema", schema);

            array.add(definition);
        }
        return array;
    }

    private ObjectNode textMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        content.add(block);
        message.set("content", content);
        return message;
    }

    private String extractText(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(block.path("text").asText());
            }
        }
        return sb.length() == 0 ? "I don't have an answer for that." : sb.toString();
    }
}
