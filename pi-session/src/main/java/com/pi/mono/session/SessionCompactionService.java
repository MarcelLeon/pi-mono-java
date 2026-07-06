package com.pi.mono.session;

import com.pi.mono.core.AgentMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Summary orchestration for future session compaction flows.
 */
@Service
public class SessionCompactionService {

    static final String NO_PRIOR_HISTORY = "No prior history.";
    static final String SPLIT_TURN_SEPARATOR = "\n\n---\n\n**Turn Context (split turn):**\n\n";

    public enum SummaryKind {
        HISTORY,
        TURN_PREFIX
    }

    @FunctionalInterface
    public interface SummaryGenerator {
        CompletableFuture<String> summarize(List<AgentMessage> messages, SummaryKind kind);
    }

    public CompletableFuture<String> summarizeSplitTurn(
        List<AgentMessage> messagesToSummarize,
        List<AgentMessage> turnPrefixMessages,
        SummaryGenerator generator
    ) {
        List<AgentMessage> historyMessages = safeMessages(messagesToSummarize);
        List<AgentMessage> prefixMessages = safeMessages(turnPrefixMessages);

        CompletableFuture<String> historySummary = historyMessages.isEmpty()
            ? CompletableFuture.completedFuture(NO_PRIOR_HISTORY)
            : generator.summarize(historyMessages, SummaryKind.HISTORY);

        return historySummary.thenCompose(history ->
            generator.summarize(prefixMessages, SummaryKind.TURN_PREFIX)
                .thenApply(turnPrefix -> history + SPLIT_TURN_SEPARATOR + turnPrefix)
        );
    }

    private List<AgentMessage> safeMessages(List<AgentMessage> messages) {
        return messages == null ? List.of() : List.copyOf(messages);
    }
}
