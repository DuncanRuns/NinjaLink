package xyz.duncanruns.ninjalink.data;

public class StrongholdPrediction {
    public final Position position;
    public final double originalAngle; // The original measured angle
    public final double certainty;
    public final double originalDistance;

    public StrongholdPrediction(Position position, double originalAngle, double certainty, double originalDistance) {
        this.position = position;
        this.originalAngle = originalAngle;
        this.certainty = certainty;
        this.originalDistance = originalDistance;
    }
}
