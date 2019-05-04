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

package com.sk89q.worldedit.command.argument;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sk89q.worldedit.UnknownDirectionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.internal.annotation.Direction;
import com.sk89q.worldedit.internal.annotation.MultiDirection;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ConversionResult;
import org.enginehub.piston.converter.FailedConversion;
import org.enginehub.piston.converter.SuccessfulConversion;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.Key;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.enginehub.piston.converter.SuggestionHelper.limitByPrefix;

public class DirectionConverter implements ArgumentConverter<BlockVector3> {

    @AutoAnnotation
    private static Direction direction(boolean includeDiagonals) {
        return new AutoAnnotation_DirectionConverter_direction(includeDiagonals);
    }

    @AutoAnnotation
    private static MultiDirection multiDirection(boolean includeDiagonals) {
        return new AutoAnnotation_DirectionConverter_multiDirection(includeDiagonals);
    }

    public static void register(WorldEdit worldEdit, CommandManager commandManager) {
        for (boolean includeDiagonals : new boolean[] { false, true }) {
            DirectionConverter directionConverter = new DirectionConverter(worldEdit, includeDiagonals);
            commandManager.registerConverter(
                Key.of(BlockVector3.class, direction(includeDiagonals)),
                directionConverter
            );
            commandManager.registerConverter(
                Key.of(BlockVector3.class, multiDirection(includeDiagonals)),
                CommaSeparatedValuesConverter.wrap(directionConverter)
            );
        }
    }

    private static final ImmutableSet<String> NON_DIAGONALS = ImmutableSet.of(
        "north", "south", "east", "west", "up", "down"
    );
    private static final ImmutableSet<String> RELATIVE = ImmutableSet.of(
        "me", "forward", "back", "left", "right"
    );
    private static final ImmutableSet<String> DIAGONALS = ImmutableSet.of(
        "northeast", "northwest", "southeast", "southwest"
    );

    private final WorldEdit worldEdit;
    private final boolean includeDiagonals;
    private final ImmutableList<String> suggestions;

    private DirectionConverter(WorldEdit worldEdit, boolean includeDiagonals) {
        this.worldEdit = worldEdit;
        this.includeDiagonals = includeDiagonals;
        suggestions = ImmutableList.<String>builder()
            .addAll(NON_DIAGONALS)
            .addAll(RELATIVE)
            .addAll(includeDiagonals ? DIAGONALS : ImmutableList.of())
            .build();
    }

    @Override
    public ConversionResult<BlockVector3> convert(String argument, InjectedValueAccess context) {
        Player player = context.injectedValue(Key.of(Player.class))
            .orElseThrow(() -> new IllegalStateException("No player available"));
        try {
            return SuccessfulConversion.fromSingle(includeDiagonals
                ? worldEdit.getDiagonalDirection(player, argument)
                : worldEdit.getDirection(player, argument));
        } catch (UnknownDirectionException e) {
            return FailedConversion.from(e);
        }
    }

    @Override
    public Component describeAcceptableArguments() {
        return TextComponent.of("`me` to use facing direction, or any "
            + (includeDiagonals ? "direction" : "non-diagonal direction"));
    }

    @Override
    public List<String> getSuggestions(String input) {
        return limitByPrefix(suggestions.stream(), input);
    }
}
