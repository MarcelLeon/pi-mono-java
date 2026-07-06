package com.pi.mono.session;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionCompactionServiceTest {

    @Test
    void serializesSplitTurnSummaryRequests() {
        SessionCompactionService service = new SessionCompactionService();
        List<SessionCompactionService.SummaryKind> invocations = new ArrayList<>();
        CompletableFuture<String> historySummary = new CompletableFuture<>();
        CompletableFuture<String> turnPrefixSummary = new CompletableFuture<>();

        CompletableFuture<String> summary = service.summarizeSplitTurn(
            List.of(message("old context")),
            List.of(message("current turn prefix")),
            (messages, kind) -> {
                invocations.add(kind);
                return kind == SessionCompactionService.SummaryKind.HISTORY
                    ? historySummary
                    : turnPrefixSummary;
            }
        );

        assertEquals(List.of(SessionCompactionService.SummaryKind.HISTORY), invocations);
        assertFalse(summary.isDone());

        historySummary.complete("history summary");

        assertEquals(List.of(
            SessionCompactionService.SummaryKind.HISTORY,
            SessionCompactionService.SummaryKind.TURN_PREFIX
        ), invocations);
        assertFalse(summary.isDone());

        turnPrefixSummary.complete("turn summary");

        assertEquals("""
            history summary

            ---

            **Turn Context (split turn):**

            turn summary""", summary.join());
    }

    @Test
    void summarizesTurnPrefixAfterNoPriorHistoryPlaceholder() {
        SessionCompactionService service = new SessionCompactionService();
        List<SessionCompactionService.SummaryKind> invocations = new ArrayList<>();

        CompletableFuture<String> summary = service.summarizeSplitTurn(
            List.of(),
            List.of(message("current turn prefix")),
            (messages, kind) -> {
                invocations.add(kind);
                return CompletableFuture.completedFuture("turn summary");
            }
        );

        assertEquals(List.of(SessionCompactionService.SummaryKind.TURN_PREFIX), invocations);
        assertEquals("""
            No prior history.

            ---

            **Turn Context (split turn):**

            turn summary""", summary.join());
    }

    private AgentMessage message(String content) {
        return new AgentMessage(MessageRole.USER, content, Map.of());
    }
}
