package com.clanranksync;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "ClanRankSync",
	description = "Flags clan members whose in-game rank differs from a CSV or Google Sheet",
	tags = {"clan", "rank", "sync"}
)
public class ClanRankSyncPlugin extends Plugin
{
	private static final int RECHECK_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClanRankSyncOverlay overlay;

	@Inject
	private CsvRankFetcher fetcher;

	@Inject
	private ClanRankSyncConfig config;

	private ClanRankSyncPanel panel;
	private NavigationButton navButton;
	private Call pendingFetch;
	private int comparedCount;
	private int ticksSinceRecheck;

	@Getter
	private volatile Map<String, RankMismatch> mismatches = Collections.emptyMap();

	@Override
	protected void startUp()
	{
		panel = new ClanRankSyncPanel(this::refresh);
		navButton = NavigationButton.builder()
			.tooltip("Clan Rank Sync")
			.icon(ImageUtil.loadImageResource(ClanRankSyncPlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		if (pendingFetch != null)
		{
			pendingFetch.cancel();
			pendingFetch = null;
		}
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		mismatches = Collections.emptyMap();
		comparedCount = 0;
		ticksSinceRecheck = 0;
		panel = null;
		navButton = null;
	}

	private void refresh()
	{
		if (config.sourceUrl().trim().isEmpty())
		{
			panel.setError("Paste your sheet or CSV link into the Clan Rank Sync settings first.");
			return;
		}

		if (pendingFetch != null)
		{
			pendingFetch.cancel();
		}

		panel.setLoading();
		pendingFetch = fetcher.fetch(
			expected -> clientThread.invoke(() -> compare(expected)),
			error ->
			{
				log.debug("Failed to fetch expected ranks: {}", error);
				panel.setError(error);
			});
	}

	private void compare(Map<String, ExpectedRank> expected)
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			mismatches = Collections.emptyMap();
			panel.setError("Clan settings not loaded. Log in and join your clan first.");
			return;
		}

		Set<String> ignoredRanks = parseRanks(config.ignoredRanks());

		Map<String, Set<String>> aliases = parseAliases(config.rankAliases());
		Set<String> ignoredRsns = parseNames(config.ignoredRsns());

		Map<String, RankMismatch> found = new LinkedHashMap<>();
		int compared = 0;
		for (ClanMember member : settings.getMembers())
		{
			String key = normalizeName(member.getName());
			if (ignoredRsns.contains(key))
			{
				continue;
			}
			ExpectedRank entry = expected.get(key);
			if (entry == null)
			{
				continue;
			}
			String expectedRank = entry.getRank();

			String actualRank = rankName(settings, member);
			if (ignoredRanks.contains(normalizeRank(actualRank)))
			{
				continue;
			}

			compared++;
			if (!ranksMatch(actualRank, expectedRank, aliases))
			{
				found.put(key, new RankMismatch(Text.removeTags(member.getName()), actualRank, expectedRank, entry.getAltOf()));
			}
		}

		mismatches = Collections.unmodifiableMap(found);
		comparedCount = compared;
		panel.setResults(new ArrayList<>(found.values()), compared);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Map<String, RankMismatch> current = mismatches;
		if (current.isEmpty() || ++ticksSinceRecheck < RECHECK_TICKS)
		{
			return;
		}
		ticksSinceRecheck = 0;

		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return;
		}

		Set<String> ignoredRanks = parseRanks(config.ignoredRanks());
		Set<String> ignoredRsns = parseNames(config.ignoredRsns());
		Map<String, Set<String>> aliases = parseAliases(config.rankAliases());

		Map<String, RankMismatch> remaining = new LinkedHashMap<>();
		boolean changed = false;
		for (Map.Entry<String, RankMismatch> entry : current.entrySet())
		{
			RankMismatch flagged = entry.getValue();
			ClanMember member = settings.findMember(flagged.getName());
			if (member == null)
			{
				remaining.put(entry.getKey(), flagged);
				continue;
			}

			String actualRank = rankName(settings, member);
			if (ignoredRsns.contains(entry.getKey())
				|| ignoredRanks.contains(normalizeRank(actualRank))
				|| ranksMatch(actualRank, flagged.getExpectedRank(), aliases))
			{
				changed = true;
				continue;
			}

			if (actualRank.equals(flagged.getActualRank()))
			{
				remaining.put(entry.getKey(), flagged);
			}
			else
			{
				changed = true;
				remaining.put(entry.getKey(), new RankMismatch(flagged.getName(), actualRank, flagged.getExpectedRank(), flagged.getAltOf()));
			}
		}

		if (changed)
		{
			mismatches = Collections.unmodifiableMap(remaining);
			panel.setResults(new ArrayList<>(remaining.values()), comparedCount);
		}
	}

	private static String rankName(ClanSettings settings, ClanMember member)
	{
		ClanTitle title = settings.titleForRank(member.getRank());
		return title != null ? title.getName() : String.valueOf(member.getRank());
	}

	private static Set<String> parseRanks(String text)
	{
		Set<String> ranks = new HashSet<>();
		for (String rank : text.split("[,;\\n]"))
		{
			String normalized = normalizeRank(rank);
			if (!normalized.isEmpty())
			{
				ranks.add(normalized);
			}
		}
		return ranks;
	}

	static boolean ranksMatch(String actualRank, String expectedRank, Map<String, Set<String>> aliases)
	{
		String actual = normalizeRank(actualRank);
		String expected = normalizeRank(expectedRank);
		return expected.equals(actual) || aliases.getOrDefault(actual, Collections.emptySet()).contains(expected);
	}

	// Parses "in-game rank;sheet rank" pairs separated by commas or new lines.
	static Map<String, Set<String>> parseAliases(String text)
	{
		Map<String, Set<String>> aliases = new HashMap<>();
		for (String pair : text.split("[,\\n]"))
		{
			String[] parts = pair.split(";", 2);
			if (parts.length < 2)
			{
				continue;
			}

			String inGame = normalizeRank(parts[0]);
			String sheet = normalizeRank(parts[1]);
			if (!inGame.isEmpty() && !sheet.isEmpty())
			{
				aliases.computeIfAbsent(inGame, k -> new HashSet<>()).add(sheet);
			}
		}
		return aliases;
	}

	static Set<String> parseNames(String text)
	{
		Set<String> names = new HashSet<>();
		for (String name : text.split("[,;\\n]"))
		{
			String normalized = normalizeName(name);
			if (!normalized.isEmpty())
			{
				names.add(normalized);
			}
		}
		return names;
	}

	private static String normalizeRank(String rank)
	{
		return rank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	static String normalizeName(String name)
	{
		return Text.toJagexName(Text.removeTags(name)).toLowerCase(Locale.ROOT);
	}

	@Provides
	ClanRankSyncConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanRankSyncConfig.class);
	}
}
