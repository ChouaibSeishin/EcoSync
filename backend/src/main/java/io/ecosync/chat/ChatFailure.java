package io.ecosync.chat;
public final class ChatFailure extends RuntimeException {
 public final String code;
 public ChatFailure(String code){super(code);this.code=code;}
}
