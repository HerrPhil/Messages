package com.reference.implementation.messages.data.audit

import android.util.Log

class Audit {

    companion object Logger {
        fun createInstance(): Audit {
            return Audit()
        }
    }

    fun writeLog(message: String) {
        // FUTURE FEATURE - write the message to an audit logger repository or SIEM.
        Log.d("audit", message)
    }
}
