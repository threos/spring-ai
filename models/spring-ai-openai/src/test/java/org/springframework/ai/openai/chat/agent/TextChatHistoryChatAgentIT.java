/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai.chat.agent;

import java.util.List;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.chat.agent.ChatAgent;
import org.springframework.ai.chat.agent.DefaultChatAgent;
import org.springframework.ai.chat.agent.DefaultStreamingChatAgent;
import org.springframework.ai.chat.agent.StreamingChatAgent;
import org.springframework.ai.chat.history.VectorStoreChatMemoryAgentListener;
import org.springframework.ai.chat.history.VectorStoreChatMemoryRetriever;
import org.springframework.ai.chat.history.LastMaxTokenSizeContentTransformer;
import org.springframework.ai.chat.history.SystemPromptChatMemoryAugmentor;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.evaluation.BaseMemoryTest;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@Testcontainers
@SpringBootTest(classes = TextChatHistoryChatAgentIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class TextChatHistoryChatAgentIT extends BaseMemoryTest {

	private static final String COLLECTION_NAME = "test_collection";

	private static final int QDRANT_GRPC_PORT = 6334;

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.7.4");

	@Autowired
	public TextChatHistoryChatAgentIT(RelevancyEvaluator relevancyEvaluator, ChatAgent chatAgent,
			StreamingChatAgent streamingChatAgent) {
		super(relevancyEvaluator, chatAgent, streamingChatAgent);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

		@Bean
		public EmbeddingClient embeddingClient(OpenAiApi openAiApi) {
			return new OpenAiEmbeddingClient(openAiApi);
		}

		@Bean
		public VectorStore qdrantVectorStore(EmbeddingClient embeddingClient) {
			QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient
				.newBuilder(qdrantContainer.getHost(), qdrantContainer.getMappedPort(QDRANT_GRPC_PORT), false)
				.build());
			return new QdrantVectorStore(qdrantClient, COLLECTION_NAME, embeddingClient);
		}

		@Bean
		public TokenCountEstimator tokenCountEstimator() {
			return new JTokkitTokenCountEstimator();
		}

		@Bean
		public ChatAgent memoryChatAgent(OpenAiChatClient chatClient, VectorStore vectorStore,
				TokenCountEstimator tokenCountEstimator) {

			return DefaultChatAgent.builder(chatClient)
				.withRetrievers(List.of(new VectorStoreChatMemoryRetriever(vectorStore, 10)))
				.withDocumentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new SystemPromptChatMemoryAugmentor()))
				.withChatAgentListeners(List.of(new VectorStoreChatMemoryAgentListener(vectorStore)))
				.build();
		}

		@Bean
		public StreamingChatAgent memoryStreamingChatAgent(OpenAiChatClient streamingChatClient,
				VectorStore vectorStore, TokenCountEstimator tokenCountEstimator) {

			return DefaultStreamingChatAgent.builder(streamingChatClient)
				.withRetrievers(List.of(new VectorStoreChatMemoryRetriever(vectorStore, 10)))
				.withDocumentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new SystemPromptChatMemoryAugmentor()))
				.withChatAgentListeners(List.of(new VectorStoreChatMemoryAgentListener(vectorStore)))
				.build();
		}

		@Bean
		public RelevancyEvaluator relevancyEvaluator(OpenAiChatClient chatClient) {
			return new RelevancyEvaluator(chatClient);
		}

	}

}
