/*
 * Copyright 2020 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.NativeColorId;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;

/** A factory and cache for {@link ColorGraphNode} instances. */
final class ColorGraphNodeFactory {

  private final LinkedHashMap<Color, ColorGraphNode> typeIndex;
  private final ColorRegistry registry;

  private ColorGraphNodeFactory(
      LinkedHashMap<Color, ColorGraphNode> initialTypeIndex, ColorRegistry registry) {
    this.typeIndex = initialTypeIndex;
    this.registry = registry;
  }

  static ColorGraphNodeFactory createFactory(ColorRegistry colorRegistry) {
    LinkedHashMap<Color, ColorGraphNode> typeIndex = new LinkedHashMap<>();
    Color unknownColor = colorRegistry.get(NativeColorId.UNKNOWN);
    ColorGraphNode unknownColorNode = ColorGraphNode.create(unknownColor, 0);
    typeIndex.put(unknownColor, unknownColorNode);
    return new ColorGraphNodeFactory(typeIndex, colorRegistry);
  }

  /**
   * Returns the {@link ColorGraphNode} known by this factory for {@code type}.
   *
   * <p>For a given {@code type} and factory, this method will always return the same result. The
   * results are cached.
   */
  public ColorGraphNode createNode(@Nullable Color color) {
    Color key = this.simplifyColor(color);
    return this.typeIndex.computeIfAbsent(key, this::newColorGraphNode);
  }

  public ImmutableSet<ColorGraphNode> getAllKnownTypes() {
    return ImmutableSet.copyOf(this.typeIndex.values());
  }

  private ColorGraphNode newColorGraphNode(Color key) {
    int id = this.typeIndex.size();
    return ColorGraphNode.create(key, id);
  }

  // Merges different colors with the same ambiguation-behavior into one
  private Color simplifyColor(@Nullable Color type) {
    if (type == null) {
      return this.registry.get(NativeColorId.UNKNOWN);
    }

    if (type.isUnion()) {
      // First remove null/void, then recursively simplify any primitive components
      type = type.subtractNullOrVoid();
      return type.isUnion()
          ? Color.createUnion(
              type.union().stream().map(this::simplifyColor).collect(toImmutableSet()))
          : simplifyColor(type);
    } else if (type.isPrimitive()) {
      return flattenSingletonPrimitive(type);
    } else {
      return type;
    }
  }

  private Color flattenSingletonPrimitive(Color type) {
    ImmutableSet<NativeColorId> natives = type.getNativeColorIds();
    checkState(
        natives.size() == 1,
        "Expected primitive %s to correspond to a single native color, found %s",
        type,
        natives);
    NativeColorId nativeColorId = Iterables.getOnlyElement(natives);
    if (NativeColorId.NULL_OR_VOID.equals(nativeColorId)) {
      return this.registry.get(NativeColorId.UNKNOWN);
    }
    return this.registry.get(nativeColorId.box());
  }
}
