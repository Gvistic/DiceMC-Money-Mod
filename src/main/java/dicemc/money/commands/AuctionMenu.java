package dicemc.money.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dicemc.money.MoneyMod;
import dicemc.money.MoneyMod.AcctTypes;
import dicemc.money.setup.Config;
import dicemc.money.storage.AuctionItem;
import dicemc.money.storage.AuctionWSD;
import dicemc.money.storage.DatabaseManager;
import dicemc.money.storage.MoneyWSD;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

public class AuctionMenu extends AbstractContainerMenu {
	private static final int ROWS = 6;
	private static final int COLUMNS = 9;
	private static final int CONTAINER_SIZE = ROWS * COLUMNS;
	private static final int ITEM_SLOTS = (ROWS - 1) * COLUMNS; // 45 item slots, row 5 = nav

	private static final int SLOT_PREV = 45;
	private static final int SLOT_MY_LISTINGS = 47;
	private static final int SLOT_PAGE_INFO = 49;
	private static final int SLOT_COLLECT = 51;
	private static final int SLOT_NEXT = 53;

	private final SimpleContainer container;
	private final Map<Integer, Integer> slotToListingId = new HashMap<>();
	private int currentPage = 1;
	private boolean viewingMyListings = false;

	public AuctionMenu(int containerId, Inventory playerInv) {
		this(containerId, playerInv, new SimpleContainer(CONTAINER_SIZE));
	}

