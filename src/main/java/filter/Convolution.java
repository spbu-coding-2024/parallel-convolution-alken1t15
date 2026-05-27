package filter;

import image.ColorImage;

public class Convolution {
    public static ColorImage apply(ColorImage src, Kernel kernel) {
        int width = src.width;
        int height = src.height;

        byte[] dst = new byte[src.data.length];

        // Нахожу центр ядра.
        // Например, для ядра 3x3 беру центр (1, 1), для 5x5 -> (2, 2)
        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        // Прохожу по всем пикселям изображения
        for (int y = 0; y < height; y++) {
            // Здесь накапливаю сумму произведений пикселей на коэффициенты ядра
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

        // Здесь накапливаю сумму произведений пикселей на коэффициенты ядра.
        double sum = 0.0;

        for (int ky = 0; ky < kernel.height; ky++) {
            // Вычисляю координату пикселя в исходном изображении.
            // mod использую для wrap-around обработки границ:
            // если выхожу за край, "заворачиваюсь" на другую сторону изображения.
            int sy = mod(y + ky - kernelCenterY, height);
            int srcRowOffset = sy * width;
            int kernelRowOffset = ky * kernel.width;

            // Прохожу по всем элементам ядра по x.
            for (int kx = 0; kx < kernel.width; kx++) {
                int sx = mod(x + kx - kernelCenterX, width);

                int pixel = src.data[(srcRowOffset + sx) * ColorImage.CHANNELS + channel] & 0xFF;

                // Добавляю вклад этого пикселя в итоговую сумму.
                sum += pixel * kernel.values[kernelRowOffset + kx];
            }
        }

        // После свёртки применяю factor и bias.
        int value = (int) Math.round(sum * kernel.factor + kernel.bias);

        // Ограничиваю результат диапазоном допустимых значений яркости.
        return clamp(value, 0, 255);
    }

    // Этим методом зажимаю число в допустимый диапазон.
    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // Этим методом корректно обрабатываю выход за границы изображения.
    static int mod(int value, int size) {
        return ((value % size) + size) % size;
    }
}
