package pipeline;

import parallel.ParallelStrategy;

public record BatchConfig(
        int convolutionWorkers,
        int queueCapacity,
        boolean parallelConvolution,
        ParallelStrategy strategy,
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
