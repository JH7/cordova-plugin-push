package com.adobe.phonegap.push

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput

/**
 * Push Handler Activity
 */
@Suppress("HardCodedStringLiteral")
@SuppressLint("LongLogTag", "LogConditional")
class PushHandlerActivity : Activity() {
  companion object {
    private const val TAG: String = "${PushPlugin.PREFIX_TAG} (PushHandlerActivity)"
  }

  /**
   * this activity will be started if the user touches a notification that we own.
   * We send it's data off to the push plugin for processing.
   * If needed, we boot up the main activity to kickstart the application.
   *
   * @param savedInstanceState
   *
   * @see android.app.Activity#onCreate(android.os.Bundle)
   */
  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.v(TAG, "onCreate")

    intent.extras?.let { extras ->
      val notId = extras.getInt(PushConstants.NOT_ID, 0)
      val callback = extras.getString(PushConstants.CALLBACK)
      var foreground = extras.getBoolean(PushConstants.FOREGROUND, true)
      val startOnBackground = extras.getBoolean(PushConstants.START_IN_BACKGROUND, false)
      val dismissed = extras.getBoolean(PushConstants.DISMISSED, false)

      FCMService().setNotification(notId, "")

      if (!startOnBackground) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(FCMService.getAppName(this), notId)
      }

      val hasInline = processPushBundle()

      if (!hasInline && Build.VERSION.SDK_INT < Build.VERSION_CODES.N && !startOnBackground) {
        foreground = true
      }

      Log.d(TAG, "Not ID: $notId")
      Log.d(TAG, "Callback: $callback")
      Log.d(TAG, "Foreground: $foreground")
      Log.d(TAG, "Start On Background: $startOnBackground")
      Log.d(TAG, "Dismissed: $dismissed")

      finish()

      if (!dismissed) {
        Log.d(TAG, "Is Push Plugin Active: ${PushPlugin.isActive}")

        if (!PushPlugin.isActive && foreground && !hasInline) {
          Log.d(TAG, "Force Main Activity Reload: Start on Background = False")
          forceMainActivityReload(false)
        } else {
          Log.d(TAG, "Force Main Activity Reload: Start on Background = True")
          forceMainActivityReload(true)
        }
      }
    }
  }

  private fun processPushBundle(): Boolean {
    /*
     * Takes the pushBundle extras from the intent,
     * and sends it through to the PushPlugin for processing.
     */
    var hasInline = false

    intent.extras?.let { extras ->
      extras.getBundle(PushConstants.PUSH_BUNDLE)?.apply {
        putBoolean(PushConstants.FOREGROUND, false)
        putBoolean(PushConstants.COLDSTART, !PushPlugin.isActive)
        putBoolean(PushConstants.DISMISSED, extras.getBoolean(PushConstants.DISMISSED))
        putString(
          PushConstants.ACTION_CALLBACK,
          extras.getString(PushConstants.CALLBACK)
        )
        remove(PushConstants.NO_CACHE)

        RemoteInput.getResultsFromIntent(intent)?.apply {
          val reply = getCharSequence(PushConstants.INLINE_REPLY).toString()
          Log.d(TAG, "Inline Reply: $reply")

          putString(PushConstants.INLINE_REPLY, reply)

          hasInline = true
        }

        PushPlugin.sendExtras(this)
      }
    }

    return hasInline
  }

  private fun forceMainActivityReload(startOnBackground: Boolean) {
    /*
     * Forces the main activity to re-launch if it's unloaded.
     */
    val launchIntent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)

    intent.extras?.let { extras ->
      launchIntent?.apply {
        extras.getBundle(PushConstants.PUSH_BUNDLE)?.apply {
          putExtras(this)
        }

        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addFlags(Intent.FLAG_FROM_BACKGROUND)
        putExtra(PushConstants.START_IN_BACKGROUND, startOnBackground)
      }
    }

    startActivity(launchIntent)
  }

  /**
   * On Resuming of Activity
   */
  override fun onResume() {
    super.onResume()

    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancelAll()
  }
}
