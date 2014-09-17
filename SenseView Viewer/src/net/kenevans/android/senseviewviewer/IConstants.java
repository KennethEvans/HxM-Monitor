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

package net.kenevans.android.senseviewviewer;

public interface IConstants {
	/** Tag to associate with log messages. */
	public static final String TAG = "SenseView Viewer";

	public static final String PREF_FILE_DIRECTORY = "fileDirectory";

	// Messages
	/** Intent code for a file path. */
	public static final String OPEN_FILE_PATH_CODE = "OpenFileName";
	/** Intent code for a message. */
	public static final String MSG_CODE = "MessageCode";

	// Messages
	/** Request code for selecting a file. */
	public static final int FILE_SELECT_CODE = 1;
	/** Request code for plotting. */
	public static final int PLOT_SELECT_CODE = 3;
	/** Result code for for a bad file. */
	public static final int RESULT_BAD_FILE = 1000;
	/** Result code for an error. */
	public static final int RESULT_ERROR = 1001;

}
