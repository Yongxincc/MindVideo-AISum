package com.example.server.rag;

/**
 * RAG 向量索引未就绪或建立失败。
 */
public class RagIndexException extends Exception {

    public RagIndexException(String message) {
        super(message);
    }

    public RagIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
