/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2018 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imagej.legacy.command;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import net.imagej.legacy.IJ1Helper;
import net.imagej.legacy.LegacyService;

import org.scijava.MenuEntry;
import org.scijava.MenuPath;
import org.scijava.command.CommandInfo;
import org.scijava.input.Accelerator;
import org.scijava.input.InputModifiers;
import org.scijava.input.KeyCode;

/**
 * Discovers legacy ImageJ 1.x commands.
 * <p>
 * To accomplish this, we must crawl the (invisible) ImageJ 1.x AWT menu,
 * because legacy ImageJ does not store the list of commands in any other data
 * structure.
 * </p>
 *
 * @author Curtis Rueden
 * @author Barry DeZonia
 */
public class LegacyCommandFinder {

	private final LegacyService legacyService;

	public LegacyCommandFinder(final LegacyService legacyService) {
		this.legacyService = legacyService;
	}

	public List<CommandInfo> findCommands() {
		final List<CommandInfo> infos = new ArrayList<>();

		if (legacyService == null) return infos;
		final IJ1Helper ij1Helper = legacyService.getIJ1Helper();
		if (ij1Helper == null) return infos;

		final Map<String, MenuPath> menuTable = parseMenus();
		final Hashtable<String, String> commands = ij1Helper.getCommands();
		final ClassLoader classLoader = ij1Helper.getClassLoader();
		for (final String key : commands.keySet()) {
			final CommandInfo pe = createEntry(key, commands, menuTable, classLoader);
			if (pe != null) infos.add(pe);
		}
		legacyService.log().debug("Found " + infos.size() + " legacy plugins.");
		return infos;
	}

	// -- Helper methods --

	private CommandInfo createEntry(final Object key,
		final Hashtable<String, String> commands,
		final Map<String, MenuPath> menuTable,
		final ClassLoader classLoader)
	{
		final String ij1PluginString = commands.get(key).toString();

		final MenuPath menuPath = menuTable.get(key);

		final String className = parsePluginClass(ij1PluginString);
		final String arg = parseArg(ij1PluginString);

		final CommandInfo ci = //
			new LegacyCommandInfo(menuPath, className, arg, classLoader);
		return ci;
	}

	/** Creates a table mapping legacy ImageJ command labels to menu paths. */
	private Map<String, MenuPath> parseMenus() {
		final Map<String, MenuPath> menuTable = new HashMap<>();
		final JMenuBar menubar = legacyService.getIJ1Helper().getMenuBar();
		if (menubar == null) return menuTable;
		final int menuCount = menubar.getMenuCount();
		for (int i = 0; i < menuCount; i++) {
			final JMenu menu = menubar.getMenu(i);
			parseMenu(menu, i, new MenuPath(), menuTable);
		}
		return menuTable;
	}

	private void parseMenu(JMenuItem menuItem, final double weight,
		final MenuPath path, final Map<String, MenuPath> menuTable)
	{
		// build menu entry
		if(menuItem==null) menuItem=new JMenuItem("-");
		final String name = menuItem.getLabel();
		final MenuEntry entry = new MenuEntry(name, weight);
		//final MenuShortcut shortcut = menuItem.getShortcut();
		final KeyStroke shortcut = menuItem.getAccelerator();
		if (shortcut != null) {
			// convert Swing KeyStroke to ImageJ Accelerator
			final int code = shortcut.getKeyCode();
			final boolean meta = Accelerator.isCtrlReplacedWithMeta();
			final boolean ctrl = !meta;
			final boolean shift = (shortcut.getModifiers() & InputEvent.SHIFT_DOWN_MASK)>0;
			final KeyCode keyCode = KeyCode.get(code);
			final InputModifiers modifiers = new InputModifiers(false, false, ctrl,
				meta, shift, false, false, false);
			final Accelerator acc = new Accelerator(keyCode, modifiers);
			entry.setAccelerator(acc);
		}
		path.add(entry);

		if (menuItem instanceof JMenu) { // non-leaf
			// recursively process child menu items
			final JMenu menu = (JMenu) menuItem;
			final int itemCount = menu.getItemCount();
			double w = -1;
			for (int i = 0; i < itemCount; i++) {
				final JMenuItem item = menu.getItem(i);
				final boolean isSeparator = (item==null || item.getLabel().equals("-"));
				if (isSeparator) w += 10;
				else w += 1;
				parseMenu(item, w, new MenuPath(path), menuTable);
			}
		}
		else { // leaf item
			// add menu item to table
			menuTable.put(menuItem.getLabel(), path);
		}
	}

	private String parsePluginClass(final String ij1PluginString) {
		final int quote = ij1PluginString.indexOf("(");
		if (quote < 0) return ij1PluginString;
		return ij1PluginString.substring(0, quote);
	}

	private String parseArg(final String ij1PluginString) {
		final int quote = ij1PluginString.indexOf("\"");
		if (quote < 0) return "";
		final int quote2 = ij1PluginString.indexOf("\"", quote + 1);
		if (quote2 < 0) return ij1PluginString.substring(quote + 1);
		return ij1PluginString.substring(quote + 1, quote2);
	}
}
