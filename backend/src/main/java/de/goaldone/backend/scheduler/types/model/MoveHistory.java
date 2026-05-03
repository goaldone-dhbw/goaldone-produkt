package de.goaldone.backend.scheduler.types.model;

import lombok.Getter;

import java.util.ArrayList;

@Getter
public class MoveHistory {

    private final ArrayList<MoveEvent> moveHistory;
    private final int maxHistorySize = 5;

    public MoveHistory() {
        this.moveHistory = new ArrayList<>();
    }

    public void addMoveEvent(MoveEvent event) {
        this.moveHistory.add(event);

        if (moveHistory.size() > maxHistorySize) {
            this.moveHistory.removeFirst();
        }
    }

    public boolean inRecentMove(MoveEvent move) {
        return moveHistory.contains(move);
    }

}
