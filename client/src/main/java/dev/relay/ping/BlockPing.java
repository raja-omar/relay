package dev.relay.ping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One local block ping. {@code front} is the face under the crosshair when it was placed,
 * so a later group share can send the same position and face.
 */
public record BlockPing(BlockPos pos, Direction front, long expiresAt) {
}
