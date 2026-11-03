package com.clanranksync;

import lombok.Value;

@Value
class ExpectedRank
{
	String rank;
	/** Name of the main account this account is an alt of, or null if it is a main. */
	String altOf;
}
