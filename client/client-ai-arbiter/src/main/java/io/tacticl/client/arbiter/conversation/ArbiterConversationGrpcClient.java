package io.tacticl.client.arbiter.conversation;

import cidadel.ai.arbiter.conversation.v1.ArbiterConversationServiceGrpc;
import cidadel.ai.arbiter.conversation.v1.ConverseEvent;
import cidadel.ai.arbiter.conversation.v1.ConverseTurnRequest;
import cidadel.ai.arbiter.conversation.v1.Turn;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC client for {@code ArbiterConversationService.ConverseTurn}. Uses the async
 * stub on the shared arbiter channel and maps proto {@link ConverseEvent}s onto a
 * {@link ConverseEventListener}.
 *
 * <p>Exactly-once terminal delivery: whichever of a {@code done}/{@code error}
 * event or the stream's {@code onCompleted}/{@code onError} arrives first fires the
 * single terminal callback; the rest are ignored.
 */
public class ArbiterConversationGrpcClient implements ConversationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ArbiterConversationGrpcClient.class);

    /** A voice turn must finish well inside the user's patience; cap the stream. */
    private static final long DEADLINE_SECONDS = 60;

    private final ArbiterConversationServiceGrpc.ArbiterConversationServiceStub stub;

    public ArbiterConversationGrpcClient(ArbiterConversationServiceGrpc.ArbiterConversationServiceStub stub) {
        this.stub = stub;
    }

    @Override
    public void converseTurn(ConverseTurnInput input, ConverseEventListener listener) {
        ConverseTurnRequest request = toProto(input);
        AtomicBoolean finished = new AtomicBoolean(false);

        stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
            .converseTurn(request, new StreamObserver<>() {
                @Override
                public void onNext(ConverseEvent event) {
                    switch (event.getType()) {
                        case "started" -> listener.onStarted(event.getPersonaId());
                        case "token" -> listener.onToken(event.getText(), event.getPersonaId());
                        case "tool_use" -> listener.onToolUse(
                            event.getToolUse().getName(),
                            event.getToolUse().getInputJson(),
                            event.getToolUse().getTerminal());
                        case "done" -> {
                            if (finished.compareAndSet(false, true)) {
                                listener.onDone();
                            }
                        }
                        case "error" -> {
                            if (finished.compareAndSet(false, true)) {
                                listener.onError(safe(event.getMessage()));
                            }
                        }
                        default -> log.debug("Ignoring unknown ConverseEvent type '{}'", event.getType());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.warn("ConverseTurn stream failed: {}", t.toString());
                    if (finished.compareAndSet(false, true)) {
                        listener.onError("The voice brain is unavailable right now.");
                    }
                }

                @Override
                public void onCompleted() {
                    // Normal completion without an explicit 'done' event still settles the turn.
                    if (finished.compareAndSet(false, true)) {
                        listener.onDone();
                    }
                }
            });
    }

    private static ConverseTurnRequest toProto(ConverseTurnInput input) {
        ConverseTurnRequest.Builder b = ConverseTurnRequest.newBuilder()
            .setProductId(nz(input.productId()))
            .setUserId(nz(input.userId()))
            .setSessionId(nz(input.sessionId()))
            .setTurnId(nz(input.turnId()))
            .setText(nz(input.text()))
            .setPersonaHint(nz(input.personaHint()))
            .setLocale(nz(input.locale()));
        if (input.history() != null) {
            for (ConvTurn turn : input.history()) {
                if (turn == null) {
                    continue;
                }
                b.addHistory(Turn.newBuilder()
                    .setRole(nz(turn.role()))
                    .setText(nz(turn.text()))
                    .setPersonaId(nz(turn.personaId()))
                    .build());
            }
        }
        return b.build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "Sorry — something went wrong." : s;
    }
}
