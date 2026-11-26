package dev.chatfilter.chattag.core;

public final class OutboundText {
    private OutboundText() {}

    public static String strip(String text) {
        if (text.indexOf('§') < 0) return text;
        var clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§') clean.append(c);
            else if (i + 1 < text.length() && isCode(text.charAt(i + 1))) i++;
        }
        return clean.toString();
    }

    public static String chat(String text) {
        String clean = strip(text);
        return clean.isBlank() ? "" : clean;
    }

    /** The command argument has already been routed and does not include its initial slash. */
    public static String command(String text) {
        String clean = strip(text);
        return clean.isBlank() ? "" : clean;
    }

    private static boolean isCode(char c) {
        if (c >= 'A' && c <= 'Z') c = (char) (c + 'a' - 'A');
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o') || c == 'r';
    }
}
