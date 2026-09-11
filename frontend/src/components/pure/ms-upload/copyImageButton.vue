<template>
  <a-button type="text" size="mini" :loading="copying" :disabled="copying" @click.stop="handleCopy">
    {{ t('ms.upload.copyImage') }}
  </a-button>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import { Message } from '@arco-design/web-vue';

  import { useI18n } from '@/hooks/useI18n';
  import { copyImage } from '@/utils/imageClipboard';

  const props = defineProps<{ source: () => Promise<string> | string }>();
  const { t } = useI18n();
  const copying = ref(false);

  async function handleCopy() {
    if (copying.value) return;
    copying.value = true;
    try {
      await copyImage(props.source);
      Message.success(t('ms.upload.copyImageSuccess'));
    } catch (error) {
      if (error instanceof Error && error.message === 'IMAGE_COPY_UNSUPPORTED') {
        Message.warning(t('ms.upload.copyImageUnsupported'));
      } else if (error instanceof Error && error.name === 'NotAllowedError') {
        Message.warning(t('ms.upload.copyImageDenied'));
      } else {
        Message.error(t('ms.upload.copyImageFailed'));
      }
    } finally {
      copying.value = false;
    }
  }
</script>
