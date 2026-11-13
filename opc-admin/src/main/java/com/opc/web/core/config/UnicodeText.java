package com.opc.web.core.config;

final class UnicodeText
{
    private UnicodeText()
    {
    }

    static boolean hasVisibleText(String value)
    {
        return value != null && value.codePoints()
                .anyMatch(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint));
    }
}
