package nodomain.freeyourgadget.gadgetbridge.service.devices.sn60plus;

public class ActivityDTO {
    public int steps;
    public int meters;
    public int calories;

    public ActivityDTO(int steps, int meters, int calories) {
        this.steps = steps;
        this.meters = meters;
        this.calories = calories;
    }

    @Override
    public String toString() {
        return "ActivityDTO{" +
                "steps=" + steps +
                ", meters=" + meters +
                ", calories=" + calories +
                '}';
    }
}
