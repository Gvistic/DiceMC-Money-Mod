package dicemc.money.commands;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dicemc.money.MoneyMod;
import dicemc.money.MoneyMod.AcctTypes;
import dicemc.money.setup.Config;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PayCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : new String[] { "pay", "epay" }) {
			dispatcher.register(Commands.literal(name)
					.then(Commands.argument("player", StringArgumentType.word())
							.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
									.executes(PayCommand::run))));
		}
	}

	private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer sender = context.getSource().getPlayerOrException();
		String playerName = StringArgumentType.getString(context, "player");
		double amount = DoubleArgumentType.getDouble(context, "amount");

		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(playerName);
		if (profile.isEmpty()) {
			context.getSource().sendFailure(Component.translatable("message.command.playernotfound"));
			return 1;
		}
		UUID recipientId = profile.get().getId();
		String recipientName = profile.get().getName();

		if (recipientId.equals(sender.getUUID())) {
			context.getSource().sendFailure(Component.literal("You cannot pay yourself."));
			return 1;
		}

		if (MoneyWSD.get().transferFunds(AcctTypes.PLAYER.key, sender.getUUID(), AcctTypes.PLAYER.key, recipientId,
				amount)) {
			if (Config.ENABLE_HISTORY.get()) {
				MoneyMod.dbm.postEntry(System.currentTimeMillis(), sender.getUUID(), AcctTypes.PLAYER.key,
						sender.getName().getString(),
						recipientId, AcctTypes.PLAYER.key, recipientName,
						amount, "Pay Command");
			}
			context.getSource().sendSuccess(() -> Component.translatable("message.command.transfer.success",
					Config.getFormattedCurrency(amount), recipientName), true);
			return 0;
		}
		context.getSource().sendFailure(Component.translatable("message.command.transfer.failure"));
		return 1;
	}
}
