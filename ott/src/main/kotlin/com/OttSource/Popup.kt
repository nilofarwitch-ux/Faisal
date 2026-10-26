@file:Suppress(
    "DEPRECATION",
    "DEPRECATION_ERROR",
    "UNUSED_PARAMETER"
)

package com.OttSource

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast

import com.lagradost.cloudstream3.CommonActivity

object DesiExtensionPopup {

    private const val TELEGRAM_URL =
        "https://t.me/+yug8sqPegCQ1NWRl"

    private const val ICON_URL =
        "https://raw.githubusercontent.com/Faisal0786/Desi/main/Icons/images%20(1).jpeg"

    /*
     * =========================================================
     * POPUP STORAGE
     * =========================================================
     *
     * Popup frequency:
     * - Maximum 1 popup every 24 hours.
     * - Timestamp is stored in SharedPreferences.
     * - App close/open does NOT reset the timer.
     * - MainPage refresh does NOT reset the timer.
     * - Multiple MainPage requests do NOT create extra popups.
     */

    private const val POPUP_PREFS =
        "desi_extension_popup_prefs"

    private const val TELEGRAM_JOINED_KEY =
        "telegram_joined"

    /*
     * Existing key is intentionally preserved so already-installed
     * versions can continue using the previously stored timestamp.
     */
    private const val LAST_POPUP_SHOWN_KEY =
        "last_popup_shown_at"

    private const val TWENTY_FOUR_HOURS_MS =
        24L * 60L * 60L * 1000L

    private var currentDialog: Dialog? = null
    private var currentWebView: WebView? = null

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    /*
     * =========================================================
     * SHOW POPUP
     * =========================================================
     */

