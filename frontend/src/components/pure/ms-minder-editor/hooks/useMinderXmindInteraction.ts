/**
 * Xmind 预览画布交互：
 * - 工具模式：选择 / 拖拽(左键平移) / 放大 / 缩小
 * - 预览期间 window/document 捕获阶段拦截右键（尽量抑制浏览器/扩展手势）
 * - 禁用内置 Alt/根节点临时抓手等快捷手势
 * - 滚轮平移、Ctrl/⌘ + 滚轮缩放
 */

export type XmindToolMode = 'select' | 'pan' | 'zoomIn' | 'zoomOut';

export interface XmindInteractionController {
  setMode: (mode: XmindToolMode) => void;
  getMode: () => XmindToolMode;
  destroy: () => void;
}

const WHEEL_ZOOM_LEVELS = [50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200];

const GESTURE_EVENT_TYPES = [
  'mousedown',
  'mouseup',
  'mousemove',
  'pointerdown',
  'pointerup',
  'pointermove',
  'contextmenu',
  'auxclick',
] as const;

function applyCanvasAntiGestureStyle(el: HTMLElement) {
  el.style.touchAction = 'none';
  el.style.overscrollBehavior = 'none';
  // @ts-expect-error vendor prefix
  el.style.msTouchAction = 'none';
}

function isRightMouseEvent(e: MouseEvent) {
  // buttons 中 bit1=右键；用 %4 避免 eslint no-bitwise
  const rightHeld = typeof e.buttons === 'number' && e.buttons % 4 >= 2;
  return e.button === 2 || e.type === 'contextmenu' || e.type === 'auxclick' || rightHeld;
}

/**
 * 预览页级右键手势拦截（不依赖 minder 画布是否已就绪）
 */
export function bindPreviewGestureGuard(): () => void {
  const block = (event: Event) => {
    const e = event as MouseEvent;
    if (!isRightMouseEvent(e)) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    (e as any).stopImmediatePropagation?.();
  };
  GESTURE_EVENT_TYPES.forEach((name) => {
    window.addEventListener(name, block, true);
    document.addEventListener(name, block, true);
  });
  return () => {
    GESTURE_EVENT_TYPES.forEach((name) => {
      window.removeEventListener(name, block, true);
      document.removeEventListener(name, block, true);
    });
  };
}

/**
 * 绑定预览画布交互，返回控制器（含 setMode / destroy）
 */
