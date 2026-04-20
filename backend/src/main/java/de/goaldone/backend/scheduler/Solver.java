package de.goaldone.backend.scheduler;

public class Solver {

    ConstraintHandler constraintHandler;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
    }


    //TODO: @Leon Parameters, Return Types
    public void createSchedule() {
        // CPMAlgo.generateInitialSchedule
        // algo Aufrufe etc.

        // schedule besteht aus ArrayList<ScheduleEntry>

        int scheduleScore = this.constraintHandler.calculateScore(null);

    }




}
