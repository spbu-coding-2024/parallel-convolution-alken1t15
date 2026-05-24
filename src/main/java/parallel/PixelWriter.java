package parallel;

@FunctionalInterface
interface PixelWriter {
    // Через этот callback я отделяю разбиение изображения между потоками
    // от конкретного фильтра, который записывает пиксель.
    void write(int x, int y);
}
