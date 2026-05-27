package parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelImageProcessor {
    static void process(int width, int height, ParallelStrategy strategy, int threads, PixelWriter writer) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Thread count must be positive");
        }
        // Не создаю больше рабочих потоков, чем пикселей: лишние потоки
        // в любом случае не получили бы работу.
        int workerCount = Math.min(threads, width * height);

        // Здесь выбираю только способ обхода координат. Саму операцию
        // над пикселем передаёт фильтр через writer.
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

                // Перевожу линейный индекс обратно в координаты изображения.
                writer.write(pixel % width, pixel / width);
            }
        });
    }

    private static void processRows(int width, int height, int workerCount, PixelWriter writer) {
        runWorkers(workerCount, workerIndex -> {
            // Каждому потоку отдаю непрерывный непересекающийся диапазон строк.
            // Целочисленное деление распределяет остаток между диапазонами.
            int fromY = workerIndex * height / workerCount;
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
            // В этом режиме каждому потоку отдаю непересекающийся диапазон
            // столбцов, а внутри диапазона прохожу все строки.
            int fromX = workerIndex * width / workerCount;
            int toX = (workerIndex + 1) * width / workerCount;

            for (int y = 0; y < height; y++) {
                for (int x = fromX; x < toX; x++) {
                    writer.write(x, y);
                }
            }
        });
    }

    private static void processGrid(int width, int height, int workerCount, PixelWriter writer) {
        // Подбираю почти квадратную сетку, чтобы блоки имели близкие размеры
        // и нагрузка распределялась равномерно.
        int gridRows = (int) Math.floor(Math.sqrt(workerCount));
        int gridColumns = (int) Math.ceil((double) workerCount / gridRows);
        // В прямоугольной сетке блоков иногда получается немного больше,
        // чем было запрошено потоков, зато всё изображение покрыто блоками.
        int blockCount = gridRows * gridColumns;

        runWorkers(blockCount, blockIndex -> {
            // По номеру блока нахожу его позицию в сетке.
            int blockY = blockIndex / gridColumns;
            int blockX = blockIndex % gridColumns;

            // Границы блоков вычисляю через пропорции, поэтому вместе
            // блоки покрывают все пиксели без пересечений и пропусков.
            int fromY = blockY * height / gridRows;
            int toY = (blockY + 1) * height / gridRows;
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
        // Храню ссылки на созданные потоки, чтобы дождаться окончания
        // всей обработки перед возвратом результата фильтра.
        List<Thread> workers = new ArrayList<>(workerCount);

        for (int i = 0; i < workerCount; i++) {
            int workerIndex = i;

            Thread worker = new Thread(() -> body.run(workerIndex), "image-worker-" + workerIndex);
            workers.add(worker);
            worker.start();
        }

        for (Thread worker : workers) {
            try {
                // Возвращаю управление вызывающему коду только после того,
                // как каждый рабочий поток заполнил свою часть результата.
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
