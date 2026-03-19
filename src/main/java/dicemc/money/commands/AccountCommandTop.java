package dicemc.money.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;

import dicemc.money.MoneyMod.AcctTypes;
import dicemc.money.setup.Config;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AccountCommandTop implements Command<CommandSourceStack> {
	private static final AccountCommandTop CMD = new AccountCommandTop();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : new String[] { "top", "balancetop", "baltop", "ebaltop", "ebalancetop" }) {
			dispatcher.register(Commands.literal(name)
					.then(Commands.argument("page", IntegerArgumentType.integer(1))
							.executes(ctx -> runTop(ctx, IntegerArgumentType.getInteger(ctx, "page"))))
					.executes(ctx -> runTop(ctx, 1)));
		}
	}

	private static int runTop(CommandContext<CommandSourceStack> context, int page) {
		Map<UUID, Double> unsorted = MoneyWSD.get().getAccountMap(AcctTypes.PLAYER.key);
		List<Pair<UUID, Double>> sorted = new ArrayList<>();
		DecimalFormat df = new DecimalFormat("###,###,###,##0.00");

		unsorted.entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEachOrdered(x -> sorted.add(Pair.of(x.getKey(), x.getValue())));

		int perPage = Config.TOP_SIZE.get();
		if (perPage <= 0)
			return 0;
		int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / perPage));
		if (page > totalPages)
			page = totalPages;
		int start = (page - 1) * perPage;
		int end = Math.min(start + perPage, sorted.size());

		int finalPage = page;
		context.getSource().sendSuccess(() -> Component.translatable("message.command.top", sorted.size())
				.append(Component.literal(" (Page " + finalPage + "/" + totalPages + ")")), false);
		for (int i = start; i < end; i++) {
			Pair<UUID, Double> p = sorted.get(i);
			String name = context.getSource().getServer().getProfileCache().get(p.getFirst()).get().getName();
			int finalI = i;
			context.getSource()
					.sendSuccess(() -> Component.literal(
							"#" + (finalI + 1) + " " + name + ": " + Config.getFormattedCurrency(df, p.getSecond())),
							false);
		}
		return 0;
	}

	@Override
	public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return runTop(context, 1);
	}
}
