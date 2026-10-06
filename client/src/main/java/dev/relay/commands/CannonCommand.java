package dev.relay.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.relay.chat.ChatMessages;
import dev.relay.patchcrumbs.CannonWhitelist;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;

/**
 * Client /cannon pos1|pos2, nested under /relay so it does not shadow a
 * multiplayer server's own /cannon.
 */
final class CannonCommand {
	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommandManager.literal("cannon")
				.then(ClientCommandManager.literal("pos1").executes(ctx -> {
					BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
					CannonWhitelist.setPos1(pos);
					ctx.getSource().sendFeedback(ChatMessages.success(
							"Cannon position 1 set at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."));
					return 1;
				}))
				.then(ClientCommandManager.literal("pos2").executes(ctx -> {
					BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
					CannonWhitelist.setPos2(pos);
					ctx.getSource().sendFeedback(ChatMessages.success(
							"Cannon position 2 set at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "."));
					return 1;
				}))
				.then(ClientCommandManager.literal("clear").executes(ctx -> {
					CannonWhitelist.clear();
					ctx.getSource().sendFeedback(ChatMessages.success("Cannon area cleared."));
					return 1;
				}));
	}

	private CannonCommand() {
	}
}
