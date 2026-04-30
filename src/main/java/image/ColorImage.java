package image;

public class ColorImage {
    public static final int CHANNELS = 3;

    public final int width;
    public final int height;
    public final byte[] data;

    public ColorImage(int width, int height, byte[] data) {
        // Я храню цветное изображение в плоском массиве RGB:
        // для каждого пикселя подряд идут R, G и B.
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
        // Перевожу координаты пикселя в индекс первого канала R.
        return (y * width + x) * CHANNELS;
    }
}
