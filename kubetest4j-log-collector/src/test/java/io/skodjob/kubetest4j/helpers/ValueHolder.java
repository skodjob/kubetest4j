/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.skodjob.kubetest4j.helpers;

import java.util.concurrent.atomic.AtomicBoolean;

public class ValueHolder {

    static ValueHolder instance;
    public AtomicBoolean callbackCalled;

    private ValueHolder() {
        callbackCalled = new AtomicBoolean(false);
    }

    public static ValueHolder get() {
        if (instance == null) {
            instance = new ValueHolder();
        }
        return instance;
    }
}
