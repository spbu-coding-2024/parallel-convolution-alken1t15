package app;

import filter.Convolution;
import filter.Kernel;
import filter.Kernels;
import filter.MedianFilter;
import image.ColorImage;
import image.ImageUtils;
import parallel.ParallelConvolution;
import parallel.ParallelMedianFilter;
import parallel.ParallelStrategy;

import java.io.IOException;
import java.util.Locale;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        // Сначала разбираю режим запуска, а уже потом проверяю аргументы
        // конкретной команды. Так CLI остаётся одним для последовательной
        // и параллельной версии.
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
            case "apply-parallel" -> {
                if (args.length != 6) {
                    printUsage();
                    return;
                }
                ParallelStrategy strategy = ParallelStrategy.parse(args[4]);
                applyParallel(args[1], args[2], args[3], strategy, Integer.parseInt(args[5]));
            }
            case "benchmark-parallel" -> {
                if (args.length != 6) {
                    printUsage();
                    return;
                }
                ParallelStrategy strategy = ParallelStrategy.parse(args[3]);
                benchmarkParallel(args[1], args[2], strategy, Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            }
            default -> printUsage();
        }
    }

    private static void apply(String inputPath, String outputPath, String filterName) throws IOException {
        // Загружаю изображение один раз и замеряю только саму фильтрацию,
        // без чтения и записи файла.
        ColorImage input = ImageUtils.loadColor(inputPath);

        long start = System.nanoTime();
        ColorImage output = applyFilter(input, filterName);
        long elapsed = System.nanoTime() - start;

        ImageUtils.saveColor(output, outputPath);
        printDone(filterName, input, elapsed);
    }

    private static void applyParallel(
            String inputPath,
            String outputPath,
            String filterName,
            ParallelStrategy strategy,
            int threads
    ) throws IOException {
        // В параллельном режиме логика такая же, но работу по пикселям
        // делит выбранная стратегия.
        ColorImage input = ImageUtils.loadColor(inputPath);

        long start = System.nanoTime();
        ColorImage output = applyFilterParallel(input, filterName, strategy, threads);
        long elapsed = System.nanoTime() - start;

        ImageUtils.saveColor(output, outputPath);

        double ms = elapsed / 1_000_000.0;
        double mpixPerSec = throughput(input, elapsed);
        System.out.printf(Locale.US,
                "Done. Filter=%s, strategy=%s, threads=%d, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",
                filterName, strategy.name().toLowerCase(Locale.ROOT), threads, input.width, input.height, ms, mpixPerSec);
    }

    private static void benchmark(String inputPath, String filterName, int iterations) throws IOException {
        ColorImage input = ImageUtils.loadColor(inputPath);
        long total = 0;
        int checksum = 0;

        for (int i = 0; i < iterations; i++) {
            // checksum заставляет JVM реально использовать результат фильтра,
            // чтобы замер не превратился в бесполезный цикл.
            long start = System.nanoTime();
            ColorImage out = applyFilter(input, filterName);
            long elapsed = System.nanoTime() - start;

            total += elapsed;
            checksum += out.data[i % out.data.length] & 0xFF;
            System.out.printf(Locale.US, "Run %d: %.3f ms%n", i + 1, elapsed / 1_000_000.0);
        }

        printAverage(filterName, input, iterations, total, checksum);
    }

    private static void benchmarkParallel(
            String inputPath,
            String filterName,
            ParallelStrategy strategy,
            int threads,
            int iterations
    ) throws IOException {
        ColorImage input = ImageUtils.loadColor(inputPath);
        long total = 0;
        int checksum = 0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            ColorImage out = applyFilterParallel(input, filterName, strategy, threads);
            long elapsed = System.nanoTime() - start;

            total += elapsed;
            checksum += out.data[i % out.data.length] & 0xFF;
            System.out.printf(Locale.US, "Run %d: %.3f ms%n", i + 1, elapsed / 1_000_000.0);
        }

        double avgNs = (double) total / iterations;
        System.out.printf(Locale.US,
                "Average: filter=%s, strategy=%s, threads=%d, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",
                filterName, strategy.name().toLowerCase(Locale.ROOT), threads,
                input.width, input.height, iterations, avgNs / 1_000_000.0,
                throughput(input, (long) avgNs), checksum);
    }

    private static ColorImage applyFilter(ColorImage input, String filterName) {
        String name = filterName.toLowerCase(Locale.ROOT);
        if (name.startsWith("median")) {
            // Median filter не задаётся ядром свёртки, поэтому обрабатываю его
            // отдельной веткой.
            return MedianFilter.apply(input, parseMedianWindowSize(name));
        }

        Kernel kernel = Kernels.byName(name);
        return Convolution.apply(input, kernel);
    }

    private static ColorImage applyFilterParallel(
            ColorImage input,
            String filterName,
            ParallelStrategy strategy,
            int threads
    ) {
        String name = filterName.toLowerCase(Locale.ROOT);
        if (name.startsWith("median")) {
            return ParallelMedianFilter.apply(input, parseMedianWindowSize(name), strategy, threads);
        }

        Kernel kernel = Kernels.byName(name);
        return ParallelConvolution.apply(input, kernel, strategy, threads);
    }

    private static int parseMedianWindowSize(String name) {
        return switch (name) {
            case "median3" -> 3;
            case "median5" -> 5;
            case "median7" -> 7;
            default -> throw new IllegalArgumentException(
                    "Unknown median filter: " + name + ". Supported: median3, median5, median7"
            );
        };
    }

    private static void printDone(String filterName, ColorImage input, long elapsed) {
        System.out.printf(Locale.US,
                "Done. Filter=%s, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",
                filterName, input.width, input.height, elapsed / 1_000_000.0, throughput(input, elapsed));
    }

    private static void printAverage(String filterName, ColorImage input, int iterations, long total, int checksum) {
        double avgNs = (double) total / iterations;
        System.out.printf(Locale.US,
                "Average: filter=%s, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",
                filterName, input.width, input.height, iterations, avgNs / 1_000_000.0,
                throughput(input, (long) avgNs), checksum);
    }

    private static double throughput(ColorImage input, long elapsedNs) {
        double mpix = (double) input.width * input.height / 1_000_000.0;
        return mpix / (elapsedNs / 1_000_000_000.0);
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              java Main apply <input> <output> <filterName>
              java Main benchmark <input> <filterName> <iterations>
              java Main apply-parallel <input> <output> <filterName> <strategy> <threads>
              java Main benchmark-parallel <input> <filterName> <strategy> <threads> <iterations>

            Parallel strategies:
              pixels
              rows
              columns
              grid

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
