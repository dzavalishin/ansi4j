/*
 * Copyright 2022-2024 Pavel Castornii.
 *
 * This project is dual-licensed under the GNU AGPL version 3 and a commercial license.
 * See the file LICENSE.md in the root directory of the project for full license information.
 */

package com.techsenger.ansi4j.core;

import java.util.Optional;
import com.techsenger.ansi4j.core.api.Environment;
import com.techsenger.ansi4j.core.impl.TextFragmentImpl;
import com.techsenger.ansi4j.core.impl.TextHandlerResultImpl;
import com.techsenger.ansi4j.core.api.TextHandler;
import com.techsenger.ansi4j.core.api.TextHandlerResult;

/**
 * Default implementation doesn't do any modifications with text and simply returns fragment with input text.
 *
 * @author Pavel Castornii
 */
public class DefaultTextHandler implements TextHandler {

    private Environment environment;

    /**
     * {@inheritDoc}
     */
    @Override
    public TextHandlerResult handle(String text, int currentIndex) {
        return new TextHandlerResultImpl(Optional.of(new TextFragmentImpl(text, currentIndex)), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(Environment environment) {
        this.environment = environment;
    }

}
