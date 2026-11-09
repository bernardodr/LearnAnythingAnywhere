package com.laa.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laa.chat.ChatMessageRepository;
import com.laa.chat.ChatMessageRepository.Kind;
import com.laa.chat.ChatMessageRepository.StoredMessage;
import com.laa.llm.ChatMessage;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.PromptContext;
import com.laa.llm.Task;
import com.laa.llm.runs.Origin;
import com.laa.safety.TopicVerdict;
import com.laa.textbook.Textbook;
import com.laa.textbook.TextbookRepository;
import com.laa.web.ApiException;
import com.laa.workspace.WorkspaceFiles;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

/**
 * The preface: a short streamed interview about why the learner wants this topic (BRIEF section 6.2,
 * /teach MISSION-FORMAT.md), ending in a structured {@link MissionDraft} the learner confirms as MISSION.md.
 *
 * <p>The interviewer ends the interview by putting {@link #MARKER} in its reply once it has a concrete
 * mission. The marker is stripped before anything is shown or stored. The server also ends it after
 * {@link #MAX_ANSWERS} answers whatever the model does, and never before the learner has answered once.
 */
@Service
public class MissionInterview {

    public static final String MARKER = "[[MISSION_READY]]";
    public static final int MAX_ANSWERS = 5;
    public static final int MAX_ANSWER_CHARS = 2_000;
    static final String DRAFT_VARIANT = "draft";
    private static final String WRAP_UP = "Thank you, that’s everything I need for your mission.";

    /** Where a turn's output goes, as it happens. */
    public interface Events {
        void token(String text);

        void draft(MissionDraft draft);

        /** The draft couldn't be written; the turn itself was saved. */
        void draftFailed(String message);

        void done(UUID messageId, boolean interviewComplete);
    }

    public record Transcript(List<StoredMessage> messages, int answers) {}

    private final LlmGateway gateway;
    private final ChatMessageRepository chats;
    private final TextbookRepository textbooks;
    private final WorkspaceFiles workspace;

    MissionInterview(LlmGateway gateway, ChatMessageRepository chats, TextbookRepository textbooks, WorkspaceFiles workspace) {
        this.gateway = gateway;
        this.chats = chats;
        this.textbooks = textbooks;
        this.workspace = workspace;
    }

    public Transcript transcript(Textbook tb) {
        List<StoredMessage> messages = chats.thread(tb.id(), Kind.MISSION);
        return new Transcript(messages, answers(messages));
    }

