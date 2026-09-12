package com.recruitinbox.essay;

import java.nio.charset.StandardCharsets;

record EssayCounts(int characters, int charactersWithoutSpaces, int utf8Bytes, int korean2Bytes) {
    static EssayCounts of(String value) {
        String text = value == null ? "" : value;
        int characters = text.codePointCount(0, text.length());
        int noSpaces = (int) text.codePoints()
                .filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)).count();
        int korean2 = text.codePoints().map(cp -> cp <= 0x7f ? 1 : 2).sum();
        return new EssayCounts(characters, noSpaces, text.getBytes(StandardCharsets.UTF_8).length, korean2);
    }
}
