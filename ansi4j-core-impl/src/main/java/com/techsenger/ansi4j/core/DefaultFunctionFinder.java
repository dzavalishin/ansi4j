    /*
 * Copyright 2022-2024 Pavel Castornii.
 *
 * This project is dual-licensed under the GNU AGPL version 3 and a commercial license.
 * See the file LICENSE.md in the root directory of the project for full license information.
 */

package com.techsenger.ansi4j.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.techsenger.ansi4j.core.api.Environment;
import com.techsenger.ansi4j.core.api.FunctionFinder;
import com.techsenger.ansi4j.core.api.function.FunctionType;
import com.techsenger.ansi4j.core.api.iso6429.C0ControlFunction;
import com.techsenger.ansi4j.core.api.iso6429.C1ControlFunction;
import com.techsenger.ansi4j.core.api.iso6429.ControlFunction;
import com.techsenger.ansi4j.core.api.iso6429.ControlFunctionType;
import com.techsenger.ansi4j.core.api.iso6429.IndependentControlFunction;
import com.techsenger.ansi4j.core.api.utils.Characters;
import com.techsenger.ansi4j.core.impl.FunctionFinderResultImpl;
import com.techsenger.ansi4j.core.api.FunctionFinderResult;

/**
 *
 * @author Pavel Castornii
 */
public class DefaultFunctionFinder implements FunctionFinder {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFunctionFinder.class);

    private static class FunctionPair {

        private final FunctionType type;

        private final ControlFunction function;

        FunctionPair(FunctionType type, ControlFunction function) {
            this.type = type;
            this.function = function;
        }
    }

    private Environment environment;

    private final Map<Integer, ControlFunction> c0FunctionsByCode = new HashMap<>();

    private final Map<String, ControlFunction> c1FunctionsByPattern = new HashMap<>();

    private final Map<Integer, ControlFunction> c1FunctionsByCode = new HashMap<>();

    private final Map<String, ControlFunction> independentFunctionsByPattern = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<FunctionFinderResult> find(int startIndex, String text) {
        for (int offset = startIndex; offset < text.length();) {
            final int codePoint = text.codePointAt(offset);
            FunctionPair pair = null;
            if (codePoint == Characters.ESC) {
                //escape is processed separately because it can be of many types
                pair = this.resolveIndependentFunction(text, offset);
                if (pair == null && this.environment == Environment._7_BIT) {
                    pair = this.resolveC1Function(text, offset, codePoint);
                }
                if (pair == null) {
                    pair = this.resolveC0Function(codePoint);
                }
            } else if (codePoint <= 31) {
                pair = this.resolveC0Function(codePoint);
            } else if (codePoint >= 0x80 && codePoint <= 0x9F && this.environment == Environment._8_BIT) {
                pair = this.resolveC1Function(text, offset, codePoint);
            }
            if (pair != null) {
                var result = new FunctionFinderResultImpl(offset, pair.type, pair.function);
                return Optional.of(result);
            }
            offset += Character.charCount(codePoint);
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(Environment environment) {
        this.environment = environment;
        //C0
        //we adding all 34 functions, where 4 functions have code duplicates, so, after we have 32 entries in map.
        Arrays.asList(C0ControlFunction.values()).forEach(f -> {
            c0FunctionsByCode.put((int) f.getPattern().charAt(0), f);
        });
        //now we set correct functions by environment
        if (this.environment == Environment._7_BIT) {
            var f = C0ControlFunction.SO_SHIFT_OUT;
            c0FunctionsByCode.put((int) f.getPattern().charAt(0), f);
            f = C0ControlFunction.SI_SHIFT_IN;
            c0FunctionsByCode.put((int) f.getPattern().charAt(0), f);
        } else if (this.environment == Environment._8_BIT) {
            var f = C0ControlFunction.LS0_LOCKING_SHIFT_ZERO;
            c0FunctionsByCode.put((int) f.getPattern().charAt(0), f);
            f = C0ControlFunction.LS1_LOCKING_SHIFT_ONE;
            c0FunctionsByCode.put((int) f.getPattern().charAt(0), f);
        }
        logger.debug("Added {} C0 functions to index in {}", c0FunctionsByCode.size(), this.environment);
        //C1
        if (this.environment == Environment._7_BIT) {
            Arrays.asList(C1ControlFunction.values()).forEach(f -> {
                c1FunctionsByPattern.put(f.getPattern(), f);
            });
            logger.debug("Added {} C1 functions to index in {}", c1FunctionsByPattern.size(), this.environment);
        } else if (this.environment == Environment._8_BIT) {
            Arrays.asList(C1ControlFunction.values()).forEach(f -> {
                c1FunctionsByCode.put((int) ((C1ControlFunction) f).get8BitPattern().charAt(0), f);
            });
            logger.debug("Added {} C1 functions to index in {}", c1FunctionsByCode.size(), this.environment);
        } else {
            throw new IllegalStateException("Unknown environment");
        }
        //independent
        Arrays.asList(IndependentControlFunction.values()).forEach(f -> {
            independentFunctionsByPattern.put(f.getPattern(), f);
        });
        logger.debug("Added {} independent functions to index in {}", independentFunctionsByPattern.size(),
                this.environment);
    }

    private FunctionPair resolveIndependentFunction(String functionText, int offset) {
        ControlFunction function = null;
        if (offset + 1 >= functionText.length()) {
                return null;
        }
        var codePoint = functionText.codePointAt(offset + 1);
        //Fs is represented by a bit combination from 06/00 to 07/14.
        if (codePoint >= 0x60 && codePoint <= 0x7E) {
            var identifier = "" + Characters.ESC + new String(new int[] {codePoint}, 0, 1);
            function = this.independentFunctionsByPattern.get(identifier);
            if (function != null) {
                return new FunctionPair(ControlFunctionType.INDEPENDENT_FUNCTION, function);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private FunctionPair resolveC1Function(String functionText, int offset, int codePoint) {
        ControlFunction function = null;
        if (this.environment == Environment._7_BIT) {
            if (offset + 1 >= functionText.length()) {
                return null;
            }
            var identifier = "" + Characters.ESC + new String(new int[] {functionText.codePointAt(offset + 1)}, 0, 1);
            function = this.c1FunctionsByPattern.get(identifier);
        } else if (this.environment == Environment._8_BIT) {
            function = this.c1FunctionsByCode.get(codePoint);
        }
        if (function == null) {
            return null;
        }
        var openingDelimiters = C1ControlFunction.getControlStringOpeningDelimiters();
        if (function == C1ControlFunction.CSI_CONTROL_SEQUENCE_INTRODUCER) {
            return new FunctionPair(ControlFunctionType.CONTROL_SEQUENCE, function);
        } else if (openingDelimiters.contains(function)) {
            return new FunctionPair(ControlFunctionType.CONTROL_STRING, function);
        } else {
            return new FunctionPair(ControlFunctionType.C1_SET, function);
        }
    }

    private FunctionPair resolveC0Function(int codePoint) {
        ControlFunction function = this.c0FunctionsByCode.get(codePoint);
        if (function != null) {
            return new FunctionPair(ControlFunctionType.C0_SET, function);
        } else {
            return null;
        }
    }

}