    fun show() {

        mainHandler.post {

            val activity =
                CommonActivity.activity
                    ?: return@post

            /*
             * Prevent duplicate dialogs while one is already open.
             */
            if (
                currentDialog?.isShowing == true
            ) {
                return@post
            }

            /*
             * Only one popup is allowed in a complete
             * 24-hour period.
             */
            if (
                !canShowPopup(activity)
            ) {
                return@post
            }

            /*
             * First-time state is still based on whether the user
             * has clicked the Telegram JOIN button before.
             *
             * This is separate from the 24-hour popup timer.
             */
            val firstTimePopup =
                !hasTelegramJoinClick(activity)

            /*
             * =================================================
             * CREATE DIALOG
             * =================================================
             */

            val dialog =
                Dialog(activity)

            currentDialog =
                dialog

            dialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
            )

            dialog.setCancelable(
                false
            )

            dialog.setCanceledOnTouchOutside(
                false
            )

            /*
             * =================================================
             * CREATE WEBVIEW
             * =================================================
             */

            val webView =
                WebView(activity)

            currentWebView =
                webView

            configureWebView(
                webView,
                dialog,
                firstTimePopup
            )

            /*
             * =================================================
             * ROOT CONTAINER
             * =================================================
             */

            val root =
                FrameLayout(activity)

            root.setBackgroundColor(
                Color.TRANSPARENT
            )

            /*
             * Popup dimensions are native dp.
             */

            val displayMetrics =
                activity.resources.displayMetrics

            val maxWidth =
                displayMetrics.widthPixels -
                    dp(
                        activity,
                        24f
                    )

            val maxHeight =
                displayMetrics.heightPixels -
                    dp(
                        activity,
                        24f
                    )

            val popupWidth =
                dp(
                    activity,
                    382f
                ).coerceAtMost(
                    maxWidth.coerceAtLeast(1)
                )

            val popupHeight =
                dp(
                    activity,
                    555f
                ).coerceAtMost(
                    maxHeight.coerceAtLeast(1)
                )

            val webParams =
                FrameLayout.LayoutParams(
                    popupWidth,
                    popupHeight
                )

            webParams.gravity =
                Gravity.CENTER

            root.addView(
                webView,
                webParams
            )

            dialog.setContentView(
                root
            )

            /*
             * =================================================
             * SHOW
             * =================================================
             */

            try {

                dialog.show()

                /*
                 * IMPORTANT:
                 *
                 * Save the timestamp only after the dialog has
                 * actually been shown successfully.
                 *
                 * From this moment, the popup is locked for 24h.
                 */
                markPopupShown(
                    activity
                )

            } catch (
                _: Throwable
            ) {

                currentWebView =
                    null

                currentDialog =
                    null

                return@post
            }

            configureDialogWindow(
                dialog,
                popupWidth,
                popupHeight
            )

            /*
             * =================================================
             * LOAD HTML
             * =================================================
             */

            val finalHtml =
                HTML
                    .replace(
                        "__ICON_URL__",
                        ICON_URL
                    )
                    .replace(
                        "__TELEGRAM_URL__",
                        TELEGRAM_URL
                    )
                    .replace(
                        "__FIRST_TIME_POPUP__",
                        firstTimePopup.toString()
                    )

            webView.loadDataWithBaseURL(
                "https://desi-extension.local/",
                finalHtml,
                "text/html",
                "UTF-8",
                null
            )

            /*
             * No native auto-close timer.
             * First-time popup HTML countdown controls the close.
             */

            /*
             * =================================================
             * CLEANUP
             * =================================================
             */

            dialog.setOnDismissListener {

                try {

                    webView.stopLoading()

                    webView.loadUrl(
                        "about:blank"
                    )

                    webView.removeJavascriptInterface(
                        "AndroidPopup"
                    )

                    webView.clearHistory()

                    webView.removeAllViews()

                    webView.destroy()

                } catch (
                    _: Throwable
                ) {
                }

                currentWebView =
                    null

                currentDialog =
                    null
            }
        }
    }

    /*
     * =========================================================
     * DIALOG WINDOW
     * =========================================================
     */

    private fun configureDialogWindow(
        dialog: Dialog,
        width: Int,
        height: Int
    ) {

        val window =
            dialog.window
                ?: return

        /*
         * Completely transparent native dialog background.
         */
        window.setBackgroundDrawable(
            ColorDrawable(
                Color.TRANSPARENT
            )
        )

        /*
         * Remove native window elevation/outline.
         */
        try {

            window.setElevation(
                0f
            )

        } catch (
            _: Throwable
        ) {
        }

        try {

            window.decorView.elevation =
                0f

            window.decorView.background =
                ColorDrawable(
                    Color.TRANSPARENT
                )

        } catch (
            _: Throwable
        ) {
        }

        /*
         * Android dim-behind.
         */
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        val params =
            window.attributes

        params.width =
            width

        params.height =
            height

        params.gravity =
            Gravity.CENTER

        params.dimAmount =
            0.48f

        window.attributes =
            params

        try {

            window.setDimAmount(
                0.48f
            )

        } catch (
            _: Throwable
        ) {
        }

        window.decorView.setBackgroundColor(
            Color.TRANSPARENT
        )
    }

    /*
     * =========================================================
     * WEBVIEW CONFIGURATION
     * =========================================================
     */

    private fun configureWebView(
        webView: WebView,
        dialog: Dialog,
        firstTimePopup: Boolean
    ) {

        /*
         * Transparent WebView so only the HTML card is rendered.
         */
        webView.setBackgroundColor(
            Color.TRANSPARENT
        )

        /*
         * Standard hardware rendering.
         */
        webView.setLayerType(
            WebView.LAYER_TYPE_NONE,
            null
        )

        /*
         * Settings.
         */
        webView.settings.apply {

            javaScriptEnabled =
                true

            domStorageEnabled =
                true

            databaseEnabled =
                true

            allowFileAccess =
                false

            allowContentAccess =
                false

            builtInZoomControls =
                false

            displayZoomControls =
                false

            setSupportZoom(
                false
            )

            useWideViewPort =
                false

            loadWithOverviewMode =
                false

            javaScriptCanOpenWindowsAutomatically =
                true
        }

        /*
         * Android bridge.
         */
        webView.addJavascriptInterface(
            AndroidPopupBridge(
                context =
                    webView.context,
                dialog =
                    dialog,
                firstTimePopup =
                    firstTimePopup
            ),
            "AndroidPopup"
        )

        webView.webChromeClient =
            WebChromeClient()

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    return openExternalUrl(
                        view,
                        request.url.toString()
                    )
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String
                ): Boolean {

                    return openExternalUrl(
                        view,
                        url
                    )
                }
            }
    }

    /*
     * =========================================================
     * TELEGRAM URL CHECK
     * =========================================================
     */

    private fun isTelegramUrl(
        url: String
    ): Boolean {

        return url
            .trim()
            .trimEnd('/')
            .equals(
                TELEGRAM_URL.trimEnd('/'),
                ignoreCase = true
            )
    }

    /*
     * =========================================================
     * EXTERNAL URL HANDLER
     * =========================================================
     */

    private fun openExternalUrl(
        view: WebView,
        url: String
    ): Boolean {

        if (
            !url.startsWith(
                "https://"
            ) &&
            !url.startsWith(
                "http://"
            )
        ) {
            return false
        }

        return try {

            if (
                isTelegramUrl(
                    url
                )
            ) {

                markTelegramJoined(
                    view.context
                )
            }

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )

            view.context.startActivity(
                intent
            )

            true

        } catch (
            _: Throwable
        ) {

            try {

                Toast.makeText(
                    view.context,
                    "Unable to open link",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (
                _: Throwable
            ) {
            }

            true
        }
    }

    /*
     * =========================================================
     * TELEGRAM STATE
     * =========================================================
     */

    private fun hasTelegramJoinClick(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                POPUP_PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                TELEGRAM_JOINED_KEY,
                false
            )
    }

    private fun markTelegramJoined(
        context: Context
    ) {

        /*
         * Telegram click status is completely separate from
         * popup display frequency.
         *
         * Clicking JOIN:
         * - does NOT reset the 24h timer
         * - does NOT create another popup slot
         */
        context
            .getSharedPreferences(
                POPUP_PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                TELEGRAM_JOINED_KEY,
                true
            )
            .apply()
    }

    /*
     * =========================================================
     * 24-HOUR POPUP LIMIT
     * =========================================================
     */

    private fun canShowPopup(
        context: Context
    ): Boolean {

        val prefs =
            context.getSharedPreferences(
                POPUP_PREFS,
                Context.MODE_PRIVATE
            )

        val lastShown =
            prefs.getLong(
                LAST_POPUP_SHOWN_KEY,
                0L
            )

        /*
         * No popup has ever been shown.
         */
        if (
            lastShown <= 0L
        ) {
            return true
        }

        val now =
            System.currentTimeMillis()

        /*
         * Popup becomes available only after a complete
         * 24-hour period has elapsed.
         *
         * This is a rolling 24h timer, NOT a calendar-day reset.
         */
        return now - lastShown >=
            TWENTY_FOUR_HOURS_MS
    }

    /*
     * =========================================================
     * MARK POPUP DISPLAYED
     * =========================================================
     */

    private fun markPopupShown(
        context: Context
    ) {

        val now =
            System.currentTimeMillis()

        context
            .getSharedPreferences(
                POPUP_PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                LAST_POPUP_SHOWN_KEY,
                now
            )
            .apply()
    }

    /*
     * =========================================================
     * DISMISS
     * =========================================================
     */

    private fun dismissDialog(
        dialog: Dialog?
    ) {

        mainHandler.post {

            try {

                if (
                    dialog?.isShowing == true
                ) {

                    dialog.dismiss()
                }

            } catch (
                _: Throwable
            ) {
            }
        }
    }

    /*
     * =========================================================
     * DP
     * =========================================================
     */

    private fun dp(
        context: Context,
        value: Float
    ): Int {

        return (
            value *
                context.resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    /*
     * =========================================================
     * JAVASCRIPT BRIDGE
     * =========================================================
     */

    private class AndroidPopupBridge(
        private val context: Context,
        private val dialog: Dialog,
        private val firstTimePopup: Boolean
    ) {

        @JavascriptInterface
        fun telegramClicked() {

            /*
             * First JOIN click permanently records that the user
             * interacted with Telegram.
             *
             * It does NOT modify the 24-hour popup timer.
             */
            if (
                firstTimePopup ||
                !hasTelegramJoinClick(
                    context
                )
            ) {

                markTelegramJoined(
                    context
                )
            }
        }

        @JavascriptInterface
        fun close() {

            Handler(
                Looper.getMainLooper()
            ).post {

                try {

                    if (
                        dialog.isShowing
                    ) {

                        dialog.dismiss()
                    }

                } catch (
                    _: Throwable
                ) {
                }
            }
        }
    }

    /*
     * =========================================================
     * HTML
     * =========================================================
     */

    private val HTML =
        """
<!DOCTYPE html>
<html lang="en">

<head>

<meta charset="UTF-8">

<meta
    name="viewport"
    content="
        width=device-width,
        initial-scale=1.0,
        maximum-scale=1.0,
        user-scalable=no
    "
>

<title>Desi Extension</title>


<style>

/* =========================================================
   RESET
   ========================================================= */

* {
    box-sizing: border-box;

    -webkit-tap-highlight-color:
        transparent;
}


html,
body {

    margin: 0;
    padding: 0;

    width: 100%;
    height: 100%;

    overflow: hidden;

    background:
        transparent;

    font-family:
        Arial,
        Helvetica,
        sans-serif;
}


body {

    display: flex;

    align-items:
        center;

    justify-content:
        center;
}


/* =========================================================
   WRAPPER
   ========================================================= */

.popup-wrapper {

    width: 100%;
    height: 100%;

    display: flex;

    align-items:
        center;

    justify-content:
        center;

    padding: 0;
}


/* =========================================================
   MAIN GLASS CARD
   ========================================================= */

.glass-card {

    position: relative;

    width: 100%;
    height: 100%;

    max-width: 100%;
    max-height: 100%;

    overflow: hidden;

    isolation: isolate;

    border-radius: 28px;


    background:

        radial-gradient(
            circle at 7% 8%,
            rgba(34,211,238,0.24),
            transparent 25%
        ),

        radial-gradient(
            circle at 38% 0%,
            rgba(59,130,246,0.10),
            transparent 24%
        ),

        radial-gradient(
            circle at 92% 9%,
            rgba(139,92,246,0.28),
            transparent 27%
        ),

        radial-gradient(
            circle at 100% 50%,
            rgba(236,72,153,0.20),
            transparent 25%
        ),

        radial-gradient(
            circle at 10% 92%,
            rgba(16,185,129,0.18),
            transparent 26%
        ),

        radial-gradient(
            circle at 82% 94%,
            rgba(245,158,11,0.16),
            transparent 24%
        ),

        radial-gradient(
            circle at 48% 42%,
            rgba(59,130,246,0.09),
            transparent 34%
        ),

        linear-gradient(
            145deg,
            #080B12 0%,
            #111827 38%,
            #17152A 68%,
            #0D1720 100%
        );


    backdrop-filter:
        blur(22px)
        saturate(155%);

    -webkit-backdrop-filter:
        blur(22px)
        saturate(155%);


    border:
        1px solid
        rgba(255,255,255,0.13);


    box-shadow:
        inset 0 1px 0
        rgba(255,255,255,0.08);


    color:
        white;


    animation:
        popupIn
        0.55s
        cubic-bezier(.2,.8,.2,1);
}


/* =========================================================
   MOVING AURORA
   ========================================================= */

.glass-card::before {

    content: "";

    position: absolute;

    inset: -45%;

    z-index: -3;


    background:

        radial-gradient(
            circle at 15% 14%,
            rgba(34,211,238,0.30),
            transparent 15%
        ),

        radial-gradient(
            circle at 50% 5%,
            rgba(59,130,246,0.20),
            transparent 16%
        ),

        radial-gradient(
            circle at 82% 10%,
            rgba(139,92,246,0.34),
            transparent 17%
        ),

        radial-gradient(
            circle at 93% 44%,
            rgba(236,72,153,0.28),
            transparent 17%
        ),

        radial-gradient(
            circle at 65% 68%,
            rgba(244,63,94,0.15),
            transparent 16%
        ),

        radial-gradient(
            circle at 60% 91%,
            rgba(245,158,11,0.24),
            transparent 17%
        ),

        radial-gradient(
            circle at 18% 81%,
            rgba(16,185,129,0.25),
            transparent 17%
        );


    filter:
        blur(48px);


    animation:
        auroraMove
        11s
        ease-in-out
        infinite
        alternate;
}


/* =========================================================
   SECOND COLOR LAYER
   ========================================================= */

.glass-card::after {

    content: "";

    position: absolute;

    inset: 0;

    z-index: -2;


    background:

        linear-gradient(
            115deg,

            rgba(34,211,238,0.055),
            transparent 16%,

            rgba(59,130,246,0.055),
            transparent 31%,

            rgba(139,92,246,0.065),
            transparent 46%,

            rgba(236,72,153,0.055),
            transparent 61%,

            rgba(16,185,129,0.045),
            transparent 76%,

            rgba(245,158,11,0.055)
        );


    mix-blend-mode:
        screen;

    pointer-events:
        none;
}


/* =========================================================
   GLASS REFLECTION
   ========================================================= */

.glass-reflection {

    position: absolute;

    top: -45%;

    left: -20%;

    width: 145%;

    height: 55%;


    background:

        linear-gradient(
            120deg,

            transparent 0%,

            rgba(255,255,255,0.015) 18%,

            rgba(34,211,238,0.035) 28%,

            rgba(255,255,255,0.075) 39%,

            rgba(167,139,250,0.035) 50%,

            rgba(244,114,182,0.025) 60%,

            transparent 72%
        );


    transform:
        rotate(10deg);


    filter:
        blur(16px);


    animation:
        reflectionMove
        8s
        ease-in-out
        infinite;
}


/* =========================================================
   INNER GLOW
   ========================================================= */

.inner-glow {

    position: absolute;

    inset: 1px;

    border-radius: 27px;

    pointer-events:
        none;


    background:

        linear-gradient(
            135deg,

            rgba(34,211,238,0.06),
            transparent 18%,

            rgba(59,130,246,0.045),
            transparent 34%,

            rgba(139,92,246,0.065),
            transparent 49%,

            rgba(236,72,153,0.055),
            transparent 64%,

            rgba(16,185,129,0.045),
            transparent 79%,

            rgba(245,158,11,0.055)
        );


    box-shadow:

        inset 0 0 32px
        rgba(34,211,238,0.035),

        inset 0 0 50px
        rgba(139,92,246,0.035),

        inset 0 0 70px
        rgba(236,72,153,0.025);
}


/* =========================================================
   CONTENT
   ========================================================= */

.content {

    position: relative;

    z-index: 5;

    width: 100%;
    height: 100%;


    display: flex;

    flex-direction: column;

    align-items:
        center;


    padding:
        20px
        20px
        13px;
}


/* =========================================================
   APP ICON
   ========================================================= */

.app-icon {

    position: relative;

    width: 76px;
    height: 76px;

    margin-top: 1px;
    margin-bottom: 8px;


    display: flex;

    align-items:
        center;

    justify-content:
        center;


    padding: 4px;


    border-radius: 50%;


    background:

        linear-gradient(
            135deg,

            rgba(34,211,238,0.22),

            rgba(59,130,246,0.20),

            rgba(139,92,246,0.23),

            rgba(236,72,153,0.16),

            rgba(245,158,11,0.14)
        );


    box-shadow:

        0 0 18px
        rgba(34,211,238,0.12),

        0 0 30px
        rgba(139,92,246,0.14),

        0 0 42px
        rgba(236,72,153,0.07);
}


.app-icon::before {

    content: "";

    position: absolute;

    inset: -5px;

    border-radius: 50%;


    border:
        1px solid
        rgba(255,255,255,0.14);


    box-shadow:

        0 0 12px
        rgba(34,211,238,0.15),

        0 0 21px
        rgba(139,92,246,0.12),

        0 0 29px
        rgba(236,72,153,0.08);


    animation:
        iconPulse
        3s
        ease-in-out
        infinite;
}


.app-icon img {

    width: 68px;
    height: 68px;


    display: block;

    object-fit:
        cover;

    object-position:
        center;


    border-radius: 50%;


    box-shadow:
        0 7px 20px
        rgba(0,0,0,0.45);
}


/* =========================================================
   STATUS
   ========================================================= */

.status {

    display: flex;

    align-items:
        center;

    justify-content:
        center;


    gap: 6px;


    margin-bottom: 5px;


    font-size: 8px;

    font-weight: 700;

    letter-spacing: 1.4px;


    color:
        rgba(255,255,255,0.50);
}


.status-dot {

    width: 6px;
    height: 6px;

    border-radius: 50%;


    background:
        #22D3EE;


    box-shadow:

        0 0 7px
        rgba(34,211,238,0.85),

        0 0 13px
        rgba(16,185,129,0.45);


    animation:
        dotPulse
        2s
        ease-in-out
        infinite;
}


/* =========================================================
   LIVE TIME
   ========================================================= */

.live-time {

    margin-bottom: 6px;


    font-size: 11px;

    letter-spacing: 1.8px;


    color:
        rgba(255,255,255,0.68);


    text-shadow:

        0 0 9px
        rgba(34,211,238,0.20);
}


/* =========================================================
   TITLE
   ========================================================= */

h1 {

    margin: 0;


    font-size: 27px;

    line-height: 1.08;

    font-weight: 800;


    letter-spacing:
        -0.5px;


    text-align:
        center;


    background:

        linear-gradient(
            90deg,

            #FFFFFF 0%,

            #67E8F9 20%,

            #60A5FA 38%,

            #A78BFA 57%,

            #F472B6 76%,

            #FB7185 88%,

            #FDBA74 100%
        );


    -webkit-background-clip:
        text;

    background-clip:
        text;

    -webkit-text-fill-color:
        transparent;


    filter:

        drop-shadow(
            0 0 10px
            rgba(34,211,238,0.10)
        )

        drop-shadow(
            0 0 15px
            rgba(139,92,246,0.10)
        );
}


/* =========================================================
   AI BADGE
   ========================================================= */

.ai-badge {

    display: flex;

    align-items:
        center;

    justify-content:
        center;


    gap: 5px;


    margin-top: 9px;


    padding:
        6px
        11px;


    border-radius:
        999px;


    font-size: 9px;

    font-weight: 700;

    letter-spacing:
        0.3px;


    color:
        rgba(255,255,255,0.94);


    background:

        linear-gradient(
            110deg,

            rgba(34,211,238,0.10),

            rgba(59,130,246,0.13),

            rgba(139,92,246,0.16),

            rgba(236,72,153,0.12),

            rgba(16,185,129,0.09)
        );


    border:
        1px solid
        rgba(255,255,255,0.11);


    box-shadow:

        0 5px 18px
        rgba(0,0,0,0.20),

        inset 0 0 15px
        rgba(255,255,255,0.025);
}


.ai-spark {

    font-size: 10px;


    background:

        linear-gradient(
            90deg,
            #22D3EE,
            #A78BFA,
            #F472B6,
            #FDBA74
        );


    -webkit-background-clip:
        text;

    background-clip:
        text;

    -webkit-text-fill-color:
        transparent;
}


/* =========================================================
   DESCRIPTION
   ========================================================= */

.description {

    width: 100%;

    max-width: 302px;


    margin:
        16px
        0
        0;


    text-align:
        center;


    line-height:
        1.42;


    font-size:
        12px;


    color:
        rgba(255,255,255,0.80);


    text-shadow:

        0 2px 8px
        rgba(0,0,0,0.60);
}


.description strong {

    color:
        rgba(255,255,255,0.94);

    font-weight:
        700;
}


/* =========================================================
   ACTION AREA
   ========================================================= */

.actions {

    width: 100%;

    max-width: 300px;


    display: flex;

    flex-direction: column;

    align-items:
        center;


    margin-top:
        14px;
}


/* =========================================================
   COMMON BUTTON
   ========================================================= */

.btn {

    width: 100%;


    display: flex;

    align-items:
        center;

    justify-content:
        center;


    text-align:
        center;


    border-radius:
        14px;


    font-family:
        Arial,
        Helvetica,
        sans-serif;


    font-size:
        12px;


    font-weight:
        800;


    letter-spacing:
        0.6px;


    text-decoration:
        none;


    outline:
        none;


    transition:
        transform 0.2s ease,
        box-shadow 0.2s ease,
        opacity 0.2s ease;
}


/* =========================================================
   TELEGRAM BUTTON
   ========================================================= */

.btn-telegram {

    min-height:
        40px;


    gap: 7px;


    color:
        white;


    background:

        linear-gradient(
            100deg,

            #06B6D4 0%,

            #3B82F6 24%,

            #6366F1 45%,

            #8B5CF6 64%,

            #D946EF 82%,

            #EC4899 100%
        );


    border:
        1px solid
        rgba(255,255,255,0.10);


    box-shadow:

        0 9px 25px
        rgba(59,130,246,0.22),

        0 0 22px
        rgba(34,211,238,0.08);


    position:
        relative;


    overflow:
        hidden;
}


.btn-telegram::before {

    content:
        "";


    position:
        absolute;


    top:
        0;


    left:
        -120%;


    width:
        80%;


    height:
        100%;


    background:

        linear-gradient(
            90deg,
            transparent,
            rgba(255,255,255,0.30),
            transparent
        );


    transform:
        skewX(-20deg);


    animation:
        buttonShine
        4s
        ease-in-out
        infinite;
}


.btn-telegram svg {

    position:
        relative;

    z-index:
        2;

    flex-shrink:
        0;
}


.btn-telegram {

    position:
        relative;
}


.btn-telegram::after {

    content:
        "JOIN TELEGRAM";


    position:
        absolute;

    inset:
        0;


    display:
        none;
}


.btn-telegram:active {

    transform:
        scale(0.97);
}


/* =========================================================
   CLOSE BUTTON
   ========================================================= */

.btn-close {

    min-height:
        33px;


    margin-top:
        8px;


    padding:
        8px
        10px;


    color:
        rgba(255,255,255,0.62);


    background:

        linear-gradient(
            120deg,

            rgba(255,255,255,0.045),

            rgba(59,130,246,0.055),

            rgba(139,92,246,0.07),

            rgba(236,72,153,0.045),

            rgba(16,185,129,0.035)
        );


    border:
        1px solid
        rgba(255,255,255,0.10);


    box-shadow:

        inset 0 0 16px
        rgba(255,255,255,0.015);
}


.btn-close:disabled {

    opacity:
        0.43;
}


.btn-close:not(:disabled):active {

    transform:
        scale(0.97);
}


/* =========================================================
   BOTTOM LINE
   ========================================================= */

.bottom-line {

    width:
        82%;


    height:
        3px;


    margin-top:
        9px;


    border-radius:
        999px;


    background:

        linear-gradient(
            90deg,

            #06B6D4 0%,

            #3B82F6 20%,

            #6366F1 38%,

            #8B5CF6 57%,

            #D946EF 74%,

            #EC4899 88%,

            #F59E0B 100%
        );


    box-shadow:

        0 0 10px
        rgba(34,211,238,0.15),

        0 0 18px
        rgba(139,92,246,0.12);
}


/* =========================================================
   ANIMATIONS
   ========================================================= */

@keyframes popupIn {

    from {

        opacity:
            0;


        transform:
            scale(0.92)
            translateY(18px);
    }


    to {

        opacity:
            1;


        transform:
            scale(1)
            translateY(0);
    }
}


@keyframes auroraMove {

    0% {

        transform:
            translate3d(-8%, -5%, 0)
            rotate(0deg)
            scale(1);
    }


    25% {

        transform:
            translate3d(5%, 3%, 0)
            rotate(2deg)
            scale(1.04);
    }


    50% {

        transform:
            translate3d(-5%, 8%, 0)
            rotate(-2deg)
            scale(1.08);
    }


    75% {

        transform:
            translate3d(8%, -4%, 0)
            rotate(3deg)
            scale(1.06);
    }


    100% {

        transform:
            translate3d(-7%, 2%, 0)
            rotate(2deg)
            scale(1.06);
    }
}


@keyframes reflectionMove {

    0% {

        transform:
            translateX(-30%)
            rotate(10deg);
    }


    50% {

        transform:
            translateX(30%)
            rotate(10deg);
    }


    100% {

        transform:
            translateX(70%)
            rotate(10deg);
    }
}


@keyframes iconPulse {

    0%,
    100% {

        transform:
            scale(1);

        opacity:
            0.60;
    }


    50% {

        transform:
            scale(1.07);

        opacity:
            1;
    }
}


@keyframes dotPulse {

    0%,
    100% {

        transform:
            scale(1);

        opacity:
            0.65;
    }


    50% {

        transform:
            scale(1.35);

        opacity:
            1;
    }
}


@keyframes buttonShine {

    0% {

        left:
            -120%;
    }


    45% {

        left:
            140%;
    }


    100% {

        left:
            140%;
    }
}


/* =========================================================
   SMALL SCREEN
   ========================================================= */

@media (max-width: 390px) {

    .content {

        padding:
            18px
            17px
            11px;
    }


    .app-icon {

        width:
            72px;

        height:
            72px;
    }


    .app-icon img {

        width:
            64px;

        height:
            64px;
    }


    h1 {

        font-size:
            25px;
    }


    .description {

        max-width:
            286px;

        font-size:
            11.5px;
    }


    .actions {

        max-width:
            286px;
    }


    .ai-badge {

        font-size:
            8.5px;
    }
}

</style>

</head>


<body>


<div class="popup-wrapper">

    <div
        class="glass-card"
        id="popup"
    >


        <!-- =================================================
             MOVING GLASS LIGHT
             ================================================= -->

        <div
            class="glass-reflection"
        ></div>


        <!-- =================================================
             INNER GLOW
             ================================================= -->

        <div
            class="inner-glow"
        ></div>


        <!-- =================================================
             CONTENT
             ================================================= -->

        <div
            class="content"
        >


            <!-- =================================================
                 ROUND ICON
                 ================================================= -->

            <div
                class="app-icon"
            >

                <img
                    src="__ICON_URL__"
                    alt="Desi Extension"
                >

            </div>


            <!-- =================================================
                 STATUS
                 ================================================= -->

            <div
                class="status"
            >

                <span
                    class="status-dot"
                ></span>

                DESI EXTENSION

            </div>


            <!-- =================================================
                 TIME
                 ================================================= -->

            <div
                class="live-time"
                id="liveTime"
            >
                Loading...
            </div>


            <!-- =================================================
                 TITLE
                 ================================================= -->

            <h1>
                Desi Extension
            </h1>


            <!-- =================================================
                 AI BADGE
                 ================================================= -->

            <div
                class="ai-badge"
            >

                <span
                    class="ai-spark"
                >
                    ✦
                </span>

                100% AI Coded &amp; Maintained

            </div>


            <!-- =================================================
                 DESCRIPTION
                 ================================================= -->

            <p
                class="description"
            >

                Built and maintained entirely with AI.

                Join our Telegram community for

                <strong>
                    plugin requests,
                    issue reporting,
                </strong>

                and suggestions to improve the extension.

            </p>


            <!-- =================================================
                 ACTIONS
                 ================================================= -->

            <div
                class="actions"
            >


                <!-- TELEGRAM -->

                <a
                    href="__TELEGRAM_URL__"
                    class="btn btn-telegram"
                    id="telegramBtn"
                >

                    <svg
                        width="18"
                        height="18"
                        viewBox="0 0 24 24"
                        fill="currentColor"
                        aria-hidden="true"
                    >

                        <path
                            d="
                                M21.7 3.2
                                18.5 20
                                c-.24 1.19-.9 1.49-1.82.94
                                l-5.01-3.69
                                -2.42 2.33
                                c-.27.27-.5.5-1.02.5
                                l.36-5.1
                                9.28-8.38
                                c.4-.36-.09-.56-.62-.2
                                L5.79 13.84
                                .84 12.29
                                c-1.08-.34-1.1-1.08.23-1.61
                                L20.45 3.02
                                c.89-.33 1.67.21 1.25.18
                                Z
                            "
                        />

                    </svg>

                    JOIN TELEGRAM

                </a>


                <!-- CLOSE -->

                <button
                    class="btn btn-close"
                    id="closeBtn"
                    disabled
                >

                    CLOSE (5)

                </button>


            </div>


            <!-- =================================================
                 BOTTOM LINE
                 ================================================= -->

            <div
                class="bottom-line"
            ></div>


        </div>

    </div>

</div>


<script>


/* =========================================================
   POPUP MODE
   ========================================================= */

/*
 * First popup:
 * - 5-second countdown
 * - auto close
 *
 * Returning popup:
 * - immediately closable
 *
 * Popup frequency is controlled by Kotlin SharedPreferences,
 * not by this JavaScript.
 */
const FIRST_TIME_POPUP =
    __FIRST_TIME_POPUP__;


/* =========================================================
   COUNTDOWN
   ========================================================= */

const CLOSE_DELAY =
    5000;


const startedAt =
    Date.now();


const deadline =
    startedAt +
    CLOSE_DELAY;


let countdownTimer =
    null;


const closeBtn =
    document.getElementById(
        "closeBtn"
    );


const popup =
    document.getElementById(
        "popup"
    );


function closePopup() {

    if (
        window.AndroidPopup &&
        typeof window.AndroidPopup.close ===
            "function"
    ) {

        window.AndroidPopup.close();

        return;
    }


    /* Browser fallback. */

    popup.style.transition =
        "opacity 0.30s ease, transform 0.30s ease";


    popup.style.opacity =
        "0";


    popup.style.transform =
        "scale(0.92) translateY(15px)";


    setTimeout(
        function () {

            popup.remove();

        },
        320
    );
}


function updateCountdown() {

    const remainingMs =
        Math.max(
            0,
            deadline -
                Date.now()
        );


    const secondsLeft =
        Math.ceil(
            remainingMs /
                1000
        );


    if (
        secondsLeft > 0
    ) {

        closeBtn.textContent =
            "CLOSE (" +
            secondsLeft +
            ")";


        closeBtn.disabled =
            true;

        return;
    }


    if (
        countdownTimer !== null
    ) {

        clearInterval(
            countdownTimer
        );

        countdownTimer =
            null;
    }


    closeBtn.textContent =
        "CLOSE";


    closeBtn.disabled =
        false;


    /*
     * First-ever popup closes automatically after 5 seconds.
     */
    closePopup();
}


if (
    FIRST_TIME_POPUP
) {

    updateCountdown();

    countdownTimer =
        setInterval(
            updateCountdown,
            100
        );

} else {

    /*
     * Returning popup:
     * immediately closable.
     */
    closeBtn.textContent =
        "CLOSE";


    closeBtn.disabled =
        false;
}


/* =========================================================
   CLOSE BUTTON
   ========================================================= */

closeBtn.addEventListener(
    "click",
    function () {

        if (
            closeBtn.disabled
        ) {
            return;
        }


        closePopup();

    }
);


/* =========================================================
   LIVE CLOCK
   ========================================================= */

function updateClock() {

    const now =
        new Date();


    const hours =
        String(
            now.getHours()
        ).padStart(
            2,
            "0"
        );


    const minutes =
        String(
            now.getMinutes()
        ).padStart(
            2,
            "0"
        );


    const seconds =
        String(
            now.getSeconds()
        ).padStart(
            2,
            "0"
        );


    document.getElementById(
        "liveTime"
    ).textContent =

        hours +
        ":" +
        minutes +
        ":" +
        seconds;
}


updateClock();


setInterval(
    updateClock,
    1000
);


/* =========================================================
   TELEGRAM CLICK
   ========================================================= */

const telegramBtn =
    document.getElementById(
        "telegramBtn"
    );


telegramBtn.addEventListener(
    "click",
    function () {

        /*
         * Telegram click only records the interaction state.
         *
         * It does NOT reset or extend the 24-hour popup timer.
         */
        try {

            if (
                window.AndroidPopup &&
                typeof window.AndroidPopup.telegramClicked ===
                    "function"
            ) {

                window.AndroidPopup.telegramClicked();
            }

        } catch (
            e
        ) {
        }
    }
);

</script>

</body>

</html>
        """.trimIndent()
}