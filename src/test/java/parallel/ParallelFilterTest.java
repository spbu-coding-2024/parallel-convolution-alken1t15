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

    /**
     * Проверяет, что параллельная свёртка изображения даёт такой же результат,
     * как и обычная последовательная свёртка, для всех доступных стратегий распараллеливания.
     */
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

    /**
     * Проверяет, что параллельный медианный фильтр даёт такой же результат,
     * как и последовательный медианный фильтр, для всех доступных стратегий распараллеливания.
     */
    @Test
    void parallelMedianShouldMatchSequentialForAllStrategies() {
        ColorImage input = randomImage(19, 21, 2002);
        ColorImage expected = MedianFilter.apply(input, 5);

        for (ParallelStrategy strategy : ParallelStrategy.values()) {
            ColorImage actual = ParallelMedianFilter.apply(input, 5, strategy, 4);
            assertImagesEqual(expected, actual);
        }
    }

    /**
     * Проверяет, что параллельная свёртка работает корректно даже тогда,
     * когда количество потоков больше, чем количество частей изображения,
     * которые реально можно эффективно распределить между потоками.
     */
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

    /**
     * Проверяет параллельную свёртку на разных размерах изображений,
     * разных ядрах фильтров, разных стратегиях распараллеливания
     * и разном количестве потоков.
     *
     * Во всех случаях результат параллельной обработки должен полностью совпадать
     * с результатом последовательной обработки.
     */
    @Test
    void parallelConvolutionShouldMatchSequentialAcrossRandomSizesAndKernels() {
        String[] filters = {"identity", "blur3", "gaussian5", "motion9", "edge_all3", "emboss5", "mean3"};
        int[][] sizes = {
                {1, 1},
                {2, 7},
                {8, 3},
                {13, 13},
                {31, 17}
        };

        for (int[] size : sizes) {
            ColorImage input = randomImage(size[0], size[1], size[0] * 100L + size[1]);
            for (String filter : filters) {
                Kernel kernel = Kernels.byName(filter);
                ColorImage expected = Convolution.apply(input, kernel);

                for (ParallelStrategy strategy : ParallelStrategy.values()) {
                    for (int threads : new int[]{1, 2, 5, 16}) {
                        ColorImage actual = ParallelConvolution.apply(input, kernel, strategy, threads);
                        assertImagesEqual(expected, actual);
                    }
                }
            }
        }
    }

    /**
     * Проверяет параллельный медианный фильтр на разных размерах изображений,
     * разных размерах окна фильтра, разных стратегиях распараллеливания
     * и разном количестве потоков.
     *
     * Во всех случаях параллельный медианный фильтр должен выдавать тот же результат,
     * что и последовательная реализация.
     */
    @Test
    void parallelMedianShouldMatchSequentialAcrossRandomSizesAndWindows() {
        int[][] sizes = {
                {1, 1},
                {3, 2},
                {11, 9},
                {20, 7}
        };

        for (int[] size : sizes) {
            ColorImage input = randomImage(size[0], size[1], size[0] * 200L + size[1]);
            for (int windowSize : new int[]{3, 5, 7}) {
                ColorImage expected = MedianFilter.apply(input, windowSize);

                for (ParallelStrategy strategy : ParallelStrategy.values()) {
                    for (int threads : new int[]{1, 3, 12}) {
                        ColorImage actual = ParallelMedianFilter.apply(input, windowSize, strategy, threads);
                        assertImagesEqual(expected, actual);
                    }
                }
            }
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
