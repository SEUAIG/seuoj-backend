package com.seuoj.seuojbackend.exception;

public class JudgeRemoteException extends RuntimeException {
    public JudgeRemoteException(String message) {
        super(message);
    }

    public JudgeRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
