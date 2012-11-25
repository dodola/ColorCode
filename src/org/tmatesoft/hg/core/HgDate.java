/*
 * Copyright (c) 2011 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.core;

import java.util.Calendar;
import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Compound object to keep time  and time zone of a change. Time zone is not too useful unless you'd like to indicate where 
 * the change was made (original <em>hg</em> shows date of a change in its original time zone) 
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public final class HgDate implements Comparable<HgDate>, Cloneable {
	private final long time;
	private final TimeZone tzone;
	

	/**
	 * @param millis UTC, milliseconds
	 * @param timezone zone offset in seconds, UTC - local == timezone. I.e. positive in the Western Hemisphere.  
	 */
	public HgDate(long millis, int timezone) {
		time = millis;
		// @see http://pydoc.org/2.5.1/time.html  time.timezone -- difference in seconds between UTC and local standard time
		// UTC - local = timezone. local = UTC - timezone
		// In Java, timezone is positive right of Greenwich, UTC+timezone = local
		//
		//
		/*
		 * The approach with available-short didn't work out as final timezone still relied
		 * on daylight saving settings (and added/substracted hour shift according to date supplied)
		 * E.g. 1218917104000 in zone GMT+2 (hello sample, changeset #2), results in EET timezone and 23 hours instead of 22
		 * 
		String[] available = TimeZone.getAvailableIDs(-timezone * 1000);
		assert available != null && available.length > 0 : String.valueOf(timezone);
		// this is sort of hack, I don't know another way how to get 
		// abbreviated name from zone offset (other than to have own mapping)
		// And I can't use any id, because e.g. zone with id  "US/Mountain" 
		// gives incorrect (according to hg cmdline) result, unlike MST or US/Arizona (all ids for zone -0700)
		// use 1125044450000L to see the difference
		String shortID = TimeZone.getTimeZone(available[0]).getDisplayName(false, TimeZone.SHORT);
		// XXX in fact, might need to handle daylight saving time, but not sure how, 
		// getTimeZone(GMT-timezone*1000).inDaylightTime()?
		TimeZone tz = TimeZone.getTimeZone(shortID);
		*/
		int tz_hours = -timezone/3600;
		int tz_mins = timezone % 3600;
		String customId = String.format("GMT%+02d:%02d", tz_hours, tz_mins);
		TimeZone tz = TimeZone.getTimeZone(customId);
		tzone = tz;
	}
	
	public long getRawTime() {
		return time;
	}
	
	/**
	 * @return zone object by reference, do not alter it (make own copy by {@link TimeZone#clone()}, to modify). 
	 */
	public TimeZone getTimeZone() {
		return tzone;
	}
	
	@Override
	public String toString() {
		// format the same way hg does
		return toString(Locale.US);
	}
	
	public String toString(Locale l) {
		Calendar c = Calendar.getInstance(getTimeZone());
		c.setTimeInMillis(getRawTime());
		Formatter f = new Formatter(new StringBuilder(), l);
		f.format("%ta %<tb %<td %<tH:%<tM:%<tS %<tY %<tz", c);
		return f.out().toString();
	}

	public int compareTo(HgDate o) {
		return (int) (time - o.time);
	}

	@Override
	public boolean equals(Object obj) {
		if (false == obj instanceof HgDate) {
			return false;
		}
		HgDate other = (HgDate) obj;
		return compareTo(other) == 0;
	}
	
	@Override
	public int hashCode() {
		// copied from java.util.Datge
		return (int) time ^ (int) (time >> 32);
	}

	@Override
	protected Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException ex) {
			throw new InternalError(ex.toString());
		}
	}
}