    /** Checks a turn can start, before any streaming begins, so problems come back as ordinary HTTP errors. */
    public void checkTurn(Textbook tb, String answer) {
        if (tb.status() != Textbook.Status.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "This textbook’s mission is already confirmed.");
        }
        if (tb.missionDraft() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Your mission draft is ready. Edit it on the mission card.");
        }
        boolean opening = chats.thread(tb.id(), Kind.MISSION).isEmpty();
        boolean hasAnswer = answer != null && !answer.isBlank();
        if (opening && hasAnswer) throw new ApiException(HttpStatus.BAD_REQUEST, "The interview hasn’t started yet.");
        if (!opening && !hasAnswer) throw new ApiException(HttpStatus.BAD_REQUEST, "Write an answer first.");
        if (hasAnswer && answer.length() > MAX_ANSWER_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please keep answers under " + MAX_ANSWER_CHARS + " characters.");
        }
    }

    /**
     * One turn: streams the interviewer's reply, stores the answer and reply together, and once the interview
     * is complete writes the draft. Blocks until done; call {@link #checkTurn} first.
     */
    public void turn(Textbook tb, UUID userId, String answer, GenerationProfile profile, Events events) {
        List<StoredMessage> thread = chats.thread(tb.id(), Kind.MISSION);
        String learnerAnswer = answer == null || answer.isBlank() ? null : answer.strip();
        int answersAfter = answers(thread) + (learnerAnswer == null ? 0 : 1);
        boolean finalTurn = answersAfter >= MAX_ANSWERS;

        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.user(kickoff(tb))); // providers need the conversation to open with a user turn
        thread.forEach(m -> history.add(m.toChat()));
        PromptContext ctx = new PromptContext(variables(tb, answersAfter, finalTurn), history, learnerAnswer,
                userId, tb.id(), Origin.APP);

        MarkerFilter filter = new MarkerFilter(MARKER);
        gateway.stream(Task.MISSION_INTERVIEW, ctx, profile)
                .doOnNext(chunk -> {
                    String shown = filter.accept(chunk);
                    if (!shown.isEmpty()) events.token(shown);
                })
                .blockLast();
        String tail = filter.finish();
        if (!tail.isEmpty()) events.token(tail);

        boolean complete = answersAfter >= 1 && (filter.markerSeen() || finalTurn);
        String reply = filter.text();
        if (reply.isEmpty()) {
            reply = WRAP_UP;
            events.token(reply);
        }
        UUID messageId = chats.appendTurn(tb.id(), Kind.MISSION, learnerAnswer, reply);

        if (complete) {
            try {
                events.draft(writeDraft(tb, userId, profile));
            } catch (RuntimeException e) {
                events.draftFailed("Your answers are saved, but the mission draft couldn’t be written just now. Try again.");
            }
        }
        events.done(messageId, complete);
    }

    /** (Re)writes the draft from the transcript so far. */
    public MissionDraft writeDraft(Textbook tb, UUID userId, GenerationProfile profile) {
        List<StoredMessage> thread = chats.thread(tb.id(), Kind.MISSION);
        if (answers(thread) == 0) throw new ApiException(HttpStatus.CONFLICT, "Answer at least one question first.");
        StringBuilder transcript = new StringBuilder("Interview transcript:\n");
        for (StoredMessage m : thread) {
            transcript.append(m.role() == ChatMessage.Role.USER ? "LEARNER: " : "INTERVIEWER: ").append(m.content()).append('\n');
        }
        PromptContext ctx = new PromptContext(variables(tb, answers(thread), true), List.of(), transcript.toString(),
                userId, tb.id(), Origin.APP).withVariant(DRAFT_VARIANT);
        MissionDraft draft = gateway.structured(Task.MISSION_INTERVIEW, ctx, MissionDraft.class, profile);
        textbooks.saveDraft(tb.id(), draft);
        return draft;
    }

    /** Writes MISSION.md (as the learner confirmed or edited it) and opens the textbook. */
    @Transactional
    public WorkspaceFile confirm(Textbook tb, MissionDraft confirmed) {
        if (tb.status() != Textbook.Status.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "This textbook’s mission is already confirmed.");
        }
        if (tb.missionDraft() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Finish the interview before confirming a mission.");
        }
        WorkspaceFile file = workspace.write(tb.id(), WorkspaceFiles.MISSION, MissionMarkdown.render(tb.title(), confirmed));
        if (!textbooks.activate(tb.id(), confirmed)) {
            throw new ApiException(HttpStatus.CONFLICT, "This textbook’s mission is already confirmed.");
        }
        return file;
    }

    private static int answers(List<StoredMessage> thread) {
        return (int) thread.stream().filter(m -> m.role() == ChatMessage.Role.USER).count();
    }

    private static String kickoff(Textbook tb) {
        return "I’d like a textbook about: " + tb.title();
    }

    private static Map<String, String> variables(Textbook tb, int answers, boolean finalTurn) {
        Map<String, String> v = new HashMap<>();
        v.put("title", tb.title());
        v.put("topic", tb.topic());
        v.put("marker", MARKER);
        v.put("max_answers", String.valueOf(MAX_ANSWERS));
        boolean reframed = TopicVerdict.Verdict.REFRAME.name().equals(tb.topicGate().get("verdict"));
        v.put("framing_note", reframed
                ? "The learner asked about \"" + tb.topic() + "\". For this learner, the textbook covers it as \""
                        + tb.title() + "\". Keep the interview inside that framing."
                : "");
        v.put("turn_note", finalTurn
                ? "This is the final turn. Don’t ask another question: thank the learner in one short sentence, "
                        + "then end your message with " + MARKER + "."
                : "The learner has answered " + answers + " of at most " + MAX_ANSWERS + " questions so far.");
        return v;
    }
}
