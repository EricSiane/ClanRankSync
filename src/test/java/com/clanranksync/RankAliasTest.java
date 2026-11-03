package com.clanranksync;

import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class RankAliasTest
{
	@Test
	public void exactMatchIgnoresCaseOnly()
	{
		Map<String, Set<String>> none = ClanRankSyncPlugin.parseAliases("");
		Assert.assertTrue(ClanRankSyncPlugin.ranksMatch("Mithril", "mithril", none));
		Assert.assertFalse(ClanRankSyncPlugin.ranksMatch("Mithril", "Mithril Bar", none));
	}

	@Test
	public void aliasesMapInGameRankToSheetRank()
	{
		Map<String, Set<String>> aliases = ClanRankSyncPlugin.parseAliases("Mithril;Mithril Bar, rune ; Rune Bar\nIron;Iron Bar;ignored");
		Assert.assertTrue(ClanRankSyncPlugin.ranksMatch("Mithril", "Mithril Bar", aliases));
		Assert.assertTrue(ClanRankSyncPlugin.ranksMatch("Rune", "rune bar", aliases));
		Assert.assertFalse(ClanRankSyncPlugin.ranksMatch("Mithril Bar", "Mithril", aliases));
		Assert.assertFalse(ClanRankSyncPlugin.ranksMatch("Mithril", "Rune Bar", aliases));
		Assert.assertFalse(ClanRankSyncPlugin.ranksMatch("Iron", "Iron Bar", aliases));
	}

	@Test
	public void ignoredNamesAreNormalized()
	{
		Set<String> names = ClanRankSyncPlugin.parseNames("Some_Guy, other guy;\n ,Third");
		Assert.assertEquals(3, names.size());
		Assert.assertTrue(names.contains("some guy"));
		Assert.assertTrue(names.contains("other guy"));
		Assert.assertTrue(names.contains("third"));
	}

	@Test
	public void malformedPairsAreSkipped()
	{
		Assert.assertTrue(ClanRankSyncPlugin.parseAliases("Mithril, ;Rune, Iron;").isEmpty());
	}
}
