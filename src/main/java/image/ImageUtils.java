package image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public class ImageUtils {
    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpg", "jpeg", "bmp", "gif");

    public static ColorImage loadColor(String path) throws IOException {
        BufferedImage input = ImageIO.read(new File(path));
        if (input == null) {
            throw new IOException("Unsupported image format: " + path);
        }

        // Перевожу изображение в стабильный RGB-формат без потери цвета.
        BufferedImage rgb = new BufferedImage(
                input.getWidth(),
                input.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR
        );

        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(input, 0, 0, null);
        } finally {
            g.dispose();
        }

        byte[] bgr = ((DataBufferByte) rgb.getRaster().getDataBuffer()).getData();
        byte[] data = new byte[bgr.length];
        for (int i = 0; i < bgr.length; i += ColorImage.CHANNELS) {
            // BufferedImage хранит TYPE_3BYTE_BGR как BGR, а внутри проекта
            // я работаю с RGB, поэтому здесь меняю местами красный и синий.
            data[i] = bgr[i + 2];
            data[i + 1] = bgr[i + 1];
            data[i + 2] = bgr[i];
        }
        return new ColorImage(rgb.getWidth(), rgb.getHeight(), data);
    }

    public static void saveColor(ColorImage image, String path) throws IOException {
        BufferedImage output = new BufferedImage(
                image.width,
                image.height,
                BufferedImage.TYPE_3BYTE_BGR
        );

        byte[] dst = ((DataBufferByte) output.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < image.data.length; i += ColorImage.CHANNELS) {
            // При сохранении возвращаю байты из внутреннего RGB обратно в BGR,
            // потому что такой порядок ожидает BufferedImage.
            dst[i] = image.data[i + 2];
            dst[i + 1] = image.data[i + 1];
            dst[i + 2] = image.data[i];
        }

        String format = extractFormat(path);
        boolean ok = ImageIO.write(output, format, new File(path));
        if (!ok) {
            throw new IOException("No writer found for format: " + format);
        }
    }

    static String extractFormat(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || dot == path.length() - 1) {
            // Если расширение не указано, сохраняю как PNG.
            return "png";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupportedImage(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return false;
        }
        return SUPPORTED_FORMATS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
