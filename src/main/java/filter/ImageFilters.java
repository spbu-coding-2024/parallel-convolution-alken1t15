package filter;

import image.ColorImage;
import parallel.ParallelConvolution;
import parallel.ParallelMedianFilter;
import parallel.ParallelStrategy;

import java.util.Locale;

public class ImageFilters {
    public static ColorImage apply(ColorImage input, String filterName) {
        String name = filterName.toLowerCase(Locale.ROOT);
        if (name.startsWith("median")) {
            return MedianFilter.apply(input, parseMedianWindowSize(name));
        }

        return Convolution.apply(input, Kernels.byName(name));
    }

    public static ColorImage applyParallel(
            ColorImage input,
            String filterName,
            ParallelStrategy strategy,
            int threads
    ) {
        String name = filterName.toLowerCase(Locale.ROOT);
        if (name.startsWith("median")) {
            return ParallelMedianFilter.apply(input, parseMedianWindowSize(name), strategy, threads);
        }

        return ParallelConvolution.apply(input, Kernels.byName(name), strategy, threads);
    }

    public static int parseMedianWindowSize(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "median3" -> 3;
            case "median5" -> 5;
            case "median7" -> 7;
            default -> throw new IllegalArgumentException(
                    "Unknown median filter: " + name + ". Supported: median3, median5, median7"
            );
        };
    }
}
