package dicemc.money.storage;

import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class AuctionItem {
	private final int id;
	private final UUID seller;
	private final String sellerName;
	private final ItemStack item;
	private final double price;
	private final long expireTime;

	public AuctionItem(int id, UUID seller, String sellerName, ItemStack item, double price, long expireTime) {
		this.id = id;
		this.seller = seller;
		this.sellerName = sellerName;
		this.item = item;
		this.price = price;
		this.expireTime = expireTime;
	}

	public int getId() {
		return id;
	}

	public UUID getSeller() {
		return seller;
	}

	public String getSellerName() {
		return sellerName;
	}

	public ItemStack getItem() {
		return item;
	}

	public double getPrice() {
		return price;
	}

	public long getExpireTime() {
		return expireTime;
	}

	public boolean isExpired() {
		return System.currentTimeMillis() > expireTime;
	}

	public CompoundTag save(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", id);
		tag.putUUID("seller", seller);
		tag.putString("sellerName", sellerName);
		tag.put("item", item.save(provider));
		tag.putDouble("price", price);
		tag.putLong("expireTime", expireTime);
		return tag;
	}

	public static AuctionItem load(CompoundTag tag, HolderLookup.Provider provider) {
		return new AuctionItem(
				tag.getInt("id"),
				tag.getUUID("seller"),
				tag.getString("sellerName"),
				ItemStack.parse(provider, tag.getCompound("item")).orElse(ItemStack.EMPTY),
				tag.getDouble("price"),
				tag.getLong("expireTime"));
	}
}
