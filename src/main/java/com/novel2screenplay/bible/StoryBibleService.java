package com.novel2screenplay.bible;

import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 只负责：逐章维护跨章一致性登记表（StoryBible）。
 * 每章用模型抽取本章登场人物与地点，再在 Java 端按主名/别名合并去重——
 * 合并放在 Java 而非交给模型，是为了结果稳定、可控、不漂移。
 */
@Service
public class StoryBibleService {

    private final ChatClient chatClient;

    public StoryBibleService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 用本章信息更新登记表，返回合并后的新登记表。 */
    public StoryBible update(StoryBible current, Chapter chapter) {
        StoryBible found = chatClient.prompt()
                .user(Prompts.bibleExtraction(chapter))
                .call()
                .entity(StoryBible.class);
        return merge(current, found);
    }

    private StoryBible merge(StoryBible base, StoryBible add) {
        List<Character> characters = new ArrayList<>(base.characters());
        if (add != null && add.characters() != null) {
            for (Character incoming : add.characters()) {
                mergeCharacter(characters, incoming);
            }
        }

        Set<String> locations = new LinkedHashSet<>(base.locations());
        if (add != null && add.locations() != null) {
            for (String loc : add.locations()) {
                if (loc != null && !loc.isBlank()) {
                    locations.add(loc.strip());
                }
            }
        }
        return new StoryBible(characters, new ArrayList<>(locations));
    }

    /** 按主名或别名匹配既有人物：命中则并入别名/补设定，否则新增。 */
    private void mergeCharacter(List<Character> characters, Character incoming) {
        if (incoming == null || incoming.name() == null || incoming.name().isBlank()) {
            return;
        }
        for (int i = 0; i < characters.size(); i++) {
            Character existing = characters.get(i);
            if (sameCharacter(existing, incoming)) {
                characters.set(i, combine(existing, incoming));
                return;
            }
        }
        characters.add(normalize(incoming));
    }

    /** 主名相等，或一方主名出现在另一方别名中，即视为同一人。 */
    private boolean sameCharacter(Character a, Character b) {
        if (a.name().equals(b.name())) {
            return true;
        }
        return aliasesOf(a).contains(b.name()) || aliasesOf(b).contains(a.name());
    }

    private Character combine(Character existing, Character incoming) {
        Set<String> aliases = new LinkedHashSet<>(aliasesOf(existing));
        aliases.addAll(aliasesOf(incoming));
        // 把对方主名也纳入别名（若与本主名不同），保留称呼线索
        if (!existing.name().equals(incoming.name())) {
            aliases.add(incoming.name());
        }
        aliases.remove(existing.name());

        String description = !isBlank(existing.description())
                ? existing.description()
                : incoming.description();

        return new Character(existing.name(), new ArrayList<>(aliases), description);
    }

    private Character normalize(Character c) {
        List<String> aliases = c.aliases() == null ? List.of() : c.aliases();
        return new Character(c.name().strip(), new ArrayList<>(aliases), c.description());
    }

    private Set<String> aliasesOf(Character c) {
        Set<String> set = new LinkedHashSet<>();
        if (c.aliases() != null) {
            for (String a : c.aliases()) {
                if (a != null && !a.isBlank()) {
                    set.add(a.strip());
                }
            }
        }
        return set;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
