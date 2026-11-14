package com.laa.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** /teach's LEARNING-RECORD-FORMAT.md and GLOSSARY-FORMAT.md. */
class EvidenceFormatsTest {

    @Test
    void learningRecordsAreNumberedAfterTheHighestExistingOne() {
        assertThat(LearningRecords.nextPath(List.of("MISSION.md"), "Uses straight arms!")).isEqualTo("learning-records/0001-uses-straight-arms.md");
        assertThat(LearningRecords.nextPath(List.of("learning-records/0001-a.md", "learning-records/0007-b.md", "NOTES.md"), "next one"))
                .isEqualTo("learning-records/0008-next-one.md");
        assertThat(LearningRecords.nextPath(List.of(), "!!!")).isEqualTo("learning-records/0001-record.md");
        assertThat(WorkspaceFiles.isValidPath(LearningRecords.nextPath(List.of(), "A very long title with many many words in it"))).isTrue();
    }

    @Test
    void aLearningRecordIsATitleSentencesAndEvidence() {
        assertThat(LearningRecords.render("Uses straight arms to delay pump", "Explained why straight arms rest the forearms.\nTeach footwork next.",
                "Lesson 1 practice: said bones take the load."))
                .isEqualTo("""
                        # Uses straight arms to delay pump

                        Explained why straight arms rest the forearms. Teach footwork next.

                        ## Evidence
                        Lesson 1 practice: said bones take the load.
                        """);
    }

    @Test
    void glossaryTermsAreAddedThenRevisedInPlace() {
        String first = Glossary.upsert(null, "Bouldering",
                List.of(new Glossary.Term("Flagging", "Holding a leg out to balance instead of standing on it.", List.of("leg out"))));
        assertThat(first).isEqualTo("""
                # Bouldering Glossary

                The words you use correctly in Bouldering, and the ones to avoid.

                ## Terms

                **Flagging**:
                Holding a leg out to balance instead of standing on it.
                _Avoid_: leg out
                """);

        String second = Glossary.upsert(first, "Bouldering", List.of(
                new Glossary.Term("Pump", "Forearm fatigue from gripping too long.", List.of()),
                new Glossary.Term("flagging", "Using a free leg as a counterweight to stop a barn-door swing.", List.of("leg out", "kick"))));
        var terms = Glossary.parse(second);
        assertThat(terms).extracting(Glossary.Term::term).containsExactly("Flagging", "Pump"); // revised in place, new one appended
        assertThat(terms.getFirst().definition()).startsWith("Using a free leg");
        assertThat(terms.getFirst().avoid()).containsExactly("leg out", "kick");
        assertThat(terms.get(1).avoid()).isEmpty();
    }
}
