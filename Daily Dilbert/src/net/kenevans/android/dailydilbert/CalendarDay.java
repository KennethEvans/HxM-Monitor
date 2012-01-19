//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.android.dailydilbert;

import java.util.Calendar;
import java.util.Date;

/**
 * Class to manage a day represented by the year, month, and day-of-the-month.
 * These values are defines as for a Calendar. That is, the month starts with 0
 * for January.
 * 
 * @see java.util.Calendar
 */
class CalendarDay implements Comparable<Object> {
	public int year;
	public int month;
	public int day;

	/**
	 * Constructor.
	 * 
	 * @param year
	 * @param month
	 * @param day
	 */
	public CalendarDay(int year, int month, int day) {
		set(year, month, day);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// Note the month starts with 1 for the string output
		return String.format("%04d-%02d-%02d", year, month + 1, day);
	}

	/**
	 * Get the Date corresponding to this instance.
	 * 
	 * @return
	 */
	public Date getDate() {
		Calendar cal = getCalendar();
		return cal.getTime();
	}

	/**
	 * Set the values for this instance from the given Date.
	 * 
	 * @param date
	 */
	public void set(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		set(cal);
	}

	/**
	 * Get the Calendar corresponding to this instance.
	 * 
	 * @return
	 */
	public Calendar getCalendar() {
		Calendar cal = Calendar.getInstance();
		cal.set(year, month, day, 0, 0, 0);
		return cal;
	}

	/**
	 * Set the values for this instance from the given Calendar.
	 * 
	 * @param cal
	 */
	public void set(Calendar cal) {
		year = cal.get(Calendar.YEAR);
		month = cal.get(Calendar.MONTH);
		day = cal.get(Calendar.DAY_OF_MONTH);
	}

	/**
	 * Sets the values for this instance.
	 * 
	 * @param year
	 * @param month
	 * @param day
	 */
	public void set(int year, int month, int day) {
		// Use the Calendar to recalculate the fields
		Calendar cal = Calendar.getInstance();
		cal.set(year, month, day, 0, 0, 0);
		set(cal);
	}

	/**
	 * Returns a new CalendarDay with its values incremented by the specified
	 * number of days from this one. If the amount is negative, it decrements
	 * the values. The year, month, and day will be adjusted to proper values.
	 * (Dec 31, 2012 incremented by 1 is Jan 1, 2013.)
	 * 
	 * @param number
	 *            The amount by which to increment.
	 */
	public CalendarDay incrementDay(int number) {
		return new CalendarDay(year, month, day+ number);
	}

	/**
	 * Returns a CalendarDay representing the current day.
	 * 
	 * @return
	 */
	public static CalendarDay now() {
		Calendar cal = Calendar.getInstance();
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH);
		int day = cal.get(Calendar.DAY_OF_MONTH);
		return new CalendarDay(year, month, day);
	}

	/**
	 * Returns a CalendarDay representing the first strip, which was on on April
	 * 16, 1989.
	 * 
	 * @return
	 */
	public static CalendarDay first() {
		return new CalendarDay(1989, 3, 16);
	}

	@Override
	public int compareTo(Object obj) {
		// Should throw an exception if obj is not a CalendarDay
		CalendarDay cDay = (CalendarDay) obj;
		// Assumes the two CalendarDays are normalized correctly
		if (year < cDay.year) {
			return -1;
		}
		if (year > cDay.year) {
			return 1;
		}
		// Years are the same
		if (month < cDay.month) {
			return -1;
		}
		if (month > cDay.month) {
			return 1;
		}
		// Months are the same
		if (day < cDay.day) {
			return -1;
		}
		if (day > cDay.day) {
			return 1;
		}
		return 0;
	}

}
