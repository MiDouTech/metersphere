/** Rasterize at the original resolution; the clipboard's portable image format is PNG. */
export function imageToPng(source: string): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const timer = window.setTimeout(() => reject(new Error('IMAGE_LOAD_FAILED')), 15000);
    const fail = () => {
      window.clearTimeout(timer);
      reject(new Error('IMAGE_LOAD_FAILED'));
    };
    image.crossOrigin = 'anonymous';
    image.onerror = fail;
    image.onload = () => {
      window.clearTimeout(timer);
      const { naturalWidth: width, naturalHeight: height } = image;
      if (!width || !height || width * height > 40000000) {
        fail();
        return;
      }
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      try {
        const context = canvas.getContext('2d');
        if (!context) {
          fail();
          return;
        }
        context.drawImage(image, 0, 0);
        canvas.toBlob((blob) => {
          canvas.width = 0;
          canvas.height = 0;
          if (blob) resolve(blob);
          else fail();
        }, 'image/png');
      } catch {
        fail();
      }
    };
    if (!source) fail();
    else image.src = source;
  });
}

export async function copyImage(source: () => Promise<string> | string): Promise<void> {
  if (!window.isSecureContext || !navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    throw new Error('IMAGE_COPY_UNSUPPORTED');
  }
  // Start the clipboard write during the click gesture, before asynchronous image loading.
  const png = Promise.resolve().then(source).then(imageToPng);
  // A denied write may reject before the browser consumes the image promise.
  png.catch(() => undefined);
  await navigator.clipboard.write([new ClipboardItem({ 'image/png': png })]);
}
