package pipeline;

import parallel.ParallelStrategy;

public record BatchConfig(
        // Количество рабочих потоков, которые обрабатывают изображения в batch-режиме.
        // Например, если изображений много, workers распределяют их между собой.
        int convolutionWorkers,

        // Вместимость очереди задач.
        // Очередь хранит изображения или задания, которые ожидают обработки.
        int queueCapacity,

        // Флаг, который показывает, нужно ли выполнять саму свёртку параллельно.
        // false — свёртка выполняется последовательно,
        // true — свёртка дополнительно распараллеливается.
        boolean parallelConvolution,

        // Стратегия параллельной обработки.
        // Определяет, каким способом изображение делится между потоками.
        ParallelStrategy strategy,

        // Количество потоков, которые используются именно для параллельной свёртки.
        // Этот параметр имеет смысл, когда parallelConvolution = true.
        int convolutionThreads
) {
    public BatchConfig {
        if (convolutionWorkers <= 0) {
            throw new IllegalArgumentException("Convolution worker count must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        if (parallelConvolution && convolutionThreads <= 0) {
            throw new IllegalArgumentException("Convolution thread count must be positive");
        }
        if (strategy == null) {
            strategy = ParallelStrategy.GRID;
        }
        if (!parallelConvolution) {
            convolutionThreads = 1;
        }
    }

    public static BatchConfig sequentialWorkers(int convolutionWorkers, int queueCapacity) {
        return new BatchConfig(convolutionWorkers, queueCapacity, false, ParallelStrategy.GRID, 1);
    }

    public static BatchConfig parallelWorkers(
            int convolutionWorkers,
            int queueCapacity,
            ParallelStrategy strategy,
            int convolutionThreads
    ) {
        return new BatchConfig(convolutionWorkers, queueCapacity, true, strategy, convolutionThreads);
    }
}
