package dicemc.money.commands;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dicemc.money.MoneyMod;
import dicemc.money.MoneyMod.AcctTypes;
import dicemc.money.setup.Config;
import dicemc.money.storage.AuctionItem;
import dicemc.money.storage.AuctionWSD;
import dicemc.money.storage.DatabaseManager;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class AuctionCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : new String[] { "ah", "auctionhouse" }) {
			dispatcher.register(Commands.literal(name)
					.then(Commands.literal("sell")
							.then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
									.executes(AuctionCommand::sell)))
					.then(Commands.literal("buy")
							.then(Commands.argument("id", IntegerArgumentType.integer(1))
									.executes(AuctionCommand::buy)))
					.then(Commands.literal("cancel")
							.then(Commands.argument("id", IntegerArgumentType.integer(1))
									.executes(AuctionCommand::cancel)))
					.then(Commands.literal("search")
							.then(Commands.argument("term", StringArgumentType.greedyString())
									.executes(AuctionCommand::search)))
					.then(Commands.literal("collect")
							.executes(AuctionCommand::collect))
					.then(Commands.literal("mine")
							.executes(AuctionCommand::mine))
					.then(Commands.literal("admin")
							.requires(p -> p.hasPermission(Config.ADMIN_LEVEL.get()))
							.then(Commands.literal("clear")
									.executes(AuctionCommand::adminClear))
							.then(Commands.literal("remove")
									.then(Commands.argument("id", IntegerArgumentType.integer(1))
											.executes(AuctionCommand::adminRemove))))
					.executes(AuctionCommand::openGui));
		}
	}

	private static int sell(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		double price = DoubleArgumentType.getDouble(context, "price");
		ItemStack held = player.getMainHandItem();

		if (held.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.auction.sell.empty"));
			return 1;
		}

		AuctionWSD awd = AuctionWSD.get();
		int activeCount = awd.getActiveListingCount(player.getUUID());
		if (Config.AUCTION_MAX_LISTINGS.get() > 0 && activeCount >= Config.AUCTION_MAX_LISTINGS.get()) {
			player.sendSystemMessage(Component.translatable("message.auction.sell.maxlistings",
					Config.AUCTION_MAX_LISTINGS.get()));
			return 1;
		}

		double tax = price * Config.AUCTION_TAX.get();
		if (tax > 0) {
			double balance = MoneyWSD.get().getBalance(AcctTypes.PLAYER.key, player.getUUID());
			if (balance < tax) {
				player.sendSystemMessage(Component.translatable("message.auction.sell.notaxfunds",
						Config.getFormattedCurrency(tax)));
				return 1;
			}
			MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, player.getUUID(), -tax);
		}

		long duration = TimeUnit.HOURS.toMillis(Config.AUCTION_DURATION.get());
		long expireTime = System.currentTimeMillis() + duration;
		ItemStack listingItem = held.copy();
		int listingId = awd.getNextId();
		AuctionItem listing = new AuctionItem(listingId, player.getUUID(),
				player.getName().getString(), listingItem, price, expireTime);
		awd.addListing(listing);
		player.getMainHandItem().setCount(0);

		if (Config.ENABLE_HISTORY.get()) {
			MoneyMod.dbm.postEntry(System.currentTimeMillis(), player.getUUID(), AcctTypes.PLAYER.key,
					player.getName().getString(), DatabaseManager.NIL, AcctTypes.SERVER.key, "Auction House",
					tax, "Auction Listing #" + listingId + " Tax");
		}

		String taxMsg = tax > 0 ? " (Tax: " + Config.getFormattedCurrency(tax) + ")" : "";
		player.sendSystemMessage(Component.translatable("message.auction.sell.success",
				listingItem.getDisplayName(), Config.getFormattedCurrency(price), listingId)
				.append(Component.literal(taxMsg).withStyle(ChatFormatting.GRAY)));
		return 0;
	}

	private static int buy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(context, "id");
		AuctionWSD awd = AuctionWSD.get();
		AuctionItem listing = awd.getListing(id);

		if (listing == null || listing.isExpired()) {
			player.sendSystemMessage(Component.translatable("message.auction.buy.notfound"));
			return 1;
		}

		if (listing.getSeller().equals(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("message.auction.buy.own"));
			return 1;
		}

		double balance = MoneyWSD.get().getBalance(AcctTypes.PLAYER.key, player.getUUID());
		if (balance < listing.getPrice()) {
			player.sendSystemMessage(Component.translatable("message.auction.buy.funds"));
			return 1;
		}

		// Transfer funds: buyer pays, seller receives
		MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, player.getUUID(), -listing.getPrice());
		MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, listing.getSeller(), listing.getPrice());

		if (Config.ENABLE_HISTORY.get()) {
			MoneyMod.dbm.postEntry(System.currentTimeMillis(), player.getUUID(), AcctTypes.PLAYER.key,
					player.getName().getString(), listing.getSeller(), AcctTypes.PLAYER.key,
					listing.getSellerName(), listing.getPrice(), "Auction Purchase #" + id);
		}

		// Give item to buyer
		ItemStack purchasedItem = listing.getItem().copy();
		if (!player.addItem(purchasedItem))
			player.drop(purchasedItem, false);

		awd.removeListing(id);

		player.sendSystemMessage(Component.translatable("message.auction.buy.success",
				listing.getItem().getDisplayName(), Config.getFormattedCurrency(listing.getPrice())));

		// Notify seller if online
		ServerPlayer seller = player.getServer().getPlayerList().getPlayer(listing.getSeller());
		if (seller != null) {
			seller.sendSystemMessage(Component.translatable("message.auction.sold.notify",
					listing.getItem().getDisplayName(), Config.getFormattedCurrency(listing.getPrice()),
					player.getName().getString()));
		}
		return 0;
	}

	private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(context, "id");
		AuctionWSD awd = AuctionWSD.get();
		AuctionItem listing = awd.getListing(id);

		if (listing == null) {
			player.sendSystemMessage(Component.translatable("message.auction.cancel.notfound"));
			return 1;
		}

		if (!listing.getSeller().equals(player.getUUID()) && !player.hasPermissions(Config.ADMIN_LEVEL.get())) {
			player.sendSystemMessage(Component.translatable("message.auction.cancel.notowner"));
			return 1;
		}

		ItemStack returnItem = listing.getItem().copy();
		if (!player.addItem(returnItem))
			player.drop(returnItem, false);

		awd.removeListing(id);
		player.sendSystemMessage(Component.translatable("message.auction.cancel.success",
				returnItem.getDisplayName()));
		return 0;
	}

	private static int collect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		AuctionWSD awd = AuctionWSD.get();
		awd.processExpired();
		List<ItemStack> items = awd.collectExpiredItems(player.getUUID());

		if (items.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.auction.collect.empty"));
			return 1;
		}

		int count = 0;
		for (ItemStack stack : items) {
			if (!player.addItem(stack.copy()))
				player.drop(stack.copy(), false);
			count++;
		}
		player.sendSystemMessage(Component.translatable("message.auction.collect.success", count));
		return 0;
	}

	private static int mine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		AuctionWSD awd = AuctionWSD.get();
		List<AuctionItem> myListings = awd.getListingsBySeller(player.getUUID());

		if (myListings.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.auction.mine.empty"));
			return 0;
		}

		player.sendSystemMessage(Component.translatable("message.auction.mine.header")
				.withStyle(ChatFormatting.GOLD));
		for (AuctionItem item : myListings) {
			String timeLeft = item.isExpired() ? "EXPIRED" : formatTimeLeft(item.getExpireTime());
			MutableComponent line = Component.literal(" #" + item.getId() + " ")
					.withStyle(ChatFormatting.YELLOW)
					.append(item.getItem().getDisplayName())
					.append(Component.literal(" x" + item.getItem().getCount()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" - " + Config.getFormattedCurrency(item.getPrice()))
							.withStyle(ChatFormatting.GREEN))
					.append(Component.literal(" [" + timeLeft + "]").withStyle(
							item.isExpired() ? ChatFormatting.RED : ChatFormatting.GRAY));
			player.sendSystemMessage(line);
		}
		return 0;
	}

	private static int search(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String term = StringArgumentType.getString(context, "term");
		AuctionWSD awd = AuctionWSD.get();
		List<AuctionItem> results = awd.searchListings(term);

		if (results.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.auction.search.empty", term));
			return 0;
		}

		player.sendSystemMessage(Component.translatable("message.auction.search.header", term, results.size())
				.withStyle(ChatFormatting.GOLD));
		for (AuctionItem item : results) {
			sendListingLine(player, item);
		}
		return 0;
	}

	private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		AuctionMenu.openAuctionHouse(player);
		return 0;
	}

	private static void sendListingLine(ServerPlayer player, AuctionItem item) {
		String timeLeft = formatTimeLeft(item.getExpireTime());
		MutableComponent line = Component.literal(" #" + item.getId() + " ")
				.withStyle(ChatFormatting.YELLOW)
				.append(item.getItem().getDisplayName())
				.append(Component.literal(" x" + item.getItem().getCount()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" - " + Config.getFormattedCurrency(item.getPrice()))
						.withStyle(ChatFormatting.GREEN))
				.append(Component.literal(" by " + item.getSellerName()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" [" + timeLeft + "]").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(line);
	}

	private static int adminClear(CommandContext<CommandSourceStack> context) {
		AuctionWSD awd = AuctionWSD.get();
		awd.clearAllListings();
		context.getSource().sendSuccess(() -> Component.translatable("message.auction.admin.clear"), true);
		return 0;
	}

	private static int adminRemove(CommandContext<CommandSourceStack> context) {
		int id = IntegerArgumentType.getInteger(context, "id");
		AuctionWSD awd = AuctionWSD.get();
		AuctionItem listing = awd.getListing(id);

		if (listing == null) {
			context.getSource().sendFailure(Component.translatable("message.auction.cancel.notfound"));
			return 1;
		}

		// Return item to seller's expired collection
		awd.addExpiredItem(listing.getSeller(), listing.getItem().copy());
		awd.removeListing(id);
		context.getSource().sendSuccess(() -> Component.translatable("message.auction.admin.remove", id), true);
		return 0;
	}

	private static String formatTimeLeft(long expireTime) {
		long remaining = expireTime - System.currentTimeMillis();
		if (remaining <= 0)
			return "Expired";
		long hours = TimeUnit.MILLISECONDS.toHours(remaining);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
		if (hours > 0)
			return hours + "h " + minutes + "m";
		return minutes + "m";
	}
}
