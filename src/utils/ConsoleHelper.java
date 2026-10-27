package utils;

import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Scanner;

public final class ConsoleHelper {

    /**
     * Кодировка консоли JVM (на Windows обычно Cp1251) — совпадает с тем,
     * как cmd отображает кириллицу. Не переключать stdout на UTF-8 без chcp 65001
     * в той же консоли: иначе «╨б╨╕╤Б╤В╨╡╨╝╨░» вместо «Система».
     */
    private static final Charset CONSOLE_CHARSET = Charset.defaultCharset();

    private ConsoleHelper() {
    }

    public static void setup() {
        // Намеренно пусто: смена кодировки только через chcp 65001 в этой же cmd до запуска java
    }

    public static Scanner newScanner() {
        return new Scanner(new InputStreamReader(System.in, CONSOLE_CHARSET));
    }
}
