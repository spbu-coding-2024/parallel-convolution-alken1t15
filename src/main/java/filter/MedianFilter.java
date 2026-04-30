package filter;

import image.ColorImage;

public class MedianFilter {
    public static ColorImage apply(ColorImage src, int windowSize) {
        // Проверяю, что размер окна корректный:
        // для median filter беру только положительное и нечётное окно
        if (windowSize <= 0 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Median window size must be positive and odd");
        }

        int width = src.width;
        int height = src.height;

        // Сюда буду записывать результат после применения фильтра
        byte[] dst = new byte[src.data.length];

        // Считаю радиус окна вокруг текущего пикселя.
        // Например, для 3x3 radius = 1, для 5x5 radius = 2.
        int radius = windowSize / 2;

        // В этот массив собираю все значения пикселей из окна,
        // чтобы потом отсортировать их и взять медиану
        int[] window = new int[windowSize * windowSize];

        // Прохожу по всем пикселям исходного изображения
        for (int y = 0; y < height; y++) {
            int dstRowOffset = y * width;

            for (int x = 0; x < width; x++) {
                // Считаю медиану через общий метод, чтобы параллельная версия
                // не дублировала правила обработки границ.
                int dstOffset = (dstRowOffset + x) * ColorImage.CHANNELS;
                for (int channel = 0; channel < ColorImage.CHANNELS; channel++) {
                    int median = computePixel(src, x, y, channel, radius, window);
                    dst[dstOffset + channel] = (byte) median;
                }
            }
        }

        return new ColorImage(width, height, dst);
    }

    public static int computePixel(ColorImage src, int x, int y, int channel, int radius, int[] window) {
        int idx = 0;

        // Собираю все пиксели из окна вокруг текущей точки (x, y).
        for (int dy = -radius; dy <= radius; dy++) {
            // Если выходим за границы, прижимаю координату к ближайшей границе изображения.
            int sy = clamp(y + dy, 0, src.height - 1);

            for (int dx = -radius; dx <= radius; dx++) {
                int sx = clamp(x + dx, 0, src.width - 1);

                window[idx++] = src.data[ColorImage.offset(src.width, sx, sy) + channel] & 0xFF;
            }
        }

        java.util.Arrays.sort(window);

        // После сортировки беру центральный элемент: это и есть медиана.
        return window[window.length / 2];
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
