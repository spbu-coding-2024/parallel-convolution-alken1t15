package image;

public class ColorImage {
    // Я храню цвет как три канала на пиксель: R, G, B.
    public static final int CHANNELS = 3;

    public final int width;
    public final int height;
    public final byte[] data;

    public ColorImage(int width, int height, byte[] data) {
        // Я сразу проверяю входные данные, чтобы дальше фильтры работали только с корректным изображением.
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image size must be positive");
        }
        if (data.length != width * height * CHANNELS) {
            throw new IllegalArgumentException("Data length does not match RGB image size");
        }
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public static int offset(int width, int x, int y) {
        // Я перевожу координаты пикселя в позицию первого канала R внутри одномерного массива.
        return (y * width + x) * CHANNELS;
    }
}
