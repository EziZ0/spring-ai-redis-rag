# Spring AI + Redis RAG

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F)
![Redis](https://img.shields.io/badge/Redis-Vector%20Store-DC382D)
![Gemini](https://img.shields.io/badge/Google%20Gemini-Chat%20%2F%20Embeddings-4285F4)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36)

A Spring Boot service that uses **Redis as a vector store** to power Retrieval-Augmented Generation (RAG) over a small product catalog, built on **Spring AI**. On top of RAG, it exposes plain Gemini chat, raw embedding generation, and cosine-similarity endpoints — useful as a reference for wiring Spring AI's abstractions (`ChatClient`, `EmbeddingModel`, `VectorStore`) directly to Redis.

---

## Table of Contents

1. [Architecture](#architecture)
2. [How RAG Works Here](#how-rag-works-here)
3. [API Endpoints](#api-endpoints)
4. [Tech Stack](#tech-stack)
7. [Project Structure](#project-structure)

---

## Architecture

A single Spring Boot application, backed by Redis for vector storage and chat memory, and Google Gemini for the LLM and embedding model.

```mermaid
flowchart TB
    Client(["Client"])

    subgraph App["SpringAiCode Application (:8080)"]
        CTRL["OpenAiController<br/>REST endpoints"]
        CHAT["ChatClient<br/>with MessageWindowChatMemory"]
        EMB["EmbeddingModel"]
        VS["RedisVectorStore"]
        INIT["DataInitializer<br/>startup loader"]
    end

    subgraph Infra["Infrastructure"]
        REDIS[("Redis Stack<br/>product-index")]
        GEMINI[["Google Gemini API<br/>Chat + Embeddings"]]
    end

    Client --> CTRL
    CTRL --> CHAT
    CTRL --> EMB
    CTRL --> VS

    CHAT --> GEMINI
    EMB --> GEMINI
    VS <--> REDIS

    INIT -- reads --> TXT["product_details.txt"]
    INIT -- chunks and embeds --> VS
```

## How RAG Works Here

`/api/ask` is the RAG endpoint. On startup, `DataInitializer` chunks `product_details.txt` with `TokenTextSplitter` and embeds each chunk into Redis under the `product-index`. At query time, the controller retrieves the most relevant chunks and stuffs them into the prompt before calling the LLM.

```mermaid
sequenceDiagram
    actor U as User
    participant CTRL as OpenAiController
    participant VS as RedisVectorStore
    participant LLM as Gemini Chat Model

    Note over CTRL,VS: On app startup
    CTRL->>VS: DataInitializer - split and embed product_details.txt
    VS-->>VS: store vectors (product-index)

    U->>CTRL: POST /api/ask?query=...
    CTRL->>VS: similaritySearch(query, topK=5, threshold=0.7)
    VS-->>CTRL: matching Document chunks
    CTRL->>CTRL: build context string from chunks
    CTRL->>LLM: prompt(context and query)
    LLM-->>CTRL: answer (Summary / Features / Recommendation)
    CTRL-->>U: response
```

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/{message}` | Simple chat completion for the given message |
| `POST` | `/api/recommend?type=&year=&lang=` | Recommends a movie based on type, year, and language |
| `POST` | `/api/embedding?text=` | Returns the embedding vector for the given text |
| `POST` | `/api/similarity?text1=&text2=` | Returns cosine similarity (%) between two texts |
| `POST` | `/api/product?text=` | Raw vector similarity search against the product index |
| `POST` | `/api/ask?query=` | RAG: retrieves relevant product chunks (top-5, similarity ≥ 0.7) and answers the query using that context |

### Example

```bash
curl -X POST "http://localhost:8080/api/ask?query=Which+earbuds+have+the+longest+battery+life?"
```

## Tech Stack

- **Language / Runtime:** Java 21
- **Framework:** Spring Boot 3.5.0
- **AI Layer:** Spring AI 1.0.0 (`ChatClient`, `EmbeddingModel`, `VectorStore`, `QuestionAnswerAdvisor`)
- **LLM / Embeddings:** Google Gemini (via Spring AI's Gemini starter)
- **Vector Store:** Redis (via `spring-ai-starter-vector-store-redis` + Jedis `JedisPooled`)
- **Chat Memory:** In-memory (`MessageWindowChatMemory`, not persisted across restarts)
- **Text Splitting:** `TokenTextSplitter`
- **Build:** Maven (with Maven Wrapper)



## Project Structure

```
SpringAICode - Redis/
├── src/main/java/com/telusko/
│   ├── SpringAiCodeApplication.java     # Spring Boot entry point
│   ├── config/
│   │   ├── RedisVectorConfig.java       # JedisPooled + RedisVectorStore beans
│   │   └── DataInitializer.java         # Loads & embeds product_details.txt on startup
│   └── controller/
│       └── OpenAiController.java        # Chat, embedding, similarity, and RAG endpoints
└── src/main/resources/
    ├── application.properties
    └── product_details.txt              # Sample product catalog used for RAG
```

## Notes

- Chat memory is kept in-memory and is not persisted across restarts.
