package parallel;

import filter.Convolution;
import filter.Kernel;
import image.ColorImage;

public class ParallelConvolution {
    public static ColorImage apply(ColorImage src, Kernel kernel, ParallelStrategy strategy, int threads) {
        int width = src.width;
        int height = src.height;

        // Каждый поток пишет только в свои пиксели, поэтому отдельную синхронизацию
        // для массива dst здесь не добавляю.
        byte[] dst = new byte[src.data.length];

        int kernelCenterX = kernel.width / 2;
        int kernelCenterY = kernel.height / 2;

        ParallelImageProcessor.process(width, height, strategy, threads, (x, y) -> {
            int index = ColorImage.offset(width, x, y);
            for (int channel = 0; channel < ColorImage.CHANNELS; channel++) {
                dst[index + channel] = (byte) Convolution.computePixel(src, kernel, x, y, channel, kernelCenterX, kernelCenterY);
            }
        });

        return new ColorImage(width, height, dst);
    }
}
