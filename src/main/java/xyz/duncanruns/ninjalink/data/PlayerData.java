package xyz.duncanruns.ninjalink.data;

import org.jetbrains.annotations.Nullable;

public class PlayerData {
    @Nullable
    public final Position position;
    @Nullable
    public final Dimension dimension;
    @Nullable
    public final StrongholdPrediction bestStrongholdPrediction;

    public static PlayerData fromNinjabrainBotEventData(NinjabrainBotEventData data) throws NullPointerException {
        boolean hasPlayerPosition = data.hasPlayerPosition();
        NinjabrainBotEventData.PlayerPositionDto playerPosition = data.playerPosition;
        NinjabrainBotEventData.EyeThrowDto eyeThrow = data.eyeThrows.isEmpty() ? null : data.eyeThrows.get(0);
        NinjabrainBotEventData.PredictionDto prediction = eyeThrow == null || data.predictions.isEmpty() ? null : data.predictions.get(0);
        Dimension playerDimension = hasPlayerPosition ? Dimension.fromBooleanParams(playerPosition.isInOverworld, playerPosition.isInNether) : null;
        return new PlayerData(
                hasPlayerPosition ? Position.fromDimensionCoordinates(Dimension.OVERWORLD, playerPosition.xInOverworld, playerPosition.zInOverworld, playerDimension) : null,
                hasPlayerPosition ? playerDimension : null,
                prediction == null ? null : new StrongholdPrediction(
                        new Position(prediction.chunkX * 16 + 4, prediction.chunkZ * 16 + 4),
                        eyeThrow.angle,
                        prediction.certainty,
                        prediction.overworldDistance
                )
        );
    }

    public boolean isEmpty() {
        return position == null && dimension == null && bestStrongholdPrediction == null;
    }

    public boolean hasStronghold() {
        return bestStrongholdPrediction != null;
    }

    public PlayerData(@Nullable Position position, @Nullable Dimension dimension, @Nullable StrongholdPrediction bestStrongholdPrediction) {
        this.position = position;
        this.dimension = dimension;
        this.bestStrongholdPrediction = bestStrongholdPrediction;
    }
}
