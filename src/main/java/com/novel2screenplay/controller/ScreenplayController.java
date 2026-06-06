package com.novel2screenplay.controller;

import com.novel2screenplay.export.FountainExporter;
import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.pipeline.ConversionPipeline;
import com.novel2screenplay.pipeline.ConversionResult;
import com.novel2screenplay.refine.SceneRefinementService;
import com.novel2screenplay.validate.SceneValidator;
import com.novel2screenplay.validate.ValidationReport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 只负责：HTTP 入参/出参与格式选择，转调 ConversionPipeline，不含业务逻辑。
 * <p>
 * POST /api/screenplay/convert
 *   - 请求体 text/plain：小说全文
 *   - ?title=  可选，覆盖自动生成的剧名
 *   - ?style=  改编风格（默认 剧集；其余 电影/话剧/短剧/分镜 为单本形态）
 *   - ?format= yaml | fountain（默认 yaml）
 *   - ?episodes= 剧集分集的总集数（可选，缺省由模型按节奏自动决定；仅剧集形态生效）
 *   - 响应：剧本文本 + 响应头 X-Validation-Warnings（自检修复后残留问题数）
 */
@RestController
@RequestMapping("/api/screenplay")
public class ScreenplayController {

    private static final String HEADER_WARNINGS = "X-Validation-Warnings";

    private final ConversionPipeline pipeline;
    private final YamlExporter yamlExporter;
    private final FountainExporter fountainExporter;
    private final SceneRefinementService refinementService;
    private final SceneValidator sceneValidator;

    public ScreenplayController(ConversionPipeline pipeline,
                                YamlExporter yamlExporter,
                                FountainExporter fountainExporter,
                                SceneRefinementService refinementService,
                                SceneValidator sceneValidator) {
        this.pipeline = pipeline;
        this.yamlExporter = yamlExporter;
        this.fountainExporter = fountainExporter;
        this.refinementService = refinementService;
        this.sceneValidator = sceneValidator;
    }

    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convert(
            @RequestBody String novelText,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "剧集") String style,
            @RequestParam(defaultValue = "yaml") String format,
            @RequestParam(required = false) Integer episodes) {

        if (novelText == null || novelText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小说文本不能为空");
        }

        ConversionResult result = pipeline.convert(novelText, style, episodes);
        Screenplay screenplay = applyTitleOverride(result.screenplay(), title);

        boolean fountain = "fountain".equalsIgnoreCase(format);
        String body = fountain
                ? fountainExporter.toFountain(screenplay)
                : yamlExporter.toYaml(screenplay);
        MediaType contentType = fountain
                ? new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8)
                : new MediaType("text", "yaml", java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HEADER_WARNINGS, String.valueOf(result.report().count()))
                .body(body);
    }

    /**
     * 交互式精修：作者拿到初稿后，对某一场提出修改指令做局部重生成。
     * JSON 进出：{ scene, characters, instruction } → 精修后的 scene。
     * 响应头 X-Validation-Warnings 暴露精修结果的体检残留问题数。
     */
    @PostMapping(value = "/refine",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Scene> refine(@RequestBody RefineRequest request) {
        if (request == null || request.scene() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "待精修的场景不能为空");
        }
        List<Character> characters = request.characters() == null ? List.of() : request.characters();

        Scene refined = refinementService.refine(request.scene(), characters, request.instruction());

        ValidationReport report = sceneValidator.validateScene(refined, characters);

        return ResponseEntity.ok()
                .header(HEADER_WARNINGS, String.valueOf(report.count()))
                .body(refined);
    }

    /** 调用方显式传 title 时覆盖自动生成的剧名。 */
    private Screenplay applyTitleOverride(Screenplay sp, String title) {
        if (title == null || title.isBlank()) {
            return sp;
        }
        return new Screenplay(sp.meta(), title.strip(), sp.logline(), sp.style(),
                sp.characters(), sp.episodes(), sp.scenes());
    }
}
