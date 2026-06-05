package com.novel2screenplay.prompt;

import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.StoryBible;

/**
 * 只负责：集中存放并拼装 prompt 文本（逻辑与 prompt 分离）。
 * 所有调用模型的提示词都在此一处维护，便于迭代调质量。
 * 注意：输出格式指令（JSON Schema）由 Spring AI 的 BeanOutputConverter 自动追加，此处只写语义要求。
 */
public final class Prompts {

    private Prompts() {
    }

    private static final String SCENE_EXTRACTION = """
            你是专业影视编剧，负责把小说章节改编成结构化剧本场景。

            【改编原则】
            1. 场景切分：按"地点或时间发生切换"切分场景——地点变化或时间跳转，即为一个新场景；一章通常切出多个场景。
            2. 适度改编：把小说的叙述与心理描写，转写为可拍摄的画面动作(action)；删除无法影像化的冗长内心独白；为每个场景补全合理的场景标题(内外景、地点、时间)。
            3. 场景标题取值规范（务必遵守）：
               - int_ext 只能取 INT(室内) 或 EXT(室外) 或 INT_EXT(内外景兼有)。
               - location 用中文具体地点（如"听雨楼客栈大堂"）。
               - time_of_day 一律用中文，从这些里选最贴切的：清晨 / 白天 / 黄昏 / 夜晚 / 午夜 / 黎明；严禁使用英文（如 Night、DAWN）。
            4. 对白：从原文人物对话中提取台词，保留说话人；可加简短括号提示(如"(冷笑)")表达语气；不要凭空捏造大段原文没有的台词；不要产生 line 为空的对白。
            5. 人名一致：严格使用下方"人物登记表"中的主名；登记表为空时，以本章首次出现的姓名为准，同一人物前后称呼保持一致。
            6. 转场 transition：仅在确有必要时填写，用中文且取值固定为"切至"或"淡出"或"闪回"；无特殊转场则留空字符串。
            7. 来源可追溯：每个场景必须在 source.excerpt 摘录触发该场景的原文片段(10~30字，直接抄录原文)；source.chapter 与场景 id 留空，由程序统一填充。

            【改编风格】%s
            %s

            【人物登记表】（用于人名归一）
            %s

            【本章正文】（第 %d 章：%s）
            %s
            """;

    /** 拼装单章的场景抽取 prompt。styleGuidance 为风格化改编指引（可空）。 */
    public static String sceneExtraction(Chapter chapter, StoryBible bible,
                                         String style, String styleGuidance) {
        return SCENE_EXTRACTION.formatted(
                style,
                styleGuidance == null ? "" : styleGuidance,
                renderBible(bible),
                chapter.index(),
                chapter.title(),
                chapter.text());
    }

    private static final String BIBLE_EXTRACTION = """
            从下面的小说章节中，抽取「登场人物」与「地点」，用于建立剧本的人物登记表。
            - 人物：每人给出 主名(name) + 别名/称呼列表(aliases，如绰号、尊称、自称) + 一句话设定(description)。
            - 地点：本章出现的主要场景地点(locations)。
            - 只抽取本章实际出现的信息，不要杜撰章节中没有的人物或设定。

            【本章正文】（第 %d 章：%s）
            %s
            """;

    /** 拼装单章的人物/地点登记 prompt。 */
    public static String bibleExtraction(Chapter chapter) {
        return BIBLE_EXTRACTION.formatted(chapter.index(), chapter.title(), chapter.text());
    }

    private static final String TITLE_LOGLINE = """
            根据下面的剧情概要，为这部「%s」剧本拟定：
            - title：一个简洁有力、贴合内容的剧名。
            - logline：一句话故事梗概（30 字以内，点明主角、目标与核心冲突）。

            【剧情概要】（按场景顺序）
            %s
            """;

    /** 拼装剧名/梗概生成 prompt（基于各场景概要，控制 token）。 */
    public static String titleLogline(String synopsis, String style) {
        return TITLE_LOGLINE.formatted(style, synopsis);
    }

    private static final String REPAIR = """
            下面是一份自动生成的剧本(YAML)，以及它的体检问题清单。
            请仅针对问题清单逐条修复，其余内容尽量保持不变，返回修复后的完整剧本。

            【修复要求】
            - time_of_day 必须用中文：清晨 / 白天 / 黄昏 / 夜晚 / 午夜 / 黎明。
            - 对白的说话角色(character)必须是人物登记表中已有的主名或别名；不在表内的，改成最贴切的已登记角色。
            - 不要出现空台词(line 为空)；空台词请删除该条对白。
            - 每个场景必须保留 source.excerpt 原文摘录，且不要改动 source.chapter 与场景 id。
            - 不要新增或删除场景，不要大幅改写动作与对白。

            【问题清单】
            %s

            【当前剧本 YAML】
            %s
            """;

    /** 拼装"带问题清单修复剧本"的 prompt（自检修复闭环）。 */
    public static String repair(String issues, String currentYaml) {
        return REPAIR.formatted(issues, currentYaml);
    }

    private static String renderBible(StoryBible bible) {
        if (bible == null || bible.characters() == null || bible.characters().isEmpty()) {
            return "（暂无，以本章为准）";
        }
        StringBuilder sb = new StringBuilder();
        for (Character c : bible.characters()) {
            sb.append("- ").append(c.name());
            if (c.aliases() != null && !c.aliases().isEmpty()) {
                sb.append("（别名：").append(String.join("、", c.aliases())).append("）");
            }
            if (c.description() != null && !c.description().isBlank()) {
                sb.append("：").append(c.description());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
