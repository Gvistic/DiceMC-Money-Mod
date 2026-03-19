package dicemc.money.setup;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.text.DecimalFormat;

public class Config {
	public static ModConfigSpec SERVER_CONFIG;

	public static final String CATEGORY_SERVER = "server";

	public static final String SUB_CATEGORY_SERVER = "Server";

	// Data Storage Scheme Variables
	public static ModConfigSpec.ConfigValue<Boolean> SETTING;
	// Misc variables
	public static ModConfigSpec.ConfigValue<Double> STARTING_FUNDS;
	public static ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
	public static ModConfigSpec.ConfigValue<Boolean> CURRENCY_POSITION;
	public static ModConfigSpec.ConfigValue<Integer> ADMIN_LEVEL;
	public static ModConfigSpec.ConfigValue<Integer> SHOP_LEVEL;
	public static ModConfigSpec.ConfigValue<Double> LOSS_ON_DEATH;
	public static ModConfigSpec.ConfigValue<Integer> TOP_SIZE;
	public static ModConfigSpec.ConfigValue<Boolean> ENABLE_HISTORY;
	// Auction House
	public static ModConfigSpec.ConfigValue<Double> AUCTION_TAX;
	public static ModConfigSpec.ConfigValue<Integer> AUCTION_MAX_LISTINGS;
	public static ModConfigSpec.ConfigValue<Integer> AUCTION_DURATION;

	static {
		ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

		SERVER_BUILDER.comment("Server Settings").push(CATEGORY_SERVER);
		setupServer(SERVER_BUILDER);
		SERVER_BUILDER.pop();

		SERVER_CONFIG = SERVER_BUILDER.build();
	}

	private static void setupServer(ModConfigSpec.Builder builder) {
		builder.comment("Server Settings").push(SUB_CATEGORY_SERVER);
		// Misc Variables
		STARTING_FUNDS = builder.comment("The amount of money a new player starts with when first joining the world.")
				.defineInRange("starting_funds", 1000D, 0, Double.MAX_VALUE);
		CURRENCY_SYMBOL = builder.comment("the character(s) to precede money values when displayed")
				.define("currency symbol", "$");
		CURRENCY_POSITION = builder
				.comment("if true the symbol appears to the left of the value, if false, it will appear to the right.")
				.define("Currency Symbol Left Oriented", true);
		ADMIN_LEVEL = builder.comment("the op level permitted to use admin commands")
				.defineInRange("admin level", 2, 0, 4, Integer.class);
		SHOP_LEVEL = builder.comment("The minimum permission level to create basic shops.  default= 0 = all players")
				.defineInRange("shop level", 0, 0, 4, Integer.class);
		LOSS_ON_DEATH = builder.comment(
				"a percentage of the player's account that is lost on death, in decimal form. default = 0%. 0.5 = 50%")
				.defineInRange("loss on death", 0d, 0d, 1d);
		TOP_SIZE = builder
				.comment("The number of players to be displayed when the top command is used.  set to zero to disable")
				.define("top size", 10);
		ENABLE_HISTORY = builder
				.comment(
						"This setting turns enables transactions to be recorded using the H2 embedded database system.",
						"leaving this disabled will prevent the creation of the database.  It is recommended that",
						"you use an application like DBeaver to view and query your data.")
				.define("enable_history", false);
		// Auction House
		AUCTION_TAX = builder.comment(
				"The percentage tax charged when listing an item on the auction house. 0.05 = 5% of the listing price.")
				.defineInRange("auction_tax", 0.05d, 0d, 1d);
		AUCTION_MAX_LISTINGS = builder
				.comment("The maximum number of active listings a player can have. Set to 0 for unlimited.")
				.defineInRange("auction_max_listings", 10, 0, 100, Integer.class);
		AUCTION_DURATION = builder.comment("The number of hours an auction listing lasts before expiring.")
				.defineInRange("auction_duration_hours", 48, 1, 720, Integer.class);

		builder.pop();
	}

	public static String getFormattedCurrency(double value) {
		return CURRENCY_POSITION.get() ? CURRENCY_SYMBOL.get() + value : value + CURRENCY_SYMBOL.get();
	}

	public static String getFormattedCurrency(DecimalFormat df, double value) {
		return CURRENCY_POSITION.get() ? CURRENCY_SYMBOL.get() + df.format(value)
				: df.format(value) + CURRENCY_SYMBOL.get();
	}
}
