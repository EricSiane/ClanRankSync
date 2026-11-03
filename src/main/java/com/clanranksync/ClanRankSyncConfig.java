package com.clanranksync;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clan-rank-sync")
public interface ClanRankSyncConfig extends Config
{
	@ConfigSection(
		name = "Data source",
		description = "Where to pull the target roster from",
		position = 0
	)
	String dataSourceSection = "dataSource";

	@ConfigItem(
		keyName = "sourceUrl",
		name = "Sheet/CSV URL",
		description = "A Google Sheets or Drive share link (auto-converted to CSV) or a direct HTTPS URL returning CSV. Leave blank to disable",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = dataSourceSection,
		position = 0
	)
	default String sourceUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "authHeader",
		name = "Authorization header",
		description = "Optional. Sent as the Authorization header, for private API endpoints",
		secret = true,
		section = dataSourceSection,
		position = 1
	)
	default String authHeader()
	{
		return "";
	}

	@ConfigSection(
		name = "Columns",
		description = "1-based column numbers in the CSV",
		position = 1
	)
	String columnsSection = "columns";

	@ConfigItem(
		keyName = "hasHeaderRow",
		name = "CSV has header row",
		description = "Skip the first row when reading data",
		section = columnsSection,
		position = 0
	)
	default boolean hasHeaderRow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rsnColumn",
		name = "RSN column",
		description = "1-based column number containing the player's RuneScape name",
		section = columnsSection,
		position = 1
	)
	default int rsnColumn()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "rankColumn",
		name = "Rank column",
		description = "1-based column number containing the target clan rank/title",
		section = columnsSection,
		position = 2
	)
	default int rankColumn()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "syncAlts",
		name = "Sync alts to main rank",
		description = "Expect alt accounts to hold the same rank as their main. Needs the Alts column below",
		section = columnsSection,
		position = 3
	)
	default boolean syncAlts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "altsColumn",
		name = "Alts column",
		description = "1-based column number containing alt RSNs. Only used when \"Sync alts to main rank\" is on",
		section = columnsSection,
		position = 4
	)
	default int altsColumn()
	{
		return 3;
	}

	@ConfigSection(
		name = "Behavior",
		description = "How mismatches are detected",
		position = 2
	)
	String behaviorSection = "behavior";

	@ConfigItem(
		keyName = "ignoredRanks",
		name = "Ignored ranks",
		description = "Members holding these in-game ranks are never flagged, regardless of the sheet. Separate with commas",
		section = behaviorSection,
		position = 0
	)
	default String ignoredRanks()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rankAliases",
		name = "Rank aliases",
		description = "Use when the sheet names a rank differently from the game. Format: in-game rank;sheet rank. Separate pairs with commas, e.g. Mithril;Mithril Bar, Rune;Rune Bar",
		section = behaviorSection,
		position = 1
	)
	default String rankAliases()
	{
		return "";
	}

	@ConfigItem(
		keyName = "ignoredRsns",
		name = "Ignored RSNs",
		description = "These players are never flagged, whatever their rank. Separate names with commas",
		section = behaviorSection,
		position = 2
	)
	default String ignoredRsns()
	{
		return "";
	}
}
