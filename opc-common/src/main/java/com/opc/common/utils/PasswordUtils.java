package com.opc.common.utils;

import java.nio.charset.StandardCharsets;
import com.opc.common.constant.UserConstants;

public final class PasswordUtils
{
    private PasswordUtils()
    {
    }

    public static boolean isWithinBounds(String password, int minimumCodePoints)
    {
        if (!isBcryptCompatible(password))
        {
            return false;
        }
        return password.codePointCount(0, password.length()) >= minimumCodePoints;
    }

    public static boolean isBcryptCompatible(String password)
    {
        if (password == null)
        {
            return false;
        }
        int codePointCount = password.codePointCount(0, password.length());
        return codePointCount <= UserConstants.PASSWORD_MAX_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= UserConstants.PASSWORD_MAX_BYTES;
    }
}
