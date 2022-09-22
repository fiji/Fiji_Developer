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

import ij.Prefs;
import ij.gui.GenericDialog;

import java.awt.TextField;

public class GraphicalMediaWikiClient extends MediaWikiClient {
	protected String login, password;

	public GraphicalMediaWikiClient() {
		super();
	}

	public GraphicalMediaWikiClient(String wikiBaseURI) {
		super(wikiBaseURI);
	}

	public boolean login() {
		return login("Wiki login");
	}

	public boolean login(String title) {
		if (login != null && password != null)
			logIn(login, password);
		while (!isLoggedIn()) {
			GenericDialog gd = new GenericDialog(title);
			if (login == null)
				login = Prefs.get("fiji.wiki.user", "");
			gd.addStringField("Login", login, 20);
			gd.addStringField("Password", "", 20);
			((TextField)gd.getStringFields().lastElement())
				.setEchoChar('*');
			gd.showDialog();
			if (gd.wasCanceled())
				return false;

			login = gd.getNextString();
			Prefs.set("fiji.wiki.user", login);
			password = gd.getNextString();
			logIn(login, password);
		}
		return true;
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Need 1 arg: the wiki URL");
			System.exit(1);
		}

		GraphicalMediaWikiClient client =
			new GraphicalMediaWikiClient(args[0]);
		System.err.println("login: " + client.login("Test"));
	}
}
