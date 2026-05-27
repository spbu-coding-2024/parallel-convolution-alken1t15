package filter;

import image.ColorImage;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConvolutionTest {

    /**
     * Проверяет, что фильтр identity не изменяет изображение.
     * После применения фильтра результат должен полностью совпадать
     * с исходным изображением.
     */
    @Test
    void identityShouldReturnSameImage() {
        ColorImage input = randomImage(17, 13, 42);

        ColorImage output = Convolution.apply(input, Kernels.byName("identity"));

        assertImagesEqual(input, output);
    }

    /**
     * Проверяет, что ядро, состоящее только из нулей,
     * превращает изображение в полностью чёрное.
     * Все значения пикселей после обработки должны быть равны 0.
     */
    @Test
    void zeroKernelShouldProduceBlackImage() {
        ColorImage input = randomImage(9, 7, 123);
        Kernel zeroKernel = new Kernel(3, 3, new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0}, 1.0, 0.0);

        ColorImage output = Convolution.apply(input, zeroKernel);

        for (byte b : output.data) {
            assertEquals(0, b & 0xFF);
        }
    }

    /**
     * Проверяет, что после применения свёртки изображение сохраняет исходные размеры.
     * Также проверяется, что длина массива RGB-данных соответствует ширине,
     * высоте и количеству цветовых каналов.
     */
    @Test
    void outputShouldKeepSameSizeAndRgbDataLength() {
        ColorImage input = randomImage(31, 19, 7);

        ColorImage output = Convolution.apply(input, Kernels.byName("gaussian3"));

        assertEquals(input.width, output.width);
        assertEquals(input.height, output.height);
        assertEquals(input.width * input.height * ColorImage.CHANNELS, output.data.length);
    }

    /**
     * Проверяет, что значения пикселей после применения разных фильтров
     * остаются в допустимом диапазоне от 0 до 255.
     * Это важно, потому что цветовой канал не должен выходить за границы byte/RGB-значения.
     */
    @Test
    void outputValuesShouldStayInRange0To255() {
        ColorImage input = randomImage(25, 25, 99);
        String[] filters = {"blur3", "blur5", "gaussian3", "gaussian5", "edge_all3", "sharpen3", "emboss3", "motion9"};

        for (String name : filters) {
            ColorImage output = Convolution.apply(input, Kernels.byName(name));

            for (byte b : output.data) {
                int value = b & 0xFF;
                assertTrue(value >= 0 && value <= 255, "filter=" + name + ", value=" + value);
            }
        }
    }

    /**
     * Проверяет, что фильтры обрабатывают RGB-каналы независимо друг от друга.
     * Значения красного, зелёного и синего каналов не должны смешиваться между собой.
     */
    @Test
    void filtersShouldProcessRgbChannelsIndependently() {
        ColorImage input = new ColorImage(1, 1, new byte[]{10, 80, (byte) 200});

        ColorImage output = Convolution.apply(input, Kernels.byName("identity"));

        assertArrayEquals(new byte[]{10, 80, (byte) 200}, output.data);
    }

    /**
     * Проверяет, что медианный фильтр не изменяет изображение,
     * если все пиксели в нём имеют одинаковый цвет.
     * Для постоянного изображения результат должен совпадать с исходным.
     */
    @Test
    void medianOnConstantImageShouldReturnSameImage() {
        ColorImage input = constantImage(11, 8, 30, 120, 220);

        ColorImage output3 = MedianFilter.apply(input, 3);
        ColorImage output5 = MedianFilter.apply(input, 5);

        assertImagesEqual(input, output3);
        assertImagesEqual(input, output5);
    }

    /**
     * Проверяет, что медианный фильтр удаляет одиночный импульсный шум
     * в каждом RGB-канале.
     * Центральный испорченный пиксель должен быть заменён нормальным значением
     * из окружающей области.
     */
    @Test
    void medianShouldRemoveSingleImpulseNoisePerChannel() {
        ColorImage input = constantImage(7, 7, 100, 110, 120);
        int center = ColorImage.offset(input.width, 3, 3);
        input.data[center] = (byte) 255;
        input.data[center + 1] = 0;
        input.data[center + 2] = (byte) 255;

        ColorImage output = MedianFilter.apply(input, 3);

        int outCenter = ColorImage.offset(output.width, 3, 3);
        assertEquals(100, output.data[outCenter] & 0xFF);
        assertEquals(110, output.data[outCenter + 1] & 0xFF);
        assertEquals(120, output.data[outCenter + 2] & 0xFF);
    }

    /**
     * Проверяет, что расширение ядра нулями не меняет результат свёртки.
     * Ядро gaussian3 и эквивалентное ядро 5x5 с нулями по краям
     * должны давать одинаковый результат.
     */
    @Test
    void paddingKernelWithZerosShouldNotChangeResult() {
        ColorImage input = randomImage(16, 12, 2024);
        Kernel k3 = Kernels.byName("gaussian3");
        Kernel padded5 = new Kernel(
                5, 5,
                new double[]{
                        0, 0, 0, 0, 0,
                        0, 1, 2, 1, 0,
                        0, 2, 4, 2, 0,
                        0, 1, 2, 1, 0,
                        0, 0, 0, 0, 0
                },
                1.0 / 16.0, 0.0
        );

        ColorImage out1 = Convolution.apply(input, k3);
        ColorImage out2 = Convolution.apply(input, padded5);

        assertImagesEqual(out1, out2);
    }

    /**
     * Проверяет, что два противоположных сдвига подряд возвращают изображение
     * в исходное состояние.
     * Тест выполняется для разных размеров изображений, включая маленькие случаи.
     */
    @Test
    void oppositeShiftFiltersShouldComposeToIdentityAcrossImageSizes() {
        Kernel shiftLeft = new Kernel(
                3, 3,
                new double[]{
                        0, 0, 0,
                        0, 0, 1,
                        0, 0, 0
                },
                1.0, 0.0
        );
        Kernel shiftRight = new Kernel(
                3, 3,
                new double[]{
                        0, 0, 0,
                        1, 0, 0,
                        0, 0, 0
                },
                1.0, 0.0
        );

        int seed = 5000;
        for (int width = 1; width <= 19; width++) {
            for (int height = 1; height <= 17; height++) {
                ColorImage input = randomImage(width, height, seed++);

                ColorImage shiftedBack = Convolution.apply(Convolution.apply(input, shiftLeft), shiftRight);

                assertImagesEqual(input, shiftedBack);
            }
        }
    }

    /**
     * Проверяет случайные усредняющие ядра разных размеров.
     * После применения таких ядер изображение должно сохранять исходные размеры,
     * корректную длину массива RGB-данных, а значения пикселей должны оставаться
     * в диапазоне от 0 до 255.
     */
    @Test
    void randomLinearKernelsShouldKeepSizeAndRangeAcrossManyDimensions() {
        Random random = new Random(7007);
        int[] kernelSizes = {1, 3, 5, 7};

        for (int width = 1; width <= 12; width++) {
            for (int height = 1; height <= 10; height++) {
                ColorImage input = randomImage(width, height, width * 1000L + height);

                for (int kernelSize : kernelSizes) {
                    Kernel kernel = randomAveragingKernel(kernelSize, random);
                    ColorImage output = Convolution.apply(input, kernel);

                    assertEquals(width, output.width);
                    assertEquals(height, output.height);
                    assertEquals(width * height * ColorImage.CHANNELS, output.data.length);
                    for (byte b : output.data) {
                        int value = b & 0xFF;
                        assertTrue(value >= 0 && value <= 255);
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

    private static ColorImage constantImage(int width, int height, int red, int green, int blue) {
        byte[] data = new byte[width * height * ColorImage.CHANNELS];
        for (int i = 0; i < data.length; i += ColorImage.CHANNELS) {
            data[i] = (byte) red;
            data[i + 1] = (byte) green;
            data[i + 2] = (byte) blue;
        }
        return new ColorImage(width, height, data);
    }

    private static Kernel randomAveragingKernel(int size, Random random) {
        double[] values = new double[size * size];
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextInt(5);
            sum += values[i];
        }
        if (sum == 0.0) {
            values[values.length / 2] = 1.0;
            sum = 1.0;
        }
        return new Kernel(size, size, values, 1.0 / sum, 0.0);
    }

    private static void assertImagesEqual(ColorImage expected, ColorImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }
}
