package com.universalgameai

import android.content.Context

object CrashLogger {

    private const val PREFS = "universal_game_ai_crash"
    private const val KEY_ERROR = "last_error"

    fun install(context: Context) {

        val appContext = context.applicationContext

        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            try {
                val message =
                    buildString {
                        append("Thread: ")
                        append(thread.name)
                        append("\n\n")

                        append(
                            throwable.stackTraceToString()
                        )

                        throwable.cause?.let { cause ->
                            append("\n\nCAUSE:\n")
                            append(
                                cause.stackTraceToString()
                            )
                        }
                    }

                appContext
                    .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putString(
                        KEY_ERROR,
                        message
                    )
                    .apply()

            } catch (_: Exception) {
                // Never allow crash logging to cause another crash.
            }

            previousHandler?.uncaughtException(
                thread,
                throwable
            )
        }
    }

    fun getLastError(
        context: Context
    ): String? {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_ERROR,
                null
            )
    }

    fun clear(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_ERROR)
            .apply()
    }
}
