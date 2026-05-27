package pipeline;

import filter.ImageFilters;
import image.ColorImage;
import image.ImageUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class PipelineImageProcessor {
    // Специальная задача-маркер, которая означает, что чтение изображений завершено.
    // Такой объект кладётся в очередь, чтобы worker-потоки поняли, что новых задач больше не будет.
    private static final ImageJob READ_DONE = new ImageJob(-1, null, null, null);

    // Специальная задача-маркер, которая означает, что запись изображений завершена.
    // Она нужна, чтобы writer-поток корректно завершил свою работу.
    private static final WriteJob WRITE_DONE = new WriteJob(-1, null, null, 0L);

    /**
     * Обрабатывает все изображения из входной директории и сохраняет результат в выходную директорию.
     *
     * Метод строит pipeline из трёх этапов:
     * 1. reader читает изображения с диска;
     * 2. worker-потоки применяют фильтр;
     * 3. writer сохраняет обработанные изображения.
     *
     * @param inputDirectory папка с исходными изображениями
     * @param outputDirectory папка для сохранения обработанных изображений
     * @param filterName название фильтра, который нужно применить
     * @param config конфигурация batch-обработки
     * @return результат обработки: количество файлов и время работы этапов
     * @throws IOException если произошла ошибка чтения, записи или выполнения pipeline
     */
    public BatchResult processDirectory(
            Path inputDirectory,
            Path outputDirectory,
            String filterName,
            BatchConfig config
    ) throws IOException {

        // Проверяем, что входной путь действительно является директорией.
        if (!Files.isDirectory(inputDirectory)) {
            throw new IOException("Input path is not a directory: " + inputDirectory);
        }

        // Получаем список всех поддерживаемых изображений из входной папки.
        List<Path> inputs = listImages(inputDirectory);

        // Создаём выходную директорию, если она ещё не существует.
        Files.createDirectories(outputDirectory);

        // Очередь между reader-потоком и worker-потоками.
        // Reader кладёт сюда прочитанные изображения, а workers забирают их для обработки.
        BlockingQueue<ImageJob> readQueue = new ArrayBlockingQueue<>(config.queueCapacity());

        // Очередь между worker-потоками и writer-потоком.
        // Workers кладут сюда обработанные изображения, а writer сохраняет их на диск.
        BlockingQueue<WriteJob> writeQueue = new ArrayBlockingQueue<>(config.queueCapacity());

        // Хранит первую ошибку, которая произошла в любом потоке pipeline.
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // Счётчик успешно записанных изображений.
        AtomicInteger written = new AtomicInteger();

        // Суммарное время чтения изображений.
        AtomicLong readNanos = new AtomicLong();

        // Суммарное время применения фильтра.
        AtomicLong convolutionNanos = new AtomicLong();

        // Суммарное время записи изображений.
        AtomicLong writeNanos = new AtomicLong();

        // Запоминаем время начала всей batch-обработки.
        long start = System.nanoTime();

        // Поток чтения изображений.
        Thread reader = new Thread(() -> runReader(
                inputDirectory,
                inputs,
                config.convolutionWorkers(),
                readQueue,
                readNanos,
                failure
        ), "pipeline-reader");

        // Поток записи готовых изображений.
        Thread writer = new Thread(() -> runWriter(
                outputDirectory,
                writeQueue,
                written,
                writeNanos,
                failure
        ), "pipeline-writer");

        // Создаём worker-потоки, которые будут применять фильтр к изображениям.
        Thread[] workers = new Thread[config.convolutionWorkers()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> runWorker(
                    filterName,
                    config,
                    readQueue,
                    writeQueue,
                    convolutionNanos,
                    failure
            ), "pipeline-convolution-" + i);
        }

        // Запускаем writer первым, чтобы он уже был готов принимать обработанные изображения.
        writer.start();

        // Запускаем все worker-потоки.
        for (Thread worker : workers) {
            worker.start();
        }

        // Запускаем reader, который начнёт читать изображения и отправлять их workers.
        reader.start();

        // Ждём завершения чтения изображений.
        join(reader);

        // Ждём завершения всех worker-потоков.
        for (Thread worker : workers) {
            join(worker);
        }

        // После завершения workers сообщаем writer-потоку, что новых изображений больше не будет.
        putUnchecked(writeQueue, WRITE_DONE);

        // Ждём завершения записи изображений.
        join(writer);

        // Если в одном из потоков произошла ошибка, пробрасываем её как IOException.
        Throwable error = failure.get();
        if (error != null) {
            throw new IOException("Pipeline processing failed", error);
        }

        // Считаем общее время работы pipeline.
        long total = System.nanoTime() - start;

        // Возвращаем статистику batch-обработки.
        return new BatchResult(
                written.get(),
                total,
                readNanos.get(),
                convolutionNanos.get(),
                writeNanos.get()
        );
    }

    /**
     * Находит все поддерживаемые изображения во входной директории.
     *
     * @param inputDirectory папка, в которой ищем изображения
     * @return отсортированный список путей к изображениям
     * @throws IOException если произошла ошибка при обходе директории
     */
    private static List<Path> listImages(Path inputDirectory) throws IOException {
        try (Stream<Path> stream = Files.walk(inputDirectory)) {
            return stream
                    // Берём только обычные файлы, а не папки.
                    .filter(Files::isRegularFile)

                    // Оставляем только изображения поддерживаемых форматов.
                    .filter(ImageUtils::isSupportedImage)

                    // Сортируем файлы по относительному пути,
                    // чтобы порядок обработки был предсказуемым.
                    .sorted(Comparator.comparing(path -> inputDirectory.relativize(path).toString()))

                    // Собираем результат в список.
                    .toList();
        }
    }

    /**
     * Читает изображения с диска и отправляет их в очередь readQueue.
     */
    private static void runReader(
            Path inputDirectory,
            List<Path> inputs,
            int workerCount,
            BlockingQueue<ImageJob> readQueue,
            AtomicLong readNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            // Читаем изображения по одному, пока они не закончатся
            // или пока в другом потоке не произойдёт ошибка.
            for (int i = 0; i < inputs.size() && failure.get() == null; i++) {
                Path input = inputs.get(i);

                // Засекаем время чтения конкретного изображения.
                long start = System.nanoTime();

                // Загружаем изображение в объект ColorImage.
                ColorImage image = ImageUtils.loadColor(input.toString());

                // Добавляем время чтения к общей статистике.
                readNanos.addAndGet(System.nanoTime() - start);

                // Получаем относительный путь файла,
                // чтобы сохранить структуру папок в outputDirectory.
                Path relative = inputDirectory.relativize(input);

                // Передаём прочитанное изображение worker-потокам.
                readQueue.put(new ImageJob(i, relative, input, image));
            }
        } catch (Throwable ex) {
            // Сохраняем ошибку, если она ещё не была сохранена другим потоком.
            failure.compareAndSet(null, ex);
        } finally {
            // Отправляем каждому worker-потоку специальный маркер завершения.
            // Это нужно, чтобы все workers корректно вышли из цикла ожидания задач.
            for (int i = 0; i < workerCount; i++) {
                putUnchecked(readQueue, READ_DONE);
            }
        }
    }

    /**
     * Забирает изображения из readQueue, применяет к ним фильтр
     * и отправляет результат в writeQueue.
     */
    private static void runWorker(
            String filterName,
            BatchConfig config,
            BlockingQueue<ImageJob> readQueue,
            BlockingQueue<WriteJob> writeQueue,
            AtomicLong convolutionNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            // Worker работает до тех пор, пока не произошла ошибка
            // или пока он не получит READ_DONE.
            while (failure.get() == null) {

                // Берём следующую задачу из очереди.
                // Если очередь пуста, поток будет ждать.
                ImageJob job = readQueue.take();

                // Если пришёл маркер завершения, значит новых изображений больше нет.
                if (job == READ_DONE) {
                    return;
                }

                // Засекаем время применения фильтра.
                long start = System.nanoTime();

                // Если в конфигурации включена параллельная свёртка,
                // применяем фильтр параллельно.
                // Иначе используем обычную последовательную обработку.
                ColorImage output = config.parallelConvolution()
                        ? ImageFilters.applyParallel(
                        job.image(),
                        filterName,
                        config.strategy(),
                        config.convolutionThreads()
                )
                        : ImageFilters.apply(job.image(), filterName);

                // Считаем время обработки текущего изображения.
                long elapsed = System.nanoTime() - start;

                // Добавляем это время к общей статистике свёртки.
                convolutionNanos.addAndGet(elapsed);

                // Передаём обработанное изображение writer-потоку.
                writeQueue.put(new WriteJob(
                        job.index(),
                        job.relativePath(),
                        output,
                        elapsed
                ));
            }
        } catch (Throwable ex) {
            // Сохраняем ошибку, если она произошла во время обработки.
            failure.compareAndSet(null, ex);
        }
    }

    /**
     * Забирает обработанные изображения из writeQueue
     * и сохраняет их в выходную директорию.
     */
    private static void runWriter(
            Path outputDirectory,
            BlockingQueue<WriteJob> writeQueue,
            AtomicInteger written,
            AtomicLong writeNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            // Writer работает до тех пор, пока не произошла ошибка
            // или пока он не получит WRITE_DONE.
            while (failure.get() == null) {

                // Берём следующую задачу на запись.
                // Если очередь пуста, поток будет ждать.
                WriteJob job = writeQueue.take();

                // Если пришёл маркер завершения, заканчиваем работу writer-потока.
                if (job == WRITE_DONE) {
                    return;
                }

                // Формируем путь для сохранения изображения.
                // relativePath позволяет сохранить структуру папок.
                Path output = outputDirectory.resolve(job.relativePath());

                // Создаём родительские папки, если они ещё не существуют.
                Files.createDirectories(output.getParent());

                // Засекаем время записи файла.
                long start = System.nanoTime();

                // Сохраняем обработанное изображение.
                ImageUtils.saveColor(job.image(), output.toString());

                // Добавляем время записи к общей статистике.
                writeNanos.addAndGet(System.nanoTime() - start);

                // Увеличиваем счётчик успешно записанных изображений.
                written.incrementAndGet();
            }
        } catch (Throwable ex) {
            // Сохраняем ошибку, если она произошла во время записи.
            failure.compareAndSet(null, ex);
        }
    }

    /**
     * Кладёт элемент в BlockingQueue, не пробрасывая InterruptedException наружу.
     *
     * Метод повторяет попытку put, если поток был прерван.
     * После успешного добавления элемента восстанавливает флаг прерывания.
     */
    private static <T> void putUnchecked(BlockingQueue<T> queue, T item) {
        boolean interrupted = false;

        while (true) {
            try {
                // Пытаемся положить элемент в очередь.
                // Если очередь заполнена, поток будет ждать свободного места.
                queue.put(item);
                break;
            } catch (InterruptedException ex) {
                // Запоминаем, что поток был прерван,
                // но продолжаем попытку положить элемент в очередь.
                interrupted = true;
            }
        }

        // Восстанавливаем interrupted-статус потока,
        // чтобы внешний код мог узнать о прерывании.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ожидает завершения указанного потока.
     *
     * @param thread поток, завершения которого нужно дождаться
     * @throws IOException если ожидание было прервано
     */
    private static void join(Thread thread) throws IOException {
        try {
            // Ждём, пока поток завершит выполнение.
            thread.join();
        } catch (InterruptedException ex) {
            // Если текущий поток был прерван, восстанавливаем флаг прерывания.
            Thread.currentThread().interrupt();

            // Пробрасываем ошибку выше как IOException,
            // потому что processDirectory уже работает с IOException.
            throw new IOException("Pipeline processing was interrupted", ex);
        }
    }

    /**
     * Задача на чтение и обработку изображения.
     *
     * @param index порядковый номер изображения в списке
     * @param relativePath относительный путь изображения внутри inputDirectory
     * @param sourcePath полный путь к исходному изображению
     * @param image загруженное изображение
     */
    private record ImageJob(
            int index,
            Path relativePath,
            Path sourcePath,
            ColorImage image
    ) {
    }

    /**
     * Задача на запись обработанного изображения.
     *
     * @param index порядковый номер изображения
     * @param relativePath относительный путь для сохранения результата
     * @param image обработанное изображение
     * @param convolutionNanos время, потраченное на применение фильтра
     */
    private record WriteJob(
            int index,
            Path relativePath,
            ColorImage image,
            long convolutionNanos
    ) {
    }
}
