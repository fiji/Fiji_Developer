/*-
 * #%L
 * Fiji Developer plugin for Fiji.
 * %%
 * Copyright (C) 2009 - 2022 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package fiji;

import ij.IJ;

public class MediaWikiTestDrive {
	private final static String URL = "http://my.wiki.org/wiki/index.php";
	private final static String USER = System.getProperty("wiki.user");
	private final static String PASSWORD = System.getProperty("wiki.password");

	public static void main(String[] args) {
		IJ.debugMode = true;
		final MediaWikiClient client = new MediaWikiClient(URL);
		if (!client.isLoggedIn()) {
			client.logIn(USER, PASSWORD);
		}
		//client.uploadPage("Hello_" + System.currentTimeMillis(), "Hello " + System.currentTimeMillis(), "comment");
		client.logOut();
	}
}
