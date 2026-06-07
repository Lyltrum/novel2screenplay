package com.novel2screenplay.controller;

import com.novel2screenplay.export.FountainExporter;
import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel2screenplay.pipeline.ConversionPipeline;
import com.novel2screenplay.pipeline.ConversionResult;
import com.novel2screenplay.pipeline.ProgressListener;
import com.novel2screenplay.refine.SceneRefinementService;
import com.novel2screenplay.validate.SceneValidator;
import com.novel2screenplay.validate.ValidationIssue;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 *           剧本末尾附「自检报告」注释，逐条列出残留告警，便于作者定位校对
 */
@RestController
@RequestMapping("/api/screenplay")
public class ScreenplayController {

    private static final Logger log = LoggerFactory.getLogger(ScreenplayController.class);
    private static final String HEADER_WARNINGS = "X-Validation-Warnings";
    /** 长篇转换耗时较长，SSE 连接超时放宽到 30 分钟。 */
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;
    /** 专用 JSON 序列化器：把进度/结果事件转成 JSON 字符串再推送，避免与 YAMLMapper 混淆。 */
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * 流式版（文本）：与 /convert 入参一致，但用 SSE 实时推送转换进度，最后推完整结果。
     * 事件：progress{phase,message}（多条）→ result{format,warnings,filename,body} 或 error{error}。
     */
    @PostMapping(value = "/convert/stream", consumes = MediaType.TEXT_PLAIN_VALUE)
    public SseEmitter convertStream(
            @RequestBody String novelText,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "剧集") String style,
            @RequestParam(defaultValue = "yaml") String format,
            @RequestParam(required = false) Integer episodes) {
        return stream(requireText(novelText), title, style, format, episodes);
    }

    /** 流式版（文件上传）：multipart 上传 .txt，其余与流式文本版一致。 */
    @PostMapping(value = "/convert/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SseEmitter convertFileStream(
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
        return stream(requireText(novelText), title, style, format, episodes);
    }

    /**
     * 在后台线程跑转换，把流水线进度经 ProgressListener 转成 SSE 事件实时推送，
     * 完成后推 result 事件（含完整剧本），失败推 error 事件。与 buildResponse 产出一致，只是改为流式。
     */
    private SseEmitter stream(String novelText, String title, String style, String format, Integer episodes) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            try {
                ProgressListener listener = (phase, message) ->
                        send(emitter, "progress", Map.of("phase", phase, "message", message));

                ConversionResult result = pipeline.convert(novelText, style, episodes, listener);
                Screenplay screenplay = applyTitleOverride(result.screenplay(), title);

                boolean fountain = "fountain".equalsIgnoreCase(format);
                String body = (fountain ? fountainExporter.toFountain(screenplay) : yamlExporter.toYaml(screenplay))
                        + renderReport(result.report(), fountain);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("format", fountain ? "fountain" : "yaml");
                payload.put("warnings", result.report().count());
                payload.put("filename", downloadName(screenplay.title(), fountain ? "fountain" : "yml"));
                payload.put("body", body);
                send(emitter, "result", payload);
                emitter.complete();
            } catch (Exception e) {
                log.warn("流式转换失败：{}", e.getMessage());
                send(emitter, "error", Map.of("error", e.getMessage() == null ? "转换失败" : e.getMessage()));
                emitter.complete();
            } finally {
                worker.shutdown();
            }
        });
        return emitter;
    }

    /** 线程安全地推送一条 SSE 事件（data 为 JSON 字符串）；客户端断开等发送异常静默忽略。 */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            String json = JSON.writeValueAsString(data);
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
            }
        } catch (Exception e) {
            log.debug("SSE 事件发送失败（客户端可能已断开）：{}", e.getMessage());
        }
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
        body += renderReport(result.report(), fountain);   // 把残留告警内容附在末尾，作者可逐条校对
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

    /**
     * 把残留校验问题渲染成可读注释附在剧本末尾——告警不只给数量(X-Validation-Warnings)，
     * 更要让作者明确知道"哪一场、哪个字段还需人工校对"。注释不影响 YAML/Fountain 合法性
     * （解析时被忽略），下载的文件里也能直接看到。YAML 用 #，Fountain 用 /* *&#47; 包裹。
     */
    private String renderReport(ValidationReport report, boolean fountain) {
        if (report.isClean()) {
            String line = "自检通过：无残留校验问题。";
            return fountain ? "\n/*\n" + line + "\n*/\n" : "\n# " + line + "\n";
        }
        String header = "自检报告 · 残留校验告警 " + report.count()
                + " 处（自动修复后仍存在，不影响格式合法性，供作者优先校对）";
        StringBuilder sb = new StringBuilder("\n");
        if (fountain) {
            sb.append("/*\n").append(header).append("\n");
            for (ValidationIssue i : report.issues()) {
                sb.append("  ").append(i).append("\n");
            }
            sb.append("*/\n");
        } else {
            sb.append("# ").append(header).append("\n");
            for (ValidationIssue i : report.issues()) {
                sb.append("#   ").append(i).append("\n");
            }
        }
        return sb.toString();
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
