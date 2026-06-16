package filter;

import image.ColorImage;

public class Convolution {
    public static ColorImage apply(ColorImage src, Kernel kernel) {
        int width = src.width;
        int height = src.height;

        // Я создаю отдельный массив результата, чтобы исходное изображение оставалось неизменным.
        byte[] dst = new byte[src.data.length];

        // Я нахожу центр ядра: для 3x3 это (1, 1), для 5x5 это (2, 2).
        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        // Я прохожу по каждому пикселю и отдельно считаю каналы R, G и B.
        for (int y = 0; y < height; y++) {
            int dstRowOffset = y * width;

            for (int x = 0; x < width; x++) {
                int dstOffset = (dstRowOffset + x) * ColorImage.CHANNELS;
                for (int channel = 0; channel < ColorImage.CHANNELS; channel++) {
                    dst[dstOffset + channel] = (byte) computePixel(src, kernel, x, y, channel, kernelCenterX, kernelCenterY);
                }
            }
        }

        return new ColorImage(width, height, dst);
    }

    public static int computePixel(ColorImage src, Kernel kernel, int x, int y, int channel) {
        return computePixel(src, kernel, x, y, channel, kernel.width / 2, kernel.height / 2);
    }

    public static int computePixel(ColorImage src, Kernel kernel, int x, int y, int channel, int kernelCenterX, int kernelCenterY) {
        int width = src.width;
        int height = src.height;

        // Здесь я накапливаю сумму произведений значений канала на коэффициенты ядра.
        double sum = 0.0;

        for (int ky = 0; ky < kernel.height; ky++) {
            // Для границ я использую wrap-around: если выхожу за край, беру пиксель с другой стороны.
            int sy = mod(y + ky - kernelCenterY, height);
            int srcRowOffset = sy * width;
            int kernelRowOffset = ky * kernel.width;

            for (int kx = 0; kx < kernel.width; kx++) {
                int sx = mod(x + kx - kernelCenterX, width);

                int pixel = src.data[(srcRowOffset + sx) * ColorImage.CHANNELS + channel] & 0xFF;

                // Я добавляю вклад текущего соседнего пикселя в итоговое значение канала.
                sum += pixel * kernel.values[kernelRowOffset + kx];
            }
        }

        // После свёртки я применяю factor и bias из описания ядра.
        int value = (int) Math.round(sum * kernel.factor + kernel.bias);

        // Канал цвета должен остаться в диапазоне одного байта.
        return clamp(value, 0, 255);
    }

    // Этим методом я зажимаю число в допустимый диапазон.
    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // Этим методом я корректно обрабатываю отрицательные координаты при wrap-around.
    static int mod(int value, int size) {
        return ((value % size) + size) % size;
    }
}
