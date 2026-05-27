package parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelImageProcessor {
    static void process(int width, int height, ParallelStrategy strategy, int threads, PixelWriter writer) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Thread count must be positive");
        }
        // Ограничение числа рабочих потоков
        int workerCount = Math.min(threads, width * height);

        switch (strategy) {
            case PIXELS -> processPixels(width, height, workerCount, writer);
            case ROWS -> processRows(width, height, Math.min(workerCount, height), writer);
            case COLUMNS -> processColumns(width, height, Math.min(workerCount, width), writer);
            case GRID -> processGrid(width, height, workerCount, writer);
        }
    }

    private static void processPixels(int width, int height, int workerCount, PixelWriter writer) {
        // В попиксельном режиме каждый поток забирает следующий пиксель
        // через общий счетчик. Так я получаю динамическое распределение работы.
        AtomicInteger nextPixel = new AtomicInteger(0);
        int totalPixels = width * height;

        runWorkers(workerCount, workerIndex -> {
            while (true) {
                int pixel = nextPixel.getAndIncrement();
                if (pixel >= totalPixels) {
                    return;
                }

                // Одномерный номер пикселя переводится в координаты
                writer.write(pixel % width, pixel / width);
            }
        });
    }

    private static void processRows(int width, int height, int workerCount, PixelWriter writer) {
        runWorkers(workerCount, workerIndex -> {
            // Каждому потоку отдаю непрерывный диапазон строк.
            // Это первая строка которую должен обработать поток.
            int fromY = workerIndex * height / workerCount;
            //Это граница конца диапазона.
            int toY = (workerIndex + 1) * height / workerCount;

            for (int y = fromY; y < toY; y++) {
                for (int x = 0; x < width; x++) {
                    writer.write(x, y);
                }
            }
        });
    }

    private static void processColumns(int width, int height, int workerCount, PixelWriter writer) {
        runWorkers(workerCount, workerIndex -> {
            // В этом режиме каждому потоку отдаю свой диапазон столбцов.
            // Это первая строка которую должен обработать поток.
            int fromX = workerIndex * width / workerCount;
            //Это граница конца диапазона.
            int toX = (workerIndex + 1) * width / workerCount;

            for (int y = 0; y < height; y++) {
                for (int x = fromX; x < toX; x++) {
                    writer.write(x, y);
                }
            }
        });
    }

    private static void processGrid(int width, int height, int workerCount, PixelWriter writer) {
        // Считается, на сколько строк блоков нужно разбить изображение.
        int gridRows = (int) Math.floor(Math.sqrt(workerCount));
        // Считается, на сколько столбцов блоков нужно разбить изображение.
        int gridColumns = (int) Math.ceil((double) workerCount / gridRows);
        // Общее количество блоков
        int blockCount = gridRows * gridColumns;

        runWorkers(blockCount, blockIndex -> {
            // Разбиваю картинку на прямоугольные блоки и отдаю каждому потоку свой блок.
            // Номер строки блока;
            int blockY = blockIndex / gridColumns;
            // Номер столбца блока.
            int blockX = blockIndex % gridColumns;

            // Вычисление границ блока по y
            int fromY = blockY * height / gridRows;
            int toY = (blockY + 1) * height / gridRows;
            //Вычисление границ блока по x
            int fromX = blockX * width / gridColumns;
            int toX = (blockX + 1) * width / gridColumns;

            for (int y = fromY; y < toY; y++) {
                for (int x = fromX; x < toX; x++) {
                    writer.write(x, y);
                }
            }
        });
    }

    private static void runWorkers(int workerCount, WorkerBody body) {
        //Здесь создаётся список куда будут складываться все созданные потоки
        List<Thread> workers = new ArrayList<>(workerCount);

        for (int i = 0; i < workerCount; i++) {
            int workerIndex = i;

            Thread worker = new Thread(() -> body.run(workerIndex), "image-worker-" + workerIndex);
            workers.add(worker);
            worker.start();
        }

        for (Thread worker : workers) {
            try {
                // Текущий поток ждёт пока worker закончит работу.
                worker.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Parallel image processing was interrupted", ex);
            }
        }
    }

    @FunctionalInterface
    private interface WorkerBody {
        void run(int workerIndex);
    }
}
