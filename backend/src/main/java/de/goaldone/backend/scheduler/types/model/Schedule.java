package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.ScheduleEntry;
import lombok.Getter;

import java.util.ArrayList;

@Getter
public class Schedule {

   private ArrayList<ScheduleEntry> scheduledTasks;

    public Schedule() {
        this.scheduledTasks = new ArrayList<>();
    }

    public Schedule(ArrayList<ScheduleEntry> scheduledTasks) {
        this.scheduledTasks = scheduledTasks;
    }

    public void addScheduleEntry(ScheduleEntry entry) {
        this.scheduledTasks.add(entry);
    }
}