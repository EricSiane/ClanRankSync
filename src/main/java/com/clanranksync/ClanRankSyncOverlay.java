package com.clanranksync;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class ClanRankSyncOverlay extends Overlay
{
	private static final Color FILL = new Color(255, 0, 0, 60);
	private static final Color BORDER = new Color(255, 80, 80, 200);

	private final Client client;
	private final ClanRankSyncPlugin plugin;

	@Inject
	ClanRankSyncOverlay(Client client, ClanRankSyncPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<String, RankMismatch> mismatches = plugin.getMismatches();
		if (mismatches.isEmpty())
		{
			return null;
		}

		Widget names = client.getWidget(InterfaceID.ClansMembers.NAME);
		Widget viewport = client.getWidget(InterfaceID.ClansMembers.SCROLLCONTENTS);
		if (names == null || names.isHidden() || viewport == null)
		{
			return null;
		}

		Widget[] rows = names.getDynamicChildren();
		if (rows == null)
		{
			return null;
		}

		Shape oldClip = graphics.getClip();
		graphics.setClip(viewport.getBounds());
		FontMetrics fm = graphics.getFontMetrics();

		for (Widget row : rows)
		{
			String text = row.getText();
			if (text == null || text.isEmpty() || row.isSelfHidden())
			{
				continue;
			}

			RankMismatch mismatch = mismatches.get(ClanRankSyncPlugin.normalizeName(text));
			if (mismatch == null)
			{
				continue;
			}

			Rectangle b = row.getBounds();
			graphics.setColor(FILL);
			graphics.fill(b);
			graphics.setColor(BORDER);
			graphics.draw(b);

			String label = "Expected: " + mismatch.getExpectedRank();
			graphics.setColor(Color.WHITE);
			graphics.drawString(label, b.x + b.width - fm.stringWidth(label) - 2, b.y + b.height - 3);
		}

		graphics.setClip(oldClip);
		return null;
	}
}
