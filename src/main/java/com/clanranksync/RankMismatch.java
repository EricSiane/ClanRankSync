package com.clanranksync;

import lombok.Value;

@Value
class RankMismatch
{
	String name;
	String actualRank;
	String expectedRank;
	/** Main account this one is an alt of, or null. */
	String altOf;
}
