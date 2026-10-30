package io.ecosync.web;
public record ApiError(int status, String code, String message, String requestId) { }
