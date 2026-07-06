package io.metersphere.system.service.impl;

import io.metersphere.system.dto.sdk.LicenseDTO;
import io.metersphere.system.dto.sdk.LicenseInfoDTO;
import io.metersphere.system.service.LicenseService;

/**
 * Community edition LicenseService: always reports a valid license.
 * Registered via {@link io.metersphere.system.config.CommunityXpackConfiguration}.
 */
public class CommunityLicenseServiceImpl implements LicenseService {

    private static LicenseDTO validLicense() {
        LicenseDTO licenseDTO = new LicenseDTO();
        licenseDTO.setStatus("valid");
        LicenseInfoDTO licenseInfo = new LicenseInfoDTO();
        licenseInfo.setCorporation("Community");
        licenseInfo.setProduct("MeterSphere");
        licenseInfo.setEdition("Community");
        licenseInfo.setLicenseVersion("3.x");
        licenseInfo.setExpired("2099-12-31");
        licenseInfo.setCount(Integer.MAX_VALUE);
        licenseDTO.setLicense(licenseInfo);
        return licenseDTO;
    }

    @Override
    public LicenseDTO refreshLicense() {
        return validLicense();
    }

    @Override
    public LicenseDTO validate() {
        return validLicense();
    }

    @Override
    public LicenseDTO addLicense(String licenseCode, String userId) {
        return validLicense();
    }

    @Override
    public String getCode(String encrypt) {
        return encrypt;
    }
}
