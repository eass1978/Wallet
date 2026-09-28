package com.wallet.exception;

public class DuplicateWalletException extends RuntimeException {
    public DuplicateWalletException(String message) {
        super(message);
    }
}
