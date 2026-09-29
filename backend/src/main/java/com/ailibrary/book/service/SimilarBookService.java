package com.ailibrary.book.service;

import com.ailibrary.book.dto.BookView;
import com.ailibrary.book.repository.BookRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SimilarBookService {
    private final BookService bookService; private final BookRepository books; private final ObjectProvider<VectorStore> stores;
    public SimilarBookService(BookService bookService, BookRepository books, ObjectProvider<VectorStore> stores){this.bookService=bookService;this.books=books;this.stores=stores;}
    public List<BookView> similar(UUID id,int limit){
        var seed=bookService.getEntity(id); VectorStore store=stores.getIfAvailable(); if(store==null)return List.of();
        String q=seed.getTitle()+" "+Objects.toString(seed.getCategoryNames(),"")+" "+Objects.toString(seed.getDescription(),"");
        try{
            var docs=store.similaritySearch(SearchRequest.builder().query(q).topK(Math.min(50,limit+8)).similarityThreshold(.40).filterExpression("type == 'book'").build());
            LinkedHashSet<UUID> ids=new LinkedHashSet<>();
            for(Document d:docs){Object raw=d.getMetadata().get("bookId");if(raw==null)continue;try{UUID x=UUID.fromString(raw.toString());if(!x.equals(id))ids.add(x);}catch(Exception ignored){}if(ids.size()>=limit)break;}
            Map<UUID,BookView> map=new HashMap<>();books.findAllById(ids).forEach(b->map.put(b.getId(),BookMapper.toView(b)));return ids.stream().map(map::get).filter(Objects::nonNull).toList();
        }catch(RuntimeException ex){return List.of();}
    }
}
