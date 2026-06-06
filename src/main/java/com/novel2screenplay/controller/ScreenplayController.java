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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 只负责：HTTP 入参/出参与格式选择，转调 ConversionPipeline，不含业务逻辑。
 * <p>
 * POST /api/screenplay/convert（按 Content-Type 路由，两种入参等价）
 *   - text/plain：请求体直接放小说全文
 *   - multipart/form-data：上传小说 .txt 文件（字段名 file，UTF-8）
 *   - ?title=  可选，覆盖自动生成的剧名
 *   - ?style=  改编风格（默认 剧集；其余 电影/话剧/短剧/分镜 为单本形态）
 *   - ?format= yaml | fountain（默认 yaml）
 *   - ?episodes= 剧集分集的总集数（可选，缺省由模型按节奏自动决定；仅剧集形态生效）
 *   - 响应：剧本文本 + 响应头 X-Validation-Warnings（残留问题数）
 *           + Content-Disposition（以剧名为名的下载文件，便于直接存盘）
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

    /** 文本版：请求体直接放小说全文。 */
    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> convert(
            @RequestBody String novelText,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "剧集") String style,
            @RequestParam(defaultValue = "yaml") String format,
            @RequestParam(required = false) Integer episodes) {
        return buildResponse(requireText(novelText), title, style, format, episodes);
    }

    /**
     * 文件版：multipart 上传小说 .txt（字段名 file，UTF-8）；其余参数与文本版一致。
     * 与文本版共享同一路径，按 Content-Type 路由——两种入参等价。
     */
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "剧集") String style,
            @RequestParam(defaultValue = "yaml") String format,
            @RequestParam(required = false) Integer episodes) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String novelText;
        try {
            novelText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取上传文件失败：" + e.getMessage());
        }
        return buildResponse(requireText(novelText), title, style, format, episodes);
    }

    /** 文本→剧本→导出的共享流程：转换、覆盖剧名、按格式导出，附校验告警数与下载文件名。 */
    private ResponseEntity<String> buildResponse(String novelText, String title,
                                                 String style, String format, Integer episodes) {
        ConversionResult result = pipeline.convert(novelText, style, episodes);
        Screenplay screenplay = applyTitleOverride(result.screenplay(), title);

        boolean fountain = "fountain".equalsIgnoreCase(format);
        String body = fountain
                ? fountainExporter.toFountain(screenplay)
                : yamlExporter.toYaml(screenplay);
        MediaType contentType = fountain
                ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                : new MediaType("text", "yaml", StandardCharsets.UTF_8);
        String filename = downloadName(screenplay.title(), fountain ? "fountain" : "yml");

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HEADER_WARNINGS, String.valueOf(result.report().count()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    private String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小说文本不能为空");
        }
        return text;
    }

    /** 由剧名生成下载文件名：剔除文件名非法字符，空标题回退 screenplay。 */
    private String downloadName(String title, String ext) {
        String base = (title == null || title.isBlank()) ? "screenplay" : title.strip();
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        return base + "." + ext;
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
