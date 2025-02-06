package xyz.duncanruns.ninjalink.data;

public class Position {
    public final double x;
    public final double z;

    public Position(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public static Position fromDimensionCoordinates(Dimension inputDimension, double x, double z, Dimension outputDimension) {
        double scale = outputDimension.getCoordinateScale() / inputDimension.getCoordinateScale();
        return new Position(x * scale, z * scale);
    }

    public static void main(String[] args) {
        System.out.println(new Position(2268, 711).angleTo(new Position(1752, 568)));
    }

    public Position translateDimension(Dimension fromDimension, Dimension toDimension) {
        return fromDimensionCoordinates(fromDimension, this.x, this.z, toDimension);
    }

    public double distanceTo(Position other) {
        return Math.sqrt((this.x - other.x) * (this.x - other.x) + (this.z - other.z) * (this.z - other.z));
    }

    public double angleTo(Position other) {
        double dx = other.x - this.x;
        double dz = other.z - this.z;
        double angle = -Math.toDegrees(Math.atan2(dx, dz)); // Swap dx, dz to match MC system
        if (angle > 180) angle -= 360;
        return angle < -180 ? angle + 360 : angle;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ')';
    }

    public String asBlockPosString() {
        return "(" + (int) (Math.floor(x)) + ", " + (int) (Math.floor(z)) + ')';
    }
}
