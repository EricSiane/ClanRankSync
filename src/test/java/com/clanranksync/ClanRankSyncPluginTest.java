package com.clanranksync;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanRankSyncPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanRankSyncPlugin.class);
		RuneLite.main(args);
	}
}
