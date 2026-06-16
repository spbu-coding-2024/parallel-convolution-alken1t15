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
     * Результат после обработки должен полностью совпадать с исходным изображением.
     */
    @Test
    void identityShouldReturnSameImage() {
        ColorImage input = randomImage(17, 13, 42);

        ColorImage output = Convolution.apply(input, Kernels.byName("identity"));

        assertImagesEqual(input, output);
    }

    /**
     * Проверяет, что ядро свёртки, состоящее только из нулей,
     * превращает изображение в полностью чёрное.
     * Все значения RGB-каналов должны стать равны 0.
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
     * Также проверяется, что длина массива RGB-данных соответствует формуле:
     * ширина * высота * количество каналов.
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
     * Проверяет, что после применения разных фильтров значения пикселей
     * остаются в допустимом диапазоне от 0 до 255.
     * Это нужно, чтобы результат корректно представлял RGB-изображение.
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
     * Проверяет, что RGB-каналы изображения обрабатываются независимо.
     * Значения красного, зелёного и синего каналов не должны смешиваться между собой.
     */
    @Test
    void filtersShouldProcessRgbChannelsIndependently() {
        ColorImage input = new ColorImage(1, 1, new byte[]{10, 80, (byte) 200});

        ColorImage output = Convolution.apply(input, Kernels.byName("identity"));

        assertArrayEquals(new byte[]{10, 80, (byte) 200}, output.data);
    }

    /**
     * Проверяет, что медианный фильтр не изменяет постоянное изображение.
     * Если все пиксели имеют одинаковый цвет, результат должен совпадать
     * с исходным изображением для разных размеров окна фильтра.
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
     * отдельно по каждому RGB-каналу.
     * Испорченный центральный пиксель должен замениться нормальным значением
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
     * должны давать одинаковое изображение.
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

    private static void assertImagesEqual(ColorImage expected, ColorImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }
}