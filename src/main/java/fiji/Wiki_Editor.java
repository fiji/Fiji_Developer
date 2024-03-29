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
/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2009, 2010 Mark Longair, Johannes Schindelin */

/*
  This file is part of the ImageJ plugin "Tutorial Maker".

  The ImageJ plugin "Tutorial Maker" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Tutorial Maker" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package fiji;

import ij.IJ;
import ij.ImagePlus;
import ij.Menus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.plugin.BrowserLauncher;
import ij.plugin.JpegWriter;
import ij.plugin.PNG_Writer;
import ij.plugin.PlugIn;

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTML.Attribute;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.scijava.Context;
import org.scijava.ui.swing.script.TextEditor;

public class Wiki_Editor implements PlugIn, ActionListener {
	protected String title;

	protected static String URL = "http://fiji.sc/wiki/";

	protected enum Mode { TUTORIAL_MAKER, NEWS, SCREENSHOT }

	protected Mode mode;
	protected ImagePlus screenshot;

	protected enum Format {
		JPEG(".jpg"),
		PNG(".png");

		private String extension;
		Format(String extension) {
			this.extension = extension;
		}

		public void write(ImagePlus imp, String fullFilename) {
			switch (this) {
			case JPEG:
				JpegWriter.save(imp, fullFilename, JpegWriter.DEFAULT_QUALITY);
				imp.changes = false;
				break;
			case PNG:
				PNG_Writer pngWriter = new PNG_Writer();
				try {
					pngWriter.writeImage(imp, fullFilename, -1);
					imp.changes = false;
				} catch(Exception e) {
					IJ.error("PNG_Writer.writeImage failed to write to " + fullFilename);
				}
				break;
			default:
				IJ.error("[BUG] Unknown image format: " + name());
			}
		}

		public static Format byExtension(String extension) {
			String ext = extension;
			int dot = ext.lastIndexOf('.');
			if (dot < 0)
				return null;
			ext = ext.substring(dot);
			for (Format format : Format.values())
				if (ext.equals(format.extension))
					return format;
			return null;
		}
	}

	protected Format imageFormat = Format.JPEG;
	protected final static String[] imageFormatNames;

	static {
		Format[] values = Format.values();
		imageFormatNames = new String[values.length];
		for (int i = 0; i < values.length; i++)
			imageFormatNames[i] = values[i].name();
	}

	@Override
	public void run(String arg) {
		String dialogTitle = "Tutorial Maker";
		String defaultTitle = "";
		String label = "Tutorial_title";
		mode = Mode.TUTORIAL_MAKER;

		if (arg.equals("rename")) {
			rename();
			return;
		}
		else if (arg.equals("news")) {
			mode = Mode.NEWS;
			dialogTitle = "Fiji News";
			defaultTitle = new SimpleDateFormat("yyyy-MM-dd - ")
				.format(Calendar.getInstance().getTime());
			label = "News_title";
		}
		else if (arg.equals("screenshot")) {
			screenshot = IJ.getImage();
			if (screenshot == null) {
				IJ.error("Which screenshot do you want to upload?");
				return;
			}
			mode = Mode.SCREENSHOT;
			dialogTitle = "Fiji Wiki Screenshot";
			defaultTitle = screenshot.getTitle().replace('_', ' ');
			int dot = defaultTitle.lastIndexOf('.');
			if (dot > 0)
				defaultTitle = defaultTitle.substring(0, dot);
			label = "Project_title (e.g. TrakEM2)";
		}
		else
			interceptRenames();

		GenericDialog gd = new GenericDialog(dialogTitle);
		gd.addStringField(label, defaultTitle, 20);
		gd.addChoice("Image_format", imageFormatNames, imageFormatNames[0]);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		title = gd.getNextString();
		imageFormat = Format.valueOf(gd.getNextChoice());
		if (title.length() == 0)
			return;
		if (mode != Mode.SCREENSHOT)
			title = capitalize(title).replace(' ', '_');
		else {
			title = capitalize(title);
			new Prettify_Wiki_Screenshot().run(screenshot.getProcessor());
			screenshot = IJ.getImage();
			String imageTitle = title + "-snapshot" + imageFormat.extension;
			for (int i = 2; wikiHasImage(imageTitle); i++)
				imageTitle = title + "-snapshot-" + i + imageFormat.extension;
			screenshot.setTitle(imageTitle.replace(' ', '_'));
		}

		addEditor();
	}

	protected static String capitalize(String string) {
		return string.substring(0, 1).toUpperCase()
			+ string.substring(1);
	}

	protected TextEditor editor;
	protected JMenuItem upload, preview, toBackToggle, renameImage,
		changeURL, insertPluginInfobox, whiteImage, importHTML;

	protected void addEditor() {
		final Context context = (Context) IJ.runPlugIn(Context.class.getName(), "");
		editor = new TextEditor(context);
		editor.getTextArea().setLineWrap(true);

		int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

		JMenu menu = new JMenu("Wiki");
		menu.setMnemonic(KeyEvent.VK_W);
		upload = editor.addToMenu(menu, "Upload", KeyEvent.VK_U, ctrl);
		preview = editor.addToMenu(menu, "Preview", KeyEvent.VK_R, ctrl);
		if (mode == Mode.TUTORIAL_MAKER) {
			toBackToggle = editor.addToMenu(menu, "", 0, 0);
			renameImage = editor.addToMenu(menu, "Rename Image", KeyEvent.VK_I, ctrl);
			whiteImage = editor.addToMenu(menu, "Make white background image", 0, 0);
			toBackToggleSetLabel();
			insertPluginInfobox = editor.addToMenu(menu,
					"Insert Plugin Infobox", 0, 0);
		}

		changeURL = editor.addToMenu(menu, "Change Wiki URL", 0, 0);
		importHTML = editor.addToMenu(menu, "Import HTML from URL...", 0, 0);

		for (int i = 0; i < menu.getItemCount(); i++)
			menu.getItem(i).addActionListener(this);

		editor.getJMenuBar().add(menu);

		editors.add(editor);

		editor.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				if (snapshotFrame != null)
					snapshotFrame.dispose();
				editors.remove(this);
			}
		});

		String text = "", category = "";
		switch (mode) {
			case TUTORIAL_MAKER:
				text = "== " + title.replace('_', ' ') + " ==\n\n";
				category = "\n[[Category:Tutorials]]";
				break;
			case NEWS:
				category = "\n[[Category:News]]";
				break;
			case SCREENSHOT:
				try {
					text = getPageSource("Fiji:Featured_Projects");
				} catch (IOException e) {
					IJ.error("Could not get page source for '" + title + "'");
					return;
				}
				if (!text.endsWith("\n"))
					text += "\n";
				text += "\n* " + title + "|"
					+ screenshot.getTitle() + "\n"
					+ "The [[" + title + "]] plugin <describe the project here>\n";
				break;
		}
		editor.getTextArea().setText(text + category);
		editor.getTextArea().setCaretPosition(text.length());

		JMenuBar menuBar = editor.getJMenuBar();
		for (int i = menuBar.getMenuCount() - 1; i >= 0; i--) {
			String label = menuBar.getMenu(i).getText();
			if (!label.equals("File") && !label.equals("Edit") &&
					!label.equals("Wiki"))
				menuBar.remove(i);
		}

		if (mode == Mode.TUTORIAL_MAKER)
			showSnapshotFrame();

		editor.setTitle("Edit Wiki - " + title);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				editor.setVisible(true);
			}
		});
	}

	public String getText() {
		return editor.getTextArea().getText();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == upload)
			new Thread() {
				@Override
				public void run() {
					upload();
				}
			}.start();
		else if (source == preview)
			new Thread() {
				@Override
				public void run() {
					preview();
				}
			}.start();
		else if (source == renameImage)
			renameImage();
		else if (source == whiteImage)
			makeWhiteBackgroundImage();
		else if (source == toBackToggle) {
			putSnapshotsToBack = !putSnapshotsToBack;
			toBackToggleSetLabel();
		}
		else if (source == changeURL) {
			GenericDialog gd = new GenericDialog("Change URL");
			gd.addStringField("URL", URL, 40);
			gd.showDialog();
			if (!gd.wasCanceled()) {
				URL = gd.getNextString();
				int off = URL.indexOf("/index.php");
				if (off > 0)
					URL = URL.substring(0, off + 1);
				client = null;
			}
		}
		else if (source == importHTML) {
			GenericDialog gd = new GenericDialog("Import HTML from URL");
			gd.addStringField("URL", "", 40);
			gd.showDialog();
			if (!gd.wasCanceled()) {
				String url = gd.getNextString();
				if (url.startsWith("http://") || url.startsWith("https://"))
					importHTML(url);
				else
					IJ.error("Invalid URL: " + url);
			}
		}
		else if (source == insertPluginInfobox) {
			JTextArea textArea = editor.getTextArea();
			Calendar now = Calendar.getInstance();
			String today = new SimpleDateFormat("dd/MM/yyyy")
				.format(now.getTime());
			textArea.insert("{{Infobox Plugin\n"
				+ "| software               = ImageJ\n"
				+ "| name                   = \n"
				+ "| maintainer             = [mailto:author_at_example_dot_com A U Thor]\n"
				+ "| author                 = A U Thor\n"
				+ "| source                 = \n"
				+ "| released               = " + today + "\n"
				+ "| latest version         = " + today + "\n"
				+ "| status                 = \n"
				+ "| category               = [[:Category:Plugins]]\n"
				+ "| website                = \n"
				+ "}}\n", 0);
			textArea.insert("\n[[Category:Plugins]]",
				textArea.getDocument().getLength());
		}
	}

	protected boolean putSnapshotsToBack = true;

	protected void toBackToggleSetLabel() {
		toBackToggle.setText(putSnapshotsToBack ?
			"Leave snapshots in the foreground" :
			"Put snapshots into the background");
	}

	GraphicalMediaWikiClient client;


	protected void getClient() {
		if (client != null)
			return;
		client = new GraphicalMediaWikiClient(URL + "index.php");
	}

	protected void upload() {
		IJ.showStatus("Uploading " + title + "...");
		IJ.showProgress(0, 1);
		List<String> images = getImages();
		if (!saveOrUploadImages(null, images))
			return;

		getClient();

		if (!client.login("Login for " + URL))
			return;

		if (!saveOrUploadImages(client, images))
			return;

		String name = mode == Mode.SCREENSHOT ?
			"Fiji:Featured_Projects" : this.title;
		boolean result =
			client.uploadPage(name, getText(), "Add " + this.title);

		client.logOut();

		IJ.showStatus("Uploading " + name + " finished.");
		IJ.showProgress(1, 1);

		if (!result) {
			IJ.error("Could not upload!");
			return;
		}

		new BrowserLauncher().run(URL + "index.php?title= " + name);
		editor.dispose();
	}

	protected void preview() {
		IJ.showStatus("Previewing " + title + "...");
		IJ.showProgress(0, 2);

		List<String> images = getImages();
		if (!saveOrUploadImages(null, images))
			return;

		getClient();

		if (!client.login("Wiki Login (Preview)"))
			return;
		String name = mode == Mode.SCREENSHOT ?
			"Fiji:Featured_Projects" : this.title;
		String html = client.uploadOrPreviewPage(name, getText(),
				"Add " + this.title, true);
		client.logOut();

		if (html == null) {
			IJ.error("Could not parse response");
			return;
		}

		IJ.showStatus("Preparing " + name + " for preview...");
		IJ.showProgress(1, 2);

		html = html.replaceAll("<img[^>]*src=\"(?=/wiki/)",
				"$0" + URL.substring(0, URL.length() - 6));

		int start = html.indexOf("<div class='previewnote'>");
		start = html.indexOf("</div>", start) + 6;
		int end = html.indexOf("<div id='toolbar'>");
		if (end < 0)
			end = html.indexOf("<div id=\"toolbar\">");
		html = "<html>\n<head>\n<title>Preview of " + name + "</title>\n"
			+ "<meta http-equiv='Content-Type' "
			+ "content='text/html; charset=utf-8'/>\n</head>\n<body>\n"
			+ html.substring(start, end)
			+ "</body>\n</html>\n";
		Pattern imagePattern = Pattern.compile("<a href=[^>]*DestFile=",
				Pattern.DOTALL);
		String[] parts = imagePattern.split(html);

		html = parts[0];
		for (int i = 1; i < parts.length; i++) {
			int quote = parts[i].indexOf('"', 1);
			int endTag = parts[i].indexOf("</a>", quote + 1);

			String image = parts[i].substring(0, quote);
			ImagePlus imp = WindowManager.getImage(image);
			if (imp == null && Character
					.isUpperCase(image.charAt(0))) {
				image = capitalize(image);
				imp = WindowManager.getImage(image);
			}

			if (imp == null)
				html += "&lt;img src=" + image + "&gt;";
			else {
				FileInfo info = imp.getOriginalFileInfo();
				File file = new File(info.directory,
						info.fileName);
				try {
					html += "<img src=\""
						+ file.toURI().toURL() + "\">";
				} catch (Exception e) { e.printStackTrace(); }
			}

			html += parts[i].substring(endTag + 4);
		}

		try {
			File file = File.createTempFile("preview", ".html");
			FileOutputStream out = new FileOutputStream(file);
			out.write(html.getBytes());
			out.close();

			new BrowserLauncher().run(file.toURI().toURL().toString());

			IJ.showStatus("Browsing " + name);
			IJ.showProgress(2, 2);
		} catch (IOException e) {
			e.printStackTrace();
			error(e.getMessage());
		}
	}

	public String getPageSource(String pageTitle) throws IOException {
		String result = getPage(pageTitle, "edit");
		client.logOut();
		int offset = result.indexOf("id=\"wpTextbox1\"");
		if (offset < 0)
			return "";
		offset = result.indexOf('>', offset);
		if (offset < 0)
			return "";
		int endOffset = result.indexOf("</textarea>", offset);
		if (endOffset < 0)
			return "";
		return result.substring(offset + 1, endOffset);
	}

	/* This method must not log out */
	public String getPage(String pageTitle) throws IOException {
		return getPage(pageTitle, null);
	}

	public String getPage(String pageTitle, String action) throws IOException {
		getClient();
		String[] getVars = {
			"title", pageTitle
		};
		if (action != null)
			getVars = new String[] {
				"title", pageTitle,
				"action", action
			};
		String result = client.sendRequest(getVars, null);
		if (result == null || result.indexOf("Login Required") > 0 ||
				result.indexOf("Login required") > 0) {
			// Try after login
			getClient();
			if (!client.login("Login to view " + pageTitle))
				return null;
			result = client.sendRequest(getVars, null);
		}
		return result;
	}

	protected List<String> getImages() {
		List<String> result = new ArrayList<String>();
		if (mode == Mode.SCREENSHOT) {
			result.add(screenshot.getTitle());
			return result;
		}
		String text = getText();
		int image = 0;
		for (;;) {
			image = text.indexOf("[[Image:", image);
			if (image < 0)
				return result;
			image = image + 8;
			int bracket = text.indexOf("]]", image);
			int pipe = text.indexOf('|', image);
			if (bracket < 0 || (pipe >= 0 && pipe < bracket))
				bracket = pipe;
			if (bracket < 0)
				return result;
			result.add(text.substring(image, bracket));
			image = bracket + 1;
		}
	}

	protected static boolean error(String message) {
		IJ.showProgress(1, 1);
		IJ.error(message);
		return false;
	}

	protected static String normalizeImageTitle(String page) {
		String pageTitle = page.replace(' ', '_');
		if (pageTitle.length() > 0)
			pageTitle = capitalize(pageTitle);
		for (;;) {
			int colon = pageTitle.indexOf(':');
			if (colon < 0)
				break;
			pageTitle = pageTitle.substring(0, colon) + pageTitle.substring(colon + 1);
		}
		return pageTitle;
	}

	protected FileInfo setTmpFileInfo(ImagePlus imp, String fileName) {
		FileInfo info = new FileInfo();
		info.width = imp.getWidth();
		info.height = imp.getHeight();
		info.directory = IJ.getDirectory("temp");
		info.fileName = fileName;
		imp.changes = true;
		imp.setFileInfo(info);
		return info;
	}

	protected boolean saveOrUploadImages(GraphicalMediaWikiClient clientOrNull,
			List<String> images) {
		int i = 0, total = images.size() * 2 + 1;
		for (String image : images) {
			ImagePlus imp = WindowManager.getImage(image);
			if (imp == null)
				return error("There is no image " + image);
			String newTitle = normalizeImageTitle(image);
			if (!image.equals(newTitle)) {
				if (!IJ.showMessageWithCancel("Rename Image",
						"Image title '" + image
						+ "' is invalid; fix?"))
					return error("Aborted");
				imp.setTitle(newTitle);
				rename(image, newTitle);
				images.set(i, newTitle);
			}
			FileInfo info = imp.getOriginalFileInfo();
			if (info == null)
				info = setTmpFileInfo(imp, image);
			if (info.directory == null) {
				info.directory = IJ.getDirectory("temp");
				imp.changes = true;
			}
			if (info.fileName == null) {
				info.fileName = image;
				imp.changes = true;
			}
			if (imp.changes) {
				String fullFilename = info.directory + "/" + info.fileName;
				Format format = Format.byExtension(fullFilename);
				if (format == null)
					format = this.imageFormat;
				format.write(imp, fullFilename);
			}
			if (clientOrNull != null) {
				if (wikiHasImage(image))
					switch (imageExistsDialog(image)) {
					case 1: return error("Aborted");
					case 2: continue;
					}
				if (!clientOrNull.login("Login to upload " + image))
					return false;
				if (!clientOrNull.uploadFile(image, "Upload " + image
							+ " for " + title,
							new File(info.directory,
								info.fileName))
						&& !wikiHasImage(image))
					return error("Uploading "
							+ image + " failed");
				IJ.showStatus("Uploading " + image + "...");
				IJ.showProgress(++i + total / 2, total);
			}
			else
				// TODO check if it is already there
				IJ.showProgress(++i, total);
		}
		return true;
	}

	protected String getContent(Element e) {
		try {
			int start = e.getStartOffset();
			int end = e.getEndOffset();
			String content = e.getDocument().getText(start, end - start);
			if (content.endsWith("\n"))
				content = content.substring(0, content.length() - 1);
			return content;
		} catch(BadLocationException e2) {
			return "";
		}
	}

	protected Object getAttribute(Object element, Object key) {
		if (element instanceof AttributeSet)
			return ((AttributeSet)element).getAttribute(key);
		return null;
	}

	/**
	 * The only purpose of this method is to take away all excuses from Bene
	 */
	protected void importHTML(String urlString) {
		try {
			StringBuffer buffer = new StringBuffer();

			HTMLDocument html = new HTMLDocument();
			html.putProperty("IgnoreCharsetDirective", new Boolean(true));
			HTMLEditorKit kit = new HTMLEditorKit();
			URL url = new URL(urlString);
			kit.read(url.openStream(), html, 0);

			Stack<Element> stack = new Stack<Element>();
			stack.push(html.getDefaultRootElement());

			while (!stack.empty()) {
				Element e = stack.pop();
				String name = e.getName();
				if (name.equals("head") || name.equals("br") || name.equals("comment") || name.equals("form") || name.equals("script"))
					continue;

				if (name.equals("html") || name.equals("body") || name.equals("p") || name.equals("p-implied") || name.equals("div") || name.equals("center")) {
					for (int i = e.getElementCount() - 1; i >= 0; i--)
						stack.push(e.getElement(i));
					continue;
				}

				String content = getContent(e);

				if (name.equals("content")) {
					Object a = getAttribute(e, Tag.A), href;
					if (a != null && (href = getAttribute(a, Attribute.HREF)) != null)
						buffer.append("[" + new URL(url, (String)href) + " " + content + "]");
					else
						buffer.append(content);
				}
				else if (name.equals("table")) {
					// TODO: allow nested tables, allow images inside
					buffer.append("{|\n");
					for (int i = 0; i < e.getElementCount(); i++) {
						if (i > 0)
							buffer.append("|-\n");
						Element e2 = e.getElement(i);
						for (int j = 0; j < e2.getElementCount(); j++)
							buffer.append("| " + getContent(e2.getElement(j))).append("\n");
					}
					buffer.append("|}\n");
				}
				else if (name.equals("ul")) {
					// TODO: handle nested lists, probably by getting rid of the Stack and doing proper recursion
					for (int i = 0; i < e.getElementCount(); i++)
						buffer.append("* " + getContent(e.getElement(i))).append("\n");
				}
				else if (name.equals("blockquote")) {
					for (int i = e.getElementCount() - 1; i >= 0; i--)
						stack.push(e.getElement(i));
					buffer.append(";").append(content.replaceAll("\n", "\n;"));
				}
				else if (name.equals("img")) {
					String src;
					if ((src = (String)getAttribute(e, Attribute.SRC)) != null) {
						// TODO: just save it as-is, to avoid re-saving
						// TODO: verify that the Wiki does not have the name yet (and modify otherwise)
						ImagePlus image = new ImagePlus(new URL(url, src).toString());
						image.show();
						// force saving to a temporary file
						String baseName = src.substring(src.lastIndexOf('/') + 1);
						setTmpFileInfo(image, baseName);
						buffer.append("[[Image:" + baseName + "]]");
					}
				}
				else if (name.equals("h1"))
					buffer.append("= " + content + " =\n");
				else if (name.equals("h2"))
					buffer.append("== " + content + " ==\n");
				else if (name.equals("h3"))
					buffer.append("=== " + content + " ===\n");
				else if (name.equals("h4"))
					buffer.append("==== " + content + " ====\n");
				else {
					IJ.log("Unhandled tag: " + name);
					continue;
				}
				buffer.append("\n");
			}

			JTextArea pane = editor.getEditorPane();
			pane.insert(buffer.toString(), pane.getCaretPosition());
		} catch (Exception e) {
			e.printStackTrace();
			IJ.error("Could not open " + urlString + ":\n \n" + e.getMessage());
		}
	}

	protected boolean wikiHasImage(String image) {
		try {
			String html = getPage("Image:" + image);
			boolean hasFile =
				html.indexOf("No file by this name exists") < 0;
			if (hasFile)
				System.err.println("has image: " + html);
			return hasFile;
		} catch (IOException e) {
			String message = "Could not retrieve image " + image + ": "
					+ e.getMessage();
			if ("HTTP code: 404".equals(e.getMessage()))
				System.err.println(message);
			else
				IJ.error(message);
			return false;
		}
	}

	protected int imageExistsDialog(String image) {
		GenericDialog gd = new GenericDialog("Image exists");
		gd.addMessage("The image '" + image + "' exists already on "
			+ "the Wiki");
		String[] choice = {
			"Upload '" + image + "' anyway",
			"Abort",
			"Skip uploading '" + image + "'"
		};
		gd.addChoice("action", choice, choice[0]);
		gd.showDialog();
		if (gd.wasCanceled())
			return 1;
		return gd.getNextChoiceIndex();
	}

	protected static List<TextEditor> editors = new ArrayList<TextEditor>();

	protected static String originalRename, originalRenameArg;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void interceptRenames() {
		if (originalRename != null)
			return;

		Hashtable commands = Menus.getCommands();
		if (commands != null) {
			originalRename = (String)commands.get("Rename...");
			if (originalRename.endsWith("\")")) {
				int paren = originalRename.lastIndexOf("(\"");
				originalRenameArg = originalRename.substring(paren + 2,
					originalRename.length() - 2);
				originalRename = originalRename.substring(0, paren);
			}
			else
				originalRenameArg = "";

			commands.put("Rename...", getClass().getName() + "(\"rename\")");
		}
	}

	protected void rename() {
		String oldTitle = WindowManager.getCurrentImage().getTitle();
		IJ.runPlugIn(originalRename, originalRenameArg);
		String newTitle = WindowManager.getCurrentImage().getTitle();
		rename(oldTitle, newTitle);
	}

	protected void rename(String oldTitle, String newTitle) {
		if (oldTitle.equals(newTitle))
			return;
		for (TextEditor textEditor : editors) {
			if (textEditor == null || textEditor.getTextArea() == null)
				continue;
			String text = textEditor.getTextArea().getText();
			String transformed = text.replaceAll("\\[\\[Image:"
					+ oldTitle.replaceAll("\\.", "\\\\.")
					+ "(?=[]|])",
				"[[Image:" + newTitle);
			if (!text.equals(transformed)) {
				int pos = textEditor.getTextArea()
					.getCaretPosition();
				textEditor.getTextArea().setText(transformed);
				try {
					textEditor.getTextArea()
						.setCaretPosition(pos);
				} catch (Exception e) { /* ignore */ }
			}
		}
	}

	protected void renameImage() {
		List<String> images = getImages();
		if (images.size() == 0) {
			IJ.error("The text refers to no image");
			return;
		}

		String[] list = images.toArray(new String[0]);
		GenericDialog gd = new GenericDialog("Rename Image");
		gd.addChoice("image", list, list[0]);
		gd.addStringField("new_title", list[0], 20);

		final TextField textField =
			(TextField)gd.getStringFields().lastElement();
		final Choice choice = (Choice)gd.getChoices().lastElement();
		choice.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				textField.setText(choice.getSelectedItem());
			}
		});

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		String oldTitle = gd.getNextChoice();
		String newTitle = gd.getNextString();
		if (newTitle.length() == 0)
			return;

		ImagePlus image = WindowManager.getImage(oldTitle);
		if (image == null) {
			IJ.error("No such image: " + oldTitle);
			return;
		}
		image.setTitle(newTitle);
		rename(oldTitle, newTitle);
	}

	protected void makeWhiteBackgroundImage() {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final Frame white = new Frame("White");
		white.setUndecorated(true);
		Panel panel = new Panel();
		panel.setSize(screenSize);
		panel.setMinimumSize(screenSize);
		panel.setBackground(Color.WHITE);
		white.add(panel);
		white.pack();
		white.setExtendedState(Frame.MAXIMIZED_BOTH);
		WindowManager.addWindow(white);
		KeyAdapter listener = new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int key = e.getKeyCode();
				if (key == KeyEvent.VK_ENTER)
					IJ.getInstance().requestFocus();
				else if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_W) {
					WindowManager.removeWindow(white);
					white.dispose();
				}
				else if (key == KeyEvent.VK_SPACE)
					white.toBack();
			}
		};
		white.addKeyListener(listener);
		panel.addKeyListener(listener);
		white.setVisible(true);
		white.requestFocus();
	}

	protected Frame snapshotFrame;

	public void showSnapshotFrame() {
		snapshotFrame = new Frame("Snapshot");
		snapshotFrame.setLayout(new FlowLayout());
		snapshotFrame.add(createButton("Snap", 0));
		snapshotFrame.add(createButton("Snap (3sec delay)", 3000));
		snapshotFrame.pack();
		snapshotFrame.setAlwaysOnTop(true);
		snapshotFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// TODO: ask first
				snapshotFrame.dispose();
			}
		});
		snapshotFrame.setVisible(true);
	}

	protected Button createButton(String text, long delay) {
		Button button = new Button(text);
		if (delay > 0) {
			AutoSnap auto = new AutoSnap(button, delay);
			button.addActionListener(auto);
			button.addMouseListener(auto);
			snapshotFrame.addMouseListener(auto);
		}
		else {
			button.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					snapshot();
				}
			});
			button.addMouseListener(new AutoSnap(button, 1000));
		}
		return button;
	}

	class AutoSnap extends MouseAdapter implements ActionListener {
		String originalLabel;
		Button button;
		int delay = 3;

		AutoSnap(Button button, long millis) {
			this.button = button;
			originalLabel = button.getLabel();
			delay = (int)(millis / 1000);
		}

		Thread thread;
		synchronized void startThread() {
			stopThread();
			thread = new Thread() {
				@Override
				public void run() {
					delayedSnap();
					button.setLabel(originalLabel);
				}
			};
			thread.start();
		}

		protected void delayedSnap() {
			for (int i = delay; i >= 0; i--) {
					if (!sleep(1000))
						return; /* stopped */
					button.setLabel(delay == 1 ? "..." :
						"Snap in " + i + " secs");
			}
			snapshot();
		}

		synchronized void stopThread() {
			if (thread == null)
				return;
			button.setLabel(originalLabel);
			thread.interrupt();
			thread = null;
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			startThread();
		}

		@Override
		public void mouseExited(MouseEvent e) {
			stopThread();
		}

		@Override
		public void actionPerformed(ActionEvent event) {
			stopThread();
			delayedSnap();
		}
	}

	protected int snapshotCounter;

	protected String getSnapshotName() {
		for (;;) {
			String result = title
				+ "-" + (++snapshotCounter) + imageFormat.extension;
			if (WindowManager.getImage(result) == null)
				return result;
		}
	}

	protected boolean sleep(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			return false;
		}
	}

	protected void snapshot() {
		try {
			Robot robot = new Robot();
			Rectangle rectangle = new Rectangle(IJ.getScreenSize());
			snapshotFrame.setVisible(false);
			Image image = robot.createScreenCapture(rectangle);
			snapshotFrame.setVisible(true);
			if (image != null) {
				String name = getSnapshotName();
				ImagePlus imp = new ImagePlus(name, image);
				imp.show();
				if (putSnapshotsToBack)
					imp.getWindow().toBack();

				/* insert into editor */
				int p = editor.getTextArea().getCaretPosition();
				String insert = "[[Image:" + name + "]]\n";
				editor.getTextArea().insert(insert, p);
				p += insert.length();
				editor.getTextArea().setCaretPosition(p);
			}
		} catch (AWTException e) { /* ignore */ }
	}

	public static void main(String[] args) {
		new Wiki_Editor().run("");
	}
}
