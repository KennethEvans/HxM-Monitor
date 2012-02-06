package net.kenevans.android.dailydilbert;

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

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
	/** Tag to associate with log messages. */
	public static final String TAG = "DailyDilbert";

	/** Key for information URL sent to InfoActivity. */
	public static final String INFO_URL = "InformationURL";
	
	/** Key for year sent to ZoomActivity. */
	public static final String YEAR = "Year";
	
	/** Key for month sent to ZoomActivity. */
	public static final String MONTH = "Month";
	
	/** Key for day sent to ZoomActivity. */
	public static final String DAY = "Day";
	
	/** Where the image URL is found for todays strip. */
	public static final String urlPrefix = "http://www.dilbert.com";
	
	/** Where the image URL is found for archive strips. Append yyyy-mm-dd. */
	public static final String dateUrlPrefix = "http://www.dilbert.com/strips/comic/";
	
	/** Name of the file used for sharing. */
	public static final String SHARE_FILENAME = "Dilbert.png";
	
	/** Directory on the SD card where strips are saved */
	public static final String SD_CARD_DILBERT_DIRECTORY = "Dilbert";

}
