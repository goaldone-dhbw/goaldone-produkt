package de.goaldone.backend.scheduler;

import java.util.ArrayList;

public interface CPMAlgorithmus {

    //TODO: @Leon Parameters, Return Types

    // heuristische bzw erste Lösung
    void generateInitialSchedule();

    ArrayList<Object> calculateSlack();


}
