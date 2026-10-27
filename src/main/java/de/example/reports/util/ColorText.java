package de.example.reports.util;

import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/** Color/formatting only: user-supplied reasons cannot create click commands or URLs. */
public final class ColorText {
    private static final MiniMessage COLORS = MiniMessage.builder().tags(TagResolver.builder()
            .resolver(StandardTags.color()).resolver(StandardTags.decorations())
            .resolver(StandardTags.reset()).resolver(StandardTags.gradient())
            .resolver(StandardTags.rainbow()).build()).build();
    private static final Pattern LEGACY = Pattern.compile("(?i)[&§](x(?:[&§][0-9a-f]){6}|#[0-9a-f]{6}|[0-9a-fk-or])");
    private static final String[] NAMES = {"black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
            "aqua", "red", "light_purple", "yellow", "white"};
    private static final Map<Character,String> STYLES = Map.of('k',"obfuscated",'l',"bold",
            'm',"strikethrough",'n',"underlined",'o',"italic",'r',"reset");
    private ColorText() {}
    /** Wrap rendered text, never split a MiniMessage tag across lore lines. */
    public static java.util.List<Component> wrap(String text, int width) {
        if (width < 1) throw new IllegalArgumentException("width must be positive");
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.empty());
        int[] length = {0};
        wrapComponent(parse(text), net.kyori.adventure.text.format.Style.empty(), width, lines, length);
        return lines;
    }
    private static void wrapComponent(Component component, net.kyori.adventure.text.format.Style parent,
                                      int width, java.util.List<Component> lines, int[] length) {
        var style = component.style().merge(parent, net.kyori.adventure.text.format.Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof net.kyori.adventure.text.TextComponent text) {
            text.content().codePoints().forEach(cp -> {
                if (cp == '\r') return;
                if (cp == '\n' || length[0] == width) {
                    lines.add(Component.empty()); length[0] = 0;
                    if (cp == '\n') return;
                }
                int index = lines.size() - 1;
                lines.set(index, lines.get(index).append(Component.text(new String(Character.toChars(cp)), style)));
                length[0]++;
            });
        }
        for (Component child : component.children()) wrapComponent(child, style, width, lines, length);
    }
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        var matcher = LEGACY.matcher(input);
        StringBuilder normalized = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            String tag;
            if (code.charAt(0) == 'x') tag = "#" + code.substring(1).replace("&", "").replace("§", "");
            else if (code.charAt(0) == '#') tag = code;
            else {
                int color = Character.digit(code.charAt(0), 16);
                tag = color >= 0 ? NAMES[color] : STYLES.get(code.charAt(0));
            }
            // Legacy colors reset decorations; MiniMessage tags retain their native semantics.
            boolean color = tag.startsWith("#") || Character.digit(code.charAt(0), 16) >= 0;
            matcher.appendReplacement(normalized, java.util.regex.Matcher.quoteReplacement(
                    (color ? "<reset>" : "") + "<" + tag + ">"));
        }
        matcher.appendTail(normalized);
        return COLORS.deserialize(normalized.toString());
    }
}
