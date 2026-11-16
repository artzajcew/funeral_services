package dao;

import java.util.List;

/**
 * Результат запроса со страницей данных (для пагинации списков).
 */
public class Page<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int limit;

    public Page(List<T> items, long total, int page, int limit) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.limit = limit;
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }

    public int getTotalPages() {
        return limit <= 0 ? 0 : (int) Math.ceil((double) total / limit);
    }
}
