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
     * как и последовательная свёртка, для всех стратегий распараллеливания.
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
     * как и последовательный медианный фильтр, для всех стратегий распараллеливания.
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
     * Проверяет, что параллельная свёртка корректно работает даже тогда,
     * когда потоков больше, чем частей изображения, которые можно распределить.
     *
     * Это важно для маленьких изображений: лишние потоки не должны ломать результат.
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
     * Проверяет параллельную свёртку на разных входных изображениях,
     * разных фильтрах, стратегиях распараллеливания и количестве потоков.
     *
     * Для каждой комбинации параметров результат параллельной обработки
     * должен полностью совпадать с результатом последовательной свёртки.
     */
    @Test
    void parallelConvolutionShouldMatchSequentialForDifferentInputsAndThreadCounts() {
        int[][] sizes = {{1, 1}, {2, 5}, {7, 3}, {16, 11}, {25, 19}};
        String[] filters = {"identity", "gaussian3", "gaussian5", "motion9", "sharpen3"};
        int[] threadCounts = {1, 2, 4, 8, 32};

        for (int sizeIndex = 0; sizeIndex < sizes.length; sizeIndex++) {
            ColorImage input = randomImage(sizes[sizeIndex][0], sizes[sizeIndex][1], 5000 + sizeIndex);

            for (String filterName : filters) {
                Kernel kernel = Kernels.byName(filterName);
                ColorImage expected = Convolution.apply(input, kernel);

                for (ParallelStrategy strategy : ParallelStrategy.values()) {
                    for (int threads : threadCounts) {
                        ColorImage actual = ParallelConvolution.apply(input, kernel, strategy, threads);
                        assertImagesEqual(expected, actual);
                    }
                }
            }
        }
    }

    /**
     * Проверяет параллельный медианный фильтр на разных входных изображениях,
     * разных размерах окна фильтра, стратегиях распараллеливания и количестве потоков.
     *
     * Для каждой комбинации параметров параллельный медианный фильтр
     * должен выдавать тот же результат, что и последовательная реализация.
     */
    @Test
    void parallelMedianShouldMatchSequentialForDifferentInputsAndThreadCounts() {
        int[][] sizes = {{1, 1}, {2, 5}, {7, 3}, {16, 11}, {25, 19}};
        int[] windows = {3, 5, 7};
        int[] threadCounts = {1, 2, 4, 8, 32};

        for (int sizeIndex = 0; sizeIndex < sizes.length; sizeIndex++) {
            ColorImage input = randomImage(sizes[sizeIndex][0], sizes[sizeIndex][1], 6000 + sizeIndex);

            for (int window : windows) {
                ColorImage expected = MedianFilter.apply(input, window);

                for (ParallelStrategy strategy : ParallelStrategy.values()) {
                    for (int threads : threadCounts) {
                        ColorImage actual = ParallelMedianFilter.apply(input, window, strategy, threads);
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