package xyz.duncanruns.ninjalink.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// Implement data structure of https://github.com/Ninjabrain1/Ninjabrain-Bot/wiki/API#response-format
public class NinjabrainBotEventData {
    private static final Gson GSON = new Gson();

    public final List<EyeThrowDto> eyeThrows;
    public final String resultType;
    public final PlayerPositionDto playerPosition;
    public final List<PredictionDto> predictions;

    public NinjabrainBotEventData(List<EyeThrowDto> eyeThrows, String resultType, PlayerPositionDto playerPosition, List<PredictionDto> predictions) {
        this.eyeThrows = eyeThrows;
        this.resultType = resultType;
        this.playerPosition = playerPosition;
        this.predictions = predictions;
    }

    public static NinjabrainBotEventData fromJson(String string) throws JsonSyntaxException {
        return GSON.fromJson(string, NinjabrainBotEventData.class);
    }

    public static NinjabrainBotEventData empty() {
        return new NinjabrainBotEventData(Collections.emptyList(), "NONE", PlayerPositionDto.empty(), Collections.emptyList());
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public boolean hasPlayerPosition() {
        return this.playerPosition.xInOverworld != null;
    }

    public static class EyeThrowDto {
        public final double xInOverworld;
        public final double zInOverworld;
        public final double angle;
        public final double angleWithoutCorrection;
        public final double correction;
        public final double error;
        public final String type;

        public EyeThrowDto(double xInOverworld, double zInOverworld, double angle, double angleWithoutCorrection, double correction, double error, String type) {
            this.xInOverworld = xInOverworld;
            this.zInOverworld = zInOverworld;
            this.angle = angle;
            this.angleWithoutCorrection = angleWithoutCorrection;
            this.correction = correction;
            this.error = error;
            this.type = type;
        }
    }

    public static class PlayerPositionDto {
        @Nullable
        public final Double xInOverworld;
        @Nullable
        public final Double zInOverworld;
        @Nullable
        public final Double horizontalAngle;
        @Nullable
        public final Boolean isInOverworld;
        @Nullable
        public final Boolean isInNether;

        public PlayerPositionDto(@Nullable Double xInOverworld, @Nullable Double zInOverworld, @Nullable Double horizontalAngle, @Nullable Boolean isInOverworld, @Nullable Boolean isInNether) {
            this.xInOverworld = xInOverworld;
            this.zInOverworld = zInOverworld;
            this.horizontalAngle = horizontalAngle;
            this.isInOverworld = isInOverworld;
            this.isInNether = isInNether;
        }

        public static PlayerPositionDto empty() {
            return new PlayerPositionDto(null, null, null, null, null);
        }

        public boolean isEmpty() {
            return xInOverworld == null;
        }

        public Dimension getDimension() {
            return Dimension.fromBooleanParams(isInOverworld, isInNether);
        }

        public Position getPosition() {
            return Position.fromDimensionCoordinates(Dimension.OVERWORLD, xInOverworld, zInOverworld, getDimension());
        }
    }

    public static class PredictionDto {
        public final Integer chunkX;
        public final Integer chunkZ;
        public final Double certainty;
        public final Double overworldDistance;

        public PredictionDto(int chunkX, int chunkZ, double certainty, double overworldDistance) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.certainty = certainty;
            this.overworldDistance = overworldDistance;
        }
    }
}
