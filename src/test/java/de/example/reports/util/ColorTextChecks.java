package de.example.reports.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Standalone regression checks; run with exec:java and the test classpath. */
public final class ColorTextChecks {
    public static void main(String[] args) {
        check("&cRed", "Red", NamedTextColor.RED);
        check("§aGreen", "Green", NamedTextColor.GREEN);
        check("<red>Red</red>", "Red", NamedTextColor.RED);
        check("&#12abefHex", "Hex", TextColor.fromHexString("#12abef"));
        check("&x&1&2&a&b&e&fHex", "Hex", TextColor.fromHexString("#12abef"));
        check("§x§1§2§a§b§e§fHex", "Hex", TextColor.fromHexString("#12abef"));
        Component mixed = ColorText.parse("&cTitle <aqua>Reason</aqua> &eTime");
        require(plain(mixed).equals("Title Reason Time"), "Mixed formats leaked markup");
        require(hasColor(mixed, NamedTextColor.AQUA), "MiniMessage inside legacy text");
        require(hasColor(mixed, NamedTextColor.YELLOW), "Legacy after MiniMessage");
        Component reset = ColorText.parse("&lBold &cRed");
        require(hasDecoration(reset, TextDecoration.BOLD), "Legacy bold missing");
        require(!redIsBold(reset, false), "Legacy color must reset bold");
        Component unsafe = ColorText.parse("<click:run_command:'/op nobody'>text</click>");
        require(!hasClick(unsafe), "Formatting must not create executable click events");
        require(plain(ColorText.parse("Fish & chips")).equals("Fish & chips"), "Ordinary ampersand");
        require(plain(ColorText.parse("&zUnknown")).equals("&zUnknown"), "Unknown code changed");
        require(plain(ColorText.parse(null)).isEmpty(), "Null handling");
        var wrapped=ColorText.wrap("<red>abcdefghijkl</red>",5);
        require(wrapped.size()==3 && plain(wrapped.get(1)).equals("fghij"), "Lore wrapping");
        require(wrapped.stream().allMatch(c->hasColor(c,NamedTextColor.RED)), "Wrapped lore loses color");
        System.out.println("ColorText: all regression checks passed.");
    }
    private static void check(String source,String text,TextColor color) {
        Component parsed=ColorText.parse(source);
        require(plain(parsed).equals(text), "Unexpected visible text: "+source);
        require(hasColor(parsed,color), "Missing color: "+source);
    }
    private static String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    private static boolean hasColor(Component c,TextColor color){return color.equals(c.color())||c.children().stream().anyMatch(x->hasColor(x,color));}
    private static boolean hasDecoration(Component c,TextDecoration d){return c.decoration(d)==TextDecoration.State.TRUE||c.children().stream().anyMatch(x->hasDecoration(x,d));}
    private static boolean hasClick(Component c){return c.clickEvent()!=null||c.children().stream().anyMatch(ColorTextChecks::hasClick);}
    private static boolean redIsBold(Component c,boolean inherited){boolean bold=switch(c.decoration(TextDecoration.BOLD)){case TRUE->true;case FALSE->false;case NOT_SET->inherited;};return (NamedTextColor.RED.equals(c.color())&&bold)||c.children().stream().anyMatch(x->redIsBold(x,bold));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
