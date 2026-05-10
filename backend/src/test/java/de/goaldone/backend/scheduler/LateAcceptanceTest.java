package de.goaldone.backend.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LateAcceptanceTest {

    // ──────────────────────────────────────────────────────────────
    // validateMove – Akzeptanz / Ablehnung
    // ──────────────────────────────────────────────────────────────

    @Test
    void validateMove_Accept_WhenNewScoreGreaterThanCurrentScore() {
        LateAcceptance la = new LateAcceptance(5);
        la.initialize(10);

        // direkte Verbesserung → immer akzeptieren
        assertTrue(la.validateMove(15, 10));
    }

    @Test
    void validateMove_Accept_WhenNewScoreEqualToCurrentScore() {
        LateAcceptance la = new LateAcceptance(5);
        la.initialize(10);

        // gleichwertiger Score ≥ currentScore → akzeptieren
        assertTrue(la.validateMove(10, 10));
    }

    @Test
    void validateMove_Accept_WhenNewScoreBetterThanLateScore_ButWorseThanCurrentScore() {
        // Late Acceptance: neuer Score schlechter als current, aber besser als lateScore
        LateAcceptance la = new LateAcceptance(3);
        la.initialize(5);   // Puffer voll mit 5

        // Simuliere, dass current jetzt 20 ist (z.B. durch 3 Akzeptanzen)
        // Wir testen direkt: lateScore == 5, currentScore == 20, newScore == 8
        // 8 < 20 (nicht direkte Verbesserung), aber 8 >= 5 (lateScore) → akzeptieren
        assertTrue(la.validateMove(8, 20));
    }

    @Test
    void validateMove_Reject_WhenNewScoreWorseThanBothCurrentAndLateScore() {
        LateAcceptance la = new LateAcceptance(5);
        la.initialize(10);

        // newScore=5 < currentScore=10 UND < lateScore=10 → ablehnen
        assertFalse(la.validateMove(5, 10));
    }

    @Test
    void validateMove_IsDeterministic_ForSameInputs() {
        LateAcceptance la = new LateAcceptance(5);
        la.initialize(10);

        boolean first  = la.validateMove(8, 10);
        boolean second = la.validateMove(8, 10);

        assertEquals(first, second);
    }

    // ──────────────────────────────────────────────────────────────
    // Initialisierungsphase
    // ──────────────────────────────────────────────────────────────

    @Test
    void initialize_FillsEntireBufferWithInitialScore() {
        int L = 4;
        LateAcceptance la = new LateAcceptance(L);
        la.initialize(42);

        // In den ersten L Iterationen muss lateScore == initialScore sein
        for (int i = 0; i < L; i++) {
            // newScore=42 == lateScore=42 → validateMove muss true zurückgeben
            assertTrue(la.validateMove(42, 42),
                    "Puffer-Eintrag " + i + " sollte 42 sein");
            la.updateHistory(42);   // eine Iteration vorwärts
        }
    }

    @Test
    void initialize_ResetsIteration() {
        LateAcceptance la = new LateAcceptance(3);
        la.initialize(10);

        // Einige Iterationen durchführen
        la.updateHistory(12);
        la.updateHistory(15);

        // Neu initialisieren
        la.initialize(0);

        // Direkt nach Re-Init: lateScore muss 0 sein → newScore=0 wird akzeptiert
        assertTrue(la.validateMove(0, 0));
    }

    // ──────────────────────────────────────────────────────────────
    // Ringpuffer – Wrap-Around
    // ──────────────────────────────────────────────────────────────

    @Test
    void updateHistory_OverwritesOldestEntry_AfterLIterations() {
        int L = 3;
        LateAcceptance la = new LateAcceptance(L);
        la.initialize(100); // Puffer: [100, 100, 100], iteration=0

        // L Iterationen mit Score 100 → alle Slots auf 100
        la.updateHistory(100); // schreibt idx 0 → 100, iteration=1
        la.updateHistory(100); // schreibt idx 1 → 100, iteration=2
        la.updateHistory(100); // schreibt idx 2 → 100, iteration=3

        // Jetzt Wrap-Around: idx 0 wird überschrieben mit 200
        la.updateHistory(200); // schreibt idx 0 (3 % 3) → 200, iteration=4

        // Nächste validateMove liest idx 4%3=1 → noch 100
        // Score 99 < currentScore=200 und 99 < lateScore=100 → ablehnen
        assertFalse(la.validateMove(99, 200));

        la.updateHistory(100); // schreibt idx 1 → 100, iteration=5
        la.updateHistory(100); // schreibt idx 2 → 100, iteration=6
        // iteration=6, lateScore = scoreHistory[6%3=0] = 200
        // newScore=150 < currentScore=300 aber 150 < lateScore=200 → ablehnen
        assertFalse(la.validateMove(150, 300));

        // newScore=201 >= lateScore=200 → akzeptieren
        assertTrue(la.validateMove(201, 300));
    }

    @Test
    void updateHistory_WrapsAroundCorrectly_MultipleFullCycles() {
        int L = 3;
        LateAcceptance la = new LateAcceptance(L);
        la.initialize(0);

        // 2 vollständige Zyklen beschreiben
        int[] scores = {10, 20, 30, 40, 50, 60};
        for (int s : scores) {
            la.updateHistory(s);
        }

        // iteration=6, liest idx 6%3=0
        // Letzter Wert an idx 0 war scores[3]=40 (Iteration 3: 3%3=0)
        // newScore=40 >= lateScore=40 → akzeptieren (>=)
        assertTrue(la.validateMove(40, 100));
        // newScore=39 < lateScore=40 und < currentScore=100 → ablehnen
        assertFalse(la.validateMove(39, 100));
    }
}

