import filter.ImageFilters;
import image.ColorImage;
import image.ImageUtils;
import parallel.ParallelStrategy;
import pipeline.BatchConfig;
import pipeline.BatchResult;
import pipeline.PipelineImageProcessor;

import java.io.IOException;
import java.nio.file.Path;
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
            case "apply-batch" -> {
                if (args.length != 7 && args.length != 9) {
                    printUsage();
                    return;
                }
                BatchConfig config = parseBatchConfig(args, 4);
                applyBatch(args[1], args[2], args[3], config);
            }
            case "benchmark-batch" -> {
                if (args.length != 8 && args.length != 10) {
                    printUsage();
                    return;
                }
                BatchConfig config = parseBatchConfig(args, 4);
                benchmarkBatch(args[1], args[2], args[3], config, Integer.parseInt(args[args.length - 1]));
            }
            default -> printUsage();
        }
    }

    private static void apply(String inputPath, String outputPath, String filterName) throws IOException {
        // Загружаю изображение один раз и замеряю только саму фильтрацию,
        // без чтения и записи файла.
        ColorImage input = ImageUtils.loadColor(inputPath);

        long start = System.nanoTime();
        ColorImage output = ImageFilters.apply(input, filterName);
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
        ColorImage output = ImageFilters.applyParallel(input, filterName, strategy, threads);
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
            ColorImage out = ImageFilters.apply(input, filterName);
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
            ColorImage out = ImageFilters.applyParallel(input, filterName, strategy, threads);
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

    private static void applyBatch(
            String inputDirectory,
            String outputDirectory,
            String filterName,
            BatchConfig config
    ) throws IOException {

        // Создаём объект процессора, который отвечает за обработку изображений.
        // Он будет запускать pipeline batch-обработки для всей папки.
        PipelineImageProcessor processor = new PipelineImageProcessor();

        // Запускаем обработку всех изображений из входной директории.
        // inputDirectory — папка, откуда берём исходные изображения.
        // outputDirectory — папка, куда сохраняем обработанные изображения.
        // filterName — название фильтра, который нужно применить.
        // config — настройки batch-обработки: количество потоков, размер очереди,
        // режим последовательной или параллельной обработки.
        BatchResult result = processor.processDirectory(
                Path.of(inputDirectory),
                Path.of(outputDirectory),
                filterName,
                config
        );

        // Выводим результат обработки в консоль:
        // статус выполнения, название фильтра, использованную конфигурацию
        // и статистику batch-обработки.
        printBatchResult("Done", filterName, config, result);
    }

    private static void benchmarkBatch(
            String inputDirectory,
            String outputDirectory,
            String filterName,
            BatchConfig config,
            int iterations
    ) throws IOException {
        PipelineImageProcessor processor = new PipelineImageProcessor();
        long total = 0;
        int files = 0;

        for (int i = 0; i < iterations; i++) {
            BatchResult result = processor.processDirectory(
                    Path.of(inputDirectory),
                    Path.of(outputDirectory),
                    filterName,
                    config
            );
            total += result.totalNanos();
            files = result.files();
            printBatchResult("Run " + (i + 1), filterName, config, result);
        }

        double avgMs = total / (double) iterations / 1_000_000.0;
        System.out.printf(Locale.US,
                "Average: filter=%s, files=%d, workers=%d, queue=%d, mode=%s, iterations=%d, avg=%.3f ms%n",
                filterName, files, config.convolutionWorkers(), config.queueCapacity(),
                batchMode(config), iterations, avgMs);
    }

    private static BatchConfig parseBatchConfig(String[] args, int offset) {
        // Считываем количество рабочих потоков из массива аргументов.
        // offset указывает, с какой позиции начинаются параметры batch-режима.
        int workers = Integer.parseInt(args[offset]);

        // Считываем вместимость очереди задач.
        // Очередь используется для передачи задач между частями программы.
        int queueCapacity = Integer.parseInt(args[offset + 1]);

        // Считываем режим выполнения batch-обработки:
        // sequential — последовательная обработка,
        // parallel — параллельная обработка.
        // toLowerCase(Locale.ROOT) нужен, чтобы режим корректно распознавался
        // независимо от регистра букв.
        String mode = args[offset + 2].toLowerCase(Locale.ROOT);

        // В зависимости от выбранного режима создаём соответствующую конфигурацию.
        return switch (mode) {

            // Если выбран sequential-режим, создаём конфигурацию
            // с последовательной обработкой изображений.
            case "sequential" -> BatchConfig.sequentialWorkers(workers, queueCapacity);

            // Если выбран parallel-режим, дополнительно нужно считать:
            // стратегию параллельной обработки и количество потоков для свёртки.
            case "parallel" -> {

                // Проверяем, что пользователь передал все обязательные параметры.
                // Для parallel-режима после mode должны быть ещё:
                // strategy и convolutionThreads.
                if (args.length < offset + 5) {
                    throw new IllegalArgumentException(
                            "Parallel batch mode requires strategy and convolutionThreads"
                    );
                }

                // Создаём конфигурацию параллельной batch-обработки.
                yield BatchConfig.parallelWorkers(
                        workers,                         // количество рабочих потоков
                        queueCapacity,                   // размер очереди задач
                        ParallelStrategy.parse(args[offset + 3]), // стратегия распараллеливания
                        Integer.parseInt(args[offset + 4])         // число потоков для свёртки
                );
            }

            // Если пользователь передал неизвестный режим,
            // выбрасываем ошибку с пояснением.
            default -> throw new IllegalArgumentException("Unknown batch mode: " + mode);
        };
    }

    private static void printDone(String filterName, ColorImage input, long elapsed) {
        // Выводим в консоль информацию о завершении обработки одного изображения.
        // Locale.US используется, чтобы дробные числа выводились через точку,
        // например 15.324, а не 15,324.
        System.out.printf(Locale.US,
                "Done. Filter=%s, size=%dx%d, time=%.3f ms, throughput=%.3f MPix/s%n",

                // Название применённого фильтра.
                filterName,

                // Ширина изображения.
                input.width,

                // Высота изображения.
                input.height,

                // Время обработки изображения.
                // elapsed хранится в наносекундах, поэтому делим на 1_000_000.0,
                // чтобы получить миллисекунды.
                elapsed / 1_000_000.0,

                // Производительность обработки изображения:
                // сколько мегапикселей обрабатывается за секунду.
                throughput(input, elapsed)
        );
    }

    private static void printAverage(
            String filterName,
            ColorImage input,
            int iterations,
            long total,
            int checksum
    ) {
        // Считаем среднее время одной обработки изображения.
        // total — суммарное время всех запусков в наносекундах.
        // Делим его на количество итераций, чтобы получить среднее время одного запуска.
        double avgNs = (double) total / iterations;

        // Выводим в консоль средние результаты benchmark-теста.
        // Locale.US нужен, чтобы дробные числа выводились через точку.
        System.out.printf(Locale.US,
                "Average: filter=%s, image=%dx%d, iterations=%d, avg=%.3f ms, throughput=%.3f MPix/s, checksum=%d%n",

                // Название фильтра, который тестировался.
                filterName,

                // Ширина изображения.
                input.width,

                // Высота изображения.
                input.height,

                // Количество запусков benchmark-теста.
                iterations,

                // Среднее время одной обработки в миллисекундах.
                avgNs / 1_000_000.0,

                // Производительность обработки в мегапикселях в секунду.
                throughput(input, (long) avgNs),

                // Контрольная сумма результата.
                // Она нужна, чтобы убедиться, что вычисления реально выполнялись
                // и результат обработки не был оптимизирован или потерян.
                checksum
        );
    }

    private static double throughput(ColorImage input, long elapsedNs) {
        // Считаем количество пикселей изображения в мегапикселях.
        // input.width * input.height даёт общее количество пикселей,
        // а деление на 1_000_000.0 переводит это значение в мегапиксели.
        double mpix = (double) input.width * input.height / 1_000_000.0;

        // Считаем производительность обработки.
        // elapsedNs — время обработки в наносекундах.
        // Делим его на 1_000_000_000.0, чтобы получить время в секундах.
        // Итоговая формула:
        // мегапиксели / секунды = мегапикселей в секунду.
        return mpix / (elapsedNs / 1_000_000_000.0);
    }

    private static void printBatchResult(
            String prefix,
            String filterName,
            BatchConfig config,
            BatchResult result
    ) {
        // Выводим в консоль итоговую статистику batch-обработки.
        // Locale.US используется, чтобы дробные числа выводились через точку,
        // например 12.345, а не 12,345.
        System.out.printf(Locale.US,
                "%s. Filter=%s, files=%d, workers=%d, queue=%d, mode=%s, total=%.3f ms, read=%.3f ms, convolution=%.3f ms, write=%.3f ms, avg=%.3f ms/file%n",

                // Текстовый префикс сообщения, например "Done" или "Benchmark".
                prefix,

                // Название применённого фильтра.
                filterName,

                // Количество успешно обработанных файлов.
                result.files(),

                // Количество worker-потоков, которые обрабатывали изображения.
                config.convolutionWorkers(),

                // Размер очереди между этапами pipeline.
                config.queueCapacity(),

                // Режим обработки: sequential или parallel.
                batchMode(config),

                // Общее время всей batch-обработки в миллисекундах.
                result.totalMillis(),

                // Время, потраченное на чтение изображений, переводим из наносекунд в миллисекунды.
                result.readNanos() / 1_000_000.0,

                // Время, потраченное на применение фильтра, переводим из наносекунд в миллисекунды.
                result.convolutionNanos() / 1_000_000.0,

                // Время, потраченное на сохранение изображений, переводим из наносекунд в миллисекунды.
                result.writeNanos() / 1_000_000.0,

                // Среднее время обработки одного файла.
                result.averageMillisPerFile()
        );
    }

    private static String batchMode(BatchConfig config) {
        // Если параллельная свёртка отключена,
        // значит batch-обработка работает в последовательном режиме.
        if (!config.parallelConvolution()) {
            return "sequential";
        }

        // Если параллельная свёртка включена,
        // формируем строку с подробным описанием режима:
        // parallel / название стратегии / количество потоков для свёртки.
        //
        // Например:
        // parallel/rows/4
        // parallel/grid/8
        return "parallel/"
                + config.strategy().name().toLowerCase(Locale.ROOT)
                + "/"
                + config.convolutionThreads();
    }

    private static void printUsage() {
        System.out.println("""
            Usage:
              java Main apply <input> <output> <filterName>
              java Main benchmark <input> <filterName> <iterations>
              java Main apply-parallel <input> <output> <filterName> <strategy> <threads>
              java Main benchmark-parallel <input> <filterName> <strategy> <threads> <iterations>
              java Main apply-batch <inputDir> <outputDir> <filterName> <workers> <queueCapacity> <sequential|parallel> [strategy] [convolutionThreads]
              java Main benchmark-batch <inputDir> <outputDir> <filterName> <workers> <queueCapacity> <sequential|parallel> [strategy] [convolutionThreads] <iterations>

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
