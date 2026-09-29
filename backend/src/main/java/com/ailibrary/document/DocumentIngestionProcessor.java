package com.ailibrary.document;

import com.ailibrary.document.domain.UserDocument;
import com.ailibrary.document.repository.UserDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DocumentIngestionProcessor {
    private final UserDocumentRepository docs; private final DocumentService service; private final ObjectProvider<VectorStore> stores;
    public DocumentIngestionProcessor(UserDocumentRepository docs,DocumentService service,ObjectProvider<VectorStore> stores){this.docs=docs;this.service=service;this.stores=stores;}

    @Transactional
    public void ingest(UUID id){
        UserDocument d=docs.findById(id).orElse(null); if(d==null)return; d.processing(); docs.flush();
        try{
            VectorStore store=stores.getIfAvailable(); if(store==null)throw new IllegalStateException("VectorStore unavailable");
            List<Document> parsed=new TikaDocumentReader(new FileSystemResource(service.path(d))).read();
            List<Document> base=new ArrayList<>();
            for(Document p:parsed){
                if(p.getText()==null||p.getText().isBlank())continue;
                Map<String,Object> metadata=new HashMap<>();
                metadata.put("type","document_chunk"); metadata.put("ownerId",d.getOwnerId().toString()); metadata.put("documentId",d.getId().toString()); metadata.put("sourceName",d.getOriginalName());
                if(d.getBookId()!=null) metadata.put("bookId",d.getBookId().toString());
                base.add(Document.builder().text(p.getText()).metadata(metadata).build());
            }
            TokenTextSplitter splitter=TokenTextSplitter.builder().withChunkSize(800).withMinChunkSizeChars(350).withMinChunkLengthToEmbed(20).withMaxNumChunks(5000).withKeepSeparator(true).build();
            List<Document> chunks=splitter.apply(base); List<Document> indexed=new ArrayList<>(); int i=0;
            for(Document c:chunks){
                Map<String,Object> m=new HashMap<>(c.getMetadata()); m.put("chunkIndex",i);
                indexed.add(Document.builder().id("doc-"+d.getId()+"-"+i).text(c.getText()).metadata(m).build()); i++;
            }
            if(indexed.isEmpty())throw new IllegalStateException("No text extracted");
            store.add(indexed); d.ready(indexed.size());
        }catch(RuntimeException ex){d.failed(ex.getMessage());}
    }
}