export default function bindMinderXmindInteraction(minder: any): XmindInteractionController {
  const noopController: XmindInteractionController = {
    setMode: () => undefined,
    getMode: () => 'pan',
    destroy: () => undefined,
  };
  if (!minder?.on) {
    return noopController;
  }

  let mode: XmindToolMode = 'pan';
  let leftPanLastPos: { x: number; y: number } | null = null;
  let leftPanning = false;

  minder.setDefaultOptions?.({
    zoom: WHEEL_ZOOM_LEVELS,
    zoomAnimationDuration: 0,
  });

  const paperContainer = (minder.getPaper?.()?.container || minder.getRenderTarget?.() || null) as HTMLElement | null;
  const renderTarget = (minder.getRenderTarget?.() || null) as HTMLElement | null;
  const gestureRoots = Array.from(new Set([paperContainer, renderTarget].filter(Boolean))) as HTMLElement[];
  gestureRoots.forEach(applyCanvasAntiGestureStyle);

  const originalSetStatus = typeof minder.setStatus === 'function' ? minder.setStatus.bind(minder) : null;
  if (originalSetStatus) {
    // 禁用内置 hand；拖拽由工具栏左键平移接管
    minder.setStatus = function patchedSetStatus(status: string, ...args: any[]) {
      if (status === 'hand') {
        return this;
      }
      return originalSetStatus(status, ...args);
    };
  }

  function applyMode(next: XmindToolMode) {
    mode = next;
    leftPanLastPos = null;
    leftPanning = false;
    const cursorMap: Record<XmindToolMode, string> = {
      select: 'default',
      pan: '-webkit-grab',
      zoomIn: 'crosshair',
      zoomOut: 'crosshair',
    };
    minder.getPaper?.()?.setStyle?.('cursor', cursorMap[next]);
  }

  /** 画布内再拦一层右键（window 级 guard 已覆盖，此处兜底） */
  const blockBrowserGesture = (event: Event) => {
    const e = event as MouseEvent;
    if (!isRightMouseEvent(e)) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    (e as any).stopImmediatePropagation?.();
  };

  gestureRoots.forEach((el) => {
    GESTURE_EVENT_TYPES.forEach((name) => {
      el.addEventListener(name, blockBrowserGesture, true);
    });
  });

  const handleBeforeMouseDown = (e: any) => {
    const originEvent = e.originEvent as MouseEvent | undefined;
    if (!originEvent) {
      return;
    }

    if (originEvent.button === 2 || originEvent.button === 1 || originEvent.altKey) {
      e.stopPropagation();
      e.preventDefault?.();
      originEvent.preventDefault?.();
      return;
    }

    if (originEvent.button !== 0) {
      return;
    }

    if (mode === 'pan') {
      e.stopPropagation();
      e.preventDefault?.();
      originEvent.preventDefault?.();
      const pos = e.getPosition?.('view');
      leftPanLastPos = pos ? { x: pos.x, y: pos.y } : null;
      leftPanning = false;
      return;
    }

    if (mode === 'zoomIn' || mode === 'zoomOut') {
      e.stopPropagation();
      e.preventDefault?.();
      originEvent.preventDefault?.();
      minder.execCommand?.(mode === 'zoomIn' ? 'zoomin' : 'zoomout');
    }
  };

  const handleMouseMove = (e: any) => {
    if (mode !== 'pan') {
      return;
    }
    const originEvent = e.originEvent as MouseEvent | undefined;
    if (!originEvent || originEvent.buttons !== 1 || !leftPanLastPos) {
      return;
    }
    const pos = e.getPosition?.('view');
    if (!pos) {
      return;
    }
    const dx = pos.x - leftPanLastPos.x;
    const dy = pos.y - leftPanLastPos.y;
    leftPanLastPos = { x: pos.x, y: pos.y };
    if (!dx && !dy) {
      return;
    }
    leftPanning = true;
    minder.getPaper?.()?.setStyle?.('cursor', '-webkit-grabbing');
    minder.getViewDragger?.()?.move({ x: dx, y: dy });
    e.preventDefault?.();
    originEvent.preventDefault?.();
  };

  const handleMouseUp = () => {
    leftPanLastPos = null;
    if (leftPanning && mode === 'pan') {
      minder.getPaper?.()?.setStyle?.('cursor', '-webkit-grab');
    }
    leftPanning = false;
  };

  minder.on('beforemousedown', handleBeforeMouseDown);
  minder.on('mousemove', handleMouseMove);
  minder.on('mouseup', handleMouseUp);
  window.addEventListener('mouseup', handleMouseUp);

  const controller: XmindInteractionController = {
    setMode: (next) => applyMode(next),
    getMode: () => mode,
    destroy: () => {
      if (originalSetStatus) {
        minder.setStatus = originalSetStatus;
      }
      minder.off('beforemousedown', handleBeforeMouseDown);
      minder.off('mousemove', handleMouseMove);
      minder.off('mouseup', handleMouseUp);
      window.removeEventListener('mouseup', handleMouseUp);
      gestureRoots.forEach((el) => {
        GESTURE_EVENT_TYPES.forEach((name) => {
          el.removeEventListener(name, blockBrowserGesture, true);
        });
      });
      if (minder.__xmindTool === controller) {
        delete minder.__xmindTool;
      }
    },
  };

  minder.__xmindTool = controller;
  applyMode('pan');
  return controller;
}
