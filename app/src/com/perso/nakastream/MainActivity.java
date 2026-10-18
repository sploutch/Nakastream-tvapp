package com.perso.nakastream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://naka.cx";
    private static final long CURSOR_HIDE_DELAY_MS = 4000;

    private static final String ERROR_PAGE =
            "<html><body style='background:#111;color:#eee;font-family:sans-serif;"
            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>"
            + "<div style='text-align:center'><h2>Impossible de joindre Nakastream</h2>"
            + "<p>Vérifie la connexion internet de la TV.</p>"
            + "<button style='font-size:1.4em;padding:.5em 1.5em' onclick=\"location.href='" + HOME_URL + "'\">"
            + "Réessayer</button></div></body></html>";

    // Fait défiler le conteneur sous le curseur ; à défaut celui au centre de l'écran,
    // puis la page, puis le plus grand conteneur défilable visible.
    private static final String SCROLL_JS =
            "(function(x,y,dx,dy){var d=document,se=d.scrollingElement||d.documentElement,"
            + "re=/(auto|scroll|overlay)/;"
            + "function ok(el){if(el===se||el===d.body||el===d.documentElement)return true;"
            + "var s=getComputedStyle(el);"
            + "return (dy&&re.test(s.overflowY)&&el.scrollHeight>el.clientHeight+1)"
            + "||(dx&&re.test(s.overflowX)&&el.scrollWidth>el.clientWidth+1);}"
            + "function go(el){var t=el.scrollTop,l=el.scrollLeft;el.scrollBy(dx,dy);"
            + "return el.scrollTop!==t||el.scrollLeft!==l;}"
            + "function chain(el){for(;el;el=el.parentElement)if(ok(el)&&go(el))return true;return false;}"
            + "if(chain(d.elementFromPoint(x,y)))return;"
            + "if(chain(d.elementFromPoint(innerWidth/2,innerHeight/2)))return;"
            + "var c=[].filter.call(d.querySelectorAll('*'),ok).map(function(el){"
            + "var r=el.getBoundingClientRect();"
            + "return [el,Math.max(0,Math.min(r.right,innerWidth)-Math.max(r.left,0))"
            + "*Math.max(0,Math.min(r.bottom,innerHeight)-Math.max(r.top,0))];})"
            + ".filter(function(p){return p[1]>0;}).sort(function(a,b){return b[1]-a[1];});"
            + "for(var i=0;i<c.length;i++)if(go(c[i][0]))return;"
            + "})(%f,%f,%f,%f)";

    // Contrôle la plus grande vidéo de la page ; renvoie false si aucune vidéo accessible.
    private static final String MEDIA_JS =
            "(function(a){var vs=[].slice.call(document.querySelectorAll('video'));"
            + "if(!vs.length)return false;"
            + "vs.sort(function(p,q){return q.clientWidth*q.clientHeight-p.clientWidth*p.clientHeight;});"
            + "var v=vs[0];"
            + "if(a==='toggle'){if(v.paused)v.play();else v.pause();}"
            + "else if(a==='play')v.play();else if(a==='pause')v.pause();"
            + "else if(a==='ff')v.currentTime+=10;else if(a==='rw')v.currentTime-=10;"
            + "return true;})('%s')";

    private FrameLayout root;
    private WebView webView;
    private CursorView cursor;
    private final ArrayList<WebView> popups = new ArrayList<>();
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long touchDownTime = -1;
    private long lastBackPress;
    private float density;

    private final Runnable hideCursor = new Runnable() {
        @Override
        public void run() {
            cursor.setShown(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        density = getResources().getDisplayMetrics().density;

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = createWebView();
        root.addView(webView, matchParent());
        cursor = new CursorView(this);
        root.addView(cursor, matchParent());
        setContentView(root);

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            // Une adresse passée au lancement (adb shell am start -d ...) sert aux tests.
            String start = getIntent().getDataString();
            webView.loadUrl(start != null ? start : HOME_URL);
        }
        webView.requestFocus();
        hideSystemUi();
        scheduleCursorHide();
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        // Les nouveaux onglets (target=_blank, window.open) s'ouvrent dans onCreateWindow.
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Se présenter comme Chrome plutôt que comme une WebView.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace(" Version/4.0", ""));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setBackgroundColor(Color.BLACK);
        view.setWebViewClient(webViewClient);
        view.setWebChromeClient(chromeClient);
        return view;
    }

    private final WebViewClient webViewClient = new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Reste dans l'appli pour le web ; ignore intent://, market://, etc.
            return !(url.startsWith("http://") || url.startsWith("https://"));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return shouldOverrideUrlLoading(view, request.getUrl().toString());
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                view.loadDataWithBaseURL(null, ERROR_PAGE, "text/html", "utf-8", null);
            }
        }
    };

    private final WebChromeClient chromeClient = new WebChromeClient() {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            root.addView(view, root.indexOfChild(cursor), matchParent());
            currentWebView().setVisibility(View.GONE);
            hideSystemUi();
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            // Nouvel onglet : une WebView par-dessus, la page d'origine reste chargée
            // dessous (masquée, comme un onglet en arrière-plan).
            WebView popup = createWebView();
            WebView below = currentWebView();
            below.setVisibility(View.INVISIBLE);
            below.onPause(); // la page passe en "hidden" (visibilitychange)
            popups.add(popup);
            root.addView(popup, root.indexOfChild(cursor), matchParent());
            popup.requestFocus();
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            Toast.makeText(MainActivity.this, "Nouvel onglet – Retour pour le fermer",
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            closePopup(window);
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            // Évite l'icône grise par défaut avant le chargement des vidéos.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    };

    private void closePopup(WebView popup) {
        if (!popups.remove(popup)) return;
        root.removeView(popup);
        popup.destroy();
        WebView top = currentWebView();
        top.setVisibility(View.VISIBLE);
        top.onResume();
        top.requestFocus();
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        currentWebView().setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        currentWebView().requestFocus();
    }

    /** La WebView affichée : le dernier onglet ouvert, sinon la page principale. */
    private WebView currentWebView() {
        return popups.isEmpty() ? webView : popups.get(popups.size() - 1);
    }

    private View touchTarget() {
        return customView != null ? customView : currentWebView();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) moveCursor(e.getKeyCode(), e.getRepeatCount());
                return true;

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Un vrai clavier garde son comportement normal (valider un champ texte).
                InputDevice dev = e.getDevice();
                if (dev != null && dev.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) break;
                // fall through
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (e.getRepeatCount() == 0) {
                    showCursor();
                    sendTouch(down ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP);
                }
                return true;

            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (down) scrollByScreen(-1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (down) scrollByScreen(1);
                return true;

            case KeyEvent.KEYCODE_BACK:
                if (down && e.getRepeatCount() == 0) handleBack();
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return media(e, "toggle");
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return media(e, "play");
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return media(e, "pause");
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return media(e, "ff");
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return media(e, "rw");
        }
        return super.dispatchKeyEvent(e);
    }

    private void moveCursor(int keyCode, int repeat) {
        if (!cursor.isShown2()) {
            showCursor(); // le premier appui fait juste réapparaître le curseur
            return;
        }
        showCursor();
        float step = (6 + Math.min(repeat, 16) * 2.5f) * density;
        float dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: dy = -step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = step; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = step; break;
        }
        float w = root.getWidth(), h = root.getHeight();
        float nx = Math.max(0, Math.min(w - 1, cursor.x + dx));
        float ny = Math.max(0, Math.min(h - 1, cursor.y + dy));
        // Bord de l'écran atteint : on fait défiler au lieu de bouger.
        float edge = 2 * density;
        float amount = 90 + Math.min(repeat, 10) * 12; // en px CSS
        float sx = 0, sy = 0;
        if (dy < 0 && ny <= edge) sy = -amount;
        if (dy > 0 && ny >= h - 1 - edge) sy = amount;
        if (dx < 0 && nx <= edge) sx = -amount;
        if (dx > 0 && nx >= w - 1 - edge) sx = amount;
        if (sx != 0 || sy != 0) scrollPage(nx, ny, sx, sy);
        cursor.x = nx;
        cursor.y = ny;
        cursor.invalidate();
        if (touchDownTime >= 0) sendTouch(MotionEvent.ACTION_MOVE);
    }

    /** Position en px écran, défilement en px CSS. */
    private void scrollPage(float x, float y, float sx, float sy) {
        if (customView != null) return;
        currentWebView().evaluateJavascript(String.format(java.util.Locale.US, SCROLL_JS,
                x / density, y / density, sx, sy), null);
    }

    private void scrollByScreen(int direction) {
        scrollPage(cursor.x, cursor.y, 0, direction * 0.8f * currentWebView().getHeight() / density);
    }

    private void sendTouch(int action) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownTime = now;
        } else if (touchDownTime < 0) {
            return;
        }
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, cursor.x, cursor.y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        touchTarget().dispatchTouchEvent(ev);
        ev.recycle();
        if (action == MotionEvent.ACTION_UP) touchDownTime = -1;
    }

    private boolean media(final KeyEvent e, String action) {
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return true;
        currentWebView().evaluateJavascript(String.format(MEDIA_JS, action), new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) {
                    // Vidéo dans une iframe : on laisse le lecteur gérer la touche.
                    touchTarget().dispatchKeyEvent(e);
                    touchTarget().dispatchKeyEvent(KeyEvent.changeAction(e, KeyEvent.ACTION_UP));
                }
            }
        });
        return true;
    }

    private void handleBack() {
        if (customView != null) {
            exitFullscreen();
        } else if (!popups.isEmpty()) {
            closePopup(currentWebView());
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else if (SystemClock.uptimeMillis() - lastBackPress < 2000) {
            finish();
        } else {
            lastBackPress = SystemClock.uptimeMillis();
            Toast.makeText(this, "Appuie encore sur Retour pour quitter", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCursor() {
        cursor.setShown(true);
        scheduleCursorHide();
    }

    private void scheduleCursorHide() {
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, CURSOR_HIDE_DELAY_MS);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentWebView().onResume(); // les pages sous un onglet restent en pause
    }

    @Override
    protected void onPause() {
        currentWebView().onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideCursor);
        root.removeAllViews();
        for (WebView popup : popups) popup.destroy();
        popups.clear();
        webView.destroy();
        super.onDestroy();
    }
}
