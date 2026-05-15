package pipeline;

import filter.ImageFilters;
import image.ColorImage;
import image.ImageUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import parallel.ParallelStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PipelineImageProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void sequentialPipelineShouldMatchSingleImageSequentialReference() throws IOException {
        Path input = tempDir.resolve("input");
        Path output = tempDir.resolve("output");
        Files.createDirectories(input);

        ColorImage imageA = randomImage(17, 11, 101);
        ColorImage imageB = randomImage(13, 19, 202);
        ImageUtils.saveColor(imageA, input.resolve("a.png").toString());
        ImageUtils.saveColor(imageB, input.resolve("b.png").toString());

        PipelineImageProcessor processor = new PipelineImageProcessor();
        BatchResult result = processor.processDirectory(
                input,
                output,
                "gaussian5",
                BatchConfig.sequentialWorkers(2, 1)
        );

        assertEquals(2, result.files());
        assertImagesEqual(ImageFilters.apply(imageA, "gaussian5"), ImageUtils.loadColor(output.resolve("a.png").toString()));
        assertImagesEqual(ImageFilters.apply(imageB, "gaussian5"), ImageUtils.loadColor(output.resolve("b.png").toString()));
    }

    @Test
    void parallelPipelineShouldPreserveRelativeDirectoriesAndMatchReference() throws IOException {
        Path input = tempDir.resolve("input");
        Path nested = input.resolve("nested");
        Path output = tempDir.resolve("output");
        Files.createDirectories(nested);

        ColorImage image = randomImage(23, 15, 303);
        ImageUtils.saveColor(image, nested.resolve("sample.png").toString());

        PipelineImageProcessor processor = new PipelineImageProcessor();
        BatchResult result = processor.processDirectory(
                input,
                output,
                "sharpen3",
                BatchConfig.parallelWorkers(2, 2, ParallelStrategy.ROWS, 3)
        );

        Path outputImage = output.resolve("nested").resolve("sample.png");
        assertEquals(1, result.files());
        assertImagesEqual(ImageFilters.apply(image, "sharpen3"), ImageUtils.loadColor(outputImage.toString()));
    }

    @Test
    void pipelineShouldIgnoreUnsupportedFilesAndProcessEmptyDirectories() throws IOException {
        Path input = tempDir.resolve("input");
        Path output = tempDir.resolve("output");
        Files.createDirectories(input);
        Files.writeString(input.resolve("notes.txt"), "not an image");

        PipelineImageProcessor processor = new PipelineImageProcessor();
        BatchResult result = processor.processDirectory(
                input,
                output,
                "identity",
                BatchConfig.sequentialWorkers(1, 1)
        );

        assertEquals(0, result.files());
        assertFalse(Files.exists(output.resolve("notes.txt")));
    }

    @Test
    void pipelineShouldMatchReferenceForSeveralFiltersAndQueueSizes() throws IOException {
        Path input = tempDir.resolve("input-many");
        Path output = tempDir.resolve("output-many");
        Files.createDirectories(input.resolve("a"));
        Files.createDirectories(input.resolve("b"));

        ColorImage first = randomImage(9, 6, 401);
        ColorImage second = randomImage(4, 12, 402);
        ColorImage third = randomImage(16, 10, 403);
        ImageUtils.saveColor(first, input.resolve("a").resolve("first.png").toString());
        ImageUtils.saveColor(second, input.resolve("b").resolve("second.png").toString());
        ImageUtils.saveColor(third, input.resolve("third.png").toString());

        PipelineImageProcessor processor = new PipelineImageProcessor();
        for (String filter : new String[]{"identity", "mean3", "median3"}) {
            BatchConfig config = filter.startsWith("median")
                    ? BatchConfig.parallelWorkers(3, 1, ParallelStrategy.GRID, 2)
                    : BatchConfig.sequentialWorkers(3, 2);

            BatchResult result = processor.processDirectory(input, output.resolve(filter), filter, config);

            assertEquals(3, result.files());
            assertImagesEqual(
                    ImageFilters.apply(first, filter),
                    ImageUtils.loadColor(output.resolve(filter).resolve("a").resolve("first.png").toString())
            );
            assertImagesEqual(
                    ImageFilters.apply(second, filter),
                    ImageUtils.loadColor(output.resolve(filter).resolve("b").resolve("second.png").toString())
            );
            assertImagesEqual(
                    ImageFilters.apply(third, filter),
                    ImageUtils.loadColor(output.resolve(filter).resolve("third.png").toString())
            );
        }
    }

    private static ColorImage randomImage(int width, int height, long seed) {
        Random random = new Random(seed);
        byte[] data = new byte[width * height * ColorImage.CHANNELS];
        random.nextBytes(data);
        return new ColorImage(width, height, data);
    }

    private static void assertImagesEqual(ColorImage expected, ColorImage actual) {
        assertEquals(expected.width, actual.width);
        assertEquals(expected.height, actual.height);
        assertArrayEquals(expected.data, actual.data);
    }
}
