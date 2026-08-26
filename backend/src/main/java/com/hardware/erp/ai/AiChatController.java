package com.hardware.erp.ai;

import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant")
public class AiChatController {

    private final AiChatService aiChatService;

    /** Any authenticated user may ask - each answer is scoped to what that user's own permissions allow the assistant to look up. Gated by the Max subscription tier inside the service. */
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        List<AiChatMessage> history = request.history() != null ? request.history() : List.of();
        return ApiResponse.ok(new AiChatResponse(aiChatService.reply(history, request.message())));
    }
}
