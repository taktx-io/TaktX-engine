package io.taktx.engine.license;

public class LicenseException extends RuntimeException {

  public LicenseException(String licenseFileNotFound) {
    super(licenseFileNotFound);
  }
}
