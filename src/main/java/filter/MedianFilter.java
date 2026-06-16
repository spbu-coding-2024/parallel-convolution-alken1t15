package filter;

import image.ColorImage;

public class MedianFilter {
    public static ColorImage apply(ColorImage src, int windowSize) {
        // Я принимаю только положительное нечётное окно, потому что у median filter должен быть центр.
        if (windowSize <= 0 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("Median window size must be positive and odd");
        }

        int width = src.width;
        int height = src.height;

        // Результат пишу в новый массив, чтобы не портить исходные пиксели во время расчёта соседей.
        byte[] dst = new byte[src.data.length];

        // По радиусу я понимаю, сколько соседей брать в каждую сторону от текущего пикселя.
        int radius = windowSize / 2;

        // В этом массиве я собираю значения одного канала внутри окна и затем беру медиану.
        int[] window = new int[windowSize * windowSize];

        // Я обрабатываю каждый пиксель и каждый цветовой канал независимо.
        for (int y = 0; y < height; y++) {
            int dstRowOffset = y * width;

            for (int x = 0; x < width; x++) {
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

        // Я собираю все значения выбранного канала из окна вокруг текущей точки.
        for (int dy = -radius; dy <= radius; dy++) {
            // Для median filter я прижимаю координаты к краю изображения.
            int sy = clamp(y + dy, 0, src.height - 1);

            for (int dx = -radius; dx <= radius; dx++) {
                int sx = clamp(x + dx, 0, src.width - 1);

                window[idx++] = src.data[ColorImage.offset(src.width, sx, sy) + channel] & 0xFF;
            }
        }

        java.util.Arrays.sort(window);

        // После сортировки я беру центральный элемент: это и есть медиана.
        return window[window.length / 2];
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
