package dicemc.money.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import dicemc.money.MoneyMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class AuctionWSD extends SavedData {
	private static final String DATA_NAME = MoneyMod.MOD_ID + "_auction";

	private int nextId = 1;
	private final List<AuctionItem> listings = new ArrayList<>();
	private final Map<UUID, List<ItemStack>> expiredItems = new HashMap<>();

	public AuctionWSD() {
	}

	public AuctionWSD(CompoundTag nbt, HolderLookup.Provider provider) {
		nextId = nbt.getInt("nextId");
		ListTag listingsTag = nbt.getList("listings", Tag.TAG_COMPOUND);
		for (int i = 0; i < listingsTag.size(); i++) {
			listings.add(AuctionItem.load(listingsTag.getCompound(i), provider));
		}
		ListTag expiredTag = nbt.getList("expired", Tag.TAG_COMPOUND);
		for (int i = 0; i < expiredTag.size(); i++) {
			CompoundTag entry = expiredTag.getCompound(i);
			UUID owner = entry.getUUID("owner");
			ListTag items = entry.getList("items", Tag.TAG_COMPOUND);
			List<ItemStack> stacks = new ArrayList<>();
			for (int j = 0; j < items.size(); j++) {
				ItemStack stack = ItemStack.parse(provider, items.getCompound(j)).orElse(ItemStack.EMPTY);
				if (!stack.isEmpty())
					stacks.add(stack);
			}
			if (!stacks.isEmpty())
				expiredItems.put(owner, stacks);
		}
	}

	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
		nbt.putInt("nextId", nextId);
		ListTag listingsTag = new ListTag();
		for (AuctionItem item : listings) {
			listingsTag.add(item.save(provider));
		}
		nbt.put("listings", listingsTag);
		ListTag expiredTag = new ListTag();
		for (Map.Entry<UUID, List<ItemStack>> entry : expiredItems.entrySet()) {
			CompoundTag entryTag = new CompoundTag();
			entryTag.putUUID("owner", entry.getKey());
			ListTag items = new ListTag();
			for (ItemStack stack : entry.getValue()) {
				items.add(stack.save(provider));
			}
			entryTag.put("items", items);
			expiredTag.add(entryTag);
		}
		nbt.put("expired", expiredTag);
		return nbt;
	}

	public int addListing(AuctionItem item) {
		listings.add(item);
		this.setDirty();
		return item.getId();
	}

	public int getNextId() {
		return nextId++;
	}

	public AuctionItem getListing(int id) {
		for (AuctionItem item : listings) {
			if (item.getId() == id)
				return item;
		}
		return null;
	}

	public boolean removeListing(int id) {
		boolean removed = listings.removeIf(item -> item.getId() == id);
		if (removed)
			this.setDirty();
		return removed;
	}

	public List<AuctionItem> getActiveListings() {
		processExpired();
		List<AuctionItem> active = new ArrayList<>();
		for (AuctionItem item : listings) {
			if (!item.isExpired())
				active.add(item);
		}
		return active;
	}

	public List<AuctionItem> getListingsBySeller(UUID seller) {
		List<AuctionItem> result = new ArrayList<>();
		for (AuctionItem item : listings) {
			if (item.getSeller().equals(seller))
				result.add(item);
		}
		return result;
	}

	public List<AuctionItem> searchListings(String term) {
		processExpired();
		String lower = term.toLowerCase();
		List<AuctionItem> result = new ArrayList<>();
		for (AuctionItem item : listings) {
			if (!item.isExpired() && item.getItem().getDisplayName().getString().toLowerCase().contains(lower)) {
				result.add(item);
			}
		}
		return result;
	}

	public int getActiveListingCount(UUID seller) {
		int count = 0;
		for (AuctionItem item : listings) {
			if (item.getSeller().equals(seller) && !item.isExpired())
				count++;
		}
		return count;
	}

	public void processExpired() {
		List<AuctionItem> expired = new ArrayList<>();
		for (AuctionItem item : listings) {
			if (item.isExpired())
				expired.add(item);
		}
		for (AuctionItem item : expired) {
			listings.remove(item);
			expiredItems.computeIfAbsent(item.getSeller(), k -> new ArrayList<>()).add(item.getItem());
		}
		if (!expired.isEmpty())
			this.setDirty();
	}

	public List<ItemStack> getExpiredItems(UUID player) {
		return expiredItems.getOrDefault(player, new ArrayList<>());
	}

	public List<ItemStack> collectExpiredItems(UUID player) {
		List<ItemStack> items = expiredItems.remove(player);
		if (items != null && !items.isEmpty())
			this.setDirty();
		return items != null ? items : new ArrayList<>();
	}

	public void addExpiredItem(UUID player, ItemStack stack) {
		expiredItems.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
		this.setDirty();
	}

	public void clearAllListings() {
		for (AuctionItem item : listings) {
			expiredItems.computeIfAbsent(item.getSeller(), k -> new ArrayList<>()).add(item.getItem());
		}
		listings.clear();
		this.setDirty();
	}

	public static Factory<AuctionWSD> dataFactory() {
		return new SavedData.Factory<>(AuctionWSD::new, AuctionWSD::new, null);
	}

	public static AuctionWSD get() {
		if (ServerLifecycleHooks.getCurrentServer() != null)
			return ServerLifecycleHooks.getCurrentServer().overworld().getDataStorage().computeIfAbsent(dataFactory(),
					DATA_NAME);
		else
			return new AuctionWSD();
	}
}
