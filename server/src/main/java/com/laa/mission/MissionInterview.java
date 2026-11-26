package com.laa.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laa.chat.ChatMessageRepository;
import com.laa.chat.ChatMessageRepository.Kind;
import com.laa.chat.ChatMessageRepository.StoredMessage;
import com.laa.llm.Calls;
import com.laa.llm.ChatMessage;
import com.laa.llm.PromptContext;
import com.laa.llm.runs.Origin;
import com.laa.safety.SafetyLayer;
import com.laa.safety.TopicVerdict;
import com.laa.textbook.Textbook;
import com.laa.textbook.Familiarity;
import com.laa.textbook.Motif;
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
    private static final String WRAP_UP = "Thank you, that’s everything I need for your mission.";

    /** Where a turn's output goes, as it happens. */
    public interface Events {
        /** The interviewer's reply, whole, once it has passed the safety check. */
        void token(String text);

        void draft(MissionDraft draft);

        /** The draft couldn't be written; the turn itself was saved. */
        void draftFailed(String message);

        void done(UUID messageId, boolean interviewComplete);
    }

    public record Transcript(List<StoredMessage> messages, int answers) {}

    private final SafetyLayer safety;
    private final ChatMessageRepository chats;
    private final TextbookRepository textbooks;
    private final WorkspaceFiles workspace;

    MissionInterview(SafetyLayer safety, ChatMessageRepository chats, TextbookRepository textbooks, WorkspaceFiles workspace) {
        this.safety = safety;
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
     * One turn: the interviewer's reply (checked by the safety layer before it's sent), the answer and reply stored
     * together, and once the interview is complete, the draft. Blocks until done; call {@link #checkTurn} and
     * {@link SafetyLayer#admit} first.
     */
    public void turn(Textbook tb, UUID userId, String answer, Events events) {
        List<StoredMessage> thread = chats.thread(tb.id(), Kind.MISSION);
        String learnerAnswer = answer == null || answer.isBlank() ? null : answer.strip();
        int answersAfter = answers(thread) + (learnerAnswer == null ? 0 : 1);
        boolean finalTurn = answersAfter >= MAX_ANSWERS;

        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.user(kickoff(tb))); // providers need the conversation to open with a user turn
        thread.forEach(m -> history.add(m.toChat()));
        PromptContext ctx = new PromptContext(variables(tb, answersAfter, finalTurn), history, learnerAnswer,
                userId, tb.id(), Origin.APP);

        AtomicBoolean markerSeen = new AtomicBoolean();
        String reply = safety.reply(Calls.INTERVIEW_TURN, ctx, WRAP_UP, raw -> {
            markerSeen.set(raw.contains(MARKER));
            return raw.replace(MARKER, "");
        });
        events.token(reply);

        boolean complete = answersAfter >= 1 && (markerSeen.get() || finalTurn);
        UUID messageId = chats.appendTurn(tb.id(), Kind.MISSION, learnerAnswer, reply);

        if (complete) {
            try {
                events.draft(writeDraft(tb, userId));
            } catch (ApiException e) {
                events.draftFailed(e.getMessage());
            } catch (RuntimeException e) {
                events.draftFailed("Your answers are saved, but the mission draft couldn’t be written just now. Try again.");
            }
        }
        events.done(messageId, complete);
    }

    /** (Re)writes the draft from the transcript so far, through the safety layer. */
    public MissionDraft writeDraft(Textbook tb, UUID userId) {
        List<StoredMessage> thread = chats.thread(tb.id(), Kind.MISSION);
        if (answers(thread) == 0) throw new ApiException(HttpStatus.CONFLICT, "Answer at least one question first.");
        StringBuilder transcript = new StringBuilder("Interview transcript:\n");
        for (StoredMessage m : thread) {
            transcript.append(m.role() == ChatMessage.Role.USER ? "LEARNER: " : "INTERVIEWER: ").append(m.content()).append('\n');
        }
        PromptContext ctx = new PromptContext(variables(tb, answers(thread), true), List.of(), transcript.toString(),
                userId, tb.id(), Origin.APP);
        SafetyLayer.Checked<MissionDraft> draft = safety.write(Calls.MISSION_DRAFT, ctx);
        if (draft.blocked()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Your answers are saved, but the mission draft didn’t fit your reader settings. Try again.");
        }
        textbooks.saveDraft(tb.id(), draft.value());
        return draft.value();
    }

    /**
     * Writes MISSION.md (as the learner confirmed or edited it) and opens the textbook, with its cover motif: the
     * AI's pick from the draft the server kept (whatever the app sent back), or one matched from the title for a
     * draft from before motifs existed.
     */
    @Transactional
    public WorkspaceFile confirm(Textbook tb, MissionDraft confirmed) {
        if (tb.status() != Textbook.Status.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "This textbook’s mission is already confirmed.");
        }
        if (tb.missionDraft() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Finish the interview before confirming a mission.");
        }
        WorkspaceFile file = workspace.write(tb.id(), WorkspaceFiles.MISSION, MissionMarkdown.render(tb.title(), confirmed));
        Motif motif = tb.missionDraft().motif() != null ? tb.missionDraft().motif() : Motif.guess(tb.title() + " " + tb.topic());
        if (!textbooks.activate(tb.id(), confirmed, motif)) {
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
        v.put("familiarity", Familiarity.forPrompt(tb.familiarity()));
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
