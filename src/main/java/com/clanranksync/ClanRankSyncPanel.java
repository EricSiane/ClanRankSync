package com.clanranksync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class ClanRankSyncPanel extends PluginPanel
{
	private final JLabel status = new JLabel("Press refresh to compare ranks.");
	private final JPanel list = new JPanel();
	private final JButton refresh = new JButton("Refresh");

	ClanRankSyncPanel(Runnable onRefresh)
	{
		setLayout(new BorderLayout(0, 8));

		refresh.addActionListener(e -> onRefresh.run());
		JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
		top.add(refresh);
		top.add(status);
		add(top, BorderLayout.NORTH);

		list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
		add(list, BorderLayout.CENTER);
	}

	void setLoading()
	{
		SwingUtilities.invokeLater(() ->
		{
			refresh.setEnabled(false);
			status.setText("Loading...");
		});
	}

	void setError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			refresh.setEnabled(true);
			status.setText(message);
			list.removeAll();
			list.revalidate();
			list.repaint();
		});
	}

	void setResults(List<RankMismatch> mismatches, int compared)
	{
		SwingUtilities.invokeLater(() ->
		{
			refresh.setEnabled(true);
			status.setText(mismatches.isEmpty()
				? "All " + compared + " compared members match."
				: mismatches.size() + " of " + compared + " compared members are out of sync.");

			list.removeAll();
			for (RankMismatch m : mismatches)
			{
				JPanel row = new JPanel(new GridLayout(2, 1));
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
				row.add(new JLabel(m.getAltOf() == null ? m.getName() : m.getName() + " (alt of " + m.getAltOf() + ")"));
				JLabel detail = new JLabel("Has " + m.getActualRank() + ", expected " + m.getExpectedRank());
				detail.setForeground(Color.ORANGE);
				row.add(detail);
				list.add(row);
				list.add(javax.swing.Box.createVerticalStrut(4));
			}
			list.revalidate();
			list.repaint();
		});
	}
}
