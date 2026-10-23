import { defineStore } from 'pinia';

import { OptionItem } from '@/api/modules/message/index';
import { getFeatureCapabilities, getModelConfigNameList } from '@/api/modules/setting/config';

const useAIStore = defineStore('ai', {
  state: (): {
    aiSourceNameList: OptionItem[];
    llmUiEnabled: boolean;
  } => ({
    aiSourceNameList: [],
    llmUiEnabled: false,
  }),
  actions: {
    async getAISourceNameList() {
      try {
        this.llmUiEnabled = (await getFeatureCapabilities()).llmUiEnabled === true;
        this.aiSourceNameList = this.llmUiEnabled ? (await getModelConfigNameList()) || [] : [];
      } catch (error) {
        this.llmUiEnabled = false;
        this.aiSourceNameList = [];
        // eslint-disable-next-line no-console
        console.log(error);
      }
    },
  },
});

export default useAIStore;
