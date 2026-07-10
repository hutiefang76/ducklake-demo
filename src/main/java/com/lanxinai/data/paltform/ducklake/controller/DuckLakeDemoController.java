package com.lanxinai.data.paltform.ducklake.controller;

import com.lanxinai.data.paltform.ducklake.domain.DemoRecord;
import com.lanxinai.data.paltform.ducklake.dto.BatchOperationResponse;
import com.lanxinai.data.paltform.ducklake.dto.ScenarioResponse;
import com.lanxinai.data.paltform.ducklake.service.DuckLakeDemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ducklake")
@Tag(name = "DuckLake CRUD 演示", description = "使用四种 Java 数据访问方式操作同一张 DuckLake 测试表")
public class DuckLakeDemoController {

    private final DuckLakeDemoService service;

    public DuckLakeDemoController(DuckLakeDemoService service) {
        this.service = service;
    }

    @PostMapping("/table")
    @Operation(summary = "初始化测试表", description = "如果 schema 或测试表不存在则创建；重复调用是安全的。")
    @ApiResponse(responseCode = "200", description = "测试表已可用")
    public Map<String, Object> initializeTable() {
        return service.initializeTable();
    }

    @GetMapping("/{dao}/records")
    @Operation(summary = "查询测试数据", description = "按创建时间倒序返回指定数量的数据。")
    public List<DemoRecord> findAll(
            @Parameter(description = "DAO 类型：jdbc、jdbc-template、mybatis、jpa", example = "jdbc")
            @PathVariable String dao,
            @Parameter(description = "最多返回多少条，范围 1～10000", example = "20")
            @RequestParam(defaultValue = "100") int limit) {
        return service.findAll(dao, limit);
    }

    @GetMapping("/{dao}/count")
    @Operation(summary = "查询数据总条数")
    public Map<String, Object> count(
            @Parameter(description = "DAO 类型", example = "jdbc-template") @PathVariable String dao) {
        return Map.of("dao", dao, "count", service.count(dao));
    }

    @PostMapping("/{dao}/records")
    @Operation(summary = "新增 N 条数据", description = "自动生成一个 batchId，后续修改和删除只处理该批次。")
    public BatchOperationResponse insert(
            @Parameter(description = "DAO 类型", example = "mybatis") @PathVariable String dao,
            @Parameter(description = "新增条数，范围 1～1000", example = "10")
            @RequestParam(name = "n", defaultValue = "10") int numberOfRows) {
        return service.insert(dao, numberOfRows);
    }

    @PutMapping("/{dao}/records/{batchId}")
    @Operation(summary = "修改指定批次的前 N 条数据", description = "在 name 后追加 suffix，并把状态改为 UPDATED。")
    public BatchOperationResponse update(
            @Parameter(description = "DAO 类型", example = "jpa") @PathVariable String dao,
            @Parameter(description = "新增接口返回的 batchId") @PathVariable String batchId,
            @Parameter(description = "最多修改多少条", example = "5")
            @RequestParam(name = "n", defaultValue = "5") int numberOfRows,
            @Parameter(description = "追加到 name 的文本", example = "-changed")
            @RequestParam(defaultValue = "-updated") String suffix) {
        return service.update(dao, batchId, numberOfRows, suffix);
    }

    @DeleteMapping("/{dao}/records/{batchId}")
    @Operation(summary = "删除指定批次的前 N 条数据")
    public BatchOperationResponse delete(
            @Parameter(description = "DAO 类型", example = "jdbc") @PathVariable String dao,
            @Parameter(description = "新增接口返回的 batchId") @PathVariable String batchId,
            @Parameter(description = "最多删除多少条", example = "3")
            @RequestParam(name = "n", defaultValue = "5") int numberOfRows) {
        return service.delete(dao, batchId, numberOfRows);
    }

    @PostMapping("/{dao}/scenario")
    @Operation(summary = "一键执行完整 CRUD 场景",
            description = "依次新增、修改、删除并查询剩余数据，最适合在 Swagger UI 中直接查看效果。")
    public ScenarioResponse scenario(
            @Parameter(description = "DAO 类型", example = "jdbc-template") @PathVariable String dao,
            @Parameter(description = "新增条数", example = "10") @RequestParam(defaultValue = "10") int insertN,
            @Parameter(description = "修改条数", example = "5") @RequestParam(defaultValue = "5") int updateN,
            @Parameter(description = "删除条数", example = "3") @RequestParam(defaultValue = "3") int deleteN) {
        return service.scenario(dao, insertN, updateN, deleteN);
    }
}
