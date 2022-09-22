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
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class Upload_Image_To_Wiki implements PlugInFilter {
	String url = "http://fiji.sc/wiki/index.php";
	int jpegQuality = 75;
	String title;

	public int setup(String arg, ImagePlus image) {
		title = image == null ? null : image.getTitle();
		return DOES_ALL | NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		BufferedImage image = getBufferedImage(ip);
		byte[] jpeg = null, png = null;
		try { jpeg = getBytes(image, "jpeg"); } catch(IOException e) {}
		try { png = getBytes(image, "png"); } catch(IOException e) {}
		if (jpeg == null && png == null) {
			IJ.error("Could not construct JPEG nor PNG");
			return;
		}
		boolean usePNG = jpeg == null || jpeg.length > png.length;

		if (title == null)
			title = "<name>";
		else {
			String[] extensions = {
				".jpg", ".jpeg", ".JPG", ".JPEG", ".png", ".PNG"
			};
			for (String extension : extensions)
				if (title.endsWith(extension))
					title = title.substring(0,
						title.length()
						- extension.length());
		}
		title += usePNG ? ".png" : ".jpg";

		GenericDialog gd = new GenericDialog("Upload "
				+ (usePNG ? "PNG" : "JPEG"));
		gd.addStringField("name", title, 30);
		gd.addStringField("summary", title, 30);
		gd.addCheckbox("copy [[Image:<name>]] to clipboard", true);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		title = gd.getNextString();
		String summary = gd.getNextString();
		boolean copyToClipboard = gd.getNextBoolean();

		GraphicalMediaWikiClient client =
			new GraphicalMediaWikiClient(url);
		if (!client.login())
			return;
		if (!client.uploadFile(title, summary, usePNG ? png : jpeg))
			IJ.error("Failed to upload " + title);
		client.logOut();

		if (copyToClipboard)
			copyToClipboard();
	}

	BufferedImage getBufferedImage(ImageProcessor ip) {
		BufferedImage image = ip.getBufferedImage();
		if (!ip.isDefaultLut())
			return image;

		// we know it is a grayscale image here
		int width = ip.getWidth();
		int height = ip.getHeight();
		BufferedImage gray = new BufferedImage(width, height,
				BufferedImage.TYPE_BYTE_GRAY);
		Graphics g = gray.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return gray;
	}

	byte[] getBytes(BufferedImage image, String format) throws IOException {
		Iterator iter = ImageIO.getImageWritersByFormatName(format);
		ImageWriter writer = (ImageWriter)iter.next();
		if (writer == null)
			return null;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageOutputStream ios = ImageIO.createImageOutputStream(bytes);
		writer.setOutput(ios);
		ImageWriteParam param = writer.getDefaultWriteParam();
		if (format.equals("jpeg")) {
			param.setCompressionMode(param.MODE_EXPLICIT);
			param.setCompressionQuality(jpegQuality / 100f);
			if (jpegQuality == 100)
				param.setSourceSubsampling(1, 1, 0, 0);
		}
		IIOImage iioImage = new IIOImage(image, null, null);
		writer.write(null, iioImage, param);
		ios.close();
		writer.dispose();
		return bytes.toByteArray();
	}

	void copyToClipboard() {
		StringSelection selection =
			new StringSelection("[[Image:" + title + "]]");
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(selection, null);
		} catch (Exception e) { /* ignore */ }
		try {
			Toolkit.getDefaultToolkit().getSystemSelection()
				.setContents(selection, null);
		} catch (Exception e) { /* ignore */ }
	}
}
