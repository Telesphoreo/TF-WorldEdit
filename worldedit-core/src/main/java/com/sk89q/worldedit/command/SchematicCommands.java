/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.command.binding.Switch;
import com.sk89q.worldedit.util.command.parametric.Optional;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.io.file.FilenameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Commands that work with schematic files.
 */
public class SchematicCommands {

    /**
     * 9 schematics per page fits in the MC chat window.
     */
    private static final int SCHEMATICS_PER_PAGE = 9;
    private static final Logger log = LoggerFactory.getLogger(SchematicCommands.class);
    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public SchematicCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
            aliases = { "load" },
            usage = "[<format>] <filename>",
            desc = "Load a schematic into your clipboard",
            min = 1, max = 2
    )
    @CommandPermissions({ "worldedit.clipboard.load", "worldedit.schematic.load" })
    public void load(Player player, LocalSession session, @Optional("sponge") String formatName, String filename) throws FilenameException {
        LocalConfiguration config = worldEdit.getConfiguration();

        File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);
        File f = worldEdit.getSafeOpenFile(player, dir, filename, BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension(), ClipboardFormats.getFileExtensionArray());

        if (!f.exists()) {
            player.printError("Schematic " + filename + " does not exist!");
            return;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(f);
        if (format == null) {
            format = ClipboardFormats.findByAlias(formatName);
        }
        if (format == null) {
            player.printError("Unknown schematic format: " + formatName);
            return;
        }

        try (Closer closer = Closer.create()) {
            FileInputStream fis = closer.register(new FileInputStream(f));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ClipboardReader reader = closer.register(format.getReader(bis));

            Clipboard clipboard = reader.read();
            session.setClipboard(new ClipboardHolder(clipboard));

            log.info(player.getName() + " loaded " + f.getCanonicalPath());
            player.print(filename + " loaded. Paste it with //paste");
        } catch (IOException e) {
            player.printError("Schematic could not read or it does not exist: " + e.getMessage());
            log.warn("Failed to load a saved clipboard", e);
        }
    }

    @Command(
            aliases = { "save" },
            flags = "f",
            usage = "[<format>] <filename>",
            desc = "Save a schematic into your clipboard",
            help = "-f is required to overwrite an existing file",
            min = 1, max = 2
    )
    @CommandPermissions({ "worldedit.clipboard.save", "worldedit.schematic.save" })
    public void save(Player player, LocalSession session, @Optional("sponge") String formatName,
                     String filename, @Switch('f') boolean allowOverwrite) throws CommandException, WorldEditException {
        LocalConfiguration config = worldEdit.getConfiguration();

        File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);

        ClipboardFormat format = ClipboardFormats.findByAlias(formatName);
        if (format == null) {
            player.printError("Unknown schematic format: " + formatName);
            return;
        }

        File f = worldEdit.getSafeSaveFile(player, dir, filename, format.getPrimaryFileExtension());

        boolean overwrite = f.exists();
        if (overwrite) {
            if (!player.hasPermission("worldedit.schematic.delete")) {
                throw new CommandException("That schematic already exists!");
            }
            if (!allowOverwrite) {
                player.printError("That schematic already exists. Use the -f flag to overwrite it.");
                return;
            }
        }

        ClipboardHolder holder = session.getClipboard();
        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        Clipboard target;

        // If we have a transform, bake it into the copy
        if (!transform.isIdentity()) {
            FlattenedClipboardTransform result = FlattenedClipboardTransform.transform(clipboard, transform);
            target = new BlockArrayClipboard(result.getTransformedRegion());
            target.setOrigin(clipboard.getOrigin());
            Operations.completeLegacy(result.copyTo(target));
        } else {
            target = clipboard;
        }

        // Create parent directories
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new CommandException("Could not create folder for schematics!");
            }
        }

        try (Closer closer = Closer.create()) {
            FileOutputStream fos = closer.register(new FileOutputStream(f));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ClipboardWriter writer = closer.register(format.getWriter(bos));
            writer.write(target);

            log.info(player.getName() + " saved " + f.getCanonicalPath() + (overwrite ? " (overwriting previous file)" : ""));
            player.print(filename + " saved" + (overwrite ? " (overwriting previous file)." : "."));
        } catch (IOException e) {
            player.printError("Schematic could not written: " + e.getMessage());
            log.warn("Failed to write a saved clipboard", e);
        }
    }

    @Command(
            aliases = { "delete", "d" },
            usage = "<filename>",
            desc = "Delete a saved schematic",
            help = "Delete a schematic from the schematic list",
            min = 1,
            max = 1
    )
    @CommandPermissions("worldedit.schematic.delete")
    public void delete(Actor actor, String filename) throws WorldEditException {
        LocalConfiguration config = worldEdit.getConfiguration();
        File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);

        File f = worldEdit.getSafeOpenFile(actor instanceof Player ? ((Player) actor) : null,
                dir, filename, "schematic", ClipboardFormats.getFileExtensionArray());

        if (!f.exists()) {
            actor.printError("Schematic " + filename + " does not exist!");
            return;
        }

        if (!f.delete()) {
            actor.printError("Deletion of " + filename + " failed! Maybe it is read-only.");
            return;
        }

        actor.print(filename + " has been deleted.");
        try {
            log.info(actor.getName() + " deleted " + f.getCanonicalPath());
        } catch (IOException e) {
            log.info(actor.getName() + " deleted " + f.getAbsolutePath());
        }
    }

    @Command(
            aliases = {"formats", "listformats", "f"},
            desc = "List available formats",
            max = 0
    )
    @CommandPermissions("worldedit.schematic.formats")
    public void formats(Actor actor) throws WorldEditException {
        actor.print("Available clipboard formats (Name: Lookup names)");
        StringBuilder builder;
        boolean first = true;
        for (ClipboardFormat format : ClipboardFormats.getAll()) {
            builder = new StringBuilder();
            builder.append(format.getName()).append(": ");
            for (String lookupName : format.getAliases()) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append(lookupName);
                first = false;
            }
            first = true;
            actor.print(builder.toString());
        }
    }

    @Command(
            aliases = {"list", "all", "ls"},
            desc = "List saved schematics",
            max = 1,
            flags = "dnp",
            help = "List all schematics in the schematics directory\n" +
                    " -d sorts by date, oldest first\n" +
                    " -n sorts by date, newest first\n" +
                    " -p <page> prints the requested page\n" +
                    "Note: Format is not thoroughly verified until loading."
    )
    @CommandPermissions("worldedit.schematic.list")
    public void list(Actor actor, CommandContext args, @Switch('p') @Optional("1") int page) throws WorldEditException {
        File dir = worldEdit.getWorkingDirectoryFile(worldEdit.getConfiguration().saveDir);
        List<File> fileList = allFiles(dir);

        if (fileList == null || fileList.isEmpty()) {
            actor.printError("No schematics found.");
            return;
        }

        File[] files = new File[fileList.size()];
        fileList.toArray(files);

        int pageCount = files.length / SCHEMATICS_PER_PAGE + 1;
        if (page < 1) {
            actor.printError("Page must be at least 1");
            return;
        }
        if (page > pageCount) {
            actor.printError("Page must be less than " + (pageCount + 1));
            return;
        }

        final int sortType = args.hasFlag('d') ? -1 : args.hasFlag('n') ? 1 : 0;
        // cleanup file list
        Arrays.sort(files, (f1, f2) -> {
            // http://stackoverflow.com/questions/203030/best-way-to-list-files-in-java-sorted-by-date-modified
            int res;
            if (sortType == 0) { // use name by default
                int p = f1.getParent().compareTo(f2.getParent());
                if (p == 0) { // same parent, compare names
                    res = f1.getName().compareTo(f2.getName());
                } else { // different parent, sort by that
                    res = p;
                }
            } else {
                res = Long.compare(f1.lastModified(), f2.lastModified()); // use date if there is a flag
                if (sortType == 1) res = -res; // flip date for newest first instead of oldest first
            }
            return res;
        });

        List<String> schematics = listFiles(worldEdit.getConfiguration().saveDir, files);
        int offset = (page - 1) * SCHEMATICS_PER_PAGE;

        actor.print("Available schematics (Filename: Format) [" + page + "/" + pageCount + "]:");
        StringBuilder build = new StringBuilder();
        int limit = Math.min(offset + SCHEMATICS_PER_PAGE, schematics.size());
        for (int i = offset; i < limit;) {
            build.append(schematics.get(i));
            if (++i != limit) {
                build.append("\n");
            }
        }

        actor.print(build.toString());
    }

    private List<File> allFiles(File root) {
        File[] files = root.listFiles();
        if (files == null) return null;
        List<File> fileList = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory()) {
                List<File> subFiles = allFiles(f);
                if (subFiles == null) continue; // empty subdir
                fileList.addAll(subFiles);
            } else {
                fileList.add(f);
            }
        }
        return fileList;
    }

    private List<String> listFiles(String prefix, File[] files) {
        if (prefix == null) prefix = "";
        List<String> result = new ArrayList<>();
        for (File file : files) {
            StringBuilder build = new StringBuilder();

            build.append("\u00a72");
            //ClipboardFormat format = ClipboardFormats.findByFile(file);
            Multimap<String, ClipboardFormat> exts = ClipboardFormats.getFileExtensionMap();
            ClipboardFormat format = exts.get(Files.getFileExtension(file.getName()))
                    .stream().findFirst().orElse(null);
            boolean inRoot = file.getParentFile().getName().equals(prefix);
            build.append(inRoot ? file.getName() : file.getPath().split(Pattern.quote(prefix + File.separator))[1])
                    .append(": ").append(format == null ? "Unknown" : format.getName() + "*");
            result.add(build.toString());
        }
        return result;
    }
}
