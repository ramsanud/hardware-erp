package com.hardware.erp.ai.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Spring collects every AiTool bean into this list automatically - adding a new tool is just adding a new @Component, no registry edit needed. */
@Component
@RequiredArgsConstructor
public class AiToolRegistry {

    private final List<AiTool> tools;

    /** Only the tools the caller actually holds the permission for - the assistant never even offers to look up something it can't show. */
    public List<AiTool> availableTo(Predicate<String> hasPermission) {
        return tools.stream()
                .filter(tool -> tool.requiredPermission() == null || hasPermission.test(tool.requiredPermission()))
                .toList();
    }

    public AiTool find(String name, List<AiTool> available) {
        return available.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    public Map<String, AiTool> asMap(List<AiTool> tools) {
        return tools.stream().collect(Collectors.toMap(AiTool::name, t -> t));
    }
}
