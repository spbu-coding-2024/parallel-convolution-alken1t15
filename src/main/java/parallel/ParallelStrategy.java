package parallel;

import java.util.Locale;

public enum ParallelStrategy {
    PIXELS,
    ROWS,
    COLUMNS,
    GRID;

    public static ParallelStrategy parse(String value) {
        try {
            // Привожу имя стратегии к одному виду, чтобы в CLI можно было писать
            // pixels, rows, columns или grid без учета регистра.
            return ParallelStrategy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown parallel strategy: " + value + ". Supported: pixels, rows, columns, grid"
            );
        }
    }
}
