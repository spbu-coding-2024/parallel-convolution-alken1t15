package image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class ImageUtils {
    public static ColorImage loadColor(String path) throws IOException {
        // Я читаю исходный файл через ImageIO, чтобы поддерживать обычные форматы вроде PNG и JPG.
        BufferedImage input = ImageIO.read(new File(path));
        if (input == null) {
            throw new IOException("Unsupported image format: " + path);
        }

        // Я перевожу изображение в стабильный 3-байтовый формат без потери цвета.
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

        // В BufferedImage каналы лежат как BGR, поэтому я перекладываю их в более понятный порядок RGB.
        byte[] bgr = ((DataBufferByte) rgb.getRaster().getDataBuffer()).getData();
        byte[] data = new byte[bgr.length];
        for (int i = 0; i < bgr.length; i += ColorImage.CHANNELS) {
            data[i] = bgr[i + 2];
            data[i + 1] = bgr[i + 1];
            data[i + 2] = bgr[i];
        }
        return new ColorImage(rgb.getWidth(), rgb.getHeight(), data);
    }

    public static void saveColor(ColorImage image, String path) throws IOException {
        // Для сохранения я снова создаю BufferedImage в формате BGR, который хорошо поддерживается ImageIO.
        BufferedImage output = new BufferedImage(
                image.width,
                image.height,
                BufferedImage.TYPE_3BYTE_BGR
        );

        byte[] dst = ((DataBufferByte) output.getRaster().getDataBuffer()).getData();
        // Мой внутренний формат RGB, а BufferedImage ожидает BGR, поэтому меняю местами R и B.
        for (int i = 0; i < image.data.length; i += ColorImage.CHANNELS) {
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
        // Формат вывода я беру из расширения файла, а если расширения нет, сохраняю как PNG.
        int dot = path.lastIndexOf('.');
        if (dot == -1 || dot == path.length() - 1) {
            return "png";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
