import { defineStore } from 'pinia';
import dayjs from 'dayjs';

import { getLicenseInfo } from '@/api/modules/setting/authorizedManagement';

import type { LicenseInfo } from '@/models/setting/authorizedManagement';

const useLicenseStore = defineStore('license', {
  persist: true,
  state: (): { licenseInfo: LicenseInfo | null; expiredDuring: boolean; expiredDays: number } => ({
    licenseInfo: null,
    expiredDuring: false,
    expiredDays: 0,
  }),
  getters: {
    isUnlimited(): boolean {
      return import.meta.env.VITE_MS_UNLIMITED === 'true';
    },
  },
  actions: {
    setLicenseInfo(info: LicenseInfo) {
      this.licenseInfo = info;
    },
    removeLicenseStatus() {
      if (this.licenseInfo) {
        this.licenseInfo.status = null;
      }
    },
    hasLicense() {
      if (this.isUnlimited) {
        return true;
      }
      return this.licenseInfo?.status === 'valid';
    },
    getExpirationTime(resTime: string) {
      const today = Date.now();
      const startDate = dayjs(today).format('YYYY-MM-DD');
      const endDate = dayjs(resTime);

      const daysDifference = endDate.diff(startDate, 'day');
      this.expiredDays = daysDifference;
      if (daysDifference <= 30 && daysDifference >= 0) {
        this.expiredDuring = true;
      } else if (daysDifference <= 0 && daysDifference >= -30) {
        this.expiredDuring = true;
      } else {
        this.expiredDuring = false;
      }
    },
    // license校验
    async getValidateLicense() {
      try {
        const result = await getLicenseInfo();
        if (!result?.status) {
          return;
        }
        this.setLicenseInfo(result);
        if (result.license?.expired) {
          this.getExpirationTime(result.license.expired);
        }
      } catch (error) {
        // eslint-disable-next-line no-console
        console.log(error);
      }
    },
  },
});

export default useLicenseStore;
