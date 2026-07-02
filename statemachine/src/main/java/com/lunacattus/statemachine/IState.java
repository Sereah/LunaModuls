package com.lunacattus.statemachine;

import android.os.Message;

/**
 * <p>
 * The interface for implementing states in a {@link StateMachine}
 */
public interface IState {

    static final boolean HANDLED = true;

    static final boolean NOT_HANDLED = false;

    void enter();

    void exit();

    boolean processMessage(Message msg);

    String getName();
}
