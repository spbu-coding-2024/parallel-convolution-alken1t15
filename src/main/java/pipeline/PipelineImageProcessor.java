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
    private static final ImageJob READ_DONE = new ImageJob(-1, null, null, null);
    private static final WriteJob WRITE_DONE = new WriteJob(-1, null, null, 0L);

    public BatchResult processDirectory(
            Path inputDirectory,
            Path outputDirectory,
            String filterName,
            BatchConfig config
    ) throws IOException {
        if (!Files.isDirectory(inputDirectory)) {
            throw new IOException("Input path is not a directory: " + inputDirectory);
        }

        List<Path> inputs = listImages(inputDirectory);
        Files.createDirectories(outputDirectory);

        BlockingQueue<ImageJob> readQueue = new ArrayBlockingQueue<>(config.queueCapacity());
        BlockingQueue<WriteJob> writeQueue = new ArrayBlockingQueue<>(config.queueCapacity());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger written = new AtomicInteger();
        AtomicLong readNanos = new AtomicLong();
        AtomicLong convolutionNanos = new AtomicLong();
        AtomicLong writeNanos = new AtomicLong();

        long start = System.nanoTime();

        Thread reader = new Thread(() -> runReader(
                inputDirectory,
                inputs,
                config.convolutionWorkers(),
                readQueue,
                readNanos,
                failure
        ), "pipeline-reader");

        Thread writer = new Thread(() -> runWriter(
                outputDirectory,
                writeQueue,
                written,
                writeNanos,
                failure
        ), "pipeline-writer");

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

        writer.start();
        for (Thread worker : workers) {
            worker.start();
        }
        reader.start();

        join(reader);
        for (Thread worker : workers) {
            join(worker);
        }
        putUnchecked(writeQueue, WRITE_DONE);
        join(writer);

        Throwable error = failure.get();
        if (error != null) {
            throw new IOException("Pipeline processing failed", error);
        }

        long total = System.nanoTime() - start;
        return new BatchResult(written.get(), total, readNanos.get(), convolutionNanos.get(), writeNanos.get());
    }

    private static List<Path> listImages(Path inputDirectory) throws IOException {
        try (Stream<Path> stream = Files.walk(inputDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(ImageUtils::isSupportedImage)
                    .sorted(Comparator.comparing(path -> inputDirectory.relativize(path).toString()))
                    .toList();
        }
    }

    private static void runReader(
            Path inputDirectory,
            List<Path> inputs,
            int workerCount,
            BlockingQueue<ImageJob> readQueue,
            AtomicLong readNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            for (int i = 0; i < inputs.size() && failure.get() == null; i++) {
                Path input = inputs.get(i);
                long start = System.nanoTime();
                ColorImage image = ImageUtils.loadColor(input.toString());
                readNanos.addAndGet(System.nanoTime() - start);

                Path relative = inputDirectory.relativize(input);
                readQueue.put(new ImageJob(i, relative, input, image));
            }
        } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
        } finally {
            for (int i = 0; i < workerCount; i++) {
                putUnchecked(readQueue, READ_DONE);
            }
        }
    }

    private static void runWorker(
            String filterName,
            BatchConfig config,
            BlockingQueue<ImageJob> readQueue,
            BlockingQueue<WriteJob> writeQueue,
            AtomicLong convolutionNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            while (failure.get() == null) {
                ImageJob job = readQueue.take();
                if (job == READ_DONE) {
                    return;
                }

                long start = System.nanoTime();
                ColorImage output = config.parallelConvolution()
                        ? ImageFilters.applyParallel(
                        job.image(),
                        filterName,
                        config.strategy(),
                        config.convolutionThreads()
                )
                        : ImageFilters.apply(job.image(), filterName);
                long elapsed = System.nanoTime() - start;
                convolutionNanos.addAndGet(elapsed);

                writeQueue.put(new WriteJob(job.index(), job.relativePath(), output, elapsed));
            }
        } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private static void runWriter(
            Path outputDirectory,
            BlockingQueue<WriteJob> writeQueue,
            AtomicInteger written,
            AtomicLong writeNanos,
            AtomicReference<Throwable> failure
    ) {
        try {
            while (failure.get() == null) {
                WriteJob job = writeQueue.take();
                if (job == WRITE_DONE) {
                    return;
                }

                Path output = outputDirectory.resolve(job.relativePath());
                Files.createDirectories(output.getParent());

                long start = System.nanoTime();
                ImageUtils.saveColor(job.image(), output.toString());
                writeNanos.addAndGet(System.nanoTime() - start);
                written.incrementAndGet();
            }
        } catch (Throwable ex) {
            failure.compareAndSet(null, ex);
        }
    }

    private static <T> void putUnchecked(BlockingQueue<T> queue, T item) {
        boolean interrupted = false;
        while (true) {
            try {
                queue.put(item);
                break;
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread thread) throws IOException {
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Pipeline processing was interrupted", ex);
        }
    }

    private record ImageJob(int index, Path relativePath, Path sourcePath, ColorImage image) {
    }

    private record WriteJob(int index, Path relativePath, ColorImage image, long convolutionNanos) {
    }
}
