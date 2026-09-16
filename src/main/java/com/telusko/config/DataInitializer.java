package com.telusko.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer {

    @Autowired
    private VectorStore vectorStore;

    @PostConstruct
    public void initData(){
        TextReader reader = new TextReader(new ClassPathResource("product_details.txt"));

        //TokenTextSplitter textSplitter=new TokenTextSplitter();
        TokenTextSplitter splitter = new TokenTextSplitter(100, 30, 5, 500, false);
        List<Document> docs = splitter.split(reader.get());

        vectorStore.add(docs); // stores in Redis
        System.out.println("Data embedded into Redis vector store.");

    }
}
