package com.flomaestro.engine.license;

public class LicenseException extends RuntimeException {

  public LicenseException(String licenseFileNotFound) {
    super(licenseFileNotFound);
  }
}
