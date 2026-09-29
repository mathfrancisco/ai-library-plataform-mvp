package com.ailibrary.search;

import com.ailibrary.ai.AiFacade;
import com.ailibrary.book.domain.Book;
import com.ailibrary.book.repository.BookRepository;
import com.ailibrary.book.service.BookMapper;
import com.ailibrary.catalog.CatalogBook;
import com.ailibrary.catalog.CatalogService;
import com.ailibrary.search.SearchDtos.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HybridSearchService {
    private final BookRepository books; private final CatalogService catalog; private final ObjectProvider<VectorStore> vectorStore; private final AiFacade ai;
    public HybridSearchService(BookRepository books, CatalogService catalog, ObjectProvider<VectorStore> vectorStore, AiFacade ai){ this.books=books; this.catalog=catalog; this.vectorStore=vectorStore; this.ai=ai; }

    public List<SearchHit> search(String query, SearchMode mode, int limit){
        Map<String, MutableHit> merged = new LinkedHashMap<>();
        if(mode!=SearchMode.SEMANTIC) addLexical(query,limit,merged);
        if(mode!=SearchMode.LEXICAL) addSemantic(query,limit,merged);
        if(mode==SearchMode.HYBRID || mode==SearchMode.LEXICAL) addExternal(query,limit,merged);
        return merged.values().stream().sorted(Comparator.comparingDouble(MutableHit::score).reversed()).limit(limit).map(MutableHit::toView).toList();
    }

    public DiscoveryResponse discover(UUID userId, String prompt, int limit){
        DiscoveryPlan plan;
        try {
            plan=ai.structured(userId,"DISCOVERY_QUERY", "Convert a reader request into a concise book-search plan. query should contain provider-friendly keywords. Do not invent specific titles unless user named them.", prompt, DiscoveryPlan.class);
        } catch(RuntimeException ex){ plan=new DiscoveryPlan(prompt,null,null,List.of()); }
        return new DiscoveryResponse(plan, search(plan.query()==null?prompt:plan.query(), SearchMode.HYBRID, limit));
    }

    private void addLexical(String query,int limit,Map<String,MutableHit> out){
        List<Book> rows=books.lexicalSearch(query,limit); int rank=1;
        for(Book b:rows){ String key="local:"+b.getId(); out.computeIfAbsent(key,k->MutableHit.local(b,"LEXICAL")).add(rrf(rank++,1.0)); }
    }
    private void addSemantic(String query,int limit,Map<String,MutableHit> out){
        VectorStore store=vectorStore.getIfAvailable(); if(store==null)return;
        try {
            List<Document> docs=store.similaritySearch(SearchRequest.builder().query(query).topK(limit).similarityThreshold(0.45).filterExpression("type == 'book'").build());
            if(docs==null)return; int rank=1;
            for(Document d:docs){
                int currentRank = rank++;
                Object raw=d.getMetadata().get("bookId");
                if(raw==null) continue;
                try{
                    UUID id=UUID.fromString(raw.toString());
                    books.findById(id).ifPresent(b -> out.computeIfAbsent("local:"+id,k->MutableHit.local(b,"SEMANTIC")).add(rrf(currentRank,1.2)));
                }catch(Exception ignored){}
            }
        } catch(RuntimeException ignored){}
    }
    private void addExternal(String query,int limit,Map<String,MutableHit> out){
        int rank=1; for(CatalogBook b:catalog.search(query,1,limit).items()){ String key=fingerprint(b); MutableHit hit=out.computeIfAbsent(key,k->MutableHit.external(b)); hit.add(rrf(rank++,0.8)); }
    }
    private double rrf(int rank,double weight){ return weight/(60.0+rank); }
    private String fingerprint(CatalogBook b){ if(b.isbn13()!=null)return "isbn:"+b.isbn13(); String a=b.authors()==null||b.authors().isEmpty()?"":b.authors().getFirst(); return (b.title()+"::"+a).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]",""); }

    private static class MutableHit {
        UUID localBookId; String provider,externalId,title,cover,description,matchType; List<String> authors; double score;
        static MutableHit local(Book b,String match){ var h=new MutableHit(); h.localBookId=b.getId(); h.provider="local"; h.title=b.getTitle(); h.authors=BookMapper.toView(b).authors(); h.cover=b.getCoverUrl(); h.description=b.getDescription(); h.matchType=match; return h; }
        static MutableHit external(CatalogBook b){ var h=new MutableHit(); h.provider=b.provider(); h.externalId=b.externalId(); h.title=b.title(); h.authors=b.authors(); h.cover=b.coverUrl(); h.description=b.description(); h.matchType="EXTERNAL"; return h; }
        void add(double value){ score+=value; }
        double score(){return score;}
        SearchHit toView(){ return new SearchHit(localBookId,provider,externalId,title,authors==null?List.of():authors,cover,description,score,matchType); }
    }
}
