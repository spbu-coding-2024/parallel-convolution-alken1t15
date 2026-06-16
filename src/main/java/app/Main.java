package app;

import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.ColorImage;
import image.ImageUtils;

import java.io.IOException;
import java.util.Locale;

public class Main {

    public static void main(String[] args) throws Exception {
        // Я оставил только режимы первого задания: применение фильтра и последовательный benchmark.
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "apply" -> {
                if (args.length != 4) {
                    printUsage();
                    return;
                }
                apply(args[1], args[2], args[3]);
            }
            case "benchmark" -> {
                if (args.length != 4) {
                    printUsage();
                    return;
                }
                benchmark(args[1], args[2], Integer.parseInt(args[3]));
            }
            default -> printUsage();
        }
    }

    private static void apply(String inputPath, String outputPath, String filterName) throws IOException {
        // Загружаю изображение один раз, а время измеряю только для самой фильтрации.
        ColorImage input = ImageUtils.loadColor(inputPath);

        long start = System.nanoTime();
        ColorImage output = applyFilter(input, filterName);
        long elapsed = System.nanoTime() - start;

        ImageUtils.saveColor(output, outputPath);

        System.out.printf(Locale.US,
                "Done. Filter=%s, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",
                filterName, input.width, input.height, elapsed / 1_000_000.0, throughput(input, elapsed));
    }

    private static void benchmark(String inputPath, String filterName, int iterations) throws IOException {
        // В benchmark я не учитываю чтение файла: проверяю только скорость алгоритма фильтрации.
        ColorImage input = ImageUtils.loadColor(inputPath);
        long total = 0;
        int checksum = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            ColorImage out = applyFilter(input, filterName);
            long elapsed = System.nanoTime() - start;

            total += elapsed;
            // Checksum я считаю, чтобы JVM не могла считать результат вычислений неиспользуемым.
            checksum += out.data[i % out.data.length] & 0xFF;
            System.out.printf(Locale.US, "Run %d: %.3f ms%n", i + 1, elapsed / 1_000_000.0);
        }

        double avgNs = (double) total / iterations;
        System.out.printf(Locale.US,
                "Average: filter=%s, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",
                filterName, input.width, input.height, iterations, avgNs / 1_000_000.0,
                throughput(input, (long) avgNs), checksum);
    }

    private static ColorImage applyFilter(ColorImage input, String filterName) {
        String name = filterName.toLowerCase(Locale.ROOT);
        // Median-фильтры я определяю по имени, потому что у них нет обычного ядра коэффициентов.
        if (name.startsWith("median")) {
            return MedianFilter.apply(input, parseMedianWindowSize(name));
        }

        Kernel kernel = Kernels.byName(name);
        return Convolution.apply(input, kernel);
    }

    private static int parseMedianWindowSize(String name) {
        // Размер окна беру из имени фильтра, чтобы CLI оставался простым.
        return switch (name) {
            case "median3" -> 3;
            case "median5" -> 5;
            case "median7" -> 7;
            default -> throw new IllegalArgumentException(
                    "Unknown median filter: " + name + ". Supported: median3, median5, median7"
            );
        };
    }

    private static double throughput(ColorImage input, long elapsedNs) {
        // Пропускную способность считаю в мегапикселях в секунду.
        double mpix = (double) input.width * input.height / 1_000_000.0;
        return mpix / (elapsedNs / 1_000_000_000.0);
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              java app.Main apply <input> <output> <filterName>
              java app.Main benchmark <input> <filterName> <iterations>

            Filters:
              identity
              blur3
              blur5
              gaussian3
              gaussian5
              gaussian3_exact
              motion9
              edge_horizontal5
              edge_vertical5
              edge_45deg5
              edge_all3
              sharpen3
              sharpen5
              edge_excessive3
              emboss3
              emboss5
              mean3
              median3
              median5
              median7
            """);
    }
}
