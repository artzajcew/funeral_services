package ru.memory.ui;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/** Низкоуровневые компоненты консольного интерфейса. */
public final class Console {
    private static final int WIDTH = 88;
    private final Scanner scanner;
    private final java.io.Console terminal;
    private final PrintStream out;

    public Console() {
        terminal = System.console();
        scanner = terminal == null ? new Scanner(System.in) : null;
        out = System.out;
    }

    public Console(InputStream input, PrintStream output) {
        scanner = new Scanner(input);
        terminal = null;
        out = output;
    }

    public void title(String title) {
        title(title, "");
    }

    public void title(String title, String subtitle) {
        out.println("═".repeat(WIDTH));
        out.println("  " + title);
        if (!subtitle.isBlank()) out.println("  " + subtitle);
        out.println("═".repeat(WIDTH));
    }

    public void section(String value) {
        out.println("\n" + value);
        out.println("─".repeat(WIDTH));
    }

    public int menu(String... items) {
        for (int i = 0; i < items.length; i++) out.printf("  %d. %s%n", i + 1, items[i]);
        return askInt("\nВыберите действие", 1, items.length);
    }

    public String ask(String label) {
        return ask(label, null, true);
    }

    public String askOptional(String label) {
        return ask(label, null, false);
    }

    public String askDefault(String label, String defaultValue) {
        return ask(label, defaultValue, true);
    }

    private String ask(String label, String defaultValue, boolean required) {
        while (true) {
            out.print(label + (defaultValue == null || defaultValue.isBlank() ? "" : " [" + defaultValue + "]") + ": ");
            String value = nextLine().trim();
            if (!value.isEmpty()) return value;
            if (defaultValue != null) return defaultValue;
            if (!required) return "";
            error("Поле обязательно для заполнения.");
        }
    }

    public String secret(String label) {
        if (terminal != null) {
            char[] value = terminal.readPassword("%s: ", label);
            return value == null ? "" : new String(value).trim();
        }
        return ask(label);
    }

    public int askInt(String label, int min, int max) {
        while (true) {
            String raw = ask(label);
            try {
                int value = Integer.parseInt(raw);
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException error) {
                this.error("Введите целое число от " + min + " до " + max + ".");
                continue;
            }
            error("Введите целое число от " + min + " до " + max + ".");
        }
    }

    public long askLong(String label, long min) {
        while (true) {
            String raw = ask(label);
            try {
                long value = Long.parseLong(raw);
                if (value >= min) return value;
            } catch (NumberFormatException error) {
                this.error("Введите целое число не меньше " + min + ".");
                continue;
            }
            error("Введите целое число не меньше " + min + ".");
        }
    }

    public Long askOptionalLong(String label, long min) {
        while (true) {
            String raw = askOptional(label);
            if (raw.isEmpty()) return null;
            try {
                long value = Long.parseLong(raw);
                if (value >= min) return value;
            } catch (NumberFormatException error) {
                this.error("Введите целое число не меньше " + min + " либо оставьте поле пустым.");
                continue;
            }
            error("Введите целое число не меньше " + min + " либо оставьте поле пустым.");
        }
    }

    public boolean confirm(String question, boolean defaultValue) {
        out.print(question + (defaultValue ? " [Д/н]: " : " [д/Н]: "));
        String value = nextLine().trim().toLowerCase();
        if (value.isEmpty()) return defaultValue;
        return value.equals("д") || value.equals("да") || value.equals("y") || value.equals("yes");
    }

    public void table(String[] headers, List<String[]> rows, int[] widths) {
        printRow(headers, widths);
        for (int i = 0; i < widths.length; i++) {
            out.print("─".repeat(widths[i]));
            if (i < widths.length - 1) out.print("─┼─");
        }
        out.println();
        if (rows.isEmpty()) {
            out.println("Нет данных");
            return;
        }
        rows.forEach(row -> printRow(row, widths));
    }

    private void printRow(String[] values, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            String value = values[i] == null ? "" : values[i];
            if (value.length() > widths[i]) value = value.substring(0, Math.max(1, widths[i] - 1)) + "…";
            out.printf("%-" + widths[i] + "s", value);
            if (i < widths.length - 1) out.print(" │ ");
        }
        out.println();
    }

    public void info(String value) { out.println("ℹ " + value); }
    public void success(String value) { out.println("✓ " + value); }
    public void error(String value) { out.println("! " + value); }
    public void println(String value) { out.println(value); }
    public void pause() { out.print("\nНажмите Enter, чтобы продолжить…"); nextLine(); }

    private String nextLine() {
        return terminal == null ? scanner.nextLine() : terminal.readLine();
    }
}
