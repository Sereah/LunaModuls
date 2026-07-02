package com.lunacattus.statemachine;

import android.annotation.SuppressLint;
import android.os.Message;

/**
 * <p>
 * The class for implementing states in a StateMachine
 */
@SuppressLint("AndroidFrameworkRequiresPermission")
public class State implements IState {

    protected State() {
    }

    @Override
    public void enter() {
    }

    @Override
    public void exit() {
    }

    @Override
    public boolean processMessage(Message msg) {
        return false;
    }

    @Override
    public String getName() {
        String name = getClass().getName();
        int lastDollar = name.lastIndexOf('$');
        return name.substring(lastDollar + 1);
    }
}