	public AuctionMenu(int containerId, Inventory playerInv, SimpleContainer container) {
		super(MenuType.GENERIC_9x6, containerId);
		this.container = container;

		int yOffset = (ROWS - 4) * 18;

		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + yOffset));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, 161 + yOffset));
		}
	}

	public void initDisplay(Player player) {
		fillDisplay(player);
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (slotId < 0 || slotId >= CONTAINER_SIZE)
			return;

		if (slotId < ITEM_SLOTS) {
			handleItemClick(slotId, player);
		} else {
			handleNavClick(slotId, player);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	private void handleItemClick(int slotId, Player player) {
		if (!(player instanceof ServerPlayer sp))
			return;
		Integer listingId = slotToListingId.get(slotId);
		if (listingId == null)
			return;

		AuctionWSD awd = AuctionWSD.get();
		AuctionItem listing = awd.getListing(listingId);
		if (listing == null) {
			sp.sendSystemMessage(Component.translatable("message.auction.buy.notfound"));
			refreshDisplay(player);
			return;
		}

		if (viewingMyListings) {
			if (!listing.getSeller().equals(player.getUUID()) && !player.hasPermissions(Config.ADMIN_LEVEL.get())) {
				sp.sendSystemMessage(Component.translatable("message.auction.cancel.notowner"));
				return;
			}
			ItemStack returnItem = listing.getItem().copy();
			if (!player.addItem(returnItem))
				player.drop(returnItem, false);
			awd.removeListing(listingId);
			sp.sendSystemMessage(Component.translatable("message.auction.cancel.success", returnItem.getDisplayName()));
			refreshDisplay(player);
		} else {
			if (listing.isExpired()) {
				sp.sendSystemMessage(Component.translatable("message.auction.buy.notfound"));
				refreshDisplay(player);
				return;
			}
			if (listing.getSeller().equals(player.getUUID())) {
				sp.sendSystemMessage(Component.translatable("message.auction.buy.own"));
				return;
			}
			double balance = MoneyWSD.get().getBalance(AcctTypes.PLAYER.key, player.getUUID());
			if (balance < listing.getPrice()) {
				sp.sendSystemMessage(Component.translatable("message.auction.buy.funds"));
				return;
			}

			MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, player.getUUID(), -listing.getPrice());
			MoneyWSD.get().changeBalance(AcctTypes.PLAYER.key, listing.getSeller(), listing.getPrice());

			if (Config.ENABLE_HISTORY.get()) {
				MoneyMod.dbm.postEntry(System.currentTimeMillis(), player.getUUID(), AcctTypes.PLAYER.key,
						player.getName().getString(), listing.getSeller(), AcctTypes.PLAYER.key,
						listing.getSellerName(), listing.getPrice(), "Auction Purchase #" + listingId);
			}

			ItemStack purchasedItem = listing.getItem().copy();
			if (!player.addItem(purchasedItem))
				player.drop(purchasedItem, false);
			awd.removeListing(listingId);

			sp.sendSystemMessage(Component.translatable("message.auction.buy.success",
					listing.getItem().getDisplayName(), Config.getFormattedCurrency(listing.getPrice())));

			ServerPlayer seller = sp.getServer().getPlayerList().getPlayer(listing.getSeller());
			if (seller != null) {
				seller.sendSystemMessage(Component.translatable("message.auction.sold.notify",
						listing.getItem().getDisplayName(), Config.getFormattedCurrency(listing.getPrice()),
						player.getName().getString()));
			}
			refreshDisplay(player);
		}
	}

	private void handleNavClick(int slotId, Player player) {
		switch (slotId) {
			case SLOT_PREV -> {
				if (currentPage > 1) {
					currentPage--;
					refreshDisplay(player);
				}
			}
			case SLOT_NEXT -> {
				currentPage++;
				refreshDisplay(player);
			}
			case SLOT_MY_LISTINGS -> {
				viewingMyListings = !viewingMyListings;
				currentPage = 1;
				refreshDisplay(player);
			}
			case SLOT_COLLECT -> handleCollect(player);
			default -> {
			}
		}
	}

	private void handleCollect(Player player) {
		if (!(player instanceof ServerPlayer sp))
			return;
		AuctionWSD awd = AuctionWSD.get();
		awd.processExpired();
		List<ItemStack> items = awd.collectExpiredItems(player.getUUID());
		if (items.isEmpty()) {
			sp.sendSystemMessage(Component.translatable("message.auction.collect.empty"));
			return;
		}
		for (ItemStack stack : items) {
			if (!player.addItem(stack.copy()))
				player.drop(stack.copy(), false);
		}
		sp.sendSystemMessage(Component.translatable("message.auction.collect.success", items.size()));
		refreshDisplay(player);
	}

	private void refreshDisplay(Player player) {
		fillDisplay(player);
		broadcastChanges();
	}

	private void fillDisplay(Player player) {
		slotToListingId.clear();
		for (int i = 0; i < CONTAINER_SIZE; i++) {
			container.setItem(i, ItemStack.EMPTY);
		}

		AuctionWSD awd = AuctionWSD.get();
		List<AuctionItem> listings;
		if (viewingMyListings) {
			listings = awd.getListingsBySeller(player.getUUID());
		} else {
			listings = awd.getActiveListings();
		}

		int totalPages = Math.max(1, (listings.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);
		if (currentPage > totalPages)
			currentPage = totalPages;

		int start = (currentPage - 1) * ITEM_SLOTS;
		int end = Math.min(start + ITEM_SLOTS, listings.size());

		for (int i = start; i < end; i++) {
			AuctionItem listing = listings.get(i);
			int slot = i - start;
			container.setItem(slot, createDisplayItem(listing, player));
			slotToListingId.put(slot, listing.getId());
		}

		fillNavigationBar(totalPages, player);
	}

	private ItemStack createDisplayItem(AuctionItem listing, Player player) {
		ItemStack display = listing.getItem().copy();
		List<Component> lore = new ArrayList<>();
		lore.add(Component.empty());
		lore.add(styledLine("Price: " + Config.getFormattedCurrency(listing.getPrice()), ChatFormatting.GREEN));
		lore.add(styledLine("Seller: " + listing.getSellerName(), ChatFormatting.GRAY));
		lore.add(styledLine("Expires: " + formatTimeLeft(listing.getExpireTime()), ChatFormatting.GRAY));
		lore.add(styledLine("ID: #" + listing.getId(), ChatFormatting.DARK_GRAY));
		lore.add(Component.empty());

		if (viewingMyListings) {
			lore.add(styledLine("Click to cancel listing", ChatFormatting.RED));
		} else if (listing.getSeller().equals(player.getUUID())) {
			lore.add(styledLine("This is your listing", ChatFormatting.GRAY));
		} else {
			lore.add(styledLine("Click to purchase!", ChatFormatting.YELLOW));
		}

		display.set(DataComponents.LORE, new ItemLore(lore));
		return display;
	}

	private void fillNavigationBar(int totalPages, Player player) {
		ItemStack glass = namedItem(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY);
		for (int i = 45; i < 54; i++) {
			container.setItem(i, glass.copy());
		}

		if (currentPage > 1) {
			container.setItem(SLOT_PREV, namedItem(Items.ARROW, "Previous Page", ChatFormatting.YELLOW));
		}
		if (currentPage < totalPages) {
			container.setItem(SLOT_NEXT, namedItem(Items.ARROW, "Next Page", ChatFormatting.YELLOW));
		}

		if (viewingMyListings) {
			container.setItem(SLOT_MY_LISTINGS, namedItem(Items.BOOK, "Back to Browse", ChatFormatting.AQUA));
		} else {
			container.setItem(SLOT_MY_LISTINGS, namedItem(Items.BOOK, "My Listings", ChatFormatting.AQUA));
		}

		container.setItem(SLOT_PAGE_INFO,
				namedItem(Items.PAPER, "Page " + currentPage + "/" + totalPages, ChatFormatting.WHITE));

		AuctionWSD awd = AuctionWSD.get();
		List<ItemStack> expired = awd.getExpiredItems(player.getUUID());
		if (!expired.isEmpty()) {
			ItemStack collectBtn = new ItemStack(Items.CHEST, Math.min(expired.size(), 64));
			collectBtn.set(DataComponents.CUSTOM_NAME,
					Component.literal("Collect " + expired.size() + " item(s)")
							.withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withItalic(false)));
			container.setItem(SLOT_COLLECT, collectBtn);
		} else {
			container.setItem(SLOT_COLLECT, namedItem(Items.CHEST, "Collect Expired", ChatFormatting.GRAY));
		}
	}

	private static Component styledLine(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
	}

	private static ItemStack namedItem(ItemLike item, String name, ChatFormatting color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(Style.EMPTY.withColor(color).withItalic(false)));
		return stack;
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

	public static void openAuctionHouse(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(CONTAINER_SIZE);
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("message.auction.title");
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player p) {
				AuctionMenu menu = new AuctionMenu(id, playerInv, container);
				menu.initDisplay(p);
				return menu;
			}
		});
	}
}
