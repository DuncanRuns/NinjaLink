package xyz.duncanruns.ninjalink.data;

public class StrongholdPrediction {
    public final Position position;
    public final double angleFromLastThrow;
    public final double certainty;
    public final double distanceFromLastThrow;

    public StrongholdPrediction(Position position, double angleFromLastThrow, double certainty, double distanceFromLastThrow) {
        this.position = position;
        this.angleFromLastThrow = angleFromLastThrow;
        this.certainty = certainty;
        this.distanceFromLastThrow = distanceFromLastThrow;
    }
}
