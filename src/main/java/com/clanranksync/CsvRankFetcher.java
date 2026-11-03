package com.clanranksync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

class CsvRankFetcher
{
	private static final int MAX_BYTES = 5 * 1024 * 1024;
	private static final Pattern SHEET = Pattern.compile("^https://docs\\.google\\.com/spreadsheets/d/([\\w-]+)");
	private static final Pattern GID = Pattern.compile("[#&?]gid=(\\d+)");
	private static final Pattern DRIVE_FILE = Pattern.compile("^https://drive\\.google\\.com/file/d/([\\w-]+)");
	private static final Pattern DRIVE_ID = Pattern.compile("^https://drive\\.google\\.com/open\\?(?:.*&)?id=([\\w-]+)");

	private final OkHttpClient okHttpClient;
	private final ClanRankSyncConfig config;

	@Inject
	CsvRankFetcher(OkHttpClient okHttpClient, ClanRankSyncConfig config)
	{
		this.okHttpClient = okHttpClient;
		this.config = config;
	}

	/**
	 * Callbacks run on an OkHttp thread.
	 */
	Call fetch(Consumer<Map<String, ExpectedRank>> onSuccess, Consumer<String> onError)
	{
		HttpUrl url = HttpUrl.parse(toDownloadUrl(config.sourceUrl().trim()));
		if (url == null || !url.isHttps())
		{
			onError.accept("Enter a valid https:// link to a CSV file or Google Sheet.");
			return null;
		}

		Request.Builder request = new Request.Builder().url(url);
		String auth = config.authHeader().trim();
		if (!auth.isEmpty())
		{
			request.header("Authorization", auth);
		}

		Call call = okHttpClient.newCall(request.build());
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				if (!call.isCanceled())
				{
					onError.accept("Request failed: " + e.getMessage());
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						onError.accept("Server returned HTTP " + response.code()
							+ (response.code() == 401 || response.code() == 403 || response.code() == 404 ? " (is the link shared with \"anyone with the link\"?)" : ""));
						return;
					}

					ResponseBody body = response.body();
					MediaType type = body == null ? null : body.contentType();
					if (type != null && type.subtype().equalsIgnoreCase("html"))
					{
						onError.accept("The link returned a web page, not a CSV. Make sure it is publicly shared.");
						return;
					}

					Map<String, ExpectedRank> ranks = parse(readCapped(body.byteStream()));
					onSuccess.accept(ranks);
				}
				catch (Exception e)
				{
					onError.accept(e.getMessage() == null ? e.toString() : e.getMessage());
				}
			}
		});
		return call;
	}

	static String toDownloadUrl(String url)
	{
		Matcher sheet = SHEET.matcher(url);
		if (sheet.find() && !url.contains("/pub") && !url.contains("/export") && !url.contains("/gviz/"))
		{
			Matcher gid = GID.matcher(url);
			return "https://docs.google.com/spreadsheets/d/" + sheet.group(1) + "/export?format=csv"
				+ (gid.find() ? "&gid=" + gid.group(1) : "");
		}

		Matcher drive = DRIVE_FILE.matcher(url);
		if (drive.find() || (drive = DRIVE_ID.matcher(url)).find())
		{
			return "https://drive.google.com/uc?export=download&id=" + drive.group(1);
		}

		return url;
	}

	private static String readCapped(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) != -1)
		{
			out.write(buf, 0, n);
			if (out.size() > MAX_BYTES)
			{
				throw new IOException("CSV is larger than 5 MB");
			}
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	private Map<String, ExpectedRank> parse(String csv)
	{
		List<List<String>> rows = parseCsv(csv);
		if (rows.isEmpty())
		{
			throw new IllegalStateException("The CSV is empty");
		}

		int nameCol = config.rsnColumn() - 1;
		int rankCol = config.rankColumn() - 1;
		int altsCol = config.syncAlts() ? config.altsColumn() - 1 : -1;

		if (nameCol < 0 || rankCol < 0)
		{
			throw new IllegalStateException("The RSN and Rank column numbers must be 1 or higher");
		}

		Map<String, ExpectedRank> mains = new HashMap<>();
		Map<String, ExpectedRank> alts = new HashMap<>();
		for (List<String> row : rows.subList(config.hasHeaderRow() ? 1 : 0, rows.size()))
		{
			String name = cell(row, nameCol);
			String rank = cell(row, rankCol);
			if (name.isEmpty() || rank.isEmpty())
			{
				continue;
			}

			String mainKey = ClanRankSyncPlugin.normalizeName(name);
			mains.put(mainKey, new ExpectedRank(rank, null));

			if (altsCol < 0)
			{
				continue;
			}

			for (String altName : splitAlts(cell(row, altsCol)))
			{
				String altKey = ClanRankSyncPlugin.normalizeName(altName);
				if (!altKey.equals(mainKey))
				{
					alts.putIfAbsent(altKey, new ExpectedRank(rank, name));
				}
			}
		}

		// Being listed as someone's alt overrides any row the alt has of its own.
		mains.putAll(alts);
		return mains;
	}

	// Spaces are part of RSNs, so anything that can't appear in a name (commas, semicolons, slashes, newlines, braces...) separates alts.
	static List<String> splitAlts(String cell)
	{
		List<String> names = new ArrayList<>();
		for (String part : cell.split("[^\\p{L}\\p{N} \\u00A0_-]+"))
		{
			String name = part.trim();
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return names;
	}

	private static String cell(List<String> row, int index)
	{
		return index < row.size() ? row.get(index).trim() : "";
	}

	static List<List<String>> parseCsv(String text)
	{
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;

		if (text.startsWith("\uFEFF"))
		{
			text = text.substring(1);
		}

		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (quoted)
			{
				if (c == '"')
				{
					if (i + 1 < text.length() && text.charAt(i + 1) == '"')
					{
						field.append('"');
						i++;
					}
					else
					{
						quoted = false;
					}
				}
				else
				{
					field.append(c);
				}
			}
			else if (c == '"')
			{
				quoted = true;
			}
			else if (c == ',')
			{
				row.add(field.toString());
				field.setLength(0);
			}
			else if (c == '\n' || c == '\r')
			{
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n')
				{
					i++;
				}
				row.add(field.toString());
				field.setLength(0);
				if (row.size() > 1 || !row.get(0).isEmpty())
				{
					rows.add(row);
				}
				row = new ArrayList<>();
			}
			else
			{
				field.append(c);
			}
		}

		if (field.length() > 0 || !row.isEmpty())
		{
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}
}
