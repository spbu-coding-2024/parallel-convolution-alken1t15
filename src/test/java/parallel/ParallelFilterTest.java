package parallel;

import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.ColorImage;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelFilterTest {
    @Test
    void parallelConvolutionShouldMatchSequentialForAllStrategies() {
        ColorImage input = randomImage(23, 17, 1001);
        Kernel kernel = Kernels.byName("gaussian5");
        ColorImage expected = Convolution.apply(input, kernel);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            ColorImage actual = ParallelConvolution.apply(input, kernel, strategy, 4);
            assertImagesEqual(expected, actual);
        }
    }

    @Test
    void parallelMedianShouldMatchSequentialForAllStrategies() {
        ColorImage input = randomImage(19, 21, 2002);
        ColorImage expected = MedianFilter.apply(input, 5);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            ColorImage actual = ParallelMedianFilter.apply(input, 5, strategy, 4);
            assertImagesEqual(expected, actual);
        }
    }

    @Test
    void parallelConvolutionShouldWorkWhenThreadsMoreThanImageParts() {
        ColorImage input = randomImage(5, 4, 3003);
        Kernel kernel = Kernels.byName("sharpen3");
        ColorImage expected = Convolution.apply(input, kernel);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            ColorImage actual = ParallelConvolution.apply(input, kernel, strategy, 16);
            assertImagesEqual(expected, actual);
        }
    }

    private static ColorImage randomImage(int width, int height, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[width * height * ColorImage.CHANNELS];
        random.nextBytes(data);
        return new ColorImage(width, height, data);
    }

    private static void assertImagesEqual(ColorImage expected, ColorImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }
}
