package io.ecosync.restcontract;

/** Public codes only; never carry source bodies, URLs, arguments or credentials. */
public final class RestFailure extends RuntimeException {
 public final String code;
 public RestFailure(String code){super(code);this.code=code.matches("REST_[A-Z_]{1,48}")?code:"REST_UNAVAILABLE";}
}
