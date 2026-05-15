package pipeline;

public record BatchResult(
        int files,
        long totalNanos,
        long readNanos,
        long convolutionNanos,
        long writeNanos
) {
    public double totalMillis() {
        return totalNanos / 1_000_000.0;
    }

    public double averageMillisPerFile() {
        return files == 0 ? 0.0 : totalMillis() / files;
    }
}
