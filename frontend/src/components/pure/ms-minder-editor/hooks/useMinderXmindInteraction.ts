/**
 * Xmind 预览画布交互增强：
 * - 右键拖拽平移
 * - 滚轮平移（沿用 kityminder 默认）
 * - Ctrl/⌘ + 滚轮缩放（沿用 kityminder 默认，并细化缩放档位）
 */

const WHEEL_ZOOM_LEVELS = [50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200];

/**
 * 绑定预览画布交互，返回解绑函数
 */
export default function bindMinderXmindInteraction(minder: any) {
  if (!minder?.on) {
    return () => undefined;
  }

  // 细化 Ctrl+滚轮缩放档位；普通滚轮平移由 kityminder View 模块处理
  minder.setDefaultOptions?.({
    zoom: WHEEL_ZOOM_LEVELS,
    zoomAnimationDuration: 0,
  });

  let rightPanLastPos: { x: number; y: number } | null = null;
  let rightPanning = false;

  const paperContainer = minder.getPaper?.()?.container || minder.getRenderTarget?.() || null;

  const handleBeforeMouseDown = (e: any) => {
    // 右键交给自定义平移，避免与内置 hand 临时拖拽叠加
    if (e.originEvent?.button === 2) {
      e.stopPropagation();
      e.preventDefault?.();
      const pos = e.getPosition?.('view');
      rightPanLastPos = pos ? { x: pos.x, y: pos.y } : null;
      rightPanning = false;
    }
  };

  const handleMouseMove = (e: any) => {
    const originEvent = e.originEvent as MouseEvent | undefined;
    if (!originEvent || originEvent.buttons !== 2 || !rightPanLastPos) {
      return;
    }
    const pos = e.getPosition?.('view');
    if (!pos) {
      return;
    }
    const dx = pos.x - rightPanLastPos.x;
    const dy = pos.y - rightPanLastPos.y;
    rightPanLastPos = { x: pos.x, y: pos.y };
    if (!dx && !dy) {
      return;
    }
    rightPanning = true;
    minder.getPaper?.()?.setStyle?.('cursor', '-webkit-grabbing');
    minder.getViewDragger?.()?.move({ x: dx, y: dy });
    e.preventDefault?.();
    originEvent.preventDefault?.();
  };

  const handleMouseUp = () => {
    rightPanLastPos = null;
    if (rightPanning) {
      minder.getPaper?.()?.setStyle?.('cursor', 'default');
    }
    rightPanning = false;
  };

  const handleContextMenu = (event: Event) => {
    event.preventDefault();
  };

  minder.on('beforemousedown', handleBeforeMouseDown);
  minder.on('mousemove', handleMouseMove);
  minder.on('mouseup', handleMouseUp);
  paperContainer?.addEventListener('contextmenu', handleContextMenu);
  window.addEventListener('mouseup', handleMouseUp);

  return () => {
    minder.off('beforemousedown', handleBeforeMouseDown);
    minder.off('mousemove', handleMouseMove);
    minder.off('mouseup', handleMouseUp);
    paperContainer?.removeEventListener('contextmenu', handleContextMenu);
    window.removeEventListener('mouseup', handleMouseUp);
  };
}
