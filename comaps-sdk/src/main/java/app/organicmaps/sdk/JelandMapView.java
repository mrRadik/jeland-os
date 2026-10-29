package app.organicmaps.sdk;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.display.DisplayType;
import app.organicmaps.sdk.location.LocationHelper;

/**
 * Texture host for embedding the upstream renderer in Compose. SurfaceView's
 * separate window layer does not follow clipped/animated dashboard cards.
 * Touch forwarding follows CoMaps MapView (Apache-2.0; upstream NOTICE retained).
 */
public final class JelandMapView extends TextureView implements TextureView.SurfaceTextureListener {
  private final Map map = new Map(DisplayType.Device);
  private Surface surface;
  private Rect viewport;
  private Rect appliedViewport;
  private int followOffset;
  private int appliedOffset = -1;
  public JelandMapView(Context context, LocationHelper location, MapRenderingListener listener) {
    super(context);
    map.setLocationHelper(location);
    map.setMapRenderingListener(listener);
    map.onCreate(false);
    setSurfaceTextureListener(this);
    setOpaque(true);
  }
  public Map map() { return map; }
  public void setViewport(int top, int right, int bottom, int offset) {
    viewport = new Rect(0, top, right, bottom);
    followOffset = offset;
    applyViewport();
  }
  private void applyViewport() {
    if (surface == null || viewport == null || !Map.isEngineCreated()) return;
    if (viewport.equals(appliedViewport) && followOffset == appliedOffset) return;
    Framework.nativeSetVisibleRect(viewport.left, viewport.top, viewport.right, viewport.bottom);
    map.updateMyPositionRoutingOffset(followOffset);
    map.updateBottomWidgetsOffset(getContext(), 0, Math.max(0, getHeight() - viewport.bottom));
    appliedViewport = new Rect(viewport);
    appliedOffset = followOffset;
  }
  @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
    surface = new Surface(texture);
    appliedViewport = null;
    Rect frame = new Rect(0, 0, width, height);
    map.onSurfaceCreated(getContext(), surface, frame, getResources().getDisplayMetrics().densityDpi);
    // TextureView does not emit an initial size-changed callback. CoMaps needs
    // this second step when reattaching its existing engine to a new viewport.
    map.onSurfaceChanged(getContext(), surface, frame, true);
    applyViewport();
  }
  @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
    map.onSurfaceChanged(getContext(), surface, new Rect(0, 0, width, height), false);
    appliedViewport = null;
    applyViewport();
  }
  @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
    map.onSurfaceDestroyed(false);
    if (surface != null) { surface.release(); surface = null; }
    return true;
  }
  @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}
  @Override public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    int pointer = event.getActionIndex();
    switch (action) {
      case MotionEvent.ACTION_UP: action = Map.NATIVE_ACTION_UP; pointer = 0; break;
      case MotionEvent.ACTION_POINTER_UP: action = Map.NATIVE_ACTION_UP; break;
      case MotionEvent.ACTION_DOWN: action = Map.NATIVE_ACTION_DOWN; pointer = 0; break;
      case MotionEvent.ACTION_POINTER_DOWN: action = Map.NATIVE_ACTION_DOWN; break;
      case MotionEvent.ACTION_MOVE: action = Map.NATIVE_ACTION_MOVE; pointer = Map.INVALID_POINTER_MASK; break;
      case MotionEvent.ACTION_CANCEL: action = Map.NATIVE_ACTION_CANCEL; break;
      default: return true;
    }
    Map.onTouch(action, event, pointer);
    return true;
  }
}
