package com.ailibrary.book.service;

import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fills books.title_key for rows created before V4, in small batches. */
@Component
public class BookTitleKeyBackfill {
    private static final Logger log = LoggerFactory.getLogger(BookTitleKeyBackfill.class);
    private final BookRepository books;
    private final TransactionTemplate tx;

    public BookTitleKeyBackfill(BookRepository books, PlatformTransactionManager transactions) {
        this.books = books;
        this.tx = new TransactionTemplate(transactions);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int total = 0;
        while (true) {
            Integer done = tx.execute(s -> {
                List<Book> batch = books.findTop500ByTitleKeyIsNull();
                batch.forEach(Book::refreshTitleKey);
                books.saveAll(batch);
                return batch.size();
            });
            if (done == null || done == 0) break;
            total += done;
        }
        if (total > 0) log.info("Backfilled title_key for {} book(s)", total);
    }
}
