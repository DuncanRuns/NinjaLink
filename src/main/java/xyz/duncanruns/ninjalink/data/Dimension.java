package xyz.duncanruns.ninjalink.data;

public enum Dimension {
    OVERWORLD(1, "Overworld"), NETHER(1 / 8d, "Nether"), END(1, "End");

    private final double coordinateScale;
    private final String displayName;

    Dimension(double coordinateScale, String displayName) {
        this.coordinateScale = coordinateScale;
        this.displayName = displayName;
    }

    public static Dimension fromBooleanParams(boolean isInOverworld, boolean isInNether) {
        if (isInOverworld) return OVERWORLD;
        else if (isInNether) return NETHER;
        return END;
    }

    public double getCoordinateScale() {
        return coordinateScale;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
