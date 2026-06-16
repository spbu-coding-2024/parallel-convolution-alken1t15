package filter;

public class Kernel {
    public final int width;
    public final int height;
    public final double[] values;
    public final double factor;
    public final double bias;

    public Kernel(int width, int height, double[] values, double factor, double bias) {
        // Я принимаю только ядра с нечётными размерами, потому что у свёртки
        // должен быть один центральный коэффициент относительно текущего пикселя.
        if (width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("Kernel sizes must be odd");
        }
        // Число коэффициентов должно точно соответствовать прямоугольной
        // форме ядра, иначе расчёт соседей использовал бы некорректные данные.
        if (values.length != width * height) {
            throw new IllegalArgumentException("Kernel value count does not match kernel size");
        }
        this.width = width;
        this.height = height;
        this.values = values;
        // factor нормирует полученную сумму, а bias позволяет добавить
        // постоянный сдвиг яркости, например для эффекта emboss.
        this.factor = factor;
        this.bias = bias;
    }
}
