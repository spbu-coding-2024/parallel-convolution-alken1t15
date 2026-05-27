package parallel;

import filter.Convolution;
import filter.Kernel;
import image.ColorImage;

public class ParallelConvolution {
    public static ColorImage apply(ColorImage src, Kernel kernel, ParallelStrategy strategy, int threads) {
        int width = src.width;
        int height = src.height;

        // Исходное изображение только читается, поэтому все потоки могут
        // одновременно использовать его для расчёта соседних значений.
        // Каждый поток пишет только в свои пиксели, поэтому отдельную синхронизацию
        // для массива dst здесь не добавляю.
        byte[] dst = new byte[src.data.length];

        // Центр ядра один и тот же для всех пикселей, вычисляю его один раз
        // до запуска потоков.
        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        ParallelImageProcessor.process(width, height, strategy, threads, (x, y) -> {
            // Одна назначенная координата означает три записи: по одной
            // для каждого цветового канала текущего пикселя.
            int index = ColorImage.offset(width, x, y);
            for (int channel = 0; channel < ColorImage.CHANNELS; channel++) {
                dst[index + channel] = (byte) Convolution.computePixel(src, kernel, x, y, channel, kernelCenterX, kernelCenterY);
            }
        });

        return new ColorImage(width, height, dst);
    }
}
