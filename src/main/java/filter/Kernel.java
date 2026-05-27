package filter;

public class Kernel {
    public final int width;
    public final int height;
    public final double[] values;
    public final double factor;
    public final double bias;

    public Kernel(int width, int height, double[] values, double factor, double bias) {
        // Я принимаю только нечётные размеры ядра, потому что для свёртки
        // нужен однозначный центральный элемент.
        if (width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("Kernel sizes must be odd");
        }
        if (values.length != width * height) {
            throw new IllegalArgumentException("Kernel value count does not match kernel size");
        }
        // factor нормирует сумму, а bias сдвигает яркость после свёртки.
        this.width = width;
        this.height = height;
        this.values = values;
        this.factor = factor;
        this.bias = bias;
    }
}
