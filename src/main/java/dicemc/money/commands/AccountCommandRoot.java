package dicemc.money.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dicemc.money.MoneyMod.AcctTypes;
import dicemc.money.setup.Config;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class AccountCommandRoot implements Command<CommandSourceStack> {
	private static final AccountCommandRoot CMD = new AccountCommandRoot();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("money")
				.then(AccountCommandAdmin.register(dispatcher))
				.then(AccountCommandTransfer.register(dispatcher))
				.then(Commands.argument("player", StringArgumentType.word())
						.executes(AccountCommandRoot::runOther))
				.executes(CMD));
		for (String alias : new String[] { "balance", "bal", "ebal", "emoney", "ebalance" }) {
			dispatcher.register(Commands.literal(alias)
					.then(Commands.argument("player", StringArgumentType.word())
							.executes(AccountCommandRoot::runOther))
					.executes(CMD));
		}
	}

	@Override
	public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Double balP = MoneyWSD.get().getBalance(AcctTypes.PLAYER.key,
				context.getSource().getEntityOrException().getUUID());
		context.getSource().sendSuccess(() -> Component.literal(Config.getFormattedCurrency(balP)), false);
		return 0;
	}

	private static int runOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String playerName = StringArgumentType.getString(context, "player");
		Optional<GameProfile> profile = context.getSource().getServer().getProfileCache().get(playerName);
		if (profile.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Player not found: " + playerName));
			return 0;
		}
		GameProfile gp = profile.get();
		Double balP = MoneyWSD.get().getBalance(AcctTypes.PLAYER.key, gp.getId());
		String name = gp.getName();
		context.getSource().sendSuccess(() -> Component.literal(name + ": " + Config.getFormattedCurrency(balP)),
				false);
		return 0;
	}

}
