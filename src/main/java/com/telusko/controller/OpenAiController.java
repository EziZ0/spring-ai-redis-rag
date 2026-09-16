package com.telusko.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenAiController {

    private ChatClient chatClient;

    private ChatMemory chatMemory;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

//    public OpenAiController(OpenAiChatModel chatModel){
//        this.chatClient=ChatClient.create(chatModel);
//    }

    public OpenAiController(ChatClient.Builder builder) {

       this.chatMemory= MessageWindowChatMemory.builder().build();

       this.chatClient=builder
               .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
               .build();
    }

    @GetMapping("/api/{message}")
    public ResponseEntity<String> getAnswer(@PathVariable String message){

      ChatResponse chatResponse= chatClient
              .prompt(message)
              .call()
              .chatResponse();

      System.out.println(chatResponse.getMetadata().getModel());

      String response=chatResponse
              .getResult()
              .getOutput()
              .getText();

      return ResponseEntity.ok(response);
    }

    @PostMapping("/api/recommend")
    public String recommend(@RequestParam String type,@RequestParam String year,@RequestParam String lang){

        String tempt="""
                   I want to watch a {type} movie tonight with good rating,
                   looking for movies around this year {year}.
                   The language im looking for is {lang}.
                   Suggest one specific movie and tell me the cast and length of the movie.
                   
                   response format should be:
                   1. Movie name
                   2. basic plot
                   3. cast
                   4. length
                   5. IMDB rating
                """;

        PromptTemplate promptTemplate=new PromptTemplate(tempt);
        Prompt prompt=promptTemplate.create(Map.of("type",type,"year",year,"lang",lang));

        String response = chatClient
                .prompt(prompt)
                .call()
                .content();

        return response;
    }

    @PostMapping("/api/embedding")
    public float[] embeddings(@RequestParam String text){
         return embeddingModel.embed(text);
    }


    @PostMapping("/api/similarity")
    public double getSimilarity(@RequestParam String text1,@RequestParam String text2){
       float[] embedding1=embeddingModel.embed(text1);
       float[] embedding2=embeddingModel.embed(text2);

       double dotProduct=0;
       double norm1=0;
       double norm2=0;

       for (int i=0;i<embedding1.length;i++){
           dotProduct+=embedding1[i]*embedding2[i];
           norm1+=Math.pow(embedding1[i],2);
           norm2+=Math.pow(embedding2[i],2);
       }

       return dotProduct*100 /(Math.sqrt(norm1)*Math.sqrt(norm2));
    }

    @PostMapping("/api/product")
    public List<Document> getProducts(@RequestParam String text){

        return vectorStore.similaritySearch(text);
    }

//    @PostMapping("/api/ask")
//    public String getAnswerUsingRag(@RequestParam String query){
//
//        return chatClient
//                .prompt(query)
//                .advisors(new QuestionAnswerAdvisor(vectorStore))
//                .call()
//                .content();
//    }

    @PostMapping("/api/ask")
    public String getAnswerUsingRag2(@RequestParam String query){

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)                       // get top 5 most similar documents
                        .similarityThreshold(0.7f)     // filter documents with similarity score below threshold
                        .build()
        );

        // Build a combined context string from document contents
        StringBuilder contextBuilder = new StringBuilder();
        for (Document doc : documents) {
            contextBuilder.append(doc.getFormattedContent()).append("\n");
        }

        String context = contextBuilder.toString();

        // Fill template variables with user query and relevant context
        Map<String, Object> variables = new HashMap<>();
        variables.put("userQuery", query);
        variables.put("context", context);

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template("""
                            Use the following context to answer the user's question.
                            Context: {context}
                                    
                            Question: {userQuery}
                            
                            Respond in the following format:
                            1. Summary
                            2. Specific product features (if relevant)
                            3. Recommendation (if applicable)
                        """)
                .variables(variables)
                .build();

        // Call the chat model and return the generated response
        return chatClient.prompt(promptTemplate.create()).call().content();
    }

}
