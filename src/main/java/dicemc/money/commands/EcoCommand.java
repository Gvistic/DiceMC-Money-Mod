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
import dicemc.money.storage.DatabaseManager;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class EcoCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : new String[] { "eco", "economy", "eeco", "eeconomy" }) {
			dispatcher.register(Commands.literal(name)
					.requires(p -> p.hasPermission(Config.ADMIN_LEVEL.get()))
					.then(Commands.literal("give")
							.then(Commands.argument("player", StringArgumentType.word())
									.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0d))
											.executes(ctx -> give(ctx)))))
					.then(Commands.literal("take")
							.then(Commands.argument("player", StringArgumentType.word())
									.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0d))
											.executes(ctx -> take(ctx)))))
					.then(Commands.literal("set")
							.then(Commands.argument("player", StringArgumentType.word())
									.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0d))
											.executes(ctx -> set(ctx)))))
					.then(Commands.literal("reset")
							.then(Commands.argument("player", StringArgumentType.word())
									.executes(ctx -> reset(ctx)))));
		}
	}

	private static GameProfile resolvePlayer(CommandContext<CommandSourceStack> context) {
		String playerName = StringArgumentType.getString(context, "player");
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(playerName);
		return profile.orElse(null);
	}

	private static void logHistory(CommandContext<CommandSourceStack> context, UUID pid, double value, String action) {
		if (!Config.ENABLE_HISTORY.get())
			return;
		boolean isPlayer = context.getSource().getEntity() instanceof ServerPlayer;
		UUID srcID = isPlayer ? context.getSource().getEntity().getUUID() : DatabaseManager.NIL;
		ResourceLocation srcType = isPlayer ? AcctTypes.PLAYER.key : AcctTypes.SERVER.key;
		String srcName = isPlayer ? context.getSource().getServer().getProfileCache().get(srcID).get().getName()
				: "Console";
		MoneyMod.dbm.postEntry(System.currentTimeMillis(), srcID, srcType, srcName,
				pid, AcctTypes.PLAYER.key, MoneyMod.dbm.server.getProfileCache().get(pid).get().getName(),
				value, action);
	}

	private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		GameProfile player = resolvePlayer(context);
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.command.playernotfound"));
			return 1;
		}
		double value = DoubleArgumentType.getDouble(context, "amount");
		boolean result = MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, player.getId(), value);
		if (result) {
			logHistory(context, player.getId(), value, "Eco Give Command");
			context.getSource().sendSuccess(() -> Component.translatable("message.command.give.success",
					Config.getFormattedCurrency(value), player.getName()), true);
			return 0;
		}
		context.getSource().sendFailure(Component.translatable("message.command.change.failure"));
		return 1;
	}

	private static int take(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		GameProfile player = resolvePlayer(context);
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.command.playernotfound"));
			return 1;
		}
		double value = DoubleArgumentType.getDouble(context, "amount");
		boolean result = MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, player.getId(), -value);
		if (result) {
			logHistory(context, player.getId(), value, "Eco Take Command");
			context.getSource().sendSuccess(() -> Component.translatable("message.command.take.success",
					Config.getFormattedCurrency(value), player.getName()), true);
			return 0;
		}
		context.getSource().sendFailure(Component.translatable("message.command.change.failure"));
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		GameProfile player = resolvePlayer(context);
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.command.playernotfound"));
			return 1;
		}
		double value = DoubleArgumentType.getDouble(context, "amount");
		boolean result = MoneyWSD.get().setBalance(AcctTypes.PLAYER.key, player.getId(), value);
		if (result) {
			logHistory(context, player.getId(), value, "Eco Set Command");
			context.getSource().sendSuccess(() -> Component.translatable("message.command.set.success",
					player.getName(), Config.getFormattedCurrency(value)), true);
			return 0;
		}
		context.getSource().sendFailure(Component.translatable("message.command.set.failure"));
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		GameProfile player = resolvePlayer(context);
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.command.playernotfound"));
			return 1;
		}
		double startingFunds = Config.STARTING_FUNDS.get();
		boolean result = MoneyWSD.get().setBalance(AcctTypes.PLAYER.key, player.getId(), startingFunds);
		if (result) {
			logHistory(context, player.getId(), startingFunds, "Eco Reset Command");
			context.getSource().sendSuccess(() -> Component.translatable("message.command.set.success",
					player.getName(), Config.getFormattedCurrency(startingFunds)), true);
			return 0;
		}
		context.getSource().sendFailure(Component.translatable("message.command.set.failure"));
		return 1;
	}
}
