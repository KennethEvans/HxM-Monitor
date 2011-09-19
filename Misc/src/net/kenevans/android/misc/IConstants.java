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

package net.kenevans.android.misc;

import android.net.Uri;

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
	// Log tag
	/** Tag to associate with log messages. */
	public static final String TAG = "Misc";

	// SMS database
	/** The URI for messages. Has all messages. */
	public static final Uri SMS_URI = Uri.parse("content://sms");
	/** SMS database column for the id. Identifies the row. */
	public static final String COL_ID = "_id";
	/** SMS database column for the address. */
	public static final String COL_ADDRESS = "address";
	/** SMS database column for the date. */
	public static final String COL_DATE = "date";
	/** SMS database column for the body. */
	public static final String COL_BODY = "body";

	// Conversations database
	// From SMS Fix Time:
	// The content://sms URI does not notify when a thread is deleted, so
	// instead we use the content://mms-sms/conversations URI for observing.
	// This provider, however, does not play nice when looking for and editing
	// the existing messages. So, we use the original content://sms URI for our
	// editing
	// /**
	// * The URI for message conversations. The id is the id of the last item in
	// * the conversation.
	// */
	// private static final Uri observingURI = Uri
	// .parse("content://mms-sms/conversations");

	// Messages
	/** Request code for displaying a message. */
	public static final int ACTIVITY_DISPLAY_MESSAGE = 0;
	/**
	 * Result code for ACTIVITY_DISPLAY_MESSAGE indicating the previous message.
	 */
	public static final int RESULT_PREV = 1000;
	/**
	 * Result code for ACTIVITY_DISPLAY_MESSAGE indicating the next message.
	 */
	public static final int RESULT_NEXT = 1001;

	// Mapping
	/** Value denoting a latitude in extras */
	public static final String LATITUDE = "net.kenevans.android.misc.latitude";
	/** Value denoting a latitude in extras */
	public static final String LONGITUDE = "net.kenevans.android.misc.longitude";
	/** Value denoting a SID in extras */
	public static final String SID = "net.kenevans.android.misc.sid";
	/** Value denoting a NID in extras */
	public static final String NID = "net.kenevans.android.misc.nid";

}
