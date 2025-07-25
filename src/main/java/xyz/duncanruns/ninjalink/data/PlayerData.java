package xyz.duncanruns.ninjalink.data;

import org.jetbrains.annotations.Nullable;

public class PlayerData {
    @Nullable
    public final Position position;
    @Nullable
    public final Dimension dimension;
    @Nullable
    public final StrongholdPrediction bestStrongholdPrediction;

    public PlayerData(@Nullable Position position, @Nullable Dimension dimension, @Nullable StrongholdPrediction bestStrongholdPrediction) {
        this.position = position;
        this.dimension = dimension;
        this.bestStrongholdPrediction = bestStrongholdPrediction;
    }

    public static PlayerData fromNinjabrainBotEventData(NinjabrainBotEventData data) throws NullPointerException {
        boolean hasPlayerPosition = data.hasPlayerPosition();
        NinjabrainBotEventData.PlayerPositionDto playerPosition = data.playerPosition;
        NinjabrainBotEventData.EyeThrowDto eyeThrow = data.eyeThrows.isEmpty() ? null : data.eyeThrows.get(data.eyeThrows.size() - 1);
        NinjabrainBotEventData.PredictionDto prediction = eyeThrow == null || data.predictions.isEmpty() ? null : data.predictions.get(0);
        Dimension playerDimension = hasPlayerPosition ? Dimension.fromBooleanParams(playerPosition.isInOverworld, playerPosition.isInNether) : null;
        Position shPosition = prediction == null ? null : new Position(prediction.chunkX * 16 + 4, prediction.chunkZ * 16 + 4);
        return new PlayerData(
                hasPlayerPosition ? Position.fromDimensionCoordinates(Dimension.OVERWORLD, playerPosition.xInOverworld, playerPosition.zInOverworld, playerDimension) : null,
                hasPlayerPosition ? playerDimension : null,
                prediction == null ? null : new StrongholdPrediction(
                        shPosition,
                        eyeThrow.angle,
                        prediction.certainty,
                        new Position(eyeThrow.xInOverworld, eyeThrow.zInOverworld).distanceTo(shPosition)
                )
        );
    }

    public static PlayerData fromF3CData(F3CData f3CData) {
        return new PlayerData(
                new Position(f3CData.x, f3CData.z),
                f3CData.dimension,
                null
        );
    }

    public static PlayerData empty() {
        return new PlayerData(null, null, null);
    }

    public boolean isEmpty() {
        return position == null && dimension == null && bestStrongholdPrediction == null;
    }

    public boolean hasStronghold() {
        return bestStrongholdPrediction != null;
    }
}
