package com.clacjz.medical.rag.controller;

import com.clacjz.medical.rag.mapper.UserMapper;
import com.clacjz.medical.rag.model.dto.SysConfigDTO;
import com.clacjz.medical.rag.model.entity.User;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "Config API", description = "系统配置管理接口")
public class ConfigController {

    private final ConfigService configService;
    private final UserMapper userMapper;

    @GetMapping
    @Operation(summary = "获取所有配置", description = "获取所有系统配置")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<SysConfigDTO>>> getAllConfigs() {
        log.info("Get all configs");
        List<SysConfigDTO> configs = configService.getAllConfigs();
        return ResponseEntity.ok(Result.success(configs));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取配置", description = "获取指定分类下的所有配置项")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<SysConfigDTO>>> getConfigByCategory(
            @Parameter(description = "配置分类") @PathVariable String category) {
        log.info("Get config by category: {}", category);
        List<SysConfigDTO> configs = configService.getConfigByCategory(category);
        return ResponseEntity.ok(Result.success(configs));
    }

    @GetMapping("/{configKey}")
    @Operation(summary = "获取单个配置", description = "根据配置键获取单个配置项")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<SysConfigDTO>> getConfigByKey(
            @Parameter(description = "配置键") @PathVariable String configKey) {
        log.info("Get config by key: {}", configKey);
        SysConfigDTO config = configService.getConfigByKey(configKey);
        return ResponseEntity.ok(Result.success(config));
    }

    @PutMapping("/{configKey}")
    @Operation(summary = "更新单个配置", description = "更新指定配置项的值")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<SysConfigDTO>> updateConfig(
            @Parameter(description = "配置键") @PathVariable String configKey,
            @Parameter(description = "配置值") @RequestParam String value,
            @AuthenticationPrincipal UserDetails userDetails) {

        String operatorId = getOperatorId(userDetails);
        log.info("Update config: key={}, value={}, operator={}", configKey, value, operatorId);

        SysConfigDTO updated = configService.updateConfig(configKey, value, operatorId);
        return ResponseEntity.ok(Result.success("配置更新成功", updated));
    }

    @PutMapping("/category/{category}")
    @Operation(summary = "批量更新配置", description = "更新指定分类下的多个配置项")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Result<List<SysConfigDTO>>> batchUpdateConfig(
            @Parameter(description = "配置分类") @PathVariable String category,
            @RequestBody Map<String, String> values,
            @AuthenticationPrincipal UserDetails userDetails) {

        String operatorId = getOperatorId(userDetails);
        log.info("Batch update config: category={}, values={}, operator={}", category, values, operatorId);

        List<SysConfigDTO> updated = configService.updateConfigByCategory(category, values, operatorId);
        return ResponseEntity.ok(Result.success("配置批量更新成功", updated));
    }

    private String getOperatorId(UserDetails userDetails) {
        if (userDetails == null) {
            return "system";
        }
        User user = userMapper.selectByUsername(userDetails.getUsername());
        return user != null ? user.getId() : "system";
    }
}